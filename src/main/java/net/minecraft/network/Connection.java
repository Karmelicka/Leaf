package net.minecraft.network;

import com.google.common.base.Suppliers;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.Mth;
import net.minecraft.util.SampleLogger;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {

    private static final float AVERAGE_PACKETS_SMOOTHING = 0.75F;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Marker ROOT_MARKER = MarkerFactory.getMarker("NETWORK");
    public static final Marker PACKET_MARKER = (Marker) Util.make(MarkerFactory.getMarker("NETWORK_PACKETS"), (marker) -> {
        marker.add(Connection.ROOT_MARKER);
    });
    public static final Marker PACKET_RECEIVED_MARKER = (Marker) Util.make(MarkerFactory.getMarker("PACKET_RECEIVED"), (marker) -> {
        marker.add(Connection.PACKET_MARKER);
    });
    public static final Marker PACKET_SENT_MARKER = (Marker) Util.make(MarkerFactory.getMarker("PACKET_SENT"), (marker) -> {
        marker.add(Connection.PACKET_MARKER);
    });
    public static final AttributeKey<ConnectionProtocol.CodecData<?>> ATTRIBUTE_SERVERBOUND_PROTOCOL = AttributeKey.valueOf("serverbound_protocol");
    public static final AttributeKey<ConnectionProtocol.CodecData<?>> ATTRIBUTE_CLIENTBOUND_PROTOCOL = AttributeKey.valueOf("clientbound_protocol");
    public static final Supplier<NioEventLoopGroup> NETWORK_WORKER_GROUP = Suppliers.memoize(() -> {
        return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    public static final Supplier<EpollEventLoopGroup> NETWORK_EPOLL_WORKER_GROUP = Suppliers.memoize(() -> {
        return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    public static final Supplier<DefaultEventLoopGroup> LOCAL_WORKER_GROUP = Suppliers.memoize(() -> {
        return new DefaultEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    private final PacketFlow receiving;
    private final Queue<Consumer<Connection>> pendingActions = Queues.newConcurrentLinkedQueue();
    public Channel channel;
    public SocketAddress address;
    // Spigot Start
    public java.util.UUID spoofedUUID;
    public com.mojang.authlib.properties.Property[] spoofedProfile;
    public boolean preparing = true;
    // Spigot End
    @Nullable
    private volatile PacketListener disconnectListener;
    @Nullable
    private volatile PacketListener packetListener;
    @Nullable
    private Component disconnectedReason;
    private boolean encrypted;
    private boolean disconnectionHandled;
    private int receivedPackets;
    private int sentPackets;
    private float averageReceivedPackets;
    private float averageSentPackets;
    private int tickCount;
    private boolean handlingFault;
    @Nullable
    private volatile Component delayedDisconnect;
    @Nullable
    BandwidthDebugMonitor bandwidthDebugMonitor;
    public String hostname = ""; // CraftBukkit - add field
    // Paper start - NetworkClient implementation
    public int protocolVersion;
    public java.net.InetSocketAddress virtualHost;
    private static boolean enableExplicitFlush = Boolean.getBoolean("paper.explicit-flush"); // Paper - Disable explicit network manager flushing
    // Paper end

    // Paper start - add utility methods
    public final net.minecraft.server.level.ServerPlayer getPlayer() {
        if (this.packetListener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl impl) {
            return impl.player;
        } else if (this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl impl) {
            org.bukkit.craftbukkit.entity.CraftPlayer player = impl.getCraftPlayer();
            return player == null ? null : player.getHandle();
        }
        return null;
    }
    // Paper end - add utility methods

    public Connection(PacketFlow side) {
        this.receiving = side;
    }

    public void channelActive(ChannelHandlerContext channelhandlercontext) throws Exception {
        super.channelActive(channelhandlercontext);
        this.channel = channelhandlercontext.channel();
        this.address = this.channel.remoteAddress();
        // Spigot Start
        this.preparing = false;
        // Spigot End
        if (this.delayedDisconnect != null) {
            this.disconnect(this.delayedDisconnect);
        }

    }

    public static void setInitialProtocolAttributes(Channel channel) {
        channel.attr(Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL).set(ConnectionProtocol.HANDSHAKING.codec(PacketFlow.SERVERBOUND));
        channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).set(ConnectionProtocol.HANDSHAKING.codec(PacketFlow.CLIENTBOUND));
    }

    public void channelInactive(ChannelHandlerContext channelhandlercontext) {
        this.disconnect(Component.translatable("disconnect.endOfStream"));
    }

    public void exceptionCaught(ChannelHandlerContext channelhandlercontext, Throwable throwable) {
        // Paper start - Handle large packets disconnecting client
        if (throwable instanceof io.netty.handler.codec.EncoderException && throwable.getCause() instanceof PacketEncoder.PacketTooLargeException packetTooLargeException) {
            final Packet<?> packet = packetTooLargeException.getPacket();
            final io.netty.util.Attribute<ConnectionProtocol.CodecData<?>> codecDataAttribute = channelhandlercontext.channel().attr(packetTooLargeException.codecKey);
            if (packet.packetTooLarge(this)) {
                ProtocolSwapHandler.swapProtocolIfNeeded(codecDataAttribute, packet);
                return;
            } else if (packet.isSkippable()) {
                Connection.LOGGER.debug("Skipping packet due to errors", throwable.getCause());
                ProtocolSwapHandler.swapProtocolIfNeeded(codecDataAttribute, packet);
                return;
            } else {
                throwable = throwable.getCause();
            }
        }
        // Paper end - Handle large packets disconnecting client
        if (throwable instanceof SkipPacketException) {
            Connection.LOGGER.debug("Skipping packet due to errors", throwable.getCause());
        } else {
            boolean flag = !this.handlingFault;

            this.handlingFault = true;
            if (this.channel.isOpen()) {
                net.minecraft.server.level.ServerPlayer player = this.getPlayer(); // Paper - Add API for quit reason
                if (throwable instanceof TimeoutException) {
                    Connection.LOGGER.debug("Timeout", throwable);
                    if (player != null) player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.TIMED_OUT; // Paper - Add API for quit reason
                    this.disconnect(Component.translatable("disconnect.timeout"));
                } else {
                    MutableComponent ichatmutablecomponent = Component.translatable("disconnect.genericReason", "Internal Exception: " + throwable);

                    if (player != null) player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.ERRONEOUS_STATE; // Paper - Add API for quit reason
                    if (flag) {
                        Connection.LOGGER.debug("Failed to sent packet", throwable);
                        if (this.getSending() == PacketFlow.CLIENTBOUND) {
                            ConnectionProtocol enumprotocol = ((ConnectionProtocol.CodecData) this.channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).get()).protocol();
                            Packet<?> packet = enumprotocol == ConnectionProtocol.LOGIN ? new ClientboundLoginDisconnectPacket(ichatmutablecomponent) : new ClientboundDisconnectPacket(ichatmutablecomponent);

                            this.send((Packet) packet, PacketSendListener.thenRun(() -> {
                                this.disconnect(ichatmutablecomponent);
                            }));
                        } else {
                            this.disconnect(ichatmutablecomponent);
                        }

                        this.setReadOnly();
                    } else {
                        Connection.LOGGER.debug("Double fault", throwable);
                        this.disconnect(ichatmutablecomponent);
                    }
                }

            }
        }
        if (net.minecraft.server.MinecraftServer.getServer().isDebugging()) io.papermc.paper.util.TraceUtil.printStackTrace(throwable); // Spigot // Paper
    }

    protected void channelRead0(ChannelHandlerContext channelhandlercontext, Packet<?> packet) {
        if (this.channel.isOpen()) {
            PacketListener packetlistener = this.packetListener;

            if (packetlistener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else {
                if (packetlistener.shouldHandleMessage(packet)) {
                    try {
                        Connection.genericsFtw(packet, packetlistener);
                    } catch (RunningOnDifferentThreadException cancelledpackethandleexception) {
                        ;
                    } catch (RejectedExecutionException rejectedexecutionexception) {
                        this.disconnect(Component.translatable("multiplayer.disconnect.server_shutdown"));
                    } catch (ClassCastException classcastexception) {
                        Connection.LOGGER.error("Received {} that couldn't be processed", packet.getClass(), classcastexception);
                        this.disconnect(Component.translatable("multiplayer.disconnect.invalid_packet"));
                    }

                    ++this.receivedPackets;
                }

            }
        }
    }

    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener listener) {
        packet.handle((T) listener); // CraftBukkit - decompile error
    }

    public void suspendInboundAfterProtocolChange() {
        this.channel.config().setAutoRead(false);
    }

    public void resumeInboundAfterProtocolChange() {
        this.channel.config().setAutoRead(true);
    }

    public void setListener(PacketListener packetListener) {
        Validate.notNull(packetListener, "packetListener", new Object[0]);
        PacketFlow enumprotocoldirection = packetListener.flow();

        if (enumprotocoldirection != this.receiving) {
            throw new IllegalStateException("Trying to set listener for wrong side: connection is " + this.receiving + ", but listener is " + enumprotocoldirection);
        } else {
            ConnectionProtocol enumprotocol = packetListener.protocol();
            ConnectionProtocol enumprotocol1 = ((ConnectionProtocol.CodecData) this.channel.attr(Connection.getProtocolKey(enumprotocoldirection)).get()).protocol();

            if (enumprotocol1 != enumprotocol) {
                throw new IllegalStateException("Trying to set listener for protocol " + enumprotocol.id() + ", but current " + enumprotocoldirection + " protocol is " + enumprotocol1.id());
            } else {
                this.packetListener = packetListener;
                this.disconnectListener = null;
            }
        }
    }

    public void setListenerForServerboundHandshake(PacketListener packetListener) {
        if (this.packetListener != null) {
            throw new IllegalStateException("Listener already set");
        } else if (this.receiving == PacketFlow.SERVERBOUND && packetListener.flow() == PacketFlow.SERVERBOUND && packetListener.protocol() == ConnectionProtocol.HANDSHAKING) {
            this.packetListener = packetListener;
        } else {
            throw new IllegalStateException("Invalid initial listener");
        }
    }

    public void initiateServerboundStatusConnection(String address, int port, ClientStatusPacketListener listener) {
        this.initiateServerboundConnection(address, port, listener, ClientIntent.STATUS);
    }

    public void initiateServerboundPlayConnection(String address, int port, ClientLoginPacketListener listener) {
        this.initiateServerboundConnection(address, port, listener, ClientIntent.LOGIN);
    }

    private void initiateServerboundConnection(String address, int port, PacketListener listener, ClientIntent intent) {
        this.disconnectListener = listener;
        this.runOnceConnected((networkmanager) -> {
            networkmanager.setClientboundProtocolAfterHandshake(intent);
            this.setListener(listener);
            networkmanager.sendPacket(new ClientIntentionPacket(SharedConstants.getCurrentVersion().getProtocolVersion(), address, port, intent), (PacketSendListener) null, true);
        });
    }

    public void setClientboundProtocolAfterHandshake(ClientIntent intent) {
        this.channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).set(intent.protocol().codec(PacketFlow.CLIENTBOUND));
    }

    public void send(Packet<?> packet) {
        this.send(packet, (PacketSendListener) null);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
        this.send(packet, callbacks, true);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        if (this.isConnected()) {
            this.flushQueue();
            this.sendPacket(packet, callbacks, flush);
        } else {
            this.pendingActions.add((networkmanager) -> {
                networkmanager.sendPacket(packet, callbacks, flush);
            });
        }

    }

    public void runOnceConnected(Consumer<Connection> task) {
        if (this.isConnected()) {
            this.flushQueue();
            task.accept(this);
        } else {
            this.pendingActions.add(task);
        }

    }

    private void sendPacket(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        ++this.sentPackets;
        if (this.channel.eventLoop().inEventLoop()) {
            this.doSendPacket(packet, callbacks, flush);
        } else {
            this.channel.eventLoop().execute(() -> {
                this.doSendPacket(packet, callbacks, flush);
            });
        }

    }

    private void doSendPacket(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        ChannelFuture channelfuture = flush ? this.channel.writeAndFlush(packet) : this.channel.write(packet);

        if (callbacks != null) {
            channelfuture.addListener((future) -> {
                if (future.isSuccess()) {
                    callbacks.onSuccess();
                } else {
                    Packet<?> packet1 = callbacks.onFailure();

                    if (packet1 != null) {
                        ChannelFuture channelfuture1 = this.channel.writeAndFlush(packet1);

                        channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    }
                }

            });
        }

        channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    public void flushChannel() {
        if (this.isConnected()) {
            this.flush();
        } else {
            this.pendingActions.add(Connection::flush);
        }

    }

    private void flush() {
        if (this.channel.eventLoop().inEventLoop()) {
            this.channel.flush();
        } else {
            this.channel.eventLoop().execute(() -> {
                this.channel.flush();
            });
        }

    }

    private static AttributeKey<ConnectionProtocol.CodecData<?>> getProtocolKey(PacketFlow side) {
        AttributeKey attributekey;

        switch (side) {
            case CLIENTBOUND:
                attributekey = Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL;
                break;
            case SERVERBOUND:
                attributekey = Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL;
                break;
            default:
                throw new IncompatibleClassChangeError();
        }

        return attributekey;
    }

    private void flushQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            Queue queue = this.pendingActions;

            synchronized (this.pendingActions) {
                Consumer consumer;

                while ((consumer = (Consumer) this.pendingActions.poll()) != null) {
                    consumer.accept(this);
                }

            }
        }
    }

    private static final int MAX_PER_TICK = io.papermc.paper.configuration.GlobalConfiguration.get().misc.maxJoinsPerTick; // Paper - Buffer joins to world
    private static int joinAttemptsThisTick; // Paper - Buffer joins to world
    private static int currTick; // Paper - Buffer joins to world
    public void tick() {
        this.flushQueue();
        // Paper start - Buffer joins to world
        if (Connection.currTick != net.minecraft.server.MinecraftServer.currentTick) {
            Connection.currTick = net.minecraft.server.MinecraftServer.currentTick;
            Connection.joinAttemptsThisTick = 0;
        }
        // Paper end - Buffer joins to world
        PacketListener packetlistener = this.packetListener;

        if (packetlistener instanceof TickablePacketListener) {
            TickablePacketListener tickablepacketlistener = (TickablePacketListener) packetlistener;

            // Paper start - Buffer joins to world
            if (!(this.packetListener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginPacketListener)
                || loginPacketListener.state != net.minecraft.server.network.ServerLoginPacketListenerImpl.State.VERIFYING
                || Connection.joinAttemptsThisTick++ < MAX_PER_TICK) {
            tickablepacketlistener.tick();
            }
            // Paper end - Buffer joins to world
        }

        if (!this.isConnected() && !this.disconnectionHandled) {
            this.handleDisconnection();
        }

        if (this.channel != null) {
            if (enableExplicitFlush) this.channel.eventLoop().execute(() -> this.channel.flush()); // Paper - Disable explicit network manager flushing; we don't need to explicit flush here, but allow opt in incase issues are found to a better version
        }

        if (this.tickCount++ % 20 == 0) {
            this.tickSecond();
        }

        if (this.bandwidthDebugMonitor != null) {
            this.bandwidthDebugMonitor.tick();
        }

    }

    protected void tickSecond() {
        this.averageSentPackets = Mth.lerp(0.75F, (float) this.sentPackets, this.averageSentPackets);
        this.averageReceivedPackets = Mth.lerp(0.75F, (float) this.receivedPackets, this.averageReceivedPackets);
        this.sentPackets = 0;
        this.receivedPackets = 0;
    }

    public SocketAddress getRemoteAddress() {
        return this.address;
    }

    public String getLoggableAddress(boolean logIps) {
        return this.address == null ? "local" : (logIps ? this.address.toString() : "IP hidden");
    }

    public void disconnect(Component disconnectReason) {
        // Spigot Start
        this.preparing = false;
        // Spigot End
        if (this.channel == null) {
            this.delayedDisconnect = disconnectReason;
        }

        if (this.isConnected()) {
            this.channel.close(); // We can't wait as this may be called from an event loop.
            this.disconnectedReason = disconnectReason;
        }

    }

    public boolean isMemoryConnection() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public PacketFlow getReceiving() {
        return this.receiving;
    }

    public PacketFlow getSending() {
        return this.receiving.getOpposite();
    }

    public static Connection connectToServer(InetSocketAddress address, boolean useEpoll, @Nullable SampleLogger packetSizeLog) {
        Connection networkmanager = new Connection(PacketFlow.CLIENTBOUND);

        if (packetSizeLog != null) {
            networkmanager.setBandwidthLogger(packetSizeLog);
        }

        ChannelFuture channelfuture = Connection.connect(address, useEpoll, networkmanager);

        channelfuture.syncUninterruptibly();
        return networkmanager;
    }

    public static ChannelFuture connect(InetSocketAddress address, boolean useEpoll, final Connection connection) {
        Class oclass;
        EventLoopGroup eventloopgroup;

        if (Epoll.isAvailable() && useEpoll) {
            oclass = EpollSocketChannel.class;
            eventloopgroup = (EventLoopGroup) Connection.NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            oclass = NioSocketChannel.class;
            eventloopgroup = (EventLoopGroup) Connection.NETWORK_WORKER_GROUP.get();
        }

        return ((Bootstrap) ((Bootstrap) ((Bootstrap) (new Bootstrap()).group(eventloopgroup)).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) {
                Connection.setInitialProtocolAttributes(channel);

                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                    ;
                }

                ChannelPipeline channelpipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));

                Connection.configureSerialization(channelpipeline, PacketFlow.CLIENTBOUND, connection.bandwidthDebugMonitor);
                connection.configurePacketHandler(channelpipeline);
            }
        })).channel(oclass)).connect(address.getAddress(), address.getPort());
    }

    public static void configureSerialization(ChannelPipeline pipeline, PacketFlow side, @Nullable BandwidthDebugMonitor packetSizeLogger) {
        PacketFlow enumprotocoldirection1 = side.getOpposite();
        AttributeKey<ConnectionProtocol.CodecData<?>> attributekey = Connection.getProtocolKey(side);
        AttributeKey<ConnectionProtocol.CodecData<?>> attributekey1 = Connection.getProtocolKey(enumprotocoldirection1);

        pipeline.addLast("splitter", new Varint21FrameDecoder(packetSizeLogger)).addLast("decoder", new PacketDecoder(attributekey)).addLast("prepender", new Varint21LengthFieldPrepender()).addLast("encoder", new PacketEncoder(attributekey1)).addLast("unbundler", new PacketBundleUnpacker(attributekey1)).addLast("bundler", new PacketBundlePacker(attributekey));
    }

    public void configurePacketHandler(ChannelPipeline pipeline) {
        pipeline.addLast(new ChannelHandler[]{new FlowControlHandler()}).addLast("packet_handler", this);
    }

    private static void configureInMemoryPacketValidation(ChannelPipeline pipeline, PacketFlow side) {
        PacketFlow enumprotocoldirection1 = side.getOpposite();
        AttributeKey<ConnectionProtocol.CodecData<?>> attributekey = Connection.getProtocolKey(side);
        AttributeKey<ConnectionProtocol.CodecData<?>> attributekey1 = Connection.getProtocolKey(enumprotocoldirection1);

        pipeline.addLast("validator", new PacketFlowValidator(attributekey, attributekey1));
    }

    public static void configureInMemoryPipeline(ChannelPipeline pipeline, PacketFlow side) {
        Connection.configureInMemoryPacketValidation(pipeline, side);
    }

    public static Connection connectToLocalServer(SocketAddress address) {
        final Connection networkmanager = new Connection(PacketFlow.CLIENTBOUND);

        ((Bootstrap) ((Bootstrap) ((Bootstrap) (new Bootstrap()).group((EventLoopGroup) Connection.LOCAL_WORKER_GROUP.get())).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) {
                Connection.setInitialProtocolAttributes(channel);
                ChannelPipeline channelpipeline = channel.pipeline();

                Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.CLIENTBOUND);
                networkmanager.configurePacketHandler(channelpipeline);
            }
        })).channel(LocalChannel.class)).connect(address).syncUninterruptibly();
        return networkmanager;
    }

    public void setEncryptionKey(Cipher decryptionCipher, Cipher encryptionCipher) {
        this.encrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(decryptionCipher));
        this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(encryptionCipher));
    }

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    @Nullable
    public PacketListener getPacketListener() {
        return this.packetListener;
    }

    @Nullable
    public Component getDisconnectedReason() {
        return this.disconnectedReason;
    }

    public void setReadOnly() {
        if (this.channel != null) {
            this.channel.config().setAutoRead(false);
        }

    }

    public void setupCompression(int compressionThreshold, boolean rejectsBadPackets) {
        if (compressionThreshold >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                ((CompressionDecoder) this.channel.pipeline().get("decompress")).setThreshold(compressionThreshold, rejectsBadPackets);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress", new CompressionDecoder(compressionThreshold, rejectsBadPackets));
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                ((CompressionEncoder) this.channel.pipeline().get("compress")).setThreshold(compressionThreshold);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress", new CompressionEncoder(compressionThreshold));
            }
            this.channel.pipeline().fireUserEventTriggered(io.papermc.paper.network.ConnectionEvent.COMPRESSION_THRESHOLD_SET); // Paper - Add Channel initialization listeners
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
            this.channel.pipeline().fireUserEventTriggered(io.papermc.paper.network.ConnectionEvent.COMPRESSION_DISABLED); // Paper - Add Channel initialization listeners
        }

    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.disconnectionHandled) {
                Connection.LOGGER.warn("handleDisconnection() called twice");
            } else {
                this.disconnectionHandled = true;
                PacketListener packetlistener = this.getPacketListener();
                PacketListener packetlistener1 = packetlistener != null ? packetlistener : this.disconnectListener;

                if (packetlistener1 != null) {
                    Component ichatbasecomponent = (Component) Objects.requireNonNullElseGet(this.getDisconnectedReason(), () -> {
                        return Component.translatable("multiplayer.disconnect.generic");
                    });

                    packetlistener1.onDisconnect(ichatbasecomponent);
                }
                this.pendingActions.clear(); // Free up packet queue.
                // Paper start - Add PlayerConnectionCloseEvent
                final PacketListener packetListener = this.getPacketListener();
                if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl commonPacketListener) {
                    /* Player was logged in, either game listener or configuration listener */
                    final com.mojang.authlib.GameProfile profile = commonPacketListener.getOwner();
                    new com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent(profile.getId(),
                        profile.getName(), ((InetSocketAddress) this.address).getAddress(), false).callEvent();
                } else if (packetListener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginListener) {
                    /* Player is login stage */
                    switch (loginListener.state) {
                        case VERIFYING:
                        case WAITING_FOR_DUPE_DISCONNECT:
                        case PROTOCOL_SWITCHING:
                        case ACCEPTED:
                            final com.mojang.authlib.GameProfile profile = loginListener.authenticatedProfile; /* Should be non-null at this stage */
                            new com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent(profile.getId(), profile.getName(),
                                ((InetSocketAddress) this.address).getAddress(), false).callEvent();
                    }
                }
                // Paper end - Add PlayerConnectionCloseEvent

            }
        }
    }

    public float getAverageReceivedPackets() {
        return this.averageReceivedPackets;
    }

    public float getAverageSentPackets() {
        return this.averageSentPackets;
    }

    public void setBandwidthLogger(SampleLogger log) {
        this.bandwidthDebugMonitor = new BandwidthDebugMonitor(log);
    }
}
