// mc-dev import
package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public record ClientboundSystemChatPacket(Component content, boolean overlay) implements Packet<ClientGamePacketListener> {

    // Spigot start
    public ClientboundSystemChatPacket(net.md_5.bungee.api.chat.BaseComponent[] content, boolean overlay) {
        this(Component.Serializer.fromJson(net.md_5.bungee.chat.ComponentSerializer.toString(content)), overlay);
    }
    // Spigot end

    public ClientboundSystemChatPacket(FriendlyByteBuf buf) {
        this(buf.readComponentTrusted(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeComponent(this.content);
        buf.writeBoolean(this.overlay);
    }

    public void handle(ClientGamePacketListener listener) {
        listener.handleSystemChat(this);
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
