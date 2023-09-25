package net.minecraft.world.entity.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

public class Giant extends Monster {
    public Giant(EntityType<? extends Giant> type, Level world) {
        super(type, world);
        this.safeFallDistance = 10.0F; // Purpur
    }

    // Purpur start
    @Override
    public boolean isRidable() {
        return level().purpurConfig.giantRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.giantRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.giantControllable;
    }

    @Override
    protected void registerGoals() {
        if (level().purpurConfig.giantHaveAI) {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this));
            this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
            this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0D));
            if (level().purpurConfig.giantHaveHostileAI) {
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
                this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this));
                this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
                this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
                this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, false));
                this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
                this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, true));
            }
        }
    }

    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.giantTakeDamageFromWater;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.giantAlwaysDropExp;
    }
    // Purpur end

    @Override
    protected void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.giantMaxHealth);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.level().purpurConfig.giantMovementSpeed);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(this.level().purpurConfig.giantAttackDamage);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        SpawnGroupData groupData = super.finalizeSpawn(world, difficulty, spawnReason, entityData, entityNbt);
        if (groupData == null) {
            populateDefaultEquipmentSlots(this.random, difficulty);
            populateDefaultEquipmentEnchantments(this.random, difficulty);
        }
        return groupData;
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(this.random, difficulty);
        // TODO make configurable
        if (random.nextFloat() < (level().getDifficulty() == Difficulty.HARD ? 0.1F : 0.05F)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }
    }

    @Override
    public float getJumpPower() {
        // make giants jump as high as everything else relative to their size
        // 1.0 makes bottom of feet about as high as their waist when they jump
        return level().purpurConfig.giantJumpHeight;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 10.440001F;
    }

    @Override
    protected float ridingOffset(Entity vehicle) {
        return -3.75F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 100.0D).add(Attributes.MOVEMENT_SPEED, 0.5D).add(Attributes.ATTACK_DAMAGE, 50.0D);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return super.getWalkTargetValue(pos, world); // Purpur - fix light requirements for natural spawns
    }
}
