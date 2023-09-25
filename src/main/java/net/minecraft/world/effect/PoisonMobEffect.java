package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class PoisonMobEffect extends MobEffect {

    protected PoisonMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        super.applyEffectTick(entity, amplifier);
        if (entity.getHealth() > entity.level().purpurConfig.entityMinimalHealthPoison) { // Purpur
            entity.hurt(entity.damageSources().poison(), entity.level().purpurConfig.entityPoisonDegenerationAmount);  // CraftBukkit - DamageSource.MAGIC -> CraftEventFactory.POISON // Purpur
        }

    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int k = 25 >> amplifier;

        return k > 0 ? duration % k == 0 : true;
    }
}
