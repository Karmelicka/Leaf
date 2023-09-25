package net.minecraft.world.entity.item;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
// CraftBukkit end
import org.bukkit.event.player.PlayerAttemptPickupItemEvent; // Paper

public class ItemEntity extends Entity implements TraceableEntity {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final int LIFETIME = 6000;
    private static final int INFINITE_PICKUP_DELAY = 32767;
    private static final int INFINITE_LIFETIME = -32768;
    public int age;
    public int pickupDelay;
    public int health;
    @Nullable
    public UUID thrower;
    @Nullable
    private Entity cachedThrower;
    @Nullable
    public UUID target;
    public final float bobOffs;
    private int lastTick = MinecraftServer.currentTick - 1; // CraftBukkit
    public boolean canMobPickup = true; // Paper - Item#canEntityPickup
    private int despawnRate = -1; // Paper - Alternative item-despawn-rate
    public net.kyori.adventure.util.TriState frictionState = net.kyori.adventure.util.TriState.NOT_SET; // Paper - Friction API
    // Purpur start
    public boolean immuneToCactus = false;
    public boolean immuneToExplosion = false;
    public boolean immuneToFire = false;
    public boolean immuneToLightning = false;
    // Purpur end

    public ItemEntity(EntityType<? extends ItemEntity> type, Level world) {
        super(type, world);
        this.health = 5;
        this.bobOffs = this.random.nextFloat() * 3.1415927F * 2.0F;
        this.setYRot(this.random.nextFloat() * 360.0F);
    }

    public ItemEntity(Level world, double x, double y, double z, ItemStack stack) {
        // Paper start - Don't use level random in entity constructors (to make them thread-safe)
        this(EntityType.ITEM, world);
        this.setPos(x, y, z);
        this.setDeltaMovement(this.random.nextDouble() * 0.2D - 0.1D, 0.2D, this.random.nextDouble() * 0.2D - 0.1D);
        this.setItem(stack);
        // Paper end - Don't use level random in entity constructors
    }

    public ItemEntity(Level world, double x, double y, double z, ItemStack stack, double velocityX, double velocityY, double velocityZ) {
        this(EntityType.ITEM, world);
        this.setPos(x, y, z);
        this.setDeltaMovement(velocityX, velocityY, velocityZ);
        this.setItem(stack);
    }

    private ItemEntity(ItemEntity entity) {
        super(entity.getType(), entity.level());
        this.health = 5;
        this.setItem(entity.getItem().copy());
        this.copyPosition(entity);
        this.age = entity.age;
        this.bobOffs = entity.bobOffs;
    }

    @Override
    public boolean dampensVibrations() {
        return this.getItem().is(ItemTags.DAMPENS_VIBRATIONS);
    }

    @Nullable
    @Override
    public Entity getOwner() {
        if (this.cachedThrower != null && !this.cachedThrower.isRemoved()) {
            return this.cachedThrower;
        } else {
            if (this.thrower != null) {
                Level world = this.level();

                if (world instanceof ServerLevel) {
                    ServerLevel worldserver = (ServerLevel) world;

                    this.cachedThrower = worldserver.getEntity(this.thrower);
                    return this.cachedThrower;
                }
            }

            return null;
        }
    }

    @Override
    public void restoreFrom(Entity original) {
        super.restoreFrom(original);
        if (original instanceof ItemEntity) {
            ItemEntity entityitem = (ItemEntity) original;

            this.cachedThrower = entityitem.cachedThrower;
        }

    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(ItemEntity.DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            super.tick();
            // CraftBukkit start - Use wall time for pickup and despawn timers
            int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
            if (this.pickupDelay != 32767) this.pickupDelay -= elapsedTicks;
            this.pickupDelay = Math.max(0, this.pickupDelay); // Paper - don't go below 0
            if (this.age != -32768) this.age += elapsedTicks;
            this.lastTick = MinecraftServer.currentTick;
            // CraftBukkit end

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 vec3d = this.getDeltaMovement();
            float f = this.getEyeHeight() - 0.11111111F;

            if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > (double) f) {
                this.setUnderwaterMovement();
            } else if (this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > (double) f) {
                this.setUnderLavaMovement();
            } else if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            if (this.level().isClientSide) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7D));
                if (this.noPhysics) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
                }
            }

            if (!this.onGround() || this.getDeltaMovement().horizontalDistanceSqr() > 9.999999747378752E-6D || (this.tickCount + this.getId()) % 4 == 0) { // Paper - Diff on change
                this.move(MoverType.SELF, this.getDeltaMovement());
                float f1 = 0.98F;

                // Paper start - Friction API
                if (frictionState == net.kyori.adventure.util.TriState.FALSE) {
                    f1 = 1F;
                } else if (this.onGround()) {
                    // Paper end - Friction API
                    f1 = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f1, 0.98D, (double) f1));
                if (this.onGround()) {
                    Vec3 vec3d1 = this.getDeltaMovement();

                    if (vec3d1.y < 0.0D) {
                        this.setDeltaMovement(vec3d1.multiply(1.0D, -0.5D, 1.0D));
                    }
                }
            }

            boolean flag = Mth.floor(this.xo) != Mth.floor(this.getX()) || Mth.floor(this.yo) != Mth.floor(this.getY()) || Mth.floor(this.zo) != Mth.floor(this.getZ());
            int i = flag ? 2 : 40;

            if (this.tickCount % i == 0 && !this.level().isClientSide && this.isMergable()) {
                this.mergeWithNeighbours();
            }

            /* CraftBukkit start - moved up
            if (this.age != -32768) {
                ++this.age;
            }
            // CraftBukkit end */

            this.hasImpulse |= this.updateInWaterStateAndDoFluidPushing();
            if (!this.level().isClientSide) {
                double d0 = this.getDeltaMovement().subtract(vec3d).lengthSqr();

                if (d0 > 0.01D) {
                    this.hasImpulse = true;
                }
            }

            if (!this.level().isClientSide && this.age >= this.despawnRate) { // Spigot // Paper - Alternative item-despawn-rate
                // CraftBukkit start - fire ItemDespawnEvent
                if (CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                    this.age = 0;
                    return;
                }
                // CraftBukkit end
                this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                return; // Gale - EMC - reduce hopper item checks
            }
            this.markNearbyHopperCartsAsImmune(); // Gale - EMC - reduce hopper item checks

        }
    }

    // Gale start - EMC - reduce hopper item checks
    private void markNearbyHopperCartsAsImmune() {
        var config = level().galeConfig().smallOptimizations.reducedIntervals.checkNearbyItem.hopper.minecart;
        // No need to mark hopper minecarts as immune if they can pull every tick anyway
        if (config.interval <= 1) {
            return;
        }
        if (config.temporaryImmunity.duration > 0 && this.isAlive() && this.onGround && !this.isRemoved() && (config.temporaryImmunity.nearbyItemMaxAge == -1 || this.age <= config.temporaryImmunity.nearbyItemMaxAge) && this.age % Math.max(1, config.temporaryImmunity.checkForMinecartNearItemInterval) == 0 && config.temporaryImmunity.maxItemHorizontalDistance >= 0 && config.temporaryImmunity.maxItemVerticalDistance >= 0) {
            AABB aabb = this.getBoundingBox().inflate(config.temporaryImmunity.maxItemHorizontalDistance, config.temporaryImmunity.maxItemVerticalDistance, config.temporaryImmunity.maxItemHorizontalDistance);
            for (Entity entity : this.level().getEntities(this, aabb)) {
                if (entity instanceof MinecartHopper) {
                    ((MinecartHopper) entity).pickupImmunity = MinecraftServer.currentTick + config.temporaryImmunity.duration;
                }
            }
        }
    }
    // Gale end - EMC - reduce hopper item checks

    // Spigot start - copied from above
    @Override
    public void inactiveTick() {
        // CraftBukkit start - Use wall time for pickup and despawn timers
        int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
        if (this.pickupDelay != 32767) this.pickupDelay -= elapsedTicks;
        this.pickupDelay = Math.max(0, this.pickupDelay); // Paper - don't go below 0
        if (this.age != -32768) this.age += elapsedTicks;
        this.lastTick = MinecraftServer.currentTick;
        // CraftBukkit end

        if (!this.level().isClientSide && this.age >= this.despawnRate) { // Spigot // Paper - Alternative item-despawn-rate
            // CraftBukkit start - fire ItemDespawnEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                this.age = 0;
                return;
            }
            // CraftBukkit end
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            return; // Gale - EMC - reduce hopper item checks
        }
        // Gale start - EMC - reduce hopper item checks
        if (level().galeConfig().smallOptimizations.reducedIntervals.checkNearbyItem.hopper.minecart.temporaryImmunity.checkForMinecartNearItemWhileInactive) {
            this.markNearbyHopperCartsAsImmune();
        }
        // Gale end - EMC - reduce hopper item checks
    }
    // Spigot end

    @Override
    protected BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void setUnderwaterMovement() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x * 0.9900000095367432D, vec3d.y + (double) (vec3d.y < 0.05999999865889549D ? 5.0E-4F : 0.0F), vec3d.z * 0.9900000095367432D);
    }

    private void setUnderLavaMovement() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x * 0.949999988079071D, vec3d.y + (double) (vec3d.y < 0.05999999865889549D ? 5.0E-4F : 0.0F), vec3d.z * 0.949999988079071D);
    }

    private void mergeWithNeighbours() {
        if (this.isMergable()) {
            // Spigot start
            double radius = this.level().spigotConfig.itemMerge;
            List<ItemEntity> list = this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(radius, radius - 0.5D, radius), (entityitem) -> {
                // Spigot end
                return entityitem != this && entityitem.isMergable();
            });
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ItemEntity entityitem = (ItemEntity) iterator.next();

                if (entityitem.isMergable()) {
                    // Paper start - Fix items merging through walls
                    if (this.level().paperConfig().fixes.fixItemsMergingThroughWalls) {
                        if (this.level().clipDirect(this.position(), entityitem.position(),
                            net.minecraft.world.phys.shapes.CollisionContext.of(this)) == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                            continue;
                        }
                    }
                    // Paper end - Fix items merging through walls
                    this.tryToMerge(entityitem);
                    if (this.isRemoved()) {
                        break;
                    }
                }
            }

        }
    }

    private boolean isMergable() {
        ItemStack itemstack = this.getItem();

        return this.isAlive() && this.pickupDelay != 32767 && this.age != -32768 && this.age < this.despawnRate && itemstack.getCount() < itemstack.getMaxStackSize(); // Paper - Alternative item-despawn-rate
    }

    private void tryToMerge(ItemEntity other) {
        ItemStack itemstack = this.getItem();
        ItemStack itemstack1 = other.getItem();

        if (Objects.equals(this.target, other.target) && ItemEntity.areMergable(itemstack, itemstack1)) {
            if (true || itemstack1.getCount() < itemstack.getCount()) { // Spigot
                ItemEntity.merge(this, itemstack, other, itemstack1);
            } else {
                ItemEntity.merge(other, itemstack1, this, itemstack);
            }

        }
    }

    public static boolean areMergable(ItemStack stack1, ItemStack stack2) {
        return !stack2.is(stack1.getItem()) ? false : (stack2.getCount() + stack1.getCount() > stack2.getMaxStackSize() ? false : (stack2.hasTag() ^ stack1.hasTag() ? false : !stack2.hasTag() || stack2.getTag().equals(stack1.getTag())));
    }

    public static ItemStack merge(ItemStack stack1, ItemStack stack2, int maxCount) {
        int j = Math.min(Math.min(stack1.getMaxStackSize(), maxCount) - stack1.getCount(), stack2.getCount());
        ItemStack itemstack2 = stack1.copyWithCount(stack1.getCount() + j);

        stack2.shrink(j);
        return itemstack2;
    }

    private static void merge(ItemEntity targetEntity, ItemStack stack1, ItemStack stack2) {
        ItemStack itemstack2 = ItemEntity.merge(stack1, stack2, 64);

        targetEntity.setItem(itemstack2);
    }

    private static void merge(ItemEntity targetEntity, ItemStack targetStack, ItemEntity sourceEntity, ItemStack sourceStack) {
        // CraftBukkit start
        if (!CraftEventFactory.callItemMergeEvent(sourceEntity, targetEntity)) {
            return;
        }
        // CraftBukkit end
        ItemEntity.merge(targetEntity, targetStack, sourceStack);
        targetEntity.pickupDelay = Math.max(targetEntity.pickupDelay, sourceEntity.pickupDelay);
        targetEntity.age = Math.min(targetEntity.age, sourceEntity.age);
        if (sourceStack.isEmpty()) {
            sourceEntity.discard(EntityRemoveEvent.Cause.MERGE); // CraftBukkit - add Bukkit remove cause);
        }

    }

    @Override
    public boolean fireImmune() {
        return this.getItem().getItem().isFireResistant() || super.fireImmune();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Purpur start
        if (
            (immuneToCactus && source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)) ||
            (immuneToFire && (source.is(DamageTypeTags.IS_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE))) ||
            (immuneToLightning && source.is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT)) ||
            (immuneToExplosion && source.is(DamageTypeTags.IS_EXPLOSION))
        ) {
            return false;
        } else if (this.isInvulnerableTo(source)) {
        // Purpur end
            return false;
        } else if (!this.getItem().isEmpty() && this.getItem().is(Items.NETHER_STAR) && source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        } else if (!this.getItem().getItem().canBeHurtBy(source)) {
            return false;
        } else if (this.level().isClientSide) {
            return true;
        } else {
            // CraftBukkit start
            if (CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, amount)) {
                return false;
            }
            // CraftBukkit end
            this.markHurt();
            this.health = (int) ((float) this.health - amount);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
            if (this.health <= 0) {
                this.getItem().onDestroyed(this);
                this.discard(EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
            }

            return true;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        // Paper start - Friction API
        if (this.frictionState != net.kyori.adventure.util.TriState.NOT_SET) {
            nbt.putString("Paper.FrictionState", this.frictionState.toString());
        }
        // Paper end - Friction API
        nbt.putShort("Health", (short) this.health);
        nbt.putShort("Age", (short) this.age);
        nbt.putShort("PickupDelay", (short) this.pickupDelay);
        if (this.thrower != null) {
            nbt.putUUID("Thrower", this.thrower);
        }

        if (this.target != null) {
            nbt.putUUID("Owner", this.target);
        }

        if (!this.getItem().isEmpty()) {
            nbt.put("Item", this.getItem().save(new CompoundTag()));
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        this.health = nbt.getShort("Health");
        this.age = nbt.getShort("Age");
        if (nbt.contains("PickupDelay")) {
            this.pickupDelay = nbt.getShort("PickupDelay");
        }

        if (nbt.hasUUID("Owner")) {
            this.target = nbt.getUUID("Owner");
        }

        if (nbt.hasUUID("Thrower")) {
            this.thrower = nbt.getUUID("Thrower");
            this.cachedThrower = null;
        }

        // Paper start - Friction API
        if (nbt.contains("Paper.FrictionState")) {
            String fs = nbt.getString("Paper.FrictionState");
            try {
                frictionState = net.kyori.adventure.util.TriState.valueOf(fs);
            } catch (Exception ignored) {
                com.mojang.logging.LogUtils.getLogger().error("Unknown friction state " + fs + " for " + this);
            }
        }
        // Paper end - Friction API

        CompoundTag nbttagcompound1 = nbt.getCompound("Item");

        this.setItem(ItemStack.of(nbttagcompound1));
        if (this.getItem().isEmpty()) {
            this.discard(null); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    public void playerTouch(net.minecraft.world.entity.player.Player player) {
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            Item item = itemstack.getItem();
            int i = itemstack.getCount();

            // CraftBukkit start - fire PlayerPickupItemEvent
            int canHold = player.getInventory().canHold(itemstack);
            int remaining = i - canHold;
            boolean flyAtPlayer = false; // Paper

            // Paper start - PlayerAttemptPickupItemEvent
            if (this.pickupDelay <= 0) {
                PlayerAttemptPickupItemEvent attemptEvent = new PlayerAttemptPickupItemEvent((org.bukkit.entity.Player) player.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                this.level().getCraftServer().getPluginManager().callEvent(attemptEvent);

                flyAtPlayer = attemptEvent.getFlyAtPlayer();
                if (attemptEvent.isCancelled()) {
                    if (flyAtPlayer) {
                        player.take(this, i);
                    }

                    return;
                }
            }
            // Paper end - PlayerAttemptPickupItemEvent

            if (this.pickupDelay <= 0 && canHold > 0) {
                itemstack.setCount(canHold);
                // Call legacy event
                PlayerPickupItemEvent playerEvent = new PlayerPickupItemEvent((Player) player.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                playerEvent.setCancelled(!playerEvent.getPlayer().getCanPickupItems());
                this.level().getCraftServer().getPluginManager().callEvent(playerEvent);
                flyAtPlayer = playerEvent.getFlyAtPlayer(); // Paper
                if (playerEvent.isCancelled()) {
                    itemstack.setCount(i); // SPIGOT-5294 - restore count
                    // Paper start
                    if (flyAtPlayer) {
                        player.take(this, i);
                    }
                    // Paper end
                    return;
                }

                // Call newer event afterwards
                EntityPickupItemEvent entityEvent = new EntityPickupItemEvent((Player) player.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                entityEvent.setCancelled(!entityEvent.getEntity().getCanPickupItems());
                this.level().getCraftServer().getPluginManager().callEvent(entityEvent);
                if (entityEvent.isCancelled()) {
                    itemstack.setCount(i); // SPIGOT-5294 - restore count
                    return;
                }

                // Update the ItemStack if it was changed in the event
                ItemStack current = this.getItem();
                if (!itemstack.equals(current)) {
                    itemstack = current;
                } else {
                    itemstack.setCount(canHold + remaining); // = i
                }

                // Possibly < 0; fix here so we do not have to modify code below
                this.pickupDelay = 0;
            } else if (this.pickupDelay == 0) {
                // ensure that the code below isn't triggered if canHold says we can't pick the items up
                this.pickupDelay = -1;
            }
            // CraftBukkit end

            if (this.pickupDelay == 0 && (this.target == null || this.target.equals(player.getUUID())) && player.getInventory().add(itemstack)) {
                if (flyAtPlayer) // Paper - PlayerPickupItemEvent
                player.take(this, i);
                if (itemstack.isEmpty()) {
                    this.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
                    itemstack.setCount(i);
                }

                player.awardStat(Stats.ITEM_PICKED_UP.get(item), i);
                player.onItemPickup(this);
            }

        }
    }

    @Override
    public Component getName() {
        Component ichatbasecomponent = this.getCustomName();

        return (Component) (ichatbasecomponent != null ? ichatbasecomponent : Component.translatable(this.getItem().getDescriptionId()));
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel destination) {
        Entity entity = super.changeDimension(destination);

        if (!this.level().isClientSide && entity instanceof ItemEntity) {
            ((ItemEntity) entity).mergeWithNeighbours();
        }

        return entity;
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(ItemEntity.DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        this.getEntityData().set(ItemEntity.DATA_ITEM, stack);
        this.despawnRate = this.level().paperConfig().entities.spawning.altItemDespawnRate.enabled ? this.level().paperConfig().entities.spawning.altItemDespawnRate.items.getOrDefault(stack.getItem(), this.level().spigotConfig.itemDespawnRate) : this.level().spigotConfig.itemDespawnRate; // Paper - Alternative item-despawn-rate
        // Purpur start
        if (level().purpurConfig.itemImmuneToCactus.contains(stack.getItem())) immuneToCactus = true;
        if (level().purpurConfig.itemImmuneToExplosion.contains(stack.getItem())) immuneToExplosion = true;
        if (level().purpurConfig.itemImmuneToFire.contains(stack.getItem())) immuneToFire = true;
        if (level().purpurConfig.itemImmuneToLightning.contains(stack.getItem())) immuneToLightning = true;
        // level end
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (ItemEntity.DATA_ITEM.equals(data)) {
            this.getItem().setEntityRepresentation(this);
        }

    }

    public void setTarget(@Nullable UUID owner) {
        this.target = owner;
    }

    public void setThrower(Entity thrower) {
        this.thrower = thrower.getUUID();
        this.cachedThrower = thrower;
    }

    public int getAge() {
        return this.age;
    }

    public void setDefaultPickUpDelay() {
        this.pickupDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickupDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickupDelay = 32767;
    }

    public void setPickUpDelay(int pickupDelay) {
        this.pickupDelay = pickupDelay;
    }

    public boolean hasPickUpDelay() {
        return this.pickupDelay > 0;
    }

    public void setUnlimitedLifetime() {
        this.age = -32768;
    }

    public void setExtendedLifetime() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = this.despawnRate - 1; // Spigot // Paper - Alternative item-despawn-rate
    }

    public float getSpin(float tickDelta) {
        return ((float) this.getAge() + tickDelta) / 20.0F + this.bobOffs;
    }

    public ItemEntity copy() {
        return new ItemEntity(this);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return 180.0F - this.getSpin(0.5F) / 6.2831855F * 360.0F;
    }
}
