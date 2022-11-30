package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Items;

public record EntityEquipmentPredicate(Optional<ItemPredicate> head, Optional<ItemPredicate> chest, Optional<ItemPredicate> legs, Optional<ItemPredicate> feet, Optional<ItemPredicate> mainhand, Optional<ItemPredicate> offhand) {
    public static final Codec<EntityEquipmentPredicate> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.strictOptionalField(ItemPredicate.CODEC, "head").forGetter(EntityEquipmentPredicate::head), ExtraCodecs.strictOptionalField(ItemPredicate.CODEC, "chest").forGetter(EntityEquipmentPredicate::chest), ExtraCodecs.strictOptionalField(ItemPredicate.CODEC, "legs").forGetter(EntityEquipmentPredicate::legs), ExtraCodecs.strictOptionalField(ItemPredicate.CODEC, "feet").forGetter(EntityEquipmentPredicate::feet), ExtraCodecs.strictOptionalField(ItemPredicate.CODEC, "mainhand").forGetter(EntityEquipmentPredicate::mainhand), ExtraCodecs.strictOptionalField(ItemPredicate.CODEC, "offhand").forGetter(EntityEquipmentPredicate::offhand)).apply(instance, EntityEquipmentPredicate::new);
    });
    public static final EntityEquipmentPredicate CAPTAIN = EntityEquipmentPredicate.Builder.equipment().head(ItemPredicate.Builder.item().of(Items.WHITE_BANNER).hasNbt(Raid.LEADER_BANNER.getTag())).build(); // Gale - Lithium - cache ominous banner item

    public boolean matches(@Nullable Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            if (this.head.isPresent() && !this.head.get().matches(livingEntity.getItemBySlot(EquipmentSlot.HEAD))) {
                return false;
            } else if (this.chest.isPresent() && !this.chest.get().matches(livingEntity.getItemBySlot(EquipmentSlot.CHEST))) {
                return false;
            } else if (this.legs.isPresent() && !this.legs.get().matches(livingEntity.getItemBySlot(EquipmentSlot.LEGS))) {
                return false;
            } else if (this.feet.isPresent() && !this.feet.get().matches(livingEntity.getItemBySlot(EquipmentSlot.FEET))) {
                return false;
            } else if (this.mainhand.isPresent() && !this.mainhand.get().matches(livingEntity.getItemBySlot(EquipmentSlot.MAINHAND))) {
                return false;
            } else {
                return !this.offhand.isPresent() || this.offhand.get().matches(livingEntity.getItemBySlot(EquipmentSlot.OFFHAND));
            }
        } else {
            return false;
        }
    }

    public static class Builder {
        private Optional<ItemPredicate> head = Optional.empty();
        private Optional<ItemPredicate> chest = Optional.empty();
        private Optional<ItemPredicate> legs = Optional.empty();
        private Optional<ItemPredicate> feet = Optional.empty();
        private Optional<ItemPredicate> mainhand = Optional.empty();
        private Optional<ItemPredicate> offhand = Optional.empty();

        public static EntityEquipmentPredicate.Builder equipment() {
            return new EntityEquipmentPredicate.Builder();
        }

        public EntityEquipmentPredicate.Builder head(ItemPredicate.Builder item) {
            this.head = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder chest(ItemPredicate.Builder item) {
            this.chest = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder legs(ItemPredicate.Builder item) {
            this.legs = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder feet(ItemPredicate.Builder item) {
            this.feet = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder mainhand(ItemPredicate.Builder item) {
            this.mainhand = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder offhand(ItemPredicate.Builder item) {
            this.offhand = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate build() {
            return new EntityEquipmentPredicate(this.head, this.chest, this.legs, this.feet, this.mainhand, this.offhand);
        }
    }
}
