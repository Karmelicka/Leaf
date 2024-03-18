package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class SpectralArrow extends AbstractArrow {

    private static final ItemStack DEFAULT_ARROW_STACK = new ItemStack(Items.SPECTRAL_ARROW);
    public int duration = 200;

    public SpectralArrow(EntityType<? extends SpectralArrow> type, Level world) {
        super(type, world, SpectralArrow.DEFAULT_ARROW_STACK);
    }

    public SpectralArrow(Level world, LivingEntity owner, ItemStack stack) {
        super(EntityType.SPECTRAL_ARROW, owner, world, stack);
    }

    public SpectralArrow(Level world, double x, double y, double z, ItemStack stack) {
        super(EntityType.SPECTRAL_ARROW, x, y, z, world, stack);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && !this.inGround) {
            this.level().addParticle(ParticleTypes.INSTANT_EFFECT, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }

    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        MobEffectInstance mobeffect = new MobEffectInstance(MobEffects.GLOWING, this.duration, 0);

        target.addEffect(mobeffect, this.getEffectSource(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Duration")) {
            this.duration = nbt.getInt("Duration");
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Duration", this.duration);
    }
}
