package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class DamageSource {

    private final Holder<DamageType> type;
    @Nullable
    private final Entity causingEntity;
    @Nullable
    private final Entity directEntity;
    @Nullable
    private final Vec3 damageSourcePosition;
    // CraftBukkit start
    @Nullable
    private org.bukkit.block.Block directBlock; // The block that caused the damage. damageSourcePosition is not used for all block damages
    private boolean withSweep = false;
    private boolean melting = false;
    private boolean poison = false;
    private Entity customCausingEntity = null; // This field is a helper for when causing entity damage is not set by vanilla

    public DamageSource sweep() {
        this.withSweep = true;
        return this;
    }

    public boolean isSweep() {
        return this.withSweep;
    }

    public DamageSource melting() {
        this.melting = true;
        return this;
    }

    public boolean isMelting() {
        return this.melting;
    }

    public DamageSource poison() {
        this.poison = true;
        return this;
    }

    public boolean isPoison() {
        return this.poison;
    }

    public Entity getCausingEntity() {
        return (this.customCausingEntity != null) ? this.customCausingEntity : this.causingEntity;
    }

    public DamageSource customCausingEntity(Entity entity) {
        DamageSource damageSource = this.cloneInstance();
        damageSource.customCausingEntity = entity;
        return damageSource;
    }

    public org.bukkit.block.Block getDirectBlock() {
        return this.directBlock;
    }

    public DamageSource directBlock(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos blockPosition) {
        if (blockPosition == null || world == null) {
            return this;
        }
        return this.directBlock(org.bukkit.craftbukkit.block.CraftBlock.at(world, blockPosition));
    }

    public DamageSource directBlock(org.bukkit.block.Block block) {
        if (block == null) {
            return this;
        }
        // Cloning the instance lets us return unique instances of DamageSource without affecting constants defined in DamageSources
        DamageSource damageSource = this.cloneInstance();
        damageSource.directBlock = block;
        return damageSource;
    }

    private DamageSource cloneInstance() {
        DamageSource damageSource = new DamageSource(this.type, this.directEntity, this.causingEntity, this.damageSourcePosition);
        damageSource.directBlock = this.getDirectBlock();
        damageSource.withSweep = this.isSweep();
        damageSource.poison = this.isPoison();
        damageSource.melting = this.isMelting();
        return damageSource;
    }
    // CraftBukkit end

    public String toString() {
        return "DamageSource (" + this.type().msgId() + ")";
    }

    public float getFoodExhaustion() {
        return this.type().exhaustion();
    }

    public boolean isIndirect() {
        return this.causingEntity != this.directEntity;
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity source, @Nullable Entity attacker, @Nullable Vec3 position) {
        this.type = type;
        this.causingEntity = attacker;
        this.directEntity = source;
        this.damageSourcePosition = position;
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity source, @Nullable Entity attacker) {
        this(type, source, attacker, (Vec3) null);
    }

    public DamageSource(Holder<DamageType> type, Vec3 position) {
        this(type, (Entity) null, (Entity) null, position);
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity attacker) {
        this(type, attacker, attacker);
    }

    public DamageSource(Holder<DamageType> type) {
        this(type, (Entity) null, (Entity) null, (Vec3) null);
    }

    @Nullable
    public Entity getDirectEntity() {
        return this.directEntity;
    }

    @Nullable
    public Entity getEntity() {
        return this.causingEntity;
    }

    public Component getLocalizedDeathMessage(LivingEntity killed) {
        String s = "death.attack." + this.type().msgId();

        if (this.causingEntity == null && this.directEntity == null) {
            LivingEntity entityliving1 = killed.getKillCredit();
            String s1 = s + ".player";

            return entityliving1 != null ? Component.translatable(s1, killed.getDisplayName(), entityliving1.getDisplayName()) : Component.translatable(s, killed.getDisplayName());
        } else {
            Component ichatbasecomponent = this.causingEntity == null ? this.directEntity.getDisplayName() : this.causingEntity.getDisplayName();
            Entity entity = this.causingEntity;
            ItemStack itemstack;

            if (entity instanceof LivingEntity) {
                LivingEntity entityliving2 = (LivingEntity) entity;

                itemstack = entityliving2.getMainHandItem();
            } else {
                itemstack = ItemStack.EMPTY;
            }

            ItemStack itemstack1 = itemstack;

            return !itemstack1.isEmpty() && itemstack1.hasCustomHoverName() ? Component.translatable(s + ".item", killed.getDisplayName(), ichatbasecomponent, itemstack1.getDisplayName()) : Component.translatable(s, killed.getDisplayName(), ichatbasecomponent);
        }
    }

    public String getMsgId() {
        return this.type().msgId();
    }

    public boolean scalesWithDifficulty() {
        boolean flag;

        switch (this.type().scaling()) {
            case NEVER:
                flag = false;
                break;
            case WHEN_CAUSED_BY_LIVING_NON_PLAYER:
                flag = this.causingEntity instanceof LivingEntity && !(this.causingEntity instanceof Player);
                break;
            case ALWAYS:
                flag = true;
                break;
            default:
                throw new IncompatibleClassChangeError();
        }

        return flag;
    }

    public boolean isCreativePlayer() {
        Entity entity = this.getEntity();
        boolean flag;

        if (entity instanceof Player) {
            Player entityhuman = (Player) entity;

            if (entityhuman.getAbilities().instabuild) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    @Nullable
    public Vec3 getSourcePosition() {
        return this.damageSourcePosition != null ? this.damageSourcePosition : (this.directEntity != null ? this.directEntity.position() : null);
    }

    @Nullable
    public Vec3 sourcePositionRaw() {
        return this.damageSourcePosition;
    }

    public boolean is(TagKey<DamageType> tag) {
        return this.type.is(tag);
    }

    public boolean is(ResourceKey<DamageType> typeKey) {
        return this.type.is(typeKey);
    }

    public DamageType type() {
        return (DamageType) this.type.value();
    }

    public Holder<DamageType> typeHolder() {
        return this.type;
    }

    // Paper start - add critical damage API
    private boolean critical;
    public boolean isCritical() {
        return this.critical;
    }
    public DamageSource critical() {
        return this.critical(true);
    }
    public DamageSource critical(boolean critical) {
        this.critical = critical;
        return this;
    }
    // Paper end - add critical damage API
}
