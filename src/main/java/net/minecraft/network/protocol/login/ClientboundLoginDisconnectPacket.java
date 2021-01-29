package net.minecraft.network.protocol.login;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public class ClientboundLoginDisconnectPacket implements Packet<ClientLoginPacketListener> {
    private final Component reason;

    public ClientboundLoginDisconnectPacket(Component reason) {
        this.reason = reason;
    }

    public ClientboundLoginDisconnectPacket(FriendlyByteBuf buf) {
        this.reason = Component.Serializer.fromJsonLenient(buf.readUtf(FriendlyByteBuf.MAX_COMPONENT_STRING_LENGTH)); // Paper - diff on change
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        // Paper start - Adventure
        //buf.writeUtf(Component.Serializer.toJson(this.reason));

        // In the login phase, buf.adventure$locale field is always null
        buf.writeJsonWithCodec(net.minecraft.network.chat.ComponentSerialization.localizedCodec(java.util.Locale.US), this.reason, FriendlyByteBuf.MAX_COMPONENT_STRING_LENGTH);
        // Paper end - Adventure
    }

    @Override
    public void handle(ClientLoginPacketListener listener) {
        listener.handleDisconnect(this);
    }

    public Component getReason() {
        return this.reason;
    }
}
