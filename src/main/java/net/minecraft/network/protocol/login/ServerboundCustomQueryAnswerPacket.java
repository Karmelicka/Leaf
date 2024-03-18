package net.minecraft.network.protocol.login;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;

public record ServerboundCustomQueryAnswerPacket(int transactionId, @Nullable CustomQueryAnswerPayload payload) implements Packet<ServerLoginPacketListener> {
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    public static ServerboundCustomQueryAnswerPacket read(FriendlyByteBuf buf) {
        int i = buf.readVarInt();
        return new ServerboundCustomQueryAnswerPacket(i, readPayload(i, buf));
    }

    private static CustomQueryAnswerPayload readPayload(int queryId, FriendlyByteBuf buf) {
        return readUnknownPayload(buf);
    }

    private static CustomQueryAnswerPayload readUnknownPayload(FriendlyByteBuf buf) {
        int i = buf.readableBytes();
        if (i >= 0 && i <= 1048576) {
            buf.skipBytes(i);
            return DiscardedQueryAnswerPayload.INSTANCE;
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.transactionId);
        buf.writeNullable(this.payload, (bufx, response) -> {
            response.write(bufx);
        });
    }

    @Override
    public void handle(ServerLoginPacketListener listener) {
        listener.handleCustomQueryPacket(this);
    }
}
