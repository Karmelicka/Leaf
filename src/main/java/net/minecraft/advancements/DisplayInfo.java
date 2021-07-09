package net.minecraft.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

public class DisplayInfo {
    public static final Codec<DisplayInfo> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ItemStack.ADVANCEMENT_ICON_CODEC.fieldOf("icon").forGetter(DisplayInfo::getIcon), ComponentSerialization.CODEC.fieldOf("title").forGetter(DisplayInfo::getTitle), ComponentSerialization.CODEC.fieldOf("description").forGetter(DisplayInfo::getDescription), ExtraCodecs.strictOptionalField(ResourceLocation.CODEC, "background").forGetter(DisplayInfo::getBackground), ExtraCodecs.strictOptionalField(AdvancementType.CODEC, "frame", AdvancementType.TASK).forGetter(DisplayInfo::getType), ExtraCodecs.strictOptionalField(Codec.BOOL, "show_toast", true).forGetter(DisplayInfo::shouldShowToast), ExtraCodecs.strictOptionalField(Codec.BOOL, "announce_to_chat", true).forGetter(DisplayInfo::shouldAnnounceChat), ExtraCodecs.strictOptionalField(Codec.BOOL, "hidden", false).forGetter(DisplayInfo::isHidden)).apply(instance, DisplayInfo::new);
    });
    private final Component title;
    private final Component description;
    private final ItemStack icon;
    private final Optional<ResourceLocation> background;
    private final AdvancementType type;
    private final boolean showToast;
    private final boolean announceChat;
    private final boolean hidden;
    private float x;
    private float y;
    public final io.papermc.paper.advancement.AdvancementDisplay paper = new io.papermc.paper.advancement.PaperAdvancementDisplay(this); // Paper - Add more advancement API

    public DisplayInfo(ItemStack icon, Component title, Component description, Optional<ResourceLocation> background, AdvancementType frame, boolean showToast, boolean announceToChat, boolean hidden) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.background = background;
        this.type = frame;
        this.showToast = showToast;
        this.announceChat = announceToChat;
        this.hidden = hidden;
    }

    public void setLocation(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Component getTitle() {
        return this.title;
    }

    public Component getDescription() {
        return this.description;
    }

    public ItemStack getIcon() {
        return this.icon;
    }

    public Optional<ResourceLocation> getBackground() {
        return this.background;
    }

    public AdvancementType getType() {
        return this.type;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public boolean shouldShowToast() {
        return this.showToast;
    }

    public boolean shouldAnnounceChat() {
        return this.announceChat;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void serializeToNetwork(FriendlyByteBuf buf) {
        buf.writeComponent(this.title);
        buf.writeComponent(this.description);
        buf.writeItem(this.icon);
        buf.writeEnum(this.type);
        int i = 0;
        if (this.background.isPresent()) {
            i |= 1;
        }

        if (this.showToast) {
            i |= 2;
        }

        if (this.hidden) {
            i |= 4;
        }

        buf.writeInt(i);
        this.background.ifPresent(buf::writeResourceLocation);
        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
    }

    public static DisplayInfo fromNetwork(FriendlyByteBuf buf) {
        Component component = buf.readComponentTrusted();
        Component component2 = buf.readComponentTrusted();
        ItemStack itemStack = buf.readItem();
        AdvancementType advancementType = buf.readEnum(AdvancementType.class);
        int i = buf.readInt();
        Optional<ResourceLocation> optional = (i & 1) != 0 ? Optional.of(buf.readResourceLocation()) : Optional.empty();
        boolean bl = (i & 2) != 0;
        boolean bl2 = (i & 4) != 0;
        DisplayInfo displayInfo = new DisplayInfo(itemStack, component, component2, optional, advancementType, bl, false, bl2);
        displayInfo.setLocation(buf.readFloat(), buf.readFloat());
        return displayInfo;
    }
}
