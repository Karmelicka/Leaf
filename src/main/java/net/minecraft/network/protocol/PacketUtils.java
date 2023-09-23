package net.minecraft.network.protocol;

import com.mojang.logging.LogUtils;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
// CraftBukkit end
import net.minecraft.util.thread.BlockableEventLoop;

public class PacketUtils {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Paper start - detailed watchdog information
    public static final java.util.concurrent.ConcurrentLinkedDeque<PacketListener> packetProcessing = new java.util.concurrent.ConcurrentLinkedDeque<>();
    static final java.util.concurrent.atomic.AtomicLong totalMainThreadPacketsProcessed = new java.util.concurrent.atomic.AtomicLong();

    public static long getTotalProcessedPackets() {
        return totalMainThreadPacketsProcessed.get();
    }

    public static java.util.List<PacketListener> getCurrentPacketProcessors() {
        java.util.List<PacketListener> ret = new java.util.ArrayList<>(4);
        for (PacketListener listener : packetProcessing) {
            ret.add(listener);
        }

        return ret;
    }
    // Paper end - detailed watchdog information

    public PacketUtils() {}

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T listener, ServerLevel world) throws RunningOnDifferentThreadException {
        PacketUtils.ensureRunningOnSameThread(packet, listener, (BlockableEventLoop) world.getServer());
    }

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T listener, BlockableEventLoop<?> engine) throws RunningOnDifferentThreadException {
        if (!engine.isSameThread()) {
            engine.execute(() -> { // Paper - Fix preemptive player kick on a server shutdown
                packetProcessing.push(listener); // Paper - detailed watchdog information
                try { // Paper - detailed watchdog information
                if (MinecraftServer.getServer().hasStopped() || (listener instanceof ServerCommonPacketListenerImpl && ((ServerCommonPacketListenerImpl) listener).processedDisconnect)) return; // CraftBukkit, MC-142590
                if (listener.shouldHandleMessage(packet)) {

                    try {
                        packet.handle(listener);
                    } catch (Exception exception) {
                        label25:
                        {
                            if (exception instanceof ReportedException) {
                                ReportedException reportedexception = (ReportedException) exception;

                                if (reportedexception.getCause() instanceof OutOfMemoryError) {
                                    break label25;
                                }
                            }

                            if (!listener.shouldPropagateHandlingExceptions()) {
                                PacketUtils.LOGGER.error("Failed to handle packet {}, suppressing error", packet, exception);
                                return;
                            }
                        }

                        if (exception instanceof ReportedException) {
                            ReportedException reportedexception1 = (ReportedException) exception;

                            listener.fillCrashReport(reportedexception1.getReport());
                            throw exception;
                        }

                        CrashReport crashreport = CrashReport.forThrowable(exception, "Main thread packet handler");

                        listener.fillCrashReport(crashreport);
                        throw new ReportedException(crashreport);
                    }
                } else {
                    PacketUtils.LOGGER.debug("Ignoring packet due to disconnection: {}", packet);
                }
                // Paper start - detailed watchdog information
                } finally {
                    totalMainThreadPacketsProcessed.getAndIncrement();
                    packetProcessing.pop();
                }
                // Paper end - detailed watchdog information

            });
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
            // CraftBukkit start - SPIGOT-5477, MC-142590
        } else if (MinecraftServer.getServer().hasStopped() || (listener instanceof ServerCommonPacketListenerImpl && ((ServerCommonPacketListenerImpl) listener).processedDisconnect)) {
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
            // CraftBukkit end
        }
    }
}
