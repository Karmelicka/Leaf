package net.minecraft.server.level;

import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ComplexItem;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import org.slf4j.Logger;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.CraftWorldBorder;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.event.CraftPortalEvent;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftDimensionUtil;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedMainHandEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.MainHand;
// CraftBukkit end

public class ServerPlayer extends Player {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
    private static final int FLY_STAT_RECORDING_SPEED = 25;
    public ServerGamePacketListenerImpl connection;
    public final MinecraftServer server;
    public final ServerPlayerGameMode gameMode;
    private final PlayerAdvancements advancements;
    private final ServerStatsCounter stats;
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    public int lastSentExp = -99999999;
    public int spawnInvulnerableTime = 60;
    private ChatVisiblity chatVisibility;
    private boolean canChatColor;
    private long lastActionTime;
    @Nullable
    private Entity camera;
    public boolean isChangingDimension;
    public boolean seenCredits;
    private final ServerRecipeBook recipeBook;
    @Nullable
    private Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance;
    public String language = "en_us"; // CraftBukkit - default
    @Nullable
    private Vec3 startingToFallPosition;
    @Nullable
    private Vec3 enteredNetherPosition;
    @Nullable
    private Vec3 enteredLavaOnVehiclePosition;
    private SectionPos lastSectionPos;
    private ChunkTrackingView chunkTrackingView;
    private ResourceKey<Level> respawnDimension;
    @Nullable
    private BlockPos respawnPosition;
    private boolean respawnForced;
    private float respawnAngle;
    private final TextFilter textFilter;
    private boolean textFilteringEnabled;
    private boolean allowsListing;
    public WardenSpawnTracker wardenSpawnTracker;
    public final ContainerSynchronizer containerSynchronizer;
    private final ContainerListener containerListener;
    @Nullable
    private RemoteChatSession chatSession;
    private int containerCounter;
    public boolean wonGame;

    // CraftBukkit start
    public String displayName;
    public Component listName;
    public org.bukkit.Location compassTarget;
    public int newExp = 0;
    public int newLevel = 0;
    public int newTotalExp = 0;
    public boolean keepLevel = false;
    public double maxHealthCache;
    public boolean joining = true;
    public boolean sentListPacket = false;
    public String kickLeaveMessage = null; // SPIGOT-3034: Forward leave message to PlayerQuitEvent
    // CraftBukkit end
    public boolean isRealPlayer; // Paper
    public final com.destroystokyo.paper.util.misc.PooledLinkedHashSets.PooledObjectLinkedOpenHashSet<ServerPlayer> cachedSingleHashSet; // Paper

    public ServerPlayer(MinecraftServer server, ServerLevel world, GameProfile profile, ClientInformation clientOptions) {
        super(world, world.getSharedSpawnPos(), world.getSharedSpawnAngle(), profile);
        this.chatVisibility = ChatVisiblity.FULL;
        this.canChatColor = true;
        this.lastActionTime = Util.getMillis();
        this.recipeBook = new ServerRecipeBook();
        this.requestedViewDistance = 2;
        this.language = "en_us";
        this.lastSectionPos = SectionPos.of(0, 0, 0);
        this.chunkTrackingView = ChunkTrackingView.EMPTY;
        this.respawnDimension = Level.OVERWORLD;
        this.wardenSpawnTracker = new WardenSpawnTracker(0, 0, 0);
        this.containerSynchronizer = new ContainerSynchronizer() {
            @Override
            public void sendInitialData(AbstractContainerMenu handler, NonNullList<ItemStack> stacks, ItemStack cursorStack, int[] properties) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetContentPacket(handler.containerId, handler.incrementStateId(), stacks, cursorStack));

                for (int i = 0; i < properties.length; ++i) {
                    this.broadcastDataValue(handler, i, properties[i]);
                }

            }

            @Override
            public void sendSlotChange(AbstractContainerMenu handler, int slot, ItemStack stack) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(handler.containerId, handler.incrementStateId(), slot, stack));
            }

            @Override
            public void sendCarriedChange(AbstractContainerMenu handler, ItemStack stack) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(-1, handler.incrementStateId(), -1, stack));
            }

            @Override
            public void sendDataChange(AbstractContainerMenu handler, int property, int value) {
                this.broadcastDataValue(handler, property, value);
            }

            private void broadcastDataValue(AbstractContainerMenu handler, int property, int value) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetDataPacket(handler.containerId, property, value));
            }
        };
        this.containerListener = new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack stack) {
                Slot slot = handler.getSlot(slotId);

                if (!(slot instanceof ResultSlot)) {
                    if (slot.container == ServerPlayer.this.getInventory()) {
                        CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                    }

                }
            }

            @Override
            public void dataChanged(AbstractContainerMenu handler, int property, int value) {}
        };
        this.textFilter = server.createTextFilterForPlayer(this);
        this.gameMode = server.createGameModeForPlayer(this);
        this.server = server;
        this.stats = server.getPlayerList().getPlayerStats(this);
        this.advancements = server.getPlayerList().getPlayerAdvancements(this);
        this.setMaxUpStep(1.0F);
        this.fudgeSpawnLocation(world);
        this.updateOptions(clientOptions);

        this.cachedSingleHashSet = new com.destroystokyo.paper.util.misc.PooledLinkedHashSets.PooledObjectLinkedOpenHashSet<>(this); // Paper

        // CraftBukkit start
        this.displayName = this.getScoreboardName();
        this.bukkitPickUpLoot = true;
        this.maxHealthCache = this.getMaxHealth();
    }

    // Yes, this doesn't match Vanilla, but it's the best we can do for now.
    // If this is an issue, PRs are welcome
    public final BlockPos getSpawnPoint(ServerLevel worldserver) {
        BlockPos blockposition = worldserver.getSharedSpawnPos();

        if (worldserver.dimensionType().hasSkyLight() && worldserver.serverLevelData.getGameType() != GameType.ADVENTURE) {
            int i = Math.max(0, this.server.getSpawnRadius(worldserver));
            int j = Mth.floor(worldserver.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                BlockPos blockposition1 = PlayerRespawnLogic.getOverworldRespawnPos(worldserver, blockposition.getX() + j2 - i, blockposition.getZ() + k2 - i);

                if (blockposition1 != null) {
                    return blockposition1;
                }
            }
        }

        return blockposition;
    }
    // CraftBukkit end

    public void fudgeSpawnLocation(ServerLevel world) {
        BlockPos blockposition = world.getSharedSpawnPos();

        if (world.dimensionType().hasSkyLight() && world.serverLevelData.getGameType() != GameType.ADVENTURE) { // CraftBukkit
            int i = Math.max(0, this.server.getSpawnRadius(world));
            int j = Mth.floor(world.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                BlockPos blockposition1 = PlayerRespawnLogic.getOverworldRespawnPos(world, blockposition.getX() + j2 - i, blockposition.getZ() + k2 - i);

                if (blockposition1 != null) {
                    this.moveTo(blockposition1, 0.0F, 0.0F);
                    if (world.noCollision((Entity) this)) {
                        break;
                    }
                }
            }
        } else {
            this.moveTo(blockposition, 0.0F, 0.0F);

            while (!world.noCollision((Entity) this) && this.getY() < (double) (world.getMaxBuildHeight() - 1)) {
                this.setPos(this.getX(), this.getY() + 1.0D, this.getZ());
            }
        }

    }

    private int getCoprime(int horizontalSpawnArea) {
        return horizontalSpawnArea <= 16 ? horizontalSpawnArea - 1 : 17;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("warden_spawn_tracker", 10)) {
            DataResult<WardenSpawnTracker> dataresult = WardenSpawnTracker.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbt.get("warden_spawn_tracker"))); // CraftBukkit - decompile error
            Logger logger = ServerPlayer.LOGGER;

            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((wardenspawntracker) -> {
                this.wardenSpawnTracker = wardenspawntracker;
            });
        }

        if (nbt.contains("enteredNetherPosition", 10)) {
            CompoundTag nbttagcompound1 = nbt.getCompound("enteredNetherPosition");

            this.enteredNetherPosition = new Vec3(nbttagcompound1.getDouble("x"), nbttagcompound1.getDouble("y"), nbttagcompound1.getDouble("z"));
        }

        this.seenCredits = nbt.getBoolean("seenCredits");
        if (nbt.contains("recipeBook", 10)) {
            this.recipeBook.fromNbt(nbt.getCompound("recipeBook"), this.server.getRecipeManager());
        }
        this.getBukkitEntity().readExtraData(nbt); // CraftBukkit

        if (this.isSleeping()) {
            this.stopSleeping();
        }

        // CraftBukkit start
        String spawnWorld = nbt.getString("SpawnWorld");
        CraftWorld oldWorld = (CraftWorld) Bukkit.getWorld(spawnWorld);
        if (oldWorld != null) {
            this.respawnDimension = oldWorld.getHandle().dimension();
        }
        // CraftBukkit end

        if (nbt.contains("SpawnX", 99) && nbt.contains("SpawnY", 99) && nbt.contains("SpawnZ", 99)) {
            this.respawnPosition = new BlockPos(nbt.getInt("SpawnX"), nbt.getInt("SpawnY"), nbt.getInt("SpawnZ"));
            this.respawnForced = nbt.getBoolean("SpawnForced");
            this.respawnAngle = nbt.getFloat("SpawnAngle");
            if (nbt.contains("SpawnDimension")) {
                DataResult<ResourceKey<Level>> dataresult1 = Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, nbt.get("SpawnDimension")); // CraftBukkit - decompile error
                Logger logger1 = ServerPlayer.LOGGER;

                Objects.requireNonNull(logger1);
                this.respawnDimension = (ResourceKey) dataresult1.resultOrPartial(logger1::error).orElse(Level.OVERWORLD);
            }
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        DataResult<Tag> dataresult = WardenSpawnTracker.CODEC.encodeStart(NbtOps.INSTANCE, this.wardenSpawnTracker); // CraftBukkit - decompile error
        Logger logger = ServerPlayer.LOGGER;

        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbt.put("warden_spawn_tracker", nbtbase);
        });
        this.storeGameTypes(nbt);
        nbt.putBoolean("seenCredits", this.seenCredits);
        if (this.enteredNetherPosition != null) {
            CompoundTag nbttagcompound1 = new CompoundTag();

            nbttagcompound1.putDouble("x", this.enteredNetherPosition.x);
            nbttagcompound1.putDouble("y", this.enteredNetherPosition.y);
            nbttagcompound1.putDouble("z", this.enteredNetherPosition.z);
            nbt.put("enteredNetherPosition", nbttagcompound1);
        }

        Entity entity = this.getRootVehicle();
        Entity entity1 = this.getVehicle();

        // CraftBukkit start - handle non-persistent vehicles
        boolean persistVehicle = true;
        if (entity1 != null) {
            Entity vehicle;
            for (vehicle = entity1; vehicle != null; vehicle = vehicle.getVehicle()) {
                if (!vehicle.persist) {
                    persistVehicle = false;
                    break;
                }
            }
        }

        if (persistVehicle && entity1 != null && entity != this && entity.hasExactlyOnePlayerPassenger()) {
            // CraftBukkit end
            CompoundTag nbttagcompound2 = new CompoundTag();
            CompoundTag nbttagcompound3 = new CompoundTag();

            entity.save(nbttagcompound3);
            nbttagcompound2.putUUID("Attach", entity1.getUUID());
            nbttagcompound2.put("Entity", nbttagcompound3);
            nbt.put("RootVehicle", nbttagcompound2);
        }

        nbt.put("recipeBook", this.recipeBook.toNbt());
        nbt.putString("Dimension", this.level().dimension().location().toString());
        if (this.respawnPosition != null) {
            nbt.putInt("SpawnX", this.respawnPosition.getX());
            nbt.putInt("SpawnY", this.respawnPosition.getY());
            nbt.putInt("SpawnZ", this.respawnPosition.getZ());
            nbt.putBoolean("SpawnForced", this.respawnForced);
            nbt.putFloat("SpawnAngle", this.respawnAngle);
            dataresult = ResourceLocation.CODEC.encodeStart(NbtOps.INSTANCE, this.respawnDimension.location());
            logger = ServerPlayer.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbt.put("SpawnDimension", nbtbase);
            });
        }
        this.getBukkitEntity().setExtraData(nbt); // CraftBukkit

    }

    // CraftBukkit start - World fallback code, either respawn location or global spawn
    public void spawnIn(Level world) {
        this.setLevel(world);
        if (world == null) {
            this.unsetRemoved();
            Vec3 position = null;
            if (this.respawnDimension != null) {
                world = this.server.getLevel(this.respawnDimension);
                if (world != null && this.getRespawnPosition() != null) {
                    position = Player.findRespawnPositionAndUseSpawnBlock((ServerLevel) world, this.getRespawnPosition(), this.getRespawnAngle(), false, false).orElse(null);
                }
            }
            if (world == null || position == null) {
                world = ((CraftWorld) Bukkit.getServer().getWorlds().get(0)).getHandle();
                position = Vec3.atCenterOf(world.getSharedSpawnPos());
            }
            this.setLevel(world);
            this.setPos(position);
        }
        this.gameMode.setLevel((ServerLevel) world);
    }
    // CraftBukkit end

    public void setExperiencePoints(int points) {
        float f = (float) this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;

        this.experienceProgress = Mth.clamp((float) points / f, 0.0F, f1);
        this.lastSentExp = -1;
    }

    public void setExperienceLevels(int level) {
        this.experienceLevel = level;
        this.lastSentExp = -1;
    }

    @Override
    public void giveExperienceLevels(int levels) {
        super.giveExperienceLevels(levels);
        this.lastSentExp = -1;
    }

    @Override
    public void onEnchantmentPerformed(ItemStack enchantedItem, int experienceLevels) {
        super.onEnchantmentPerformed(enchantedItem, experienceLevels);
        this.lastSentExp = -1;
    }

    public void initMenu(AbstractContainerMenu screenHandler) {
        screenHandler.addSlotListener(this.containerListener);
        screenHandler.setSynchronizer(this.containerSynchronizer);
    }

    public void initInventoryMenu() {
        this.initMenu(this.inventoryMenu);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.send(new ClientboundPlayerCombatEnterPacket());
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        CriteriaTriggers.ENTER_BLOCK.trigger(this, state);
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new ServerItemCooldowns(this);
    }

    @Override
    public void tick() {
        // CraftBukkit start
        if (this.joining) {
            this.joining = false;
        }
        // CraftBukkit end
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        --this.spawnInvulnerableTime;
        if (this.invulnerableTime > 0) {
            --this.invulnerableTime;
        }

        this.containerMenu.broadcastChanges();
        if (!this.level().isClientSide && !this.containerMenu.stillValid(this)) {
            this.closeContainer();
            this.containerMenu = this.inventoryMenu;
        }

        Entity entity = this.getCamera();

        if (entity != this) {
            if (entity.isAlive()) {
                this.absMoveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                this.serverLevel().getChunkSource().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
            } else {
                this.setCamera(this);
            }
        }

        CriteriaTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.trackStartFallingPosition();
        this.trackEnteredOrExitedLavaOnVehicle();
        this.advancements.flushDirty(this);
    }

    public void doTick() {
        try {
            if (!this.isSpectator() || !this.touchingUnloadedChunk()) {
                super.tick();
            }

            for (int i = 0; i < this.getInventory().getContainerSize(); ++i) {
                ItemStack itemstack = this.getInventory().getItem(i);

                if (itemstack.getItem().isComplex()) {
                    Packet<?> packet = ((ComplexItem) itemstack.getItem()).getUpdatePacket(itemstack, this.level(), this);

                    if (packet != null) {
                        this.connection.send(packet);
                    }
                }
            }

            if (this.getHealth() != this.lastSentHealth || this.lastSentFood != this.foodData.getFoodLevel() || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.send(new ClientboundSetHealthPacket(this.getBukkitEntity().getScaledHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel())); // CraftBukkit
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float) this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float) this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float) this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float) this.lastRecordedExperience));
            }

            // CraftBukkit start - Force max health updates
            if (this.maxHealthCache != this.getMaxHealth()) {
                this.getBukkitEntity().updateScaledHealth();
            }
            // CraftBukkit end

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float) this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.send(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.tickCount % 20 == 0) {
                CriteriaTriggers.LOCATION.trigger(this);
            }

            // CraftBukkit start - initialize oldLevel, fire PlayerLevelChangeEvent, and tick client-sided world border
            if (this.oldLevel == -1) {
                this.oldLevel = this.experienceLevel;
            }

            if (this.oldLevel != this.experienceLevel) {
                CraftEventFactory.callPlayerLevelChangeEvent(this.getBukkitEntity(), this.oldLevel, this.experienceLevel);
                this.oldLevel = this.experienceLevel;
            }

            if (this.getBukkitEntity().hasClientWorldBorder()) {
                ((CraftWorldBorder) this.getBukkitEntity().getWorldBorder()).getHandle().tick();
            }
            // CraftBukkit end
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking player");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Player being ticked");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    @Override
    public void resetFallDistance() {
        if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
            CriteriaTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
        }

        this.startingToFallPosition = null;
        super.resetFallDistance();
    }

    public void trackStartFallingPosition() {
        if (this.fallDistance > 0.0F && this.startingToFallPosition == null) {
            this.startingToFallPosition = this.position();
        }

    }

    public void trackEnteredOrExitedLavaOnVehicle() {
        if (this.getVehicle() != null && this.getVehicle().isInLava()) {
            if (this.enteredLavaOnVehiclePosition == null) {
                this.enteredLavaOnVehiclePosition = this.position();
            } else {
                CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
            }
        }

        if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
            this.enteredLavaOnVehiclePosition = null;
        }

    }

    private void updateScoreForCriteria(ObjectiveCriteria criterion, int score) {
        // CraftBukkit - Use our scores instead
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(criterion, this, (scoreaccess) -> {
            scoreaccess.set(score);
        });
    }

    @Override
    public void die(DamageSource damageSource) {
        this.gameEvent(GameEvent.ENTITY_DIE);
        boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
        // CraftBukkit start - fire PlayerDeathEvent
        if (this.isRemoved()) {
            return;
        }
        java.util.List<org.bukkit.inventory.ItemStack> loot = new java.util.ArrayList<org.bukkit.inventory.ItemStack>(this.getInventory().getContainerSize());
        boolean keepInventory = this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || this.isSpectator();

        if (!keepInventory) {
            for (ItemStack item : this.getInventory().getContents()) {
                if (!item.isEmpty() && !EnchantmentHelper.hasVanishingCurse(item)) {
                    loot.add(CraftItemStack.asCraftMirror(item));
                }
            }
        }
        // SPIGOT-5071: manually add player loot tables (SPIGOT-5195 - ignores keepInventory rule)
        this.dropFromLootTable(damageSource, this.lastHurtByPlayerTime > 0);
        for (org.bukkit.inventory.ItemStack item : this.drops) {
            loot.add(item);
        }
        this.drops.clear(); // SPIGOT-5188: make sure to clear

        Component defaultMessage = this.getCombatTracker().getDeathMessage();

        String deathmessage = defaultMessage.getString();
        this.keepLevel = keepInventory; // SPIGOT-2222: pre-set keepLevel
        org.bukkit.event.entity.PlayerDeathEvent event = CraftEventFactory.callPlayerDeathEvent(this, loot, deathmessage, keepInventory);

        // SPIGOT-943 - only call if they have an inventory open
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        String deathMessage = event.getDeathMessage();

        if (deathMessage != null && deathMessage.length() > 0 && flag) { // TODO: allow plugins to override?
            Component ichatbasecomponent;
            if (deathMessage.equals(deathmessage)) {
                ichatbasecomponent = this.getCombatTracker().getDeathMessage();
            } else {
                ichatbasecomponent = org.bukkit.craftbukkit.util.CraftChatMessage.fromStringOrNull(deathMessage);
            }

            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), ichatbasecomponent), PacketSendListener.exceptionallySend(() -> {
                boolean flag1 = true;
                String s = ichatbasecomponent.getString(256);
                MutableComponent ichatmutablecomponent = Component.translatable("death.attack.message_too_long", Component.literal(s).withStyle(ChatFormatting.YELLOW));
                MutableComponent ichatmutablecomponent1 = Component.translatable("death.attack.even_more_magic", this.getDisplayName()).withStyle((chatmodifier) -> {
                    return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ichatmutablecomponent));
                });

                return new ClientboundPlayerCombatKillPacket(this.getId(), ichatmutablecomponent1);
            }));
            PlayerTeam scoreboardteam = this.getTeam();

            if (scoreboardteam != null && scoreboardteam.getDeathMessageVisibility() != Team.Visibility.ALWAYS) {
                if (scoreboardteam.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                    this.server.getPlayerList().broadcastSystemToTeam(this, ichatbasecomponent);
                } else if (scoreboardteam.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                    this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, ichatbasecomponent);
                }
            } else {
                this.server.getPlayerList().broadcastSystemMessage(ichatbasecomponent, false);
            }
        } else {
            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }

        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }
        // SPIGOT-5478 must be called manually now
        this.dropExperience();
        // we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.
        if (!event.getKeepInventory()) {
            this.getInventory().clearContent();
        }

        this.setCamera(this); // Remove spectated target
        // CraftBukkit end

        // CraftBukkit - Get our scores instead
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment);
        LivingEntity entityliving = this.getKillCredit();

        if (entityliving != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(entityliving.getType()));
            entityliving.awardKillScore(this, this.deathScore, damageSource);
            this.createWitherRose(entityliving);
        }

        this.level().broadcastEntityEvent(this, (byte) 3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    private void tellNeutralMobsThatIDied() {
        AABB axisalignedbb = (new AABB(this.blockPosition())).inflate(32.0D, 10.0D, 32.0D);

        this.level().getEntitiesOfClass(Mob.class, axisalignedbb, EntitySelector.NO_SPECTATORS).stream().filter((entityinsentient) -> {
            return entityinsentient instanceof NeutralMob;
        }).forEach((entityinsentient) -> {
            ((NeutralMob) entityinsentient).playerDied(this);
        });
    }

    @Override
    public void awardKillScore(Entity entityKilled, int score, DamageSource damageSource) {
        if (entityKilled != this) {
            super.awardKillScore(entityKilled, score, damageSource);
            this.increaseScore(score);
            // CraftBukkit - Get our scores instead
            this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment);
            if (entityKilled instanceof Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                // CraftBukkit - Get our scores instead
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment);
            } else {
                this.awardStat(Stats.MOB_KILLS);
            }

            this.handleTeamKill(this, entityKilled, ObjectiveCriteria.TEAM_KILL);
            this.handleTeamKill(entityKilled, this, ObjectiveCriteria.KILLED_BY_TEAM);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, entityKilled, damageSource);
        }
    }

    private void handleTeamKill(ScoreHolder targetScoreHolder, ScoreHolder aboutScoreHolder, ObjectiveCriteria[] criterions) {
        PlayerTeam scoreboardteam = this.getScoreboard().getPlayersTeam(aboutScoreHolder.getScoreboardName());

        if (scoreboardteam != null) {
            int i = scoreboardteam.getColor().getId();

            if (i >= 0 && i < criterions.length) {
                // CraftBukkit - Get our scores instead
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(criterions[i], targetScoreHolder, ScoreAccess::increment);
            }
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            boolean flag = this.server.isDedicatedServer() && this.isPvpAllowed() && source.is(DamageTypeTags.IS_FALL);

            if (!flag && this.spawnInvulnerableTime > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            } else {
                Entity entity = source.getEntity();

                if (entity instanceof Player) {
                    Player entityhuman = (Player) entity;

                    if (!this.canHarmPlayer(entityhuman)) {
                        return false;
                    }
                }

                if (entity instanceof AbstractArrow) {
                    AbstractArrow entityarrow = (AbstractArrow) entity;
                    Entity entity1 = entityarrow.getOwner();

                    if (entity1 instanceof Player) {
                        Player entityhuman1 = (Player) entity1;

                        if (!this.canHarmPlayer(entityhuman1)) {
                            return false;
                        }
                    }
                }

                return super.hurt(source, amount);
            }
        }
    }

    @Override
    public boolean canHarmPlayer(Player player) {
        return !this.isPvpAllowed() ? false : super.canHarmPlayer(player);
    }

    private boolean isPvpAllowed() {
        // CraftBukkit - this.server.isPvpAllowed() -> this.world.pvpMode
        return this.level().pvpMode;
    }

    @Nullable
    @Override
    protected PortalInfo findDimensionEntryPoint(ServerLevel destination) {
        PortalInfo shapedetectorshape = super.findDimensionEntryPoint(destination);
        destination = (shapedetectorshape == null) ? destination : shapedetectorshape.world; // CraftBukkit

        if (shapedetectorshape != null && this.level().getTypeKey() == LevelStem.OVERWORLD && destination != null && destination.getTypeKey() == LevelStem.END) { // CraftBukkit
            Vec3 vec3d = shapedetectorshape.pos.add(0.0D, -1.0D, 0.0D);

            return new PortalInfo(vec3d, Vec3.ZERO, 90.0F, 0.0F, destination, shapedetectorshape.portalEventInfo); // CraftBukkit
        } else {
            return shapedetectorshape;
        }
    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel destination) {
        // CraftBukkit start
        return this.changeDimension(destination, TeleportCause.UNKNOWN);
    }

    @Nullable
    public Entity changeDimension(ServerLevel worldserver, PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        if (this.isSleeping()) return this; // CraftBukkit - SPIGOT-3154
        // this.isChangingDimension = true; // CraftBukkit - Moved down and into PlayerList#changeDimension
        ServerLevel worldserver1 = this.serverLevel();
        ResourceKey<LevelStem> resourcekey = worldserver1.getTypeKey(); // CraftBukkit

        if (resourcekey == LevelStem.END && worldserver != null && worldserver.getTypeKey() == LevelStem.OVERWORLD) { // CraftBukkit
            this.isChangingDimension = true; // CraftBukkit - Moved down from above
            this.unRide();
            this.serverLevel().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
            if (!this.wonGame) {
                this.wonGame = true;
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, this.seenCredits ? 0.0F : 1.0F));
                this.seenCredits = true;
            }

            return this;
        } else {
            // CraftBukkit start
            /*
            WorldData worlddata = worldserver.getLevelData();

            this.connection.send(new PacketPlayOutRespawn(this.createCommonSpawnInfo(worldserver), (byte) 3));
            this.connection.send(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
            PlayerList playerlist = this.server.getPlayerList();

            playerlist.sendPlayerPermissionLevel(this);
            worldserver1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
            this.unsetRemoved();
            */
            // CraftBukkit end
            PortalInfo shapedetectorshape = this.findDimensionEntryPoint(worldserver);

            if (shapedetectorshape != null) {
                worldserver1.getProfiler().push("moving");
                worldserver = shapedetectorshape.world; // CraftBukkit
                if (worldserver == null) { } else // CraftBukkit - empty to fall through to null to event
                if (resourcekey == LevelStem.OVERWORLD && worldserver.getTypeKey() == LevelStem.NETHER) { // CraftBukkit
                    this.enteredNetherPosition = this.position();
                } else if (worldserver.getTypeKey() == LevelStem.END && shapedetectorshape.portalEventInfo != null && shapedetectorshape.portalEventInfo.getCanCreatePortal()) { // CraftBukkit
                    this.createEndPlatform(worldserver, BlockPos.containing(shapedetectorshape.pos));
                }
                // CraftBukkit start
            } else {
                return null;
            }
            Location enter = this.getBukkitEntity().getLocation();
            Location exit = (worldserver == null) ? null : CraftLocation.toBukkit(shapedetectorshape.pos, worldserver.getWorld(), shapedetectorshape.yRot, shapedetectorshape.xRot);
            PlayerTeleportEvent tpEvent = new PlayerTeleportEvent(this.getBukkitEntity(), enter, exit, cause);
            Bukkit.getServer().getPluginManager().callEvent(tpEvent);
            if (tpEvent.isCancelled() || tpEvent.getTo() == null) {
                return null;
            }
            exit = tpEvent.getTo();
            worldserver = ((CraftWorld) exit.getWorld()).getHandle();
            // CraftBukkit end

            worldserver1.getProfiler().pop();
            worldserver1.getProfiler().push("placing");
            if (true) { // CraftBukkit
                this.isChangingDimension = true; // CraftBukkit - Set teleport invulnerability only if player changing worlds

                this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(worldserver), (byte) 3));
                this.connection.send(new ClientboundChangeDifficultyPacket(this.level().getDifficulty(), this.level().getLevelData().isDifficultyLocked()));
                PlayerList playerlist = this.server.getPlayerList();

                playerlist.sendPlayerPermissionLevel(this);
                worldserver1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();

                // CraftBukkit end
                this.setServerLevel(worldserver);
                this.connection.teleport(exit); // CraftBukkit - use internal teleport without event
                this.connection.resetPosition();
                worldserver.addDuringPortalTeleport(this);
                worldserver1.getProfiler().pop();
                this.triggerDimensionChangeTriggers(worldserver1);
                this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
                playerlist.sendLevelInfo(this, worldserver);
                playerlist.sendAllPlayerInfo(this);
                Iterator iterator = this.getActiveEffects().iterator();

                while (iterator.hasNext()) {
                    MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                    this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), mobeffect));
                }

                this.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
                this.lastSentExp = -1;
                this.lastSentHealth = -1.0F;
                this.lastSentFood = -1;

                // CraftBukkit start
                PlayerChangedWorldEvent changeEvent = new PlayerChangedWorldEvent(this.getBukkitEntity(), worldserver1.getWorld());
                this.level().getCraftServer().getPluginManager().callEvent(changeEvent);
                // CraftBukkit end
            }

            return this;
        }
    }

    // CraftBukkit start
    @Override
    protected CraftPortalEvent callPortalEvent(Entity entity, ServerLevel exitWorldServer, Vec3 exitPosition, TeleportCause cause, int searchRadius, int creationRadius) {
        Location enter = this.getBukkitEntity().getLocation();
        Location exit = CraftLocation.toBukkit(exitPosition, exitWorldServer.getWorld(), this.getYRot(), this.getXRot());
        PlayerPortalEvent event = new PlayerPortalEvent(this.getBukkitEntity(), enter, exit, cause, searchRadius, true, creationRadius);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) {
            return null;
        }
        return new CraftPortalEvent(event);
    }
    // CraftBukkit end

    private void createEndPlatform(ServerLevel world, BlockPos centerPos) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = centerPos.mutable();
        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(world); // CraftBukkit

        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                for (int k = -1; k < 3; ++k) {
                    BlockState iblockdata = k == -1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();

                    blockList.setBlock(blockposition_mutableblockposition.set(centerPos).move(j, k, i), iblockdata, 3); // CraftBukkit
                }
            }
        }
        // CraftBukkit start - call portal event
        org.bukkit.event.world.PortalCreateEvent portalEvent = new org.bukkit.event.world.PortalCreateEvent((List<org.bukkit.block.BlockState>) (List) blockList.getList(), world.getWorld(), this.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.END_PLATFORM);
        world.getCraftServer().getPluginManager().callEvent(portalEvent);
        if (!portalEvent.isCancelled()) {
            blockList.updateList();
        }
        // CraftBukkit end

    }

    @Override
    protected Optional<BlockUtil.FoundRectangle> getExitPortal(ServerLevel worldserver, BlockPos blockposition, boolean flag, WorldBorder worldborder, int searchRadius, boolean canCreatePortal, int createRadius) { // CraftBukkit
        Optional<BlockUtil.FoundRectangle> optional = super.getExitPortal(worldserver, blockposition, flag, worldborder, searchRadius, canCreatePortal, createRadius); // CraftBukkit

        if (optional.isPresent() || !canCreatePortal) { // CraftBukkit
            return optional;
        } else {
            Direction.Axis enumdirection_enumaxis = (Direction.Axis) this.level().getBlockState(this.portalEntrancePos).getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
            Optional<BlockUtil.FoundRectangle> optional1 = worldserver.getPortalForcer().createPortal(blockposition, enumdirection_enumaxis, this, createRadius); // CraftBukkit

            if (optional1.isEmpty()) {
                // EntityPlayer.LOGGER.error("Unable to create a portal, likely target out of worldborder"); // CraftBukkit
            }

            return optional1;
        }
    }

    public void triggerDimensionChangeTriggers(ServerLevel origin) {
        ResourceKey<Level> resourcekey = origin.dimension();
        ResourceKey<Level> resourcekey1 = this.level().dimension();
        // CraftBukkit start
        ResourceKey<Level> maindimensionkey = CraftDimensionUtil.getMainDimensionKey(origin);
        ResourceKey<Level> maindimensionkey1 = CraftDimensionUtil.getMainDimensionKey(this.level());

        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, maindimensionkey, maindimensionkey1);
        if (maindimensionkey != resourcekey || maindimensionkey1 != resourcekey1) {
            CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourcekey, resourcekey1);
        }

        if (maindimensionkey == Level.NETHER && maindimensionkey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            // CraftBukkit end
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (maindimensionkey1 != Level.NETHER) { // CraftBukkit
            this.enteredNetherPosition = null;
        }

    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer spectator) {
        return spectator.isSpectator() ? this.getCamera() == this : (this.isSpectator() ? false : super.broadcastToPlayer(spectator));
    }

    @Override
    public void take(Entity item, int count) {
        super.take(item, count);
        this.containerMenu.broadcastChanges();
    }

    // CraftBukkit start - moved bed result checks from below into separate method
    private Either<Player.BedSleepingProblem, Unit> getBedResult(BlockPos blockposition, Direction enumdirection) {
        if (!this.isSleeping() && this.isAlive()) {
            if (!this.level().dimensionType().natural() || !this.level().dimensionType().bedWorks()) {
                return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
            } else if (!this.bedInRange(blockposition, enumdirection)) {
                return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
            } else if (this.bedBlocked(blockposition, enumdirection)) {
                return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
            } else {
                this.setRespawnPosition(this.level().dimension(), blockposition, this.getYRot(), false, true, PlayerSpawnChangeEvent.Cause.BED); // CraftBukkit
                if (this.level().isDay()) {
                    return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
                } else {
                    if (!this.isCreative()) {
                        double d0 = 8.0D;
                        double d1 = 5.0D;
                        Vec3 vec3d = Vec3.atBottomCenterOf(blockposition);
                        List<Monster> list = this.level().getEntitiesOfClass(Monster.class, new AABB(vec3d.x() - 8.0D, vec3d.y() - 5.0D, vec3d.z() - 8.0D, vec3d.x() + 8.0D, vec3d.y() + 5.0D, vec3d.z() + 8.0D), (entitymonster) -> {
                            return entitymonster.isPreventingPlayerRest(this);
                        });

                        if (!list.isEmpty()) {
                            return Either.left(Player.BedSleepingProblem.NOT_SAFE);
                        }
                    }

                    return Either.right(Unit.INSTANCE);
                }
            }
        } else {
            return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    @Override
    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos blockposition, boolean force) {
        Direction enumdirection = (Direction) this.level().getBlockState(blockposition).getValue(HorizontalDirectionalBlock.FACING);
        Either<Player.BedSleepingProblem, Unit> bedResult = this.getBedResult(blockposition, enumdirection);

        if (bedResult.left().orElse(null) == Player.BedSleepingProblem.OTHER_PROBLEM) {
            return bedResult; // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        bedResult = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedEnterEvent(this, blockposition, bedResult);
        if (bedResult.left().isPresent()) {
            return bedResult;
        }

        {
            {
                {
                    Either<Player.BedSleepingProblem, Unit> either = super.startSleepInBed(blockposition, force).ifRight((unit) -> {
                        this.awardStat(Stats.SLEEP_IN_BED);
                        CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                    });

                    if (!this.serverLevel().canSleepThroughNights()) {
                        this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
                    }

                    ((ServerLevel) this.level()).updateSleepingPlayerList();
                    return either;
                }
            }
        }
        // CraftBukkit end
    }

    @Override
    public void startSleeping(BlockPos pos) {
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        super.startSleeping(pos);
    }

    private boolean bedInRange(BlockPos pos, Direction direction) {
        return this.isReachableBedBlock(pos) || this.isReachableBedBlock(pos.relative(direction.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPos pos) {
        Vec3 vec3d = Vec3.atBottomCenterOf(pos);

        return Math.abs(this.getX() - vec3d.x()) <= 3.0D && Math.abs(this.getY() - vec3d.y()) <= 2.0D && Math.abs(this.getZ() - vec3d.z()) <= 3.0D;
    }

    private boolean bedBlocked(BlockPos pos, Direction direction) {
        BlockPos blockposition1 = pos.above();

        return !this.freeAt(blockposition1) || !this.freeAt(blockposition1.relative(direction.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean skipSleepTimer, boolean updateSleepingPlayers) {
        if (!this.isSleeping()) return; // CraftBukkit - Can't leave bed if not in one!
        // CraftBukkit start - fire PlayerBedLeaveEvent
        CraftPlayer player = this.getBukkitEntity();
        BlockPos bedPosition = this.getSleepingPos().orElse(null);

        org.bukkit.block.Block bed;
        if (bedPosition != null) {
            bed = this.level().getWorld().getBlockAt(bedPosition.getX(), bedPosition.getY(), bedPosition.getZ());
        } else {
            bed = this.level().getWorld().getBlockAt(player.getLocation());
        }

        PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(player, bed, true);
        this.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        // CraftBukkit end
        if (this.isSleeping()) {
            this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(this, 2));
        }

        super.stopSleepInBed(skipSleepTimer, updateSleepingPlayers);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), TeleportCause.EXIT_BED); // CraftBukkit
        }

    }

    @Override
    public void dismountTo(double destX, double destY, double destZ) {
        this.removeVehicle();
        this.setPos(destX, destY, destZ);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return super.isInvulnerableTo(damageSource) || this.isChangingDimension();
    }

    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {}

    @Override
    protected void onChangedBlock(BlockPos pos) {
        if (!this.isSpectator()) {
            super.onChangedBlock(pos);
        }

    }

    public void doCheckFallDamage(double xDifference, double yDifference, double zDifference, boolean onGround) {
        if (!this.touchingUnloadedChunk()) {
            this.checkSupportingBlock(onGround, new Vec3(xDifference, yDifference, zDifference));
            BlockPos blockposition = this.getOnPosLegacy();

            super.checkFallDamage(yDifference, onGround, this.level().getBlockState(blockposition), blockposition);
        }
    }

    @Override
    protected void pushEntities() {
        if (this.level().tickRateManager().runsNormally()) {
            super.pushEntities();
        }

    }

    @Override
    public void openTextEdit(SignBlockEntity sign, boolean front) {
        this.connection.send(new ClientboundBlockUpdatePacket(this.level(), sign.getBlockPos()));
        this.connection.send(new ClientboundOpenSignEditorPacket(sign.getBlockPos(), front));
    }

    public int nextContainerCounter() { // CraftBukkit - void -> int
        this.containerCounter = this.containerCounter % 100 + 1;
        return this.containerCounter; // CraftBukkit
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider factory) {
        if (factory == null) {
            return OptionalInt.empty();
        } else {
            // CraftBukkit start - SPIGOT-6552: Handle inventory closing in CraftEventFactory#callInventoryOpenEvent(...)
            /*
            if (this.containerMenu != this.inventoryMenu) {
                this.closeContainer();
            }
            */
            // CraftBukkit end

            this.nextContainerCounter();
            AbstractContainerMenu container = factory.createMenu(this.containerCounter, this.getInventory(), this);

            // CraftBukkit start - Inventory open hook
            if (container != null) {
                container.setTitle(factory.getDisplayName());

                boolean cancelled = false;
                container = CraftEventFactory.callInventoryOpenEvent(this, container, cancelled);
                if (container == null && !cancelled) { // Let pre-cancelled events fall through
                    // SPIGOT-5263 - close chest if cancelled
                    if (factory instanceof Container) {
                        ((Container) factory).stopOpen(this);
                    } else if (factory instanceof ChestBlock.DoubleInventory) {
                        // SPIGOT-5355 - double chests too :(
                        ((ChestBlock.DoubleInventory) factory).inventorylargechest.stopOpen(this);
                    }
                    return OptionalInt.empty();
                }
            }
            // CraftBukkit end
            if (container == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                // CraftBukkit start
                this.containerMenu = container;
                this.connection.send(new ClientboundOpenScreenPacket(container.containerId, container.getType(), container.getTitle()));
                // CraftBukkit end
                this.initMenu(container);
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void sendMerchantOffers(int syncId, MerchantOffers offers, int levelProgress, int experience, boolean leveled, boolean refreshable) {
        this.connection.send(new ClientboundMerchantOffersPacket(syncId, offers, levelProgress, experience, leveled, refreshable));
    }

    @Override
    public void openHorseInventory(AbstractHorse horse, Container inventory) {
        // CraftBukkit start - Inventory open hook
        this.nextContainerCounter();
        AbstractContainerMenu container = new HorseInventoryMenu(this.containerCounter, this.getInventory(), inventory, horse);
        container.setTitle(horse.getDisplayName());
        container = CraftEventFactory.callInventoryOpenEvent(this, container);

        if (container == null) {
            inventory.stopOpen(this);
            return;
        }
        // CraftBukkit end
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        // this.nextContainerCounter(); // CraftBukkit - moved up
        this.connection.send(new ClientboundHorseScreenOpenPacket(this.containerCounter, inventory.getContainerSize(), horse.getId()));
        this.containerMenu = container; // CraftBukkit
        this.initMenu(this.containerMenu);
    }

    @Override
    public void openItemGui(ItemStack book, InteractionHand hand) {
        if (book.is(Items.WRITTEN_BOOK)) {
            if (WrittenBookItem.resolveBookComponents(book, this.createCommandSourceStack(), this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.send(new ClientboundOpenBookPacket(hand));
        }

    }

    @Override
    public void openCommandBlock(CommandBlockEntity commandBlock) {
        this.connection.send(ClientboundBlockEntityDataPacket.create(commandBlock, BlockEntity::saveWithoutMetadata));
    }

    @Override
    public void closeContainer() {
        CraftEventFactory.handleInventoryCloseEvent(this); // CraftBukkit
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }

    @Override
    public void doCloseContainer() {
        this.containerMenu.removed(this);
        this.inventoryMenu.transferState(this.containerMenu);
        this.containerMenu = this.inventoryMenu;
    }

    public void setPlayerInput(float sidewaysSpeed, float forwardSpeed, boolean jumping, boolean sneaking) {
        if (this.isPassenger()) {
            if (sidewaysSpeed >= -1.0F && sidewaysSpeed <= 1.0F) {
                this.xxa = sidewaysSpeed;
            }

            if (forwardSpeed >= -1.0F && forwardSpeed <= 1.0F) {
                this.zza = forwardSpeed;
            }

            this.jumping = jumping;
            // CraftBukkit start
            if (sneaking != this.isShiftKeyDown()) {
                PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getBukkitEntity(), sneaking);
                this.server.server.getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
            }
            // CraftBukkit end
            this.setShiftKeyDown(sneaking);
        }

    }

    @Override
    public void travel(Vec3 movementInput) {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.travel(movementInput);
        this.checkMovementStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    @Override
    public void rideTick() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.rideTick();
        this.checkRidingStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    public void checkMovementStatistics(double deltaX, double deltaY, double deltaZ) {
        if (!this.isPassenger() && !ServerPlayer.didNotMove(deltaX, deltaY, deltaZ)) {
            int i;

            if (this.isSwimming()) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.SWIM_ONE_CM, i);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.SWIM); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isEyeInFluid(FluidTags.WATER)) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, i);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK_UNDERWATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isInWater()) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, i);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK_ON_WATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.onClimbable()) {
                if (deltaY > 0.0D) {
                    this.awardStat(Stats.CLIMB_ONE_CM, (int) Math.round(deltaY * 100.0D));
                }
            } else if (this.onGround()) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 100.0F);
                if (i > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(Stats.SPRINT_ONE_CM, i);
                        this.causeFoodExhaustion(this.level().spigotConfig.sprintMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, i);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.CROUCH); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, i);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK); // CraftBukkit - EntityExhaustionEvent // Spigot
                    }
                }
            } else if (this.isFallFlying()) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 100.0F);
                this.awardStat(Stats.AVIATE_ONE_CM, i);
            } else {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 100.0F);
                if (i > 25) {
                    this.awardStat(Stats.FLY_ONE_CM, i);
                }
            }

        }
    }

    private void checkRidingStatistics(double deltaX, double deltaY, double deltaZ) {
        if (this.isPassenger() && !ServerPlayer.didNotMove(deltaX, deltaY, deltaZ)) {
            int i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 100.0F);
            Entity entity = this.getVehicle();

            if (entity instanceof AbstractMinecart) {
                this.awardStat(Stats.MINECART_ONE_CM, i);
            } else if (entity instanceof Boat) {
                this.awardStat(Stats.BOAT_ONE_CM, i);
            } else if (entity instanceof Pig) {
                this.awardStat(Stats.PIG_ONE_CM, i);
            } else if (entity instanceof AbstractHorse) {
                this.awardStat(Stats.HORSE_ONE_CM, i);
            } else if (entity instanceof Strider) {
                this.awardStat(Stats.STRIDER_ONE_CM, i);
            }

        }
    }

    private static boolean didNotMove(double deltaX, double deltaY, double deltaZ) {
        return deltaX == 0.0D && deltaY == 0.0D && deltaZ == 0.0D;
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
        this.stats.increment(this, stat, amount);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(stat, this, (scoreaccess) -> {
            scoreaccess.add(amount);
        });
    }

    @Override
    public void resetStat(Stat<?> stat) {
        this.stats.setValue(this, stat, 0);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(stat, this, ScoreAccess::reset); // CraftBukkit - Get our scores instead
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.addRecipes(recipes, this);
    }

    @Override
    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> ingredients) {
        CriteriaTriggers.RECIPE_CRAFTED.trigger(this, recipe.id(), ingredients);
    }

    @Override
    public void awardRecipesByKey(List<ResourceLocation> recipes) {
        List<RecipeHolder<?>> list1 = (List) recipes.stream().flatMap((minecraftkey) -> {
            return this.server.getRecipeManager().byKey(minecraftkey).stream();
        }).collect(Collectors.toList());

        this.awardRecipes(list1);
    }

    @Override
    public int resetRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.removeRecipes(recipes, this);
    }

    @Override
    public void giveExperiencePoints(int experience) {
        super.giveExperiencePoints(experience);
        this.lastSentExp = -1;
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }

    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
        this.lastSentExp = -1; // CraftBukkit - Added to reset
    }

    @Override
    public void displayClientMessage(Component message, boolean overlay) {
        this.sendSystemMessage(message, overlay);
    }

    @Override
    protected void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.send(new ClientboundEntityEventPacket(this, (byte) 9));
            super.completeUsingItem();
        }

    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchorPoint, Vec3 target) {
        super.lookAt(anchorPoint, target);
        this.connection.send(new ClientboundPlayerLookAtPacket(anchorPoint, target.x, target.y, target.z));
    }

    public void lookAt(EntityAnchorArgument.Anchor anchorPoint, Entity targetEntity, EntityAnchorArgument.Anchor targetAnchor) {
        Vec3 vec3d = targetAnchor.apply(targetEntity);

        super.lookAt(anchorPoint, vec3d);
        this.connection.send(new ClientboundPlayerLookAtPacket(anchorPoint, targetEntity, targetAnchor));
    }

    public void restoreFrom(ServerPlayer oldPlayer, boolean alive) {
        this.wardenSpawnTracker = oldPlayer.wardenSpawnTracker;
        this.chatSession = oldPlayer.chatSession;
        this.gameMode.setGameModeForPlayer(oldPlayer.gameMode.getGameModeForPlayer(), oldPlayer.gameMode.getPreviousGameModeForPlayer());
        this.onUpdateAbilities();
        if (alive) {
            this.getInventory().replaceWith(oldPlayer.getInventory());
            this.setHealth(oldPlayer.getHealth());
            this.foodData = oldPlayer.foodData;
            this.experienceLevel = oldPlayer.experienceLevel;
            this.totalExperience = oldPlayer.totalExperience;
            this.experienceProgress = oldPlayer.experienceProgress;
            this.setScore(oldPlayer.getScore());
            this.portalEntrancePos = oldPlayer.portalEntrancePos;
        } else if (this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || oldPlayer.isSpectator()) {
            this.getInventory().replaceWith(oldPlayer.getInventory());
            this.experienceLevel = oldPlayer.experienceLevel;
            this.totalExperience = oldPlayer.totalExperience;
            this.experienceProgress = oldPlayer.experienceProgress;
            this.setScore(oldPlayer.getScore());
        }

        this.enchantmentSeed = oldPlayer.enchantmentSeed;
        this.enderChestInventory = oldPlayer.enderChestInventory;
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (Byte) oldPlayer.getEntityData().get(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        // this.recipeBook.copyOverData(entityplayer.recipeBook); // CraftBukkit
        this.seenCredits = oldPlayer.seenCredits;
        this.enteredNetherPosition = oldPlayer.enteredNetherPosition;
        this.chunkTrackingView = oldPlayer.chunkTrackingView;
        this.setShoulderEntityLeft(oldPlayer.getShoulderEntityLeft());
        this.setShoulderEntityRight(oldPlayer.getShoulderEntityRight());
        this.setLastDeathLocation(oldPlayer.getLastDeathLocation());
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effect, @Nullable Entity source) {
        super.onEffectAdded(effect, source);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effect));
        if (effect.getEffect() == MobEffects.LEVITATION) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, source);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean reapplyEffect, @Nullable Entity source) {
        super.onEffectUpdated(effect, reapplyEffect, source);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effect));
        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, source);
    }

    @Override
    protected void onEffectRemoved(MobEffectInstance effect) {
        super.onEffectRemoved(effect);
        this.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), effect.getEffect()));
        if (effect.getEffect() == MobEffects.LEVITATION) {
            this.levitationStartPos = null;
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, (Entity) null);
    }

    @Override
    public void teleportTo(double destX, double destY, double destZ) {
        this.connection.teleport(destX, destY, destZ, this.getYRot(), this.getXRot(), RelativeMovement.ROTATION);
    }

    @Override
    public void teleportRelative(double offsetX, double offsetY, double offsetZ) {
        this.connection.teleport(this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ, this.getYRot(), this.getXRot(), RelativeMovement.ALL);
    }

    @Override
    public boolean teleportTo(ServerLevel world, double destX, double destY, double destZ, Set<RelativeMovement> flags, float yaw, float pitch) {
        // CraftBukkit start
        return this.teleportTo(world, destX, destY, destZ, flags, yaw, pitch, TeleportCause.UNKNOWN);
    }

    public boolean teleportTo(ServerLevel worldserver, double d0, double d1, double d2, Set<RelativeMovement> set, float f, float f1, TeleportCause cause) {
        // CraftBukkit end
        ChunkPos chunkcoordintpair = new ChunkPos(BlockPos.containing(d0, d1, d2));

        worldserver.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkcoordintpair, 1, this.getId());
        this.stopRiding();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (worldserver == this.level()) {
            this.connection.teleport(d0, d1, d2, f, f1, set, cause); // CraftBukkit
        } else {
            this.teleportTo(worldserver, d0, d1, d2, f, f1, cause); // CraftBukkit
        }

        this.setYHeadRot(f);
        return true;
    }

    @Override
    public void moveTo(double x, double y, double z) {
        super.moveTo(x, y, z);
        this.connection.resetPosition();
    }

    @Override
    public void crit(Entity target) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(target, 4));
    }

    @Override
    public void magicCrit(Entity target) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(target, 5));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
            this.updateInvisibilityStatus();
        }
    }

    public ServerLevel serverLevel() {
        return (ServerLevel) this.level();
    }

    public boolean setGameMode(GameType gameMode) {
        if (!this.gameMode.changeGameModeForPlayer(gameMode)) {
            return false;
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float) gameMode.getId()));
            if (gameMode == GameType.SPECTATOR) {
                this.removeEntitiesOnShoulder();
                this.stopRiding();
            } else {
                this.setCamera(this);
            }

            this.onUpdateAbilities();
            this.updateEffectVisibility();
            return true;
        }
    }

    @Override
    public boolean isSpectator() {
        return this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
    }

    @Override
    public boolean isCreative() {
        return this.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    @Override
    public void sendSystemMessage(Component message) {
        this.sendSystemMessage(message, false);
    }

    public void sendSystemMessage(Component message, boolean overlay) {
        if (this.acceptsSystemMessages(overlay)) {
            this.connection.send(new ClientboundSystemChatPacket(message, overlay), PacketSendListener.exceptionallySend(() -> {
                if (this.acceptsSystemMessages(false)) {
                    boolean flag1 = true;
                    String s = message.getString(256);
                    MutableComponent ichatmutablecomponent = Component.literal(s).withStyle(ChatFormatting.YELLOW);

                    return new ClientboundSystemChatPacket(Component.translatable("multiplayer.message_not_delivered", ichatmutablecomponent).withStyle(ChatFormatting.RED), false);
                } else {
                    return null;
                }
            }));
        }
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filterMaskEnabled, ChatType.Bound params) {
        if (this.acceptsChatMessages()) {
            message.sendToPlayer(this, filterMaskEnabled, params);
        }

    }

    public String getIpAddress() {
        SocketAddress socketaddress = this.connection.getRemoteAddress();

        if (socketaddress instanceof InetSocketAddress) {
            InetSocketAddress inetsocketaddress = (InetSocketAddress) socketaddress;

            return InetAddresses.toAddrString(inetsocketaddress.getAddress());
        } else {
            return "<unknown>";
        }
    }

    public void updateOptions(ClientInformation clientOptions) {
        // CraftBukkit start
        if (this.getMainArm() != clientOptions.mainHand()) {
            PlayerChangedMainHandEvent event = new PlayerChangedMainHandEvent(this.getBukkitEntity(), this.getMainArm() == HumanoidArm.LEFT ? MainHand.LEFT : MainHand.RIGHT);
            this.server.server.getPluginManager().callEvent(event);
        }
        if (!this.language.equals(clientOptions.language())) {
            PlayerLocaleChangeEvent event = new PlayerLocaleChangeEvent(this.getBukkitEntity(), clientOptions.language());
            this.server.server.getPluginManager().callEvent(event);
        }
        // CraftBukkit end
        this.language = clientOptions.language();
        this.requestedViewDistance = clientOptions.viewDistance();
        this.chatVisibility = clientOptions.chatVisibility();
        this.canChatColor = clientOptions.chatColors();
        this.textFilteringEnabled = clientOptions.textFilteringEnabled();
        this.allowsListing = clientOptions.allowsListing();
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (byte) clientOptions.modelCustomisation());
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MAIN_HAND, (byte) clientOptions.mainHand().getId());
    }

    public ClientInformation clientInformation() {
        byte b0 = (Byte) this.getEntityData().get(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION);
        HumanoidArm enummainhand = (HumanoidArm) HumanoidArm.BY_ID.apply((Byte) this.getEntityData().get(ServerPlayer.DATA_PLAYER_MAIN_HAND));

        return new ClientInformation(this.language, this.requestedViewDistance, this.chatVisibility, this.canChatColor, b0, enummainhand, this.textFilteringEnabled, this.allowsListing);
    }

    public boolean canChatInColor() {
        return this.canChatColor;
    }

    public ChatVisiblity getChatVisibility() {
        return this.chatVisibility;
    }

    private boolean acceptsSystemMessages(boolean overlay) {
        return this.chatVisibility == ChatVisiblity.HIDDEN ? overlay : true;
    }

    private boolean acceptsChatMessages() {
        return this.chatVisibility == ChatVisiblity.FULL;
    }

    public int requestedViewDistance() {
        return this.requestedViewDistance;
    }

    public void sendServerStatus(ServerStatus metadata) {
        this.connection.send(new ClientboundServerDataPacket(metadata.description(), metadata.favicon().map(ServerStatus.Favicon::iconBytes), metadata.enforcesSecureChat()));
    }

    @Override
    protected int getPermissionLevel() {
        return this.server.getProfilePermissions(this.getGameProfile());
    }

    public void resetLastActionTime() {
        this.lastActionTime = Util.getMillis();
    }

    public ServerStatsCounter getStats() {
        return this.stats;
    }

    public ServerRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }

    }

    public Entity getCamera() {
        return (Entity) (this.camera == null ? this : this.camera);
    }

    public void setCamera(@Nullable Entity entity) {
        Entity entity1 = this.getCamera();

        this.camera = (Entity) (entity == null ? this : entity);
        if (entity1 != this.camera) {
            Level world = this.camera.level();

            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;

                this.teleportTo(worldserver, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot(), TeleportCause.SPECTATE); // CraftBukkit
            }

            if (entity != null) {
                this.serverLevel().getChunkSource().move(this);
            }

            this.connection.send(new ClientboundSetCameraPacket(this.camera));
            this.connection.resetPosition();
        }

    }

    @Override
    protected void processPortalCooldown() {
        if (!this.isChangingDimension) {
            super.processPortalCooldown();
        }

    }

    @Override
    public void attack(Entity target) {
        if (this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            this.setCamera(target);
        } else {
            super.attack(target);
        }

    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    @Nullable
    public Component getTabListDisplayName() {
        return this.listName; // CraftBukkit
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public PlayerAdvancements getAdvancements() {
        return this.advancements;
    }

    // CraftBukkit start
    public void teleportTo(ServerLevel targetWorld, double x, double y, double z, float yaw, float pitch) {
        this.teleportTo(targetWorld, x, y, z, yaw, pitch, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public void teleportTo(ServerLevel worldserver, double d0, double d1, double d2, float f, float f1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        this.setCamera(this);
        this.stopRiding();
        /* CraftBukkit start - replace with bukkit handling for multi-world
        if (worldserver == this.level()) {
            this.connection.teleport(d0, d1, d2, f, f1);
        } else {
            WorldServer worldserver1 = this.serverLevel();
            WorldData worlddata = worldserver.getLevelData();

            this.connection.send(new PacketPlayOutRespawn(this.createCommonSpawnInfo(worldserver), (byte) 3));
            this.connection.send(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
            this.server.getPlayerList().sendPlayerPermissionLevel(this);
            worldserver1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
            this.unsetRemoved();
            this.moveTo(d0, d1, d2, f, f1);
            this.setServerLevel(worldserver);
            worldserver.addDuringCommandTeleport(this);
            this.triggerDimensionChangeTriggers(worldserver1);
            this.connection.teleport(d0, d1, d2, f, f1);
            this.server.getPlayerList().sendLevelInfo(this, worldserver);
            this.server.getPlayerList().sendAllPlayerInfo(this);
        }
        */
        this.getBukkitEntity().teleport(new Location(worldserver.getWorld(), d0, d1, d2, f, f1), cause);
        // CraftBukkit end

    }

    @Nullable
    public BlockPos getRespawnPosition() {
        return this.respawnPosition;
    }

    public float getRespawnAngle() {
        return this.respawnAngle;
    }

    public ResourceKey<Level> getRespawnDimension() {
        return this.respawnDimension;
    }

    public boolean isRespawnForced() {
        return this.respawnForced;
    }

    public void setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos pos, float angle, boolean forced, boolean sendMessage) {
        // CraftBukkit start
        this.setRespawnPosition(dimension, pos, angle, forced, sendMessage, PlayerSpawnChangeEvent.Cause.UNKNOWN);
    }

    public void setRespawnPosition(ResourceKey<Level> resourcekey, @Nullable BlockPos blockposition, float f, boolean flag, boolean flag1, PlayerSpawnChangeEvent.Cause cause) {
        ServerLevel newWorld = this.server.getLevel(resourcekey);
        Location newSpawn = (blockposition != null) ? CraftLocation.toBukkit(blockposition, newWorld.getWorld(), f, 0) : null;

        PlayerSpawnChangeEvent event = new PlayerSpawnChangeEvent(this.getBukkitEntity(), newSpawn, flag, cause);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        newSpawn = event.getNewSpawn();
        flag = event.isForced();

        if (newSpawn != null) {
            resourcekey = ((CraftWorld) newSpawn.getWorld()).getHandle().dimension();
            blockposition = BlockPos.containing(newSpawn.getX(), newSpawn.getY(), newSpawn.getZ());
            f = newSpawn.getYaw();
        } else {
            resourcekey = Level.OVERWORLD;
            blockposition = null;
            f = 0.0F;
        }
        // CraftBukkit end
        if (blockposition != null) {
            boolean flag2 = blockposition.equals(this.respawnPosition) && resourcekey.equals(this.respawnDimension);

            if (flag1 && !flag2) {
                this.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
            }

            this.respawnPosition = blockposition;
            this.respawnDimension = resourcekey;
            this.respawnAngle = f;
            this.respawnForced = flag;
        } else {
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnAngle = 0.0F;
            this.respawnForced = false;
        }

    }

    public SectionPos getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPos section) {
        this.lastSectionPos = section;
    }

    public ChunkTrackingView getChunkTrackingView() {
        return this.chunkTrackingView;
    }

    public void setChunkTrackingView(ChunkTrackingView chunkFilter) {
        this.chunkTrackingView = chunkFilter;
    }

    @Override
    public void playNotifySound(SoundEvent event, SoundSource category, float volume, float pitch) {
        this.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), category, this.getX(), this.getY(), this.getZ(), volume, pitch, this.random.nextLong()));
    }

    @Override
    public ItemEntity drop(ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
        ItemEntity entityitem = super.drop(stack, throwRandomly, retainOwnership);

        if (entityitem == null) {
            return null;
        } else {
            this.level().addFreshEntity(entityitem);
            ItemStack itemstack1 = entityitem.getItem();

            if (retainOwnership) {
                if (!itemstack1.isEmpty()) {
                    this.awardStat(Stats.ITEM_DROPPED.get(itemstack1.getItem()), stack.getCount());
                }

                this.awardStat(Stats.DROP);
            }

            return entityitem;
        }
    }

    public TextFilter getTextFilter() {
        return this.textFilter;
    }

    public void setServerLevel(ServerLevel world) {
        this.setLevel(world);
        this.gameMode.setLevel(world);
    }

    @Nullable
    private static GameType readPlayerMode(@Nullable CompoundTag nbt, String key) {
        return nbt != null && nbt.contains(key, 99) ? GameType.byId(nbt.getInt(key)) : null;
    }

    private GameType calculateGameModeForNewPlayer(@Nullable GameType backupGameMode) {
        GameType enumgamemode1 = this.server.getForcedGameType();

        return enumgamemode1 != null ? enumgamemode1 : (backupGameMode != null ? backupGameMode : this.server.getDefaultGameType());
    }

    public void loadGameTypes(@Nullable CompoundTag nbt) {
        this.gameMode.setGameModeForPlayer(this.calculateGameModeForNewPlayer(ServerPlayer.readPlayerMode(nbt, "playerGameType")), ServerPlayer.readPlayerMode(nbt, "previousPlayerGameType"));
    }

    private void storeGameTypes(CompoundTag nbt) {
        nbt.putInt("playerGameType", this.gameMode.getGameModeForPlayer().getId());
        GameType enumgamemode = this.gameMode.getPreviousGameModeForPlayer();

        if (enumgamemode != null) {
            nbt.putInt("previousPlayerGameType", enumgamemode.getId());
        }

    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.textFilteringEnabled;
    }

    public boolean shouldFilterMessageTo(ServerPlayer player) {
        return player == this ? false : this.textFilteringEnabled || player.textFilteringEnabled;
    }

    @Override
    public boolean mayInteract(Level world, BlockPos pos) {
        return super.mayInteract(world, pos) && world.mayInteract(this, pos);
    }

    @Override
    protected void updateUsingItem(ItemStack stack) {
        CriteriaTriggers.USING_ITEM.trigger(this, stack);
        super.updateUsingItem(stack);
    }

    public boolean drop(boolean entireStack) {
        Inventory playerinventory = this.getInventory();
        ItemStack itemstack = playerinventory.removeFromSelected(entireStack);

        this.containerMenu.findSlot(playerinventory, playerinventory.selected).ifPresent((i) -> {
            this.containerMenu.setRemoteSlot(i, playerinventory.getSelected());
        });
        return this.drop(itemstack, false, true) != null;
    }

    public boolean allowsListing() {
        return this.allowsListing;
    }

    @Override
    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.of(this.wardenSpawnTracker);
    }

    @Override
    public void onItemPickup(ItemEntity item) {
        super.onItemPickup(item);
        Entity entity = item.getOwner();

        if (entity != null) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, item.getItem(), entity);
        }

    }

    public void setChatSession(RemoteChatSession session) {
        this.chatSession = session;
    }

    @Nullable
    public RemoteChatSession getChatSession() {
        return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
    }

    @Override
    public void indicateDamage(double deltaX, double deltaZ) {
        this.hurtDir = (float) (Mth.atan2(deltaZ, deltaX) * 57.2957763671875D - (double) this.getYRot());
        this.connection.send(new ClientboundHurtAnimationPacket(this));
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        if (!super.startRiding(entity, force)) {
            return false;
        } else {
            entity.positionRider(this);
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
            if (entity instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) entity;
                Iterator iterator = entityliving.getActiveEffects().iterator();

                while (iterator.hasNext()) {
                    MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                    this.connection.send(new ClientboundUpdateMobEffectPacket(entity.getId(), mobeffect));
                }
            }

            return true;
        }
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();

        super.stopRiding();
        if (entity instanceof LivingEntity) {
            LivingEntity entityliving = (LivingEntity) entity;
            Iterator iterator = entityliving.getActiveEffects().iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                this.connection.send(new ClientboundRemoveMobEffectPacket(entity.getId(), mobeffect.getEffect()));
            }
        }

    }

    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel world) {
        return new CommonPlayerSpawnInfo(world.dimensionTypeId(), world.dimension(), BiomeManager.obfuscateSeed(world.getSeed()), this.gameMode.getGameModeForPlayer(), this.gameMode.getPreviousGameModeForPlayer(), world.isDebug(), world.isFlat(), this.getLastDeathLocation(), this.getPortalCooldown());
    }

    // CraftBukkit start - Add per-player time and weather.
    public long timeOffset = 0;
    public boolean relativeTime = true;

    public long getPlayerTime() {
        if (this.relativeTime) {
            // Adds timeOffset to the current server time.
            return this.level().getDayTime() + this.timeOffset;
        } else {
            // Adds timeOffset to the beginning of this day.
            return this.level().getDayTime() - (this.level().getDayTime() % 24000) + this.timeOffset;
        }
    }

    public WeatherType weather = null;

    public WeatherType getPlayerWeather() {
        return this.weather;
    }

    public void setPlayerWeather(WeatherType type, boolean plugin) {
        if (!plugin && this.weather != null) {
            return;
        }

        if (plugin) {
            this.weather = type;
        }

        if (type == WeatherType.DOWNFALL) {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        }
    }

    private float pluginRainPosition;
    private float pluginRainPositionPrevious;

    public void updateWeather(float oldRain, float newRain, float oldThunder, float newThunder) {
        if (this.weather == null) {
            // Vanilla
            if (oldRain != newRain) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
            }
        } else {
            // Plugin
            if (this.pluginRainPositionPrevious != this.pluginRainPosition) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.pluginRainPosition));
            }
        }

        if (oldThunder != newThunder) {
            if (this.weather == WeatherType.DOWNFALL || this.weather == null) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
            } else {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0));
            }
        }
    }

    public void tickWeather() {
        if (this.weather == null) return;

        this.pluginRainPositionPrevious = this.pluginRainPosition;
        if (this.weather == WeatherType.DOWNFALL) {
            this.pluginRainPosition += 0.01;
        } else {
            this.pluginRainPosition -= 0.01;
        }

        this.pluginRainPosition = Mth.clamp(this.pluginRainPosition, 0.0F, 1.0F);
    }

    public void resetPlayerWeather() {
        this.weather = null;
        this.setPlayerWeather(this.level().getLevelData().isRaining() ? WeatherType.DOWNFALL : WeatherType.CLEAR, false);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.getScoreboardName() + " at " + this.getX() + "," + this.getY() + "," + this.getZ() + ")";
    }

    // SPIGOT-1903, MC-98153
    public void forceSetPositionRotation(double x, double y, double z, float yaw, float pitch) {
        this.moveTo(x, y, z, yaw, pitch);
        this.connection.resetPosition();
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || !this.getBukkitEntity().isOnline();
    }

    @Override
    public Scoreboard getScoreboard() {
        return this.getBukkitEntity().getScoreboard().getHandle();
    }

    public void reset() {
        float exp = 0;
        boolean keepInventory = this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            exp = this.experienceProgress;
            this.newTotalExp = this.totalExperience;
            this.newLevel = this.experienceLevel;
        }

        this.setHealth(this.getMaxHealth());
        this.stopUsingItem(); // CraftBukkit - SPIGOT-6682: Clear active item on reset
        this.setRemainingFireTicks(0);
        this.fallDistance = 0;
        this.foodData = new FoodData(this);
        this.experienceLevel = this.newLevel;
        this.totalExperience = this.newTotalExp;
        this.experienceProgress = 0;
        this.deathTime = 0;
        this.setArrowCount(0, true); // CraftBukkit - ArrowBodyCountChangeEvent
        this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DEATH);
        this.effectsDirty = true;
        this.containerMenu = this.inventoryMenu;
        this.lastHurtByPlayer = null;
        this.lastHurtByMob = null;
        this.combatTracker = new CombatTracker(this);
        this.lastSentExp = -1;
        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            this.experienceProgress = exp;
        } else {
            this.giveExperiencePoints(this.newExp);
        }
        this.keepLevel = false;
        this.setDeltaMovement(0, 0, 0); // CraftBukkit - SPIGOT-6948: Reset velocity on death
        this.skipDropExperience = false; // CraftBukkit - SPIGOT-7462: Reset experience drop skip, so that further deaths drop xp
    }

    @Override
    public CraftPlayer getBukkitEntity() {
        return (CraftPlayer) super.getBukkitEntity();
    }
    // CraftBukkit end
}
