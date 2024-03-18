package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;

// CraftBukkit start
import io.netty.buffer.ByteBuf;
import java.util.concurrent.ExecutionException;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
// CraftBukkit end

public abstract class ServerCommonPacketListenerImpl implements ServerCommonPacketListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final Component TIMEOUT_DISCONNECTION_MESSAGE = Component.translatable("disconnect.timeout");
    protected final MinecraftServer server;
    protected final Connection connection;
    private long keepAliveTime;
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private int latency;
    private volatile boolean suspendFlushingOnServerThread = false;

    public ServerCommonPacketListenerImpl(MinecraftServer minecraftserver, Connection networkmanager, CommonListenerCookie commonlistenercookie, ServerPlayer player) { // CraftBukkit
        this.server = minecraftserver;
        this.connection = networkmanager;
        this.keepAliveTime = Util.getMillis();
        this.latency = commonlistenercookie.latency();
        // CraftBukkit start - add fields and methods
        this.player = player;
        this.cserver = minecraftserver.server;
    }
    protected final ServerPlayer player;
    protected final org.bukkit.craftbukkit.CraftServer cserver;
    public boolean processedDisconnect;

    public CraftPlayer getCraftPlayer() {
        return (this.player == null) ? null : (CraftPlayer) this.player.getBukkitEntity();
        // CraftBukkit end
    }

    @Override
    public void onDisconnect(Component reason) {
        if (this.isSingleplayerOwner()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }

    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel()); // CraftBukkit
        if (this.keepAlivePending && packet.getId() == this.keepAliveChallenge) {
            int i = (int) (Util.getMillis() - this.keepAliveTime);

            this.latency = (this.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE);
        }

    }

    @Override
    public void handlePong(ServerboundPongPacket packet) {}

    // CraftBukkit start
    private static final ResourceLocation CUSTOM_REGISTER = new ResourceLocation("register");
    private static final ResourceLocation CUSTOM_UNREGISTER = new ResourceLocation("unregister");

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        if (!(packet.payload() instanceof ServerboundCustomPayloadPacket.UnknownPayload)) {
            return;
        }
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        ResourceLocation identifier = packet.payload().id();
        ByteBuf payload = ((ServerboundCustomPayloadPacket.UnknownPayload)packet.payload()).data();

        if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_REGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().addChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t register custom payload", ex);
                this.disconnect("Invalid payload REGISTER!");
            }
        } else if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_UNREGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().removeChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t unregister custom payload", ex);
                this.disconnect("Invalid payload UNREGISTER!");
            }
        } else {
            try {
                byte[] data = new byte[payload.readableBytes()];
                payload.readBytes(data);
                this.cserver.getMessenger().dispatchIncomingMessage(this.player.getBukkitEntity(), identifier.toString(), data);
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t dispatch custom payload", ex);
                this.disconnect("Invalid custom payload!");
            }
        }

    }

    public final boolean isDisconnected() {
        return !this.player.joining && !this.connection.isConnected();
    }
    // CraftBukkit end

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, (BlockableEventLoop) this.server);
        if (packet.action() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Disconnecting {} due to resource pack {} rejection", this.playerProfile().getName(), packet.id());
            this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"));
        }
        this.cserver.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(this.getCraftPlayer(), packet.id(), PlayerResourcePackStatusEvent.Status.values()[packet.action().ordinal()])); // CraftBukkit

    }

    protected void keepConnectionAlive() {
        this.server.getProfiler().push("keepAlive");
        long i = Util.getMillis();

        if (i - this.keepAliveTime >= 25000L) { // CraftBukkit
            if (this.keepAlivePending) {
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE);
            } else {
                this.keepAlivePending = true;
                this.keepAliveTime = i;
                this.keepAliveChallenge = i;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }

        this.server.getProfiler().pop();
    }

    public void suspendFlushing() {
        this.suspendFlushingOnServerThread = true;
    }

    public void resumeFlushing() {
        this.suspendFlushingOnServerThread = false;
        this.connection.flushChannel();
    }

    public void send(Packet<?> packet) {
        this.send(packet, (PacketSendListener) null);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
        // CraftBukkit start
        if (packet == null || this.processedDisconnect) { // Spigot
            return;
        } else if (packet instanceof ClientboundSetDefaultSpawnPositionPacket) {
            ClientboundSetDefaultSpawnPositionPacket packet6 = (ClientboundSetDefaultSpawnPositionPacket) packet;
            this.player.compassTarget = CraftLocation.toBukkit(packet6.pos, this.getCraftPlayer().getWorld());
        }
        // CraftBukkit end
        boolean flag = !this.suspendFlushingOnServerThread || !this.server.isSameThread();

        try {
            this.connection.send(packet, callbacks, flag);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Packet being sent");

            crashreportsystemdetails.setDetail("Packet class", () -> {
                return packet.getClass().getCanonicalName();
            });
            throw new ReportedException(crashreport);
        }
    }

    // CraftBukkit start
    @Deprecated
    public void disconnect(Component reason) {
        this.disconnect(CraftChatMessage.fromComponent(reason));
    }
    // CraftBukkit end

    public void disconnect(String s) {
        // CraftBukkit start - fire PlayerKickEvent
        if (this.processedDisconnect) {
            return;
        }
        if (!this.cserver.isPrimaryThread()) {
            Waitable waitable = new Waitable() {
                @Override
                protected Object evaluate() {
                    ServerCommonPacketListenerImpl.this.disconnect(s);
                    return null;
                }
            };

            this.server.processQueue.add(waitable);

            try {
                waitable.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        String leaveMessage = ChatFormatting.YELLOW + this.player.getScoreboardName() + " left the game.";

        PlayerKickEvent event = new PlayerKickEvent(this.player.getBukkitEntity(), s, leaveMessage);

        if (this.cserver.getServer().isRunning()) {
            this.cserver.getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // Do not kick the player
            return;
        }
        this.player.kickLeaveMessage = event.getLeaveMessage(); // CraftBukkit - SPIGOT-3034: Forward leave message to PlayerQuitEvent
        // Send the possibly modified leave message
        final Component ichatbasecomponent = CraftChatMessage.fromString(event.getReason(), true)[0];
        // CraftBukkit end

        this.connection.send(new ClientboundDisconnectPacket(ichatbasecomponent), PacketSendListener.thenRun(() -> {
            this.connection.disconnect(ichatbasecomponent);
        }));
        this.onDisconnect(ichatbasecomponent); // CraftBukkit - fire quit instantly
        this.connection.setReadOnly();
        MinecraftServer minecraftserver = this.server;
        Connection networkmanager = this.connection;

        Objects.requireNonNull(this.connection);
        // CraftBukkit - Don't wait
        minecraftserver.wrapRunnable(networkmanager::handleDisconnection);
    }

    protected boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.playerProfile());
    }

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    protected CommonListenerCookie createCookie(ClientInformation syncedOptions) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, syncedOptions);
    }
}
