package net.minecraft.server.network;

import com.google.common.primitives.Ints;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivateKey;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import net.minecraft.CrashReportCategory;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerLoginPacketListener;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.Validate;
import org.galemc.gale.configuration.GaleGlobalConfiguration;
import org.slf4j.Logger;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
// CraftBukkit end

public class ServerLoginPacketListenerImpl implements ServerLoginPacketListener, TickablePacketListener {

    private static final AtomicInteger UNIQUE_THREAD_ID = new AtomicInteger(0);
    static final Logger LOGGER = LogUtils.getLogger();
    private static final java.util.concurrent.ExecutorService authenticatorPool = java.util.concurrent.Executors.newCachedThreadPool(new com.google.common.util.concurrent.ThreadFactoryBuilder().setNameFormat("User Authenticator #%d").setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER)).build()); // Paper - Cache authenticator threads
    private static final int MAX_TICKS_BEFORE_LOGIN = 600;
    private static final Component DISCONNECT_UNEXPECTED_QUERY = Component.translatable("multiplayer.disconnect.unexpected_query_response");
    private final byte[] challenge;
    final MinecraftServer server;
    public final Connection connection;
    public volatile ServerLoginPacketListenerImpl.State state;
    private int tick;
    @Nullable
    String requestedUsername;
    @Nullable
    public GameProfile authenticatedProfile; // Paper - public
    private final String serverId;
    private ServerPlayer player; // CraftBukkit
    public boolean iKnowThisMayNotBeTheBestIdeaButPleaseDisableUsernameValidation = false; // Paper - username validation overriding
    private int velocityLoginMessageId = -1; // Paper - Add Velocity IP Forwarding Support

    public ServerLoginPacketListenerImpl(MinecraftServer server, Connection connection) {
        this.state = ServerLoginPacketListenerImpl.State.HELLO;
        this.serverId = "";
        this.server = server;
        this.connection = connection;
        this.challenge = Ints.toByteArray(RandomSource.create().nextInt());
    }

    @Override
    public void tick() {
        // Paper start - Do not allow logins while the server is shutting down
        if (!MinecraftServer.getServer().isRunning()) {
            this.disconnect(org.bukkit.craftbukkit.util.CraftChatMessage.fromString(org.spigotmc.SpigotConfig.restartMessage)[0]);
            return;
        }
        // Paper end - Do not allow logins while the server is shutting down
        if (this.state == ServerLoginPacketListenerImpl.State.VERIFYING) {
            if (this.connection.isConnected()) { // Paper - prevent logins to be processed even though disconnect was called
            this.verifyLoginAndFinishConnectionSetup((GameProfile) Objects.requireNonNull(this.authenticatedProfile));
            } // Paper - prevent logins to be processed even though disconnect was called
        }

        if (this.state == ServerLoginPacketListenerImpl.State.WAITING_FOR_DUPE_DISCONNECT && !this.isPlayerAlreadyInWorld((GameProfile) Objects.requireNonNull(this.authenticatedProfile))) {
            this.finishLoginAndWaitForClient(this.authenticatedProfile);
        }

        if (this.tick++ == 600) {
            this.disconnect(Component.translatable("multiplayer.disconnect.slow_login"));
        }

    }

    // CraftBukkit start
    @Deprecated
    public void disconnect(String s) {
        this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(s))); // Paper - Fix hex colors not working in some kick messages
    }
    // CraftBukkit end

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected();
    }

    public void disconnect(Component reason) {
        try {
            ServerLoginPacketListenerImpl.LOGGER.info("Disconnecting {}: {}", this.getUserName(), reason.getString());
            this.connection.send(new ClientboundLoginDisconnectPacket(reason));
            this.connection.disconnect(reason);
        } catch (Exception exception) {
            ServerLoginPacketListenerImpl.LOGGER.error("Error whilst disconnecting player", exception);
        }

    }

    private boolean isPlayerAlreadyInWorld(GameProfile profile) {
        return this.server.getPlayerList().getPlayer(profile.getId()) != null;
    }

    @Override
    public void onDisconnect(Component reason) {
        // Gale start - Pufferfish - do not log disconnections with null id
        if (!GaleGlobalConfiguration.get().logToConsole.nullIdDisconnections && this.authenticatedProfile != null && this.authenticatedProfile.getId() == null) {
            var reasonString = reason.getString();
            if ("Disconnected".equals(reasonString) || Component.translatable("multiplayer.disconnect.generic").getString().equals(reasonString)) {
                return;
            }
        }
        // Gale end - Pufferfish - do not log disconnections with null id
        ServerLoginPacketListenerImpl.LOGGER.info("{} lost connection: {}", this.getUserName(), reason.getString());
    }

    public String getUserName() {
        String s = this.connection.getLoggableAddress(this.server.logIPs());

        return this.requestedUsername != null ? this.requestedUsername + " (" + s + ")" : s;
    }

    @Override
    public void handleHello(ServerboundHelloPacket packet) {
        Validate.validState(this.state == ServerLoginPacketListenerImpl.State.HELLO, "Unexpected hello packet", new Object[0]);
        if (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode() && io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.performUsernameValidation && !this.iKnowThisMayNotBeTheBestIdeaButPleaseDisableUsernameValidation) Validate.validState(Player.isValidUsername(packet.name()), "Invalid characters in username", new Object[0]); // Paper - config username validation
        this.requestedUsername = packet.name();
        GameProfile gameprofile = this.server.getSingleplayerProfile();

        if (gameprofile != null && this.requestedUsername.equalsIgnoreCase(gameprofile.getName())) {
            this.startClientVerification(gameprofile);
        } else {
            if (this.server.usesAuthentication() && !this.connection.isMemoryConnection()) {
                this.state = ServerLoginPacketListenerImpl.State.KEY;
                this.connection.send(new ClientboundHelloPacket("", this.server.getKeyPair().getPublic().getEncoded(), this.challenge));
            } else {
                // Paper start - Add Velocity IP Forwarding Support
                if (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled) {
                    this.velocityLoginMessageId = java.util.concurrent.ThreadLocalRandom.current().nextInt();
                    net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                    buf.writeByte(com.destroystokyo.paper.proxy.VelocityProxy.MAX_SUPPORTED_FORWARDING_VERSION);
                    net.minecraft.network.protocol.login.ClientboundCustomQueryPacket packet1 = new net.minecraft.network.protocol.login.ClientboundCustomQueryPacket(this.velocityLoginMessageId, new net.minecraft.network.protocol.login.ClientboundCustomQueryPacket.PlayerInfoChannelPayload(com.destroystokyo.paper.proxy.VelocityProxy.PLAYER_INFO_CHANNEL, buf));
                    this.connection.send(packet1);
                    return;
                }
                // Paper end - Add Velocity IP Forwarding Support
                // CraftBukkit start
                // Paper start - Cache authenticator threads
                authenticatorPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            GameProfile gameprofile = ServerLoginPacketListenerImpl.this.createOfflineProfile(ServerLoginPacketListenerImpl.this.requestedUsername); // Spigot

                            gameprofile = ServerLoginPacketListenerImpl.this.callPlayerPreLoginEvents(gameprofile); // Paper - Add more fields to AsyncPlayerPreLoginEvent
                            ServerLoginPacketListenerImpl.LOGGER.info("UUID of player {} is {}", gameprofile.getName(), gameprofile.getId());
                            ServerLoginPacketListenerImpl.this.startClientVerification(gameprofile);
                        } catch (Exception ex) {
                            ServerLoginPacketListenerImpl.this.disconnect("Failed to verify username!");
                            ServerLoginPacketListenerImpl.this.server.server.getLogger().log(java.util.logging.Level.WARNING, "Exception verifying " + ServerLoginPacketListenerImpl.this.requestedUsername, ex);
                        }
                    }
                });
                // Paper end - Cache authenticator threads
                // CraftBukkit end
            }

        }
    }

    void startClientVerification(GameProfile profile) {
        this.authenticatedProfile = profile;
        this.state = ServerLoginPacketListenerImpl.State.VERIFYING;
    }

    private void verifyLoginAndFinishConnectionSetup(GameProfile profile) {
        PlayerList playerlist = this.server.getPlayerList();
        // CraftBukkit start - fire PlayerLoginEvent
        this.player = playerlist.canPlayerLogin(this, profile); // CraftBukkit

        if (this.player == null) {
            // this.disconnect(ichatbasecomponent);
            // CraftBukkit end
        } else {
            if (this.server.getCompressionThreshold() >= 0 && !this.connection.isMemoryConnection()) {
                this.connection.send(new ClientboundLoginCompressionPacket(this.server.getCompressionThreshold()), PacketSendListener.thenRun(() -> {
                    this.connection.setupCompression(this.server.getCompressionThreshold(), true);
                }));
            }

            boolean flag = playerlist.disconnectAllPlayersWithProfile(profile, this.player); // CraftBukkit - add player reference

            if (flag) {
                this.state = ServerLoginPacketListenerImpl.State.WAITING_FOR_DUPE_DISCONNECT;
            } else {
                this.finishLoginAndWaitForClient(profile);
            }
        }

    }

    private void finishLoginAndWaitForClient(GameProfile profile) {
        this.state = ServerLoginPacketListenerImpl.State.PROTOCOL_SWITCHING;
        this.connection.send(new ClientboundGameProfilePacket(profile));
    }

    @Override
    public void handleKey(ServerboundKeyPacket packet) {
        Validate.validState(this.state == ServerLoginPacketListenerImpl.State.KEY, "Unexpected key packet", new Object[0]);

        final String s;

        try {
            PrivateKey privatekey = this.server.getKeyPair().getPrivate();

            if (!packet.isChallengeValid(this.challenge, privatekey)) {
                throw new IllegalStateException("Protocol error");
            }

            SecretKey secretkey = packet.getSecretKey(privatekey);
            // Paper start - Use Velocity cipher
//            Cipher cipher = Crypt.getCipher(2, secretkey);
//            Cipher cipher1 = Crypt.getCipher(1, secretkey);
            // Paper end - Use Velocity cipher

            s = (new BigInteger(Crypt.digestData("", this.server.getKeyPair().getPublic(), secretkey))).toString(16);
            this.state = ServerLoginPacketListenerImpl.State.AUTHENTICATING;
            this.connection.setupEncryption(secretkey); // Paper - Use Velocity cipher
        } catch (CryptException cryptographyexception) {
            throw new IllegalStateException("Protocol error", cryptographyexception);
        }

        // Paper start - Cache authenticator threads
        authenticatorPool.execute(new Runnable() {
            public void run() {
                String s1 = (String) Objects.requireNonNull(ServerLoginPacketListenerImpl.this.requestedUsername, "Player name not initialized");

                try {
                    ProfileResult profileresult = ServerLoginPacketListenerImpl.this.server.getSessionService().hasJoinedServer(s1, s, this.getAddress());

                    if (profileresult != null) {
                        GameProfile gameprofile = profileresult.profile();

                        // CraftBukkit start - fire PlayerPreLoginEvent
                        if (!ServerLoginPacketListenerImpl.this.connection.isConnected()) {
                            return;
                        }
                        gameprofile = ServerLoginPacketListenerImpl.this.callPlayerPreLoginEvents(gameprofile); // Paper - Add more fields to AsyncPlayerPreLoginEvent
                        // CraftBukkit end
                        ServerLoginPacketListenerImpl.LOGGER.info("UUID of player {} is {}", gameprofile.getName(), gameprofile.getId());
                        ServerLoginPacketListenerImpl.this.startClientVerification(gameprofile);
                    } else if (ServerLoginPacketListenerImpl.this.server.isSingleplayer()) {
                        ServerLoginPacketListenerImpl.LOGGER.warn("Failed to verify username but will let them in anyway!");
                        ServerLoginPacketListenerImpl.this.startClientVerification(ServerLoginPacketListenerImpl.this.createOfflineProfile(s1)); // Spigot
                    } else {
                        ServerLoginPacketListenerImpl.this.disconnect(Component.translatable("multiplayer.disconnect.unverified_username"));
                        ServerLoginPacketListenerImpl.LOGGER.error("Username '{}' tried to join with an invalid session", s1);
                    }
                } catch (AuthenticationUnavailableException authenticationunavailableexception) {
                    if (ServerLoginPacketListenerImpl.this.server.isSingleplayer()) {
                        ServerLoginPacketListenerImpl.LOGGER.warn("Authentication servers are down but will let them in anyway!");
                        ServerLoginPacketListenerImpl.this.startClientVerification(ServerLoginPacketListenerImpl.this.createOfflineProfile(s1)); // Spigot
                    } else {
                        ServerLoginPacketListenerImpl.this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.authenticationServersDown)); // Paper - Configurable kick message
                        ServerLoginPacketListenerImpl.LOGGER.error("Couldn't verify username because servers are unavailable");
                    }
                    // CraftBukkit start - catch all exceptions
                } catch (Exception exception) {
                    ServerLoginPacketListenerImpl.this.disconnect("Failed to verify username!");
                    ServerLoginPacketListenerImpl.this.server.server.getLogger().log(java.util.logging.Level.WARNING, "Exception verifying " + s1, exception);
                    // CraftBukkit end
                }

            }

            @Nullable
            private InetAddress getAddress() {
                SocketAddress socketaddress = ServerLoginPacketListenerImpl.this.connection.getRemoteAddress();

                return ServerLoginPacketListenerImpl.this.server.getPreventProxyConnections() && socketaddress instanceof InetSocketAddress ? ((InetSocketAddress) socketaddress).getAddress() : null;
            }
        });
        // Paper end - Cache authenticator threads
    }

    // CraftBukkit start
    private GameProfile callPlayerPreLoginEvents(GameProfile gameprofile) throws Exception { // Paper - Add more fields to AsyncPlayerPreLoginEvent
        // Paper start - Add Velocity IP Forwarding Support
        if (ServerLoginPacketListenerImpl.this.velocityLoginMessageId == -1 && io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled) {
            disconnect("This server requires you to connect with Velocity.");
            return gameprofile;
        }
        // Paper end - Add Velocity IP Forwarding Support
        String playerName = gameprofile.getName();
        java.net.InetAddress address = ((java.net.InetSocketAddress) this.connection.getRemoteAddress()).getAddress();
        java.util.UUID uniqueId = gameprofile.getId();
        final org.bukkit.craftbukkit.CraftServer server = ServerLoginPacketListenerImpl.this.server.server;

        // Paper start - Add more fields to AsyncPlayerPreLoginEvent
        final InetAddress rawAddress = ((InetSocketAddress) this.connection.channel.remoteAddress()).getAddress();
        com.destroystokyo.paper.profile.PlayerProfile profile = com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitMirror(gameprofile); // Paper - setPlayerProfileAPI
        AsyncPlayerPreLoginEvent asyncEvent = new AsyncPlayerPreLoginEvent(playerName, address, rawAddress, uniqueId, profile, this.connection.hostname);
        server.getPluginManager().callEvent(asyncEvent);
        profile = asyncEvent.getPlayerProfile();
        profile.complete(true); // Paper - setPlayerProfileAPI
        gameprofile = com.destroystokyo.paper.profile.CraftPlayerProfile.asAuthlibCopy(profile);
        playerName = gameprofile.getName();
        uniqueId = gameprofile.getId();
        // Paper end - Add more fields to AsyncPlayerPreLoginEvent

        if (PlayerPreLoginEvent.getHandlerList().getRegisteredListeners().length != 0) {
            final PlayerPreLoginEvent event = new PlayerPreLoginEvent(playerName, address, uniqueId);
            if (asyncEvent.getResult() != PlayerPreLoginEvent.Result.ALLOWED) {
                event.disallow(asyncEvent.getResult(), asyncEvent.kickMessage()); // Paper - Adventure
            }
            Waitable<PlayerPreLoginEvent.Result> waitable = new Waitable<PlayerPreLoginEvent.Result>() {
                @Override
                protected PlayerPreLoginEvent.Result evaluate() {
                    server.getPluginManager().callEvent(event);
                    return event.getResult();
                }
            };

            ServerLoginPacketListenerImpl.this.server.processQueue.add(waitable);
            if (waitable.get() != PlayerPreLoginEvent.Result.ALLOWED) {
                this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.kickMessage())); // Paper - Adventure
            }
        } else {
            if (asyncEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(asyncEvent.kickMessage())); // Paper - Adventure
            }
        }
        return gameprofile; // Paper - Add more fields to AsyncPlayerPreLoginEvent
    }
    // CraftBukkit end

    @Override
    public void handleCustomQueryPacket(ServerboundCustomQueryAnswerPacket packet) {
        // Paper start - Add Velocity IP Forwarding Support
        if (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled && packet.transactionId() == this.velocityLoginMessageId) {
            ServerboundCustomQueryAnswerPacket.QueryAnswerPayload payload = (ServerboundCustomQueryAnswerPacket.QueryAnswerPayload)packet.payload();
            if (payload == null) {
                this.disconnect("This server requires you to connect with Velocity.");
                return;
            }

            net.minecraft.network.FriendlyByteBuf buf = payload.buffer;

            if (!com.destroystokyo.paper.proxy.VelocityProxy.checkIntegrity(buf)) {
                this.disconnect("Unable to verify player details");
                return;
            }

            int version = buf.readVarInt();
            if (version > com.destroystokyo.paper.proxy.VelocityProxy.MAX_SUPPORTED_FORWARDING_VERSION) {
                throw new IllegalStateException("Unsupported forwarding version " + version + ", wanted upto " + com.destroystokyo.paper.proxy.VelocityProxy.MAX_SUPPORTED_FORWARDING_VERSION);
            }

            java.net.SocketAddress listening = this.connection.getRemoteAddress();
            int port = 0;
            if (listening instanceof java.net.InetSocketAddress) {
                port = ((java.net.InetSocketAddress) listening).getPort();
            }
            this.connection.address = new java.net.InetSocketAddress(com.destroystokyo.paper.proxy.VelocityProxy.readAddress(buf), port);

            this.authenticatedProfile = com.destroystokyo.paper.proxy.VelocityProxy.createProfile(buf);

            //TODO Update handling for lazy sessions, might not even have to do anything?

            // Proceed with login
            authenticatorPool.execute(() -> {
                try {
                    final GameProfile gameprofile = this.callPlayerPreLoginEvents(this.authenticatedProfile);
                    ServerLoginPacketListenerImpl.LOGGER.info("UUID of player {} is {}", gameprofile.getName(), gameprofile.getId());
                    ServerLoginPacketListenerImpl.this.startClientVerification(gameprofile);
                } catch (Exception ex) {
                    disconnect("Failed to verify username!");
                    server.server.getLogger().log(java.util.logging.Level.WARNING, "Exception verifying " + this.authenticatedProfile.getName(), ex);
                }
            });
            return;
        }
        // Paper end - Add Velocity IP Forwarding Support
        this.disconnect(ServerLoginPacketListenerImpl.DISCONNECT_UNEXPECTED_QUERY);
    }

    @Override
    public void handleLoginAcknowledgement(ServerboundLoginAcknowledgedPacket packet) {
        Validate.validState(this.state == ServerLoginPacketListenerImpl.State.PROTOCOL_SWITCHING, "Unexpected login acknowledgement packet", new Object[0]);
        CommonListenerCookie commonlistenercookie = CommonListenerCookie.createInitial((GameProfile) Objects.requireNonNull(this.authenticatedProfile));
        ServerConfigurationPacketListenerImpl serverconfigurationpacketlistenerimpl = new ServerConfigurationPacketListenerImpl(this.server, this.connection, commonlistenercookie, this.player); // CraftBukkit

        this.connection.setListener(serverconfigurationpacketlistenerimpl);
        serverconfigurationpacketlistenerimpl.startConfiguration();
        this.state = ServerLoginPacketListenerImpl.State.ACCEPTED;
    }

    @Override
    public void fillListenerSpecificCrashDetails(CrashReportCategory section) {
        section.setDetail("Login phase", () -> {
            return this.state.toString();
        });
    }

    // Spigot start
    protected GameProfile createOfflineProfile(String s) {
        java.util.UUID uuid;
        if ( this.connection.spoofedUUID != null )
        {
            uuid = this.connection.spoofedUUID;
        } else
        {
            uuid = UUIDUtil.createOfflinePlayerUUID( s );
        }

        GameProfile gameProfile = new GameProfile( uuid, s );

        if (this.connection.spoofedProfile != null)
        {
            for ( com.mojang.authlib.properties.Property property : this.connection.spoofedProfile )
            {
                if ( !ServerHandshakePacketListenerImpl.PROP_PATTERN.matcher( property.name()).matches() ) continue;
                gameProfile.getProperties().put( property.name(), property );
            }
        }

        return gameProfile;
    }
    // Spigot end

    public static enum State {

        HELLO, KEY, AUTHENTICATING, NEGOTIATING, VERIFYING, WAITING_FOR_DUPE_DISCONNECT, PROTOCOL_SWITCHING, ACCEPTED;

        private State() {}
    }
}
