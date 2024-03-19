package top.leavesmc.leaves.protocol.core;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class ProtocolUtils {

    public static void sendEmptyPayloadPacket(ServerPlayer player, ResourceLocation id) {
        player.connection.send(new ClientboundCustomPayloadPacket(new LeavesProtocolManager.EmptyPayload(id)));
    }

    public static void sendPayloadPacket(ServerPlayer player, ResourceLocation id, Consumer<FriendlyByteBuf> consumer) {
        player.connection.send(new ClientboundCustomPayloadPacket(new CustomPacketPayload() {
            @Override
            public void write(@NotNull FriendlyByteBuf buf) {
                consumer.accept(buf);
            }

            @Override
            @NotNull
            public ResourceLocation id() {
                return id;
            }
        }));
    }

    public static void sendPayloadPacket(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
}
