package net.minecraft.world.entity.animal;

import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BegGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// CraftBukkit start
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class Wolf extends TamableAnimal implements NeutralMob {

    private static final EntityDataAccessor<Boolean> DATA_INTERESTED_ID = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.INT);
    public static final Predicate<LivingEntity> PREY_SELECTOR = (entityliving) -> {
        EntityType<?> entitytypes = entityliving.getType();

        return entitytypes == EntityType.SHEEP || entitytypes == EntityType.RABBIT || entitytypes == EntityType.FOX;
    };
    // Purpur start - rabid wolf spawn chance
    private boolean isRabid = false;
    private static final Predicate<LivingEntity> RABID_PREDICATE = entity -> entity instanceof ServerPlayer || entity instanceof Mob;
    private final Goal PATHFINDER_VANILLA = new NonTameRandomTargetGoal<>(this, Animal.class, false, PREY_SELECTOR);
    private final Goal PATHFINDER_RABID = new NonTameRandomTargetGoal<>(this, LivingEntity.class, false, RABID_PREDICATE);
    private static final class AvoidRabidWolfGoal extends AvoidEntityGoal<Wolf> {
        private final Wolf wolf;

        public AvoidRabidWolfGoal(Wolf wolf, float distance, double minSpeed, double maxSpeed) {
            super(wolf, Wolf.class, distance, minSpeed, maxSpeed);
            this.wolf = wolf;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.wolf.isRabid() && this.toAvoid != null && this.toAvoid.isRabid(); // wolves which are not rabid run away from rabid wolves
        }

        @Override
        public void start() {
            this.wolf.setTarget(null);
            super.start();
        }

        @Override
        public void tick() {
            this.wolf.setTarget(null);
            super.tick();
        }
    }
    // Purpur end
    private static final float START_HEALTH = 8.0F;
    private static final float TAME_HEALTH = 20.0F;
    private float interestedAngle;
    private float interestedAngleO;
    private boolean isWet;
    private boolean isShaking;
    private float shakeAnim;
    private float shakeAnimO;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    @Nullable
    private UUID persistentAngerTarget;

    public Wolf(EntityType<? extends Wolf> type, Level world) {
        super(type, world);
        this.setTame(false);
        this.setPathfindingMalus(BlockPathTypes.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_POWDER_SNOW, -1.0F);
    }

    // Purpur start
    @Override
    public boolean isRidable() {
        return level().purpurConfig.wolfRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.wolfRidableInWater;
    }

    public void onMount(Player rider) {
        super.onMount(rider);
        setInSittingPose(false);
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.wolfControllable;
    }
    // Purpur end

    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.wolfMaxHealth);
    }

    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.wolfBreedingTicks;
    }

    public boolean isRabid() {
        return this.isRabid;
    }

    public void setRabid(boolean isRabid) {
        this.isRabid = isRabid;
        updatePathfinders(true);
    }

    public void updatePathfinders(boolean modifyEffects) {
        this.targetSelector.removeGoal(PATHFINDER_VANILLA);
        this.targetSelector.removeGoal(PATHFINDER_RABID);
        if (this.isRabid) {
            setTame(false);
            setOwnerUUID(null);
            this.targetSelector.addGoal(5, PATHFINDER_RABID);
            if (modifyEffects) this.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 1200));
        } else {
            this.targetSelector.addGoal(5, PATHFINDER_VANILLA);
            this.stopBeingAngry();
            if (modifyEffects) this.removeEffect(MobEffects.CONFUSION);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data, @Nullable CompoundTag nbt) {
        this.isRabid = world.getLevel().purpurConfig.wolfNaturalRabid > 0.0D && random.nextDouble() <= world.getLevel().purpurConfig.wolfNaturalRabid;
        this.updatePathfinders(false);
        return super.finalizeSpawn(world, difficulty, type, data, nbt);
    }

    @Override
    public void tame(Player player) {
        setCollarColor(level().purpurConfig.wolfDefaultCollarColor);
        super.tame(player);
    }

    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.wolfTakeDamageFromWater;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.wolfAlwaysDropExp;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur
        this.goalSelector.addGoal(1, new Wolf.WolfPanicGoal(1.5D));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new Wolf.WolfAvoidEntityGoal<>(this, Llama.class, 24.0F, 1.5D, 1.5D));
        this.goalSelector.addGoal(3, new AvoidRabidWolfGoal(this, 24.0F, 1.5D, 1.5D)); // Purpur
        this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(7, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new BegGoal(this, 8.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers());
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        // this.targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false, Wolf.PREY_SELECTOR)); // Purpur - moved to updatePathfinders()
        this.targetSelector.addGoal(6, new NonTameRandomTargetGoal<>(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
        this.targetSelector.addGoal(7, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, false));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).add(Attributes.MAX_HEALTH, 8.0D).add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Wolf.DATA_INTERESTED_ID, false);
        this.entityData.define(Wolf.DATA_COLLAR_COLOR, DyeColor.RED.getId());
        this.entityData.define(Wolf.DATA_REMAINING_ANGER_TIME, 0);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.WOLF_STEP, 0.15F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putByte("CollarColor", (byte) this.getCollarColor().getId());
        nbt.putBoolean("Purpur.IsRabid", this.isRabid); // Purpur
        this.addPersistentAngerSaveData(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("CollarColor", 99)) {
            this.setCollarColor(DyeColor.byId(nbt.getInt("CollarColor")));
        }
        // Purpur start
        this.isRabid = nbt.getBoolean("Purpur.IsRabid");
        this.updatePathfinders(false);
        // Purpur end

        this.readPersistentAngerSaveData(this.level(), nbt);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isAngry() ? SoundEvents.WOLF_GROWL : (this.random.nextInt(3) == 0 ? (this.isTame() && this.getHealth() < 10.0F ? SoundEvents.WOLF_WHINE : SoundEvents.WOLF_PANT) : SoundEvents.WOLF_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.isWet && !this.isShaking && !this.isPathFinding() && this.onGround()) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
            this.level().broadcastEntityEvent(this, (byte) 8);
        }

        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            // Purpur start
            if (this.age % 300 == 0 && this.isRabid()) {
                this.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400));
            }
            // Purpur end
            this.interestedAngleO = this.interestedAngle;
            if (this.isInterested()) {
                this.interestedAngle += (1.0F - this.interestedAngle) * 0.4F;
            } else {
                this.interestedAngle += (0.0F - this.interestedAngle) * 0.4F;
            }

            if (this.isInWaterRainOrBubble()) {
                this.isWet = true;
                if (this.isShaking && !this.level().isClientSide) {
                    this.level().broadcastEntityEvent(this, (byte) 56);
                    this.cancelShake();
                }
            } else if ((this.isWet || this.isShaking) && this.isShaking) {
                if (this.shakeAnim == 0.0F) {
                    this.playSound(SoundEvents.WOLF_SHAKE, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    this.gameEvent(GameEvent.ENTITY_ACTION);
                }

                this.shakeAnimO = this.shakeAnim;
                this.shakeAnim += 0.05F;
                if (this.shakeAnimO >= 2.0F) {
                    this.isWet = false;
                    this.isShaking = false;
                    this.shakeAnimO = 0.0F;
                    this.shakeAnim = 0.0F;
                }

                if (this.shakeAnim > 0.4F) {
                    float f = (float) this.getY();
                    int i = (int) (Mth.sin((this.shakeAnim - 0.4F) * 3.1415927F) * 7.0F);
                    Vec3 vec3d = this.getDeltaMovement();

                    for (int j = 0; j < i; ++j) {
                        float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
                        float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;

                        this.level().addParticle(ParticleTypes.SPLASH, this.getX() + (double) f1, (double) (f + 0.8F), this.getZ() + (double) f2, vec3d.x, vec3d.y, vec3d.z);
                    }
                }
            }

        }
    }

    private void cancelShake() {
        this.isShaking = false;
        this.shakeAnim = 0.0F;
        this.shakeAnimO = 0.0F;
    }

    @Override
    public void die(DamageSource damageSource) {
        this.isWet = false;
        this.isShaking = false;
        this.shakeAnimO = 0.0F;
        this.shakeAnim = 0.0F;
        super.die(damageSource);
    }

    public boolean isWet() {
        return this.isWet;
    }

    public float getWetShade(float tickDelta) {
        return Math.min(0.5F + Mth.lerp(tickDelta, this.shakeAnimO, this.shakeAnim) / 2.0F * 0.5F, 1.0F);
    }

    public float getBodyRollAngle(float tickDelta, float f1) {
        float f2 = (Mth.lerp(tickDelta, this.shakeAnimO, this.shakeAnim) + f1) / 1.8F;

        if (f2 < 0.0F) {
            f2 = 0.0F;
        } else if (f2 > 1.0F) {
            f2 = 1.0F;
        }

        return Mth.sin(f2 * 3.1415927F) * Mth.sin(f2 * 3.1415927F * 11.0F) * 0.15F * 3.1415927F;
    }

    public float getHeadRollAngle(float tickDelta) {
        return Mth.lerp(tickDelta, this.interestedAngleO, this.interestedAngle) * 0.15F * 3.1415927F;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.8F;
    }

    @Override
    public int getMaxHeadXRot() {
        return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            Entity entity = source.getEntity();

            // CraftBukkit - move diff down

            if (entity != null && !(entity instanceof Player) && !(entity instanceof AbstractArrow)) {
                amount = (amount + 1.0F) / 2.0F;
            }

            // CraftBukkit start
            boolean result = super.hurt(source, amount);
            if (!this.level().isClientSide && result) {
                this.setOrderedToSit(false);
            }
            return result;
            // CraftBukkit end
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean flag = target.hurt(this.damageSources().mobAttack(this), (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));

        if (flag) {
            this.doEnchantDamageEffects(this, target);
        }

        return flag;
    }

    @Override
    public void setTame(boolean tamed) {
        super.setTame(tamed);
        if (tamed) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
            this.setHealth(this.getMaxHealth()); // CraftBukkit - 20.0 -> getMaxHealth()
        } else {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(8.0D);
        }

        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0D);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();

        if (this.level().isClientSide) {
            boolean flag = this.isOwnedBy(player) || this.isTame() || itemstack.is(Items.BONE) && !this.isTame() && !this.isAngry();

            return flag ? InteractionResult.CONSUME : InteractionResult.PASS;
        } else if (this.isTame()) {
            if (this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }

                this.heal((float) item.getFoodProperties().getNutrition(), EntityRegainHealthEvent.RegainReason.EATING); // CraftBukkit
                return InteractionResult.SUCCESS;
            } else {
                if (item instanceof DyeItem) {
                    DyeItem itemdye = (DyeItem) item;

                    if (this.isOwnedBy(player)) {
                        DyeColor enumcolor = itemdye.getDyeColor();

                        if (enumcolor != this.getCollarColor()) {
                            // Paper start - Add EntityDyeEvent and CollarColorable interface
                            final io.papermc.paper.event.entity.EntityDyeEvent event = new io.papermc.paper.event.entity.EntityDyeEvent(this.getBukkitEntity(), org.bukkit.DyeColor.getByWoolData((byte) enumcolor.getId()), ((net.minecraft.server.level.ServerPlayer) player).getBukkitEntity());
                            if (!event.callEvent()) {
                                return InteractionResult.FAIL;
                            }
                            enumcolor = DyeColor.byId(event.getColor().getWoolData());
                            // Paper end - Add EntityDyeEvent and CollarColorable interface

                            this.setCollarColor(enumcolor);
                            if (!player.getAbilities().instabuild) {
                                itemstack.shrink(1);
                            }

                            return InteractionResult.SUCCESS;
                        }

                        return super.mobInteract(player, hand);
                    }
                }

                InteractionResult enuminteractionresult = super.mobInteract(player, hand);

                if ((!enuminteractionresult.consumesAction() || this.isBaby()) && this.isOwnedBy(player)) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    this.jumping = false;
                    this.navigation.stop();
                    this.setTarget((LivingEntity) null, EntityTargetEvent.TargetReason.FORGOT_TARGET, true); // CraftBukkit - reason
                    return InteractionResult.SUCCESS;
                } else {
                    return enuminteractionresult;
                }
            }
        } else if (itemstack.is(Items.BONE) && !this.isAngry()) {
            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }

            // CraftBukkit - added event call and isCancelled check.
            if ((this.level().purpurConfig.alwaysTameInCreative && player.getAbilities().instabuild) || (this.random.nextInt(3) == 0 && !CraftEventFactory.callEntityTameEvent(this, player).isCancelled())) { // Purpur
                this.tame(player);
                this.navigation.stop();
                this.setTarget((LivingEntity) null);
                this.setOrderedToSit(true);
                this.level().broadcastEntityEvent(this, (byte) 7);
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }

            return InteractionResult.SUCCESS;
        // Purpur start
        } else if (this.level().purpurConfig.wolfMilkCuresRabies && itemstack.getItem() == Items.MILK_BUCKET && this.isRabid()) {
            if (!player.isCreative()) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            this.setRabid(false);
            for (int i = 0; i < 10; ++i) {
                ((ServerLevel) level()).sendParticles(((ServerLevel) level()).players(), null, ParticleTypes.HAPPY_VILLAGER,
                        getX() + random.nextFloat(), getY() + (random.nextFloat() * 1.5), getZ() + random.nextFloat(), 1,
                        random.nextGaussian() * 0.05D, random.nextGaussian() * 0.05D, random.nextGaussian() * 0.05D, 0, true);
            }
            return InteractionResult.SUCCESS;
        // Purpur end
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 8) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
        } else if (status == 56) {
            this.cancelShake();
        } else {
            super.handleEntityEvent(status);
        }

    }

    public float getTailAngle() {
        return this.isAngry() ? 1.5393804F : (this.isTame() ? (0.55F - (this.getMaxHealth() - this.getHealth()) * 0.02F) * 3.1415927F : 0.62831855F);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        Item item = stack.getItem();

        return item.isEdible() && item.getFoodProperties().isMeat();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 8;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return (Integer) this.entityData.get(Wolf.DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int angerTime) {
        this.entityData.set(Wolf.DATA_REMAINING_ANGER_TIME, angerTime);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(Wolf.PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID angryAt) {
        this.persistentAngerTarget = angryAt;
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId((Integer) this.entityData.get(Wolf.DATA_COLLAR_COLOR));
    }

    public void setCollarColor(DyeColor color) {
        this.entityData.set(Wolf.DATA_COLLAR_COLOR, color.getId());
    }

    @Nullable
    @Override
    public Wolf getBreedOffspring(ServerLevel world, AgeableMob entity) {
        Wolf entitywolf = (Wolf) EntityType.WOLF.create(world);

        if (entitywolf != null) {
            UUID uuid = this.getOwnerUUID();

            if (uuid != null) {
                entitywolf.setOwnerUUID(uuid);
                entitywolf.setTame(true);
            }
        }

        return entitywolf;
    }

    public void setIsInterested(boolean begging) {
        this.entityData.set(Wolf.DATA_INTERESTED_ID, begging);
    }

    @Override
    public boolean canMate(Animal other) {
        if (other == this) {
            return false;
        } else if (!this.isTame()) {
            return false;
        } else if (!(other instanceof Wolf)) {
            return false;
        } else {
            Wolf entitywolf = (Wolf) other;

            return !entitywolf.isTame() ? false : (entitywolf.isInSittingPose() ? false : this.isInLove() && entitywolf.isInLove());
        }
    }

    public boolean isInterested() {
        return (Boolean) this.entityData.get(Wolf.DATA_INTERESTED_ID);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        // Leaf start - Improve readability
        if (target instanceof Creeper || target instanceof Ghast || target instanceof ArmorStand) { // Leaf - Fix MC-172047
            return false;
        }

        if (target instanceof Wolf entityWolf) {
            return !entityWolf.isTame() || entityWolf.getOwner() != owner;
        }

        if (target instanceof Player targetPlayer && owner instanceof Player ownerPlayer) {
            return ownerPlayer.canHarmPlayer(targetPlayer);
        }

        if (target instanceof AbstractHorse targetHorse) {
            return !targetHorse.isTamed();
        }

        if (target instanceof TamableAnimal tamableAnimalTarget) {
            return !tamableAnimalTarget.isTame();
        }

        return true;
        // Leaf end
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return !this.isAngry() && super.canBeLeashed(player);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.6F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height - 0.03125F * scaleFactor, -0.0625F * scaleFactor);
    }

    public static boolean checkWolfSpawnRules(EntityType<Wolf> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return world.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON) && isBrightEnoughToSpawn(world, pos);
    }

    private class WolfPanicGoal extends PanicGoal {

        public WolfPanicGoal(double d0) {
            super(Wolf.this, d0);
        }

        @Override
        protected boolean shouldPanic() {
            return this.mob.isFreezing() || this.mob.isOnFire();
        }
    }

    private class WolfAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Wolf wolf;

        public WolfAvoidEntityGoal(Wolf entitywolf, Class oclass, float f, double d0, double d1) {
            super(entitywolf, oclass, f, d0, d1);
            this.wolf = entitywolf;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.toAvoid instanceof Llama ? !this.wolf.isTame() && this.avoidLlama((Llama) this.toAvoid) : false;
        }

        private boolean avoidLlama(Llama llama) {
            return llama.getStrength() >= Wolf.this.random.nextInt(5);
        }

        @Override
        public void start() {
            Wolf.this.setTarget((LivingEntity) null);
            super.start();
        }

        @Override
        public void tick() {
            Wolf.this.setTarget((LivingEntity) null);
            super.tick();
        }
    }
}
