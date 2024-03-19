package top.leavesmc.leaves.protocol;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import top.leavesmc.leaves.protocol.core.LeavesProtocol;
import top.leavesmc.leaves.protocol.core.ProtocolHandler;
import top.leavesmc.leaves.protocol.core.ProtocolUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@LeavesProtocol(namespace = "appleskin")
public class AppleSkinProtocol {

    public static final String PROTOCOL_ID = "appleskin";

    private static final ResourceLocation SATURATION_KEY = id("saturation_sync");
    private static final ResourceLocation EXHAUSTION_KEY = id("exhaustion_sync");

    private static final Map<UUID, Float> previousSaturationLevels = new HashMap<>();
    private static final Map<UUID, Float> previousExhaustionLevels = new HashMap<>();

    private static final float MINIMUM_EXHAUSTION_CHANGE_THRESHOLD = 0.01F;

    private static final Set<ServerPlayer> players = new HashSet<>();

    @Contract("_ -> new")
    public static @NotNull ResourceLocation id(String path) {
        return new ResourceLocation(PROTOCOL_ID, path);
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerLoggedIn(@NotNull ServerPlayer player) {
        if (org.dreeam.leaf.config.modules.network.ProtocolSupport.appleskinProtocol) {
            resetPlayerData(player);
        }
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLoggedOut(@NotNull ServerPlayer player) {
        if (org.dreeam.leaf.config.modules.network.ProtocolSupport.appleskinProtocol) {
            players.remove(player);
            resetPlayerData(player);
        }
    }

    @ProtocolHandler.MinecraftRegister(ignoreId = true)
    public static void onPlayerSubscribed(@NotNull ServerPlayer player) {
        if (org.dreeam.leaf.config.modules.network.ProtocolSupport.appleskinProtocol) {
            players.add(player);
        }
    }

    @ProtocolHandler.Ticker
    public static void tick() {
        if (org.dreeam.leaf.config.modules.network.ProtocolSupport.appleskinProtocol) {
            for (ServerPlayer player : players) {
                FoodData data = player.getFoodData();

                float saturation = data.getSaturationLevel();
                Float previousSaturation = previousSaturationLevels.get(player.getUUID());
                if (previousSaturation == null || saturation != previousSaturation) {
                    ProtocolUtils.sendPayloadPacket(player, SATURATION_KEY, buf -> {
                        buf.writeFloat(saturation);
                    });
                    previousSaturationLevels.put(player.getUUID(), saturation);
                }

                float exhaustion = data.getExhaustionLevel();
                Float previousExhaustion = previousExhaustionLevels.get(player.getUUID());
                if (previousExhaustion == null || Math.abs(exhaustion - previousExhaustion) >= MINIMUM_EXHAUSTION_CHANGE_THRESHOLD) {
                    ProtocolUtils.sendPayloadPacket(player, EXHAUSTION_KEY, buf -> {
                        buf.writeFloat(exhaustion);
                    });
                    previousExhaustionLevels.put(player.getUUID(), exhaustion);
                }
            }
        }
    }

    @ProtocolHandler.ReloadServer
    public static void onServerReload() {
        if (!org.dreeam.leaf.config.modules.network.ProtocolSupport.appleskinProtocol) {
            disableAllPlayer();
        }
    }

    public static void disableAllPlayer() {
        for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            onPlayerLoggedOut(player);
        }
    }

    private static void resetPlayerData(@NotNull ServerPlayer player) {
        previousExhaustionLevels.remove(player.getUUID());
        previousSaturationLevels.remove(player.getUUID());
    }
}
