package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class WindCharge extends AbstractHurtingProjectile implements ItemSupplier {

    public static final WindCharge.WindChargeExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new WindCharge.WindChargeExplosionDamageCalculator();

    public WindCharge(EntityType<? extends WindCharge> type, Level world) {
        super(type, world);
    }

    public WindCharge(EntityType<? extends WindCharge> type, Breeze breeze, Level world) {
        super(type, breeze.getX(), breeze.getSnoutYPosition(), breeze.getZ(), world);
        this.setOwner(breeze);
    }

    @Override
    protected AABB makeBoundingBox() {
        float f = this.getType().getDimensions().width / 2.0F;
        float f1 = this.getType().getDimensions().height;
        float f2 = 0.15F;

        return new AABB(this.position().x - (double) f, this.position().y - 0.15000000596046448D, this.position().z - (double) f, this.position().x + (double) f, this.position().y - 0.15000000596046448D + (double) f1, this.position().z + (double) f);
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 0.0F;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return other instanceof WindCharge ? false : super.canCollideWith(other);
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return entity instanceof WindCharge ? false : super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (!this.level().isClientSide) {
            Entity entity = entityHitResult.getEntity();
            DamageSources damagesources = this.damageSources();
            Entity entity1 = this.getOwner();
            LivingEntity entityliving;

            if (entity1 instanceof LivingEntity) {
                LivingEntity entityliving1 = (LivingEntity) entity1;

                entityliving = entityliving1;
            } else {
                entityliving = null;
            }

            entity.hurt(damagesources.mobProjectile(this, entityliving), 1.0F);
            this.explode();
        }
    }

    public void explode() { // PAIL private -> public
        this.level().explode(this, (DamageSource) null, WindCharge.EXPLOSION_DAMAGE_CALCULATOR, this.getX(), this.getY(), this.getZ(), (float) (3.0D + this.random.nextDouble()), false, Level.ExplosionInteraction.BLOW, ParticleTypes.GUST, ParticleTypes.GUST_EMITTER, SoundEvents.WIND_BURST);
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        this.explode();
        this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public ItemStack getItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return this.getInertia();
    }

    @Nullable
    @Override
    protected ParticleOptions getTrailParticle() {
        return null;
    }

    @Override
    protected ClipContext.Block getClipType() {
        return ClipContext.Block.OUTLINE;
    }

    public static final class WindChargeExplosionDamageCalculator extends ExplosionDamageCalculator {

        public WindChargeExplosionDamageCalculator() {}

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }
    }
}
