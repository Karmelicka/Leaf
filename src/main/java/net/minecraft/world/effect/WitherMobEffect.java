package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class WitherMobEffect extends MobEffect {
    protected WitherMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        super.applyEffectTick(entity, amplifier);
        entity.hurt(entity.damageSources().wither(), 1.0F);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int i = 40 >> amplifier;
        if (i > 0) {
            return duration % i == 0;
        } else {
            return true;
        }
    }
}
