package net.minecraft.world.entity.animal.horse;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class ZombieHorse extends AbstractHorse {
    public ZombieHorse(EntityType<? extends ZombieHorse> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes().add(Attributes.MAX_HEALTH, 15.0D).add(Attributes.MOVEMENT_SPEED, (double)0.2F);
    }

    public static boolean checkZombieHorseSpawnRules(EntityType<? extends Animal> type, LevelAccessor world, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!MobSpawnType.isSpawner(reason)) {
            return Animal.checkAnimalSpawnRules(type, world, reason, pos, random);
        } else {
            return MobSpawnType.ignoresLightRequirements(reason) || isBrightEnoughToSpawn(world, pos);
        }
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(generateJumpStrength(random::nextDouble));
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_HORSE_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_HORSE_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HORSE_HURT;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel world, AgeableMob entity) {
        return EntityType.ZOMBIE_HORSE.create(world);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return !this.isTamed() ? InteractionResult.PASS : super.mobInteract(player, hand);
    }

    @Override
    protected void addBehaviourGoals() {
    }

    @Override
    protected float getPassengersRidingOffsetY(EntityDimensions dimensions, float scaleFactor) {
        return dimensions.height - (this.isBaby() ? 0.03125F : 0.28125F) * scaleFactor;
    }
}
