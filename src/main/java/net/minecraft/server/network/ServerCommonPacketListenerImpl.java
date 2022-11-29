package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
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
import org.galemc.gale.configuration.GaleGlobalConfiguration;
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
    public final Connection connection; // Paper
    private long keepAliveTime = Util.getMillis(); // Paper
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private LongList keepAlives = new LongArrayList(); // Gale - Purpur - send multiple keep-alive packets
    private int latency;
    private volatile boolean suspendFlushingOnServerThread = false;
    public final java.util.Map<java.util.UUID, net.kyori.adventure.resource.ResourcePackCallback> packCallbacks = new java.util.concurrent.ConcurrentHashMap<>(); // Paper - adventure resource pack callbacks
    // Gale start - Purpur - send multiple keep-alive packets
    private static final long KEEPALIVE_LIMIT_IN_SECONDS = Long.getLong("paper.playerconnection.keepalive", 30); // Paper - provide property to set keepalive limit
    private static final long KEEPALIVE_LIMIT = KEEPALIVE_LIMIT_IN_SECONDS * 1000;
    // Gale end - Purpur - send multiple keep-alive packets
    protected static final ResourceLocation MINECRAFT_BRAND = new ResourceLocation("brand"); // Paper - Brand support

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
        // Paper start - Fix kick event leave message not being sent
        this.onDisconnect(reason, null);
    }
    public void onDisconnect(Component reason, @Nullable net.kyori.adventure.text.Component quitMessage) {
        // Paper end - Fix kick event leave message not being sent
        if (this.isSingleplayerOwner()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }

    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packet) {
        //PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel()); // CraftBukkit // Paper - handle ServerboundKeepAlivePacket async
        // Gale start - Purpur - send multiple keep-alive packets
        if (GaleGlobalConfiguration.get().misc.keepalive.sendMultiple) {
            long id = packet.getId();
            if (!this.keepAlives.isEmpty() && this.keepAlives.contains(id)) {
                int ping = (int) (Util.getMillis() - id);
                this.latency = (this.latency * 3 + ping) / 4;
                this.keepAlives.clear(); // We got a valid response, let's roll with it and forget the rest
            }
        } else {
            // Gale end - Purpur - send multiple keep-alive packets
        if (this.keepAlivePending && packet.getId() == this.keepAliveChallenge) {
            int i = (int) (Util.getMillis() - this.keepAliveTime);

            this.latency = (this.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            // Paper start - This needs to be handled on the main thread for plugins
            server.submit(() -> {
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE, org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT); // Paper - kick event cause
            });
            // Paper end - This needs to be handled on the main thread for plugins
        }
        } // Gale - Purpur - send multiple keep-alive packets

    }

    @Override
    public void handlePong(ServerboundPongPacket packet) {}

    // CraftBukkit start
    private static final ResourceLocation CUSTOM_REGISTER = new ResourceLocation("register");
    private static final ResourceLocation CUSTOM_UNREGISTER = new ResourceLocation("unregister");

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        // Paper start - Brand support
        if (packet.payload() instanceof net.minecraft.network.protocol.common.custom.BrandPayload brandPayload) {
            this.player.clientBrandName = brandPayload.brand();
        }
        // Paper end - Brand support
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
                this.disconnect("Invalid payload REGISTER!", org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        } else if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_UNREGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().removeChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t unregister custom payload", ex);
                this.disconnect("Invalid payload UNREGISTER!", org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        } else {
            try {
                byte[] data = new byte[payload.readableBytes()];
                payload.readBytes(data);
                // Paper start - Brand support; Retain this incase upstream decides to 'break' the new mechanism in favour of backwards compat...
                if (identifier.equals(MINECRAFT_BRAND)) {
                    try {
                        this.player.clientBrandName = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.copiedBuffer(data)).readUtf(256);
                    } catch (StringIndexOutOfBoundsException ex) {
                        this.player.clientBrandName = "illegal";
                    }
                }
                // Paper end - Brand support
                this.cserver.getMessenger().dispatchIncomingMessage(this.player.getBukkitEntity(), identifier.toString(), data);
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t dispatch custom payload", ex);
                this.disconnect("Invalid custom payload!", org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        }

    }

    public final boolean isDisconnected() {
        return (!this.player.joining && !this.connection.isConnected()) || this.processedDisconnect; // Paper - Fix duplication bugs
    }
    // CraftBukkit end

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, (BlockableEventLoop) this.server);
        if (packet.action() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Disconnecting {} due to resource pack {} rejection", this.playerProfile().getName(), packet.id());
            this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"), org.bukkit.event.player.PlayerKickEvent.Cause.RESOURCE_PACK_REJECTION); // Paper - kick event cause
        }
        // Paper start - adventure pack callbacks
        // call the callbacks before the previously-existing event so the event has final say
        final net.kyori.adventure.resource.ResourcePackCallback callback;
        if (packet.action().isTerminal()) {
            callback = this.packCallbacks.remove(packet.id());
        } else {
            callback = this.packCallbacks.get(packet.id());
        }
        if (callback != null) {
            callback.packEventReceived(packet.id(), net.kyori.adventure.resource.ResourcePackStatus.valueOf(packet.action().name()), this.getCraftPlayer());
        }
        // Paper end
        // Paper start - store last pack status
        PlayerResourcePackStatusEvent.Status packStatus = PlayerResourcePackStatusEvent.Status.values()[packet.action().ordinal()];
        player.getBukkitEntity().resourcePackStatus = packStatus;
        this.cserver.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(this.getCraftPlayer(), packet.id(), packStatus)); // CraftBukkit
        // Paper end - store last pack status

    }

    protected void keepConnectionAlive() {
        // Paper start - give clients a longer time to respond to pings as per pre 1.12.2 timings
        // This should effectively place the keepalive handling back to "as it was" before 1.12.2
        long currentTime = Util.getMillis();
        long elapsedTime = currentTime - this.keepAliveTime;

        // Gale start - Purpur - send multiple keep-alive packets
        if (GaleGlobalConfiguration.get().misc.keepalive.sendMultiple) {
            if (elapsedTime >= 1000L) { // 1 second
                if (!this.processedDisconnect && this.keepAlives.size() >= KEEPALIVE_LIMIT_IN_SECONDS) {
                    LOGGER.warn("{} was kicked due to keepalive timeout!", this.player.getName());
                    disconnect(Component.translatable("disconnect.timeout"), org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT);
                } else {
                    this.keepAliveTime = currentTime; // hijack this field for 1 second intervals
                    this.keepAlives.add(currentTime); // currentTime is ID
                    send(new ClientboundKeepAlivePacket(currentTime));
                }
            }
        } else {
            // Gale end - Purpur - send multiple keep-alive packets
        if (this.keepAlivePending) {
            if (!this.processedDisconnect && elapsedTime >= KEEPALIVE_LIMIT) { // check keepalive limit, don't fire if already disconnected
                ServerGamePacketListenerImpl.LOGGER.warn("{} was kicked due to keepalive timeout!", this.player.getScoreboardName()); // more info
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE, org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT); // Paper - kick event cause
            }
        } else {
            if (elapsedTime >= 15000L) { // 15 seconds
                this.keepAlivePending = true;
                this.keepAliveTime = currentTime;
                this.keepAliveChallenge = currentTime;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }
        } // Gale - Purpur - send multiple keep-alive packets
        // Paper end - give clients a longer time to respond to pings as per pre 1.12.2 timings

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
    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public void disconnect(String s) { // Paper
        this.disconnect(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(s), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); // Paper
    }
    // CraftBukkit end

    // Paper start - kick event cause
    public void disconnect(String s, PlayerKickEvent.Cause cause) {
        this.disconnect(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(s), cause);
    }

    // Paper start
    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public void disconnect(final Component reason) {
        this.disconnect(io.papermc.paper.adventure.PaperAdventure.asAdventure(reason), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
    }

    public void disconnect(final Component reason, PlayerKickEvent.Cause cause) {
        this.disconnect(io.papermc.paper.adventure.PaperAdventure.asAdventure(reason), cause);
    }

    public void disconnect(net.kyori.adventure.text.Component reason, org.bukkit.event.player.PlayerKickEvent.Cause cause) { // Paper - kick event cause
        // Paper end
        // CraftBukkit start - fire PlayerKickEvent
        if (this.processedDisconnect) {
            return;
        }
        if (!this.cserver.isPrimaryThread()) {
            Waitable waitable = new Waitable() {
                @Override
                protected Object evaluate() {
                    ServerCommonPacketListenerImpl.this.disconnect(reason, cause); // Paper - adventure
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

        net.kyori.adventure.text.Component leaveMessage = net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? this.player.getBukkitEntity().displayName() : net.kyori.adventure.text.Component.text(this.player.getScoreboardName())); // Paper - Adventure

        PlayerKickEvent event = new PlayerKickEvent(this.player.getBukkitEntity(), reason, leaveMessage, cause); // Paper - adventure

        if (this.cserver.getServer().isRunning()) {
            this.cserver.getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // Do not kick the player
            return;
        }
        // Send the possibly modified leave message
        final Component ichatbasecomponent = io.papermc.paper.adventure.PaperAdventure.asVanilla(event.reason()); // Paper - Adventure
        // CraftBukkit end

        this.player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.KICKED; // Paper - Add API for quit reason
        this.connection.send(new ClientboundDisconnectPacket(ichatbasecomponent), PacketSendListener.thenRun(() -> {
            this.connection.disconnect(ichatbasecomponent);
        }));
        this.onDisconnect(ichatbasecomponent, event.leaveMessage()); // CraftBukkit - fire quit instantly // Paper - use kick event leave message
        this.connection.setReadOnly();
        MinecraftServer minecraftserver = this.server;
        Connection networkmanager = this.connection;

        Objects.requireNonNull(this.connection);
        // CraftBukkit - Don't wait
        minecraftserver.scheduleOnMain(networkmanager::handleDisconnection); // Paper
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
