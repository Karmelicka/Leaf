package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.ComparisonChain; // Paper
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
// CraftBukkit end

public class ChunkMap extends ChunkStorage implements ChunkHolder.PlayerProvider {

    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    // Paper - rewrite chunk system
    public final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    public final BlockableEventLoop<Runnable> mainThreadExecutor; // Paper - public
    public ChunkGenerator generator;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    public final Supplier<DimensionDataStorage> overworldDataStorage;
    private final PoiManager poiManager;
    // Paper - rewrite chunk system
    private boolean modified;
    // Paper - rewrite chunk system
    public final ChunkProgressListener progressListener;
    private final ChunkStatusUpdateListener chunkStatusListener;
    public final ChunkMap.ChunkDistanceManager distanceManager;
    private final AtomicInteger tickingGenerated;
    public final StructureTemplateManager structureTemplateManager; // Paper - rewrite chunk system
    private final String storageName;
    private final PlayerMap playerMap;
    public final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap;
    private final Long2ByteMap chunkTypeCache;
    private final Long2LongMap chunkSaveCooldowns;
    private final Queue<Runnable> unloadQueue;
    public int serverViewDistance;

    // Paper start - distance maps
    private final com.destroystokyo.paper.util.misc.PooledLinkedHashSets<ServerPlayer> pooledLinkedPlayerHashSets = new com.destroystokyo.paper.util.misc.PooledLinkedHashSets<>();
    // Paper start - use distance map to optimise tracker
    public static boolean isLegacyTrackingEntity(Entity entity) {
        return entity.isLegacyTrackingEntity;
    }

    // inlined EnumMap, TrackingRange.TrackingRangeType
    static final org.spigotmc.TrackingRange.TrackingRangeType[] TRACKING_RANGE_TYPES = org.spigotmc.TrackingRange.TrackingRangeType.values();
    public final com.destroystokyo.paper.util.misc.PlayerAreaMap[] playerEntityTrackerTrackMaps;
    final int[] entityTrackerTrackRanges;
    public final int getEntityTrackerRange(final int ordinal) {
        return this.entityTrackerTrackRanges[ordinal];
    }

    private int convertSpigotRangeToVanilla(final int vanilla) {
        return net.minecraft.server.MinecraftServer.getServer().getScaledTrackingDistance(vanilla);
    }
    // Paper end - use distance map to optimise tracker

    void addPlayerToDistanceMaps(ServerPlayer player) {
        int chunkX = io.papermc.paper.util.MCUtil.getChunkCoordinate(player.getX());
        int chunkZ = io.papermc.paper.util.MCUtil.getChunkCoordinate(player.getZ());
        // Note: players need to be explicitly added to distance maps before they can be updated
        this.nearbyPlayers.addPlayer(player);
        this.level.playerChunkLoader.addPlayer(player); // Paper - replace chunk loader
        // Paper start - use distance map to optimise entity tracker
        for (int i = 0, len = TRACKING_RANGE_TYPES.length; i < len; ++i) {
            com.destroystokyo.paper.util.misc.PlayerAreaMap trackMap = this.playerEntityTrackerTrackMaps[i];
            int trackRange = this.entityTrackerTrackRanges[i];

            trackMap.add(player, chunkX, chunkZ, Math.min(trackRange, io.papermc.paper.chunk.system.ChunkSystem.getSendViewDistance(player)));
        }
        // Paper end - use distance map to optimise entity tracker
    }

    void removePlayerFromDistanceMaps(ServerPlayer player) {
        int chunkX = io.papermc.paper.util.MCUtil.getChunkCoordinate(player.getX());
        int chunkZ = io.papermc.paper.util.MCUtil.getChunkCoordinate(player.getZ());
        // Note: players need to be explicitly added to distance maps before they can be updated
        this.nearbyPlayers.removePlayer(player);
        this.level.playerChunkLoader.removePlayer(player); // Paper - replace chunk loader
        // Paper start - use distance map to optimise tracker
        for (int i = 0, len = TRACKING_RANGE_TYPES.length; i < len; ++i) {
            this.playerEntityTrackerTrackMaps[i].remove(player);
        }
        // Paper end - use distance map to optimise tracker
        this.playerMobSpawnMap.remove(player); // Paper - optimise chunk tick iteration
    }

    void updateMaps(ServerPlayer player) {
        int chunkX = io.papermc.paper.util.MCUtil.getChunkCoordinate(player.getX());
        int chunkZ = io.papermc.paper.util.MCUtil.getChunkCoordinate(player.getZ());
        // Note: players need to be explicitly added to distance maps before they can be updated
        this.nearbyPlayers.tickPlayer(player);
        this.level.playerChunkLoader.updatePlayer(player); // Paper - replace chunk loader
        // Paper start - use distance map to optimise entity tracker
        for (int i = 0, len = TRACKING_RANGE_TYPES.length; i < len; ++i) {
            com.destroystokyo.paper.util.misc.PlayerAreaMap trackMap = this.playerEntityTrackerTrackMaps[i];
            int trackRange = this.entityTrackerTrackRanges[i];

            trackMap.update(player, chunkX, chunkZ, Math.min(trackRange, io.papermc.paper.chunk.system.ChunkSystem.getSendViewDistance(player)));
        }
        // Paper end - use distance map to optimise entity tracker
    }
    // Paper end
    // Paper start
    public final List<io.papermc.paper.chunk.SingleThreadChunkRegionManager> regionManagers = new java.util.ArrayList<>();
    public final io.papermc.paper.chunk.SingleThreadChunkRegionManager dataRegionManager;

    public static final class DataRegionData implements io.papermc.paper.chunk.SingleThreadChunkRegionManager.RegionData {
    }

    public static final class DataRegionSectionData implements io.papermc.paper.chunk.SingleThreadChunkRegionManager.RegionSectionData {

        @Override
        public void removeFromRegion(final io.papermc.paper.chunk.SingleThreadChunkRegionManager.RegionSection section,
                                     final io.papermc.paper.chunk.SingleThreadChunkRegionManager.Region from) {
            final DataRegionSectionData sectionData = (DataRegionSectionData)section.sectionData;
            final DataRegionData fromData = (DataRegionData)from.regionData;
        }

        @Override
        public void addToRegion(final io.papermc.paper.chunk.SingleThreadChunkRegionManager.RegionSection section,
                                final io.papermc.paper.chunk.SingleThreadChunkRegionManager.Region oldRegion,
                                final io.papermc.paper.chunk.SingleThreadChunkRegionManager.Region newRegion) {
            final DataRegionSectionData sectionData = (DataRegionSectionData)section.sectionData;
            final DataRegionData oldRegionData = oldRegion == null ? null : (DataRegionData)oldRegion.regionData;
            final DataRegionData newRegionData = (DataRegionData)newRegion.regionData;
        }
    }

    public final ChunkHolder getUnloadingChunkHolder(int chunkX, int chunkZ) {
        return null; // Paper - rewrite chunk system
    }
    public final io.papermc.paper.util.player.NearbyPlayers nearbyPlayers;
    // Paper end
    // Paper start - optimise chunk tick iteration
    public final Set<ChunkHolder> needsChangeBroadcasting = Sets.newConcurrentHashSet(); // Leaf - Fix tracker NPE
    public final com.destroystokyo.paper.util.misc.PlayerAreaMap playerMobSpawnMap = new gg.pufferfish.pufferfish.util.AsyncPlayerAreaMap(this.pooledLinkedPlayerHashSets); // Pufferfish
    // Paper end - optimise chunk tick iteration

    public ChunkMap(ServerLevel world, LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor executor, BlockableEventLoop<Runnable> mainThreadExecutor, LightChunkGetter chunkProvider, ChunkGenerator chunkGenerator, ChunkProgressListener worldGenerationProgressListener, ChunkStatusUpdateListener chunkStatusChangeListener, Supplier<DimensionDataStorage> persistentStateManagerFactory, int viewDistance, boolean dsync) {
        super(world.getLevel().purpurConfig.regionFormatName, world.getLevel().purpurConfig.regionFormatLinearCompressionLevel, world.getLevel().purpurConfig.linearCrashOnBrokenSymlink, session.getDimensionPath(world.dimension()).resolve("region"), dataFixer, dsync); // LinearPurpur
        // Paper - rewrite chunk system
        this.tickingGenerated = new AtomicInteger();
        this.playerMap = new PlayerMap();
        this.entityMap = new Int2ObjectLinkedOpenHashMap(); // Gale - VMP - use linked map for entity trackers - provides faster iteration
        this.chunkTypeCache = new Long2ByteOpenHashMap();
        this.chunkSaveCooldowns = new Long2LongOpenHashMap();
        this.unloadQueue = Queues.newConcurrentLinkedQueue();
        this.structureTemplateManager = structureTemplateManager;
        Path path = session.getDimensionPath(world.dimension());

        this.storageName = path.getFileName().toString();
        this.level = world;
        this.generator = chunkGenerator;
        // CraftBukkit start - SPIGOT-7051: It's a rigged game! Use delegate for random state creation, otherwise it is not so random.
        if (chunkGenerator instanceof CustomChunkGenerator) {
            chunkGenerator = ((CustomChunkGenerator) chunkGenerator).getDelegate();
        }
        // CraftBukkit end
        RegistryAccess iregistrycustom = world.registryAccess();
        long j = world.getSeed();

        if (chunkGenerator instanceof NoiseBasedChunkGenerator) {
            NoiseBasedChunkGenerator chunkgeneratorabstract = (NoiseBasedChunkGenerator) chunkGenerator;

            this.randomState = RandomState.create((NoiseGeneratorSettings) chunkgeneratorabstract.generatorSettings().value(), (HolderGetter) iregistrycustom.lookupOrThrow(Registries.NOISE), j);
        } else {
            this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), (HolderGetter) iregistrycustom.lookupOrThrow(Registries.NOISE), j);
        }

        this.chunkGeneratorState = chunkGenerator.createState(iregistrycustom.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, j, world.spigotConfig); // Spigot
        this.mainThreadExecutor = mainThreadExecutor;
        // Paper - rewrite chunk system

        Objects.requireNonNull(mainThreadExecutor);
        // Paper - rewrite chunk system

        this.progressListener = worldGenerationProgressListener;
        this.chunkStatusListener = chunkStatusChangeListener;
        // Paper - rewrite chunk system

        // Paper - rewrite chunk system
        this.lightEngine = new ThreadedLevelLightEngine(chunkProvider, this, this.level.dimensionType().hasSkyLight(), null, null); // Paper - rewrite chunk system
        this.distanceManager = new ChunkMap.ChunkDistanceManager(executor, mainThreadExecutor);
        this.overworldDataStorage = persistentStateManagerFactory;
        this.poiManager = new PoiManager(this.level.purpurConfig.regionFormatName, this.level.purpurConfig.regionFormatLinearCompressionLevel, this.level.purpurConfig.linearCrashOnBrokenSymlink, path.resolve("poi"), dataFixer, dsync, iregistrycustom, world); // LinearPurpur
        this.setServerViewDistance(viewDistance);
        // Paper start
        this.dataRegionManager = new io.papermc.paper.chunk.SingleThreadChunkRegionManager(this.level, 2, (1.0 / 3.0), 1, 6, "Data", DataRegionData::new, DataRegionSectionData::new);
        this.regionManagers.add(this.dataRegionManager);
        this.nearbyPlayers = new io.papermc.paper.util.player.NearbyPlayers(this.level);
        // Paper end
        // Paper start - use distance map to optimise entity tracker
        this.playerEntityTrackerTrackMaps = new com.destroystokyo.paper.util.misc.PlayerAreaMap[TRACKING_RANGE_TYPES.length];
        this.entityTrackerTrackRanges = new int[TRACKING_RANGE_TYPES.length];

        org.spigotmc.SpigotWorldConfig spigotWorldConfig = this.level.spigotConfig;

        for (int ordinal = 0, len = TRACKING_RANGE_TYPES.length; ordinal < len; ++ordinal) {
            org.spigotmc.TrackingRange.TrackingRangeType trackingRangeType = TRACKING_RANGE_TYPES[ordinal];
            int configuredSpigotValue;
            switch (trackingRangeType) {
                case PLAYER:
                    configuredSpigotValue = spigotWorldConfig.playerTrackingRange;
                    break;
                case ANIMAL:
                    configuredSpigotValue = spigotWorldConfig.animalTrackingRange;
                    break;
                case MONSTER:
                    configuredSpigotValue = spigotWorldConfig.monsterTrackingRange;
                    break;
                case MISC:
                    configuredSpigotValue = spigotWorldConfig.miscTrackingRange;
                    break;
                case OTHER:
                    configuredSpigotValue = spigotWorldConfig.otherTrackingRange;
                    break;
                case ENDERDRAGON:
                    configuredSpigotValue = EntityType.ENDER_DRAGON.clientTrackingRange() * 16;
                    break;
                case DISPLAY:
                    configuredSpigotValue = spigotWorldConfig.displayTrackingRange;
                    break;
                default:
                    throw new IllegalStateException("Missing case for enum " + trackingRangeType);
            }
            configuredSpigotValue = convertSpigotRangeToVanilla(configuredSpigotValue);

            int trackRange = (configuredSpigotValue >>> 4) + ((configuredSpigotValue & 15) != 0 ? 1 : 0);
            this.entityTrackerTrackRanges[ordinal] = trackRange;

            this.playerEntityTrackerTrackMaps[ordinal] = new com.destroystokyo.paper.util.misc.PlayerAreaMap(this.pooledLinkedPlayerHashSets);
        }
        // Paper end - use distance map to optimise entity tracker
    }

    // Paper start
    // always use accessor, so folia can override
    public final io.papermc.paper.util.player.NearbyPlayers getNearbyPlayers() {
        return this.nearbyPlayers;
    }
    // Paper end

    protected ChunkGenerator generator() {
        return this.generator;
    }

    protected ChunkGeneratorStructureState generatorState() {
        return this.chunkGeneratorState;
    }

    protected RandomState randomState() {
        return this.randomState;
    }

    public void debugReloadGenerator() {
        DataResult<JsonElement> dataresult = ChunkGenerator.CODEC.encodeStart(JsonOps.INSTANCE, this.generator);
        DataResult<ChunkGenerator> dataresult1 = dataresult.flatMap((jsonelement) -> {
            return ChunkGenerator.CODEC.parse(JsonOps.INSTANCE, jsonelement);
        });

        dataresult1.result().ifPresent((chunkgenerator) -> {
            this.generator = chunkgenerator;
        });
    }

    // Paper start - Optional per player mob spawns
    public void updatePlayerMobTypeMap(final Entity entity) {
        if (!this.level.paperConfig().entities.spawning.perPlayerMobSpawns) {
            return;
        }
        int index = entity.getType().getCategory().ordinal();

        final com.destroystokyo.paper.util.maplist.ReferenceList<ServerPlayer> inRange =
            this.getNearbyPlayers().getPlayers(entity.chunkPosition(), io.papermc.paper.util.player.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
        if (inRange == null) {
            return;
        }
        final Object[] backingSet = inRange.getRawData();
        for (int i = 0, len = inRange.size(); i < len; i++) {
            ++((ServerPlayer)backingSet[i]).mobCounts[index];
        }
    }

    // Paper start - per player mob count backoff
    public void updateFailurePlayerMobTypeMap(int chunkX, int chunkZ, net.minecraft.world.entity.MobCategory mobCategory) {
        if (!this.level.paperConfig().entities.spawning.perPlayerMobSpawns) {
            return;
        }
        int idx = mobCategory.ordinal();
        final com.destroystokyo.paper.util.maplist.ReferenceList<ServerPlayer> inRange =
            this.getNearbyPlayers().getPlayersByChunk(chunkX, chunkZ, io.papermc.paper.util.player.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
        if (inRange == null) {
            return;
        }
        final Object[] backingSet = inRange.getRawData();
        for (int i = 0, len = inRange.size(); i < len; i++) {
            ++((ServerPlayer)backingSet[i]).mobBackoffCounts[idx];
        }
    }
    // Paper end - per player mob count backoff
    public int getMobCountNear(final ServerPlayer player, final net.minecraft.world.entity.MobCategory mobCategory) {
        return player.mobCounts[mobCategory.ordinal()] + player.mobBackoffCounts[mobCategory.ordinal()]; // Paper - per player mob count backoff
    }
    // Paper end - Optional per player mob spawns

    public static double euclideanDistanceSquared(ChunkPos pos, Entity entity) { // Paper - optimise chunk iteration; public
        double d0 = (double) SectionPos.sectionToBlockCoord(pos.x, 8);
        double d1 = (double) SectionPos.sectionToBlockCoord(pos.z, 8);
        double d2 = d0 - entity.getX();
        double d3 = d1 - entity.getZ();

        return d2 * d2 + d3 * d3;
    }

    boolean isChunkTracked(ServerPlayer player, int chunkX, int chunkZ) {
        // Paper start - rewrite player chunk loader
        return this.level.playerChunkLoader.isChunkSent(player, chunkX, chunkZ);
        // Paper end - rewrite player chunk loader
    }

    private boolean isChunkOnTrackedBorder(ServerPlayer player, int chunkX, int chunkZ) {
        // Paper start - rewrite player chunk loader
        return this.level.playerChunkLoader.isChunkSent(player, chunkX, chunkZ, true);
        // Paper end - rewrite player chunk loader
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    protected ChunkHolder getUpdatingChunkIfPresent(long pos) {
        // Paper start - rewrite chunk system
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder holder = this.level.chunkTaskScheduler.chunkHolderManager.getChunkHolder(pos);
        return holder == null ? null : holder.vanillaChunkHolder;
        // Paper end - rewrite chunk system
    }

    @Nullable
    public ChunkHolder getVisibleChunkIfPresent(long pos) {
        // Paper start - rewrite chunk system
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder holder = this.level.chunkTaskScheduler.chunkHolderManager.getChunkHolder(pos);
        return holder == null ? null : holder.vanillaChunkHolder;
        // Paper end - rewrite chunk system
    }

    protected IntSupplier getChunkQueueLevel(long pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public String getChunkDebugData(ChunkPos chunkPos) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(chunkPos.toLong());

        if (playerchunk == null) {
            return "null";
        } else {
            String s = playerchunk.getTicketLevel() + "\n";
            ChunkStatus chunkstatus = playerchunk.getLastAvailableStatus();
            ChunkAccess ichunkaccess = playerchunk.getLastAvailable();

            if (chunkstatus != null) {
                s = s + "St: \u00a7" + chunkstatus.getIndex() + chunkstatus + "\u00a7r\n";
            }

            if (ichunkaccess != null) {
                s = s + "Ch: \u00a7" + ichunkaccess.getStatus().getIndex() + ichunkaccess.getStatus() + "\u00a7r\n";
            }

            FullChunkStatus fullchunkstatus = playerchunk.getFullStatus();

            s = s + String.valueOf('\u00a7') + fullchunkstatus.ordinal() + fullchunkstatus;
            return s + "\u00a7r";
        }
    }

    private CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.ChunkLoadingFailure>> getChunkRangeFuture(ChunkHolder centerChunk, int margin, IntFunction<ChunkStatus> distanceToStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public ReportedException debugFuturesAndCreateReportedException(IllegalStateException exception, String details) {
        StringBuilder stringbuilder = new StringBuilder();
        Consumer<ChunkHolder> consumer = (playerchunk) -> {
            playerchunk.getAllFutures().forEach((pair) -> {
                ChunkStatus chunkstatus = (ChunkStatus) pair.getFirst();
                CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completablefuture = (CompletableFuture) pair.getSecond();

                if (completablefuture != null && completablefuture.isDone() && completablefuture.join() == null) {
                    stringbuilder.append(playerchunk.getPos()).append(" - status: ").append(chunkstatus).append(" future: ").append(completablefuture).append(System.lineSeparator());
                }

            });
        };

        stringbuilder.append("Updating:").append(System.lineSeparator());
        io.papermc.paper.chunk.system.ChunkSystem.getUpdatingChunkHolders(this.level).forEach(consumer); // Paper
        stringbuilder.append("Visible:").append(System.lineSeparator());
        io.papermc.paper.chunk.system.ChunkSystem.getVisibleChunkHolders(this.level).forEach(consumer); // Paper
        CrashReport crashreport = CrashReport.forThrowable(exception, "Chunk loading");
        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Chunk loading");

        crashreportsystemdetails.setDetail("Details", (Object) details);
        crashreportsystemdetails.setDetail("Futures", (Object) stringbuilder);
        return new ReportedException(crashreport);
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> prepareEntityTickingChunk(ChunkHolder chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Nullable
    ChunkHolder updateChunkScheduling(long pos, int level, @Nullable ChunkHolder holder, int k) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Use ServerChunkCache#close"); // Paper - rewrite chunk system
    }

    // Paper start - rewrite chunk system
    protected void saveIncrementally() {
        this.level.chunkTaskScheduler.chunkHolderManager.autoSave(); // Paper - rewrite chunk system
    }
    // Paper end - - rewrite chunk system

    protected void saveAllChunks(boolean flush) {
        this.level.chunkTaskScheduler.chunkHolderManager.saveAllChunks(flush, false, false); // Paper - rewrite chunk system
    }

    protected void tick(BooleanSupplier shouldKeepTicking) {
        this.poiManager.tick(shouldKeepTicking);
        if (!this.level.noSave()) {
            this.processUnloads(shouldKeepTicking);
        }
    }

    public boolean hasWork() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void processUnloads(BooleanSupplier shouldKeepTicking) {
        this.level.chunkTaskScheduler.chunkHolderManager.processUnloads(); // Paper - rewrite chunk system

    }

    private void scheduleUnload(long pos, ChunkHolder holder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected boolean promoteChunkMap() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> schedule(ChunkHolder holder, ChunkStatus requiredStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> scheduleChunkLoad(ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public static boolean isChunkDataValid(CompoundTag nbt) { // Paper - async chunk loading
        return nbt.contains("Status", 8);
    }

    private Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> handleChunkLoadFailure(Throwable throwable, ChunkPos chunkPos) {
        if (throwable instanceof ReportedException) {
            ReportedException reportedexception = (ReportedException) throwable;
            Throwable throwable1 = reportedexception.getCause();

            if (!(throwable1 instanceof IOException)) {
                this.markPositionReplaceable(chunkPos);
                throw reportedexception;
            }

            ChunkMap.LOGGER.error("Couldn't load chunk {}", chunkPos, throwable1);
        } else if (throwable instanceof IOException) {
            ChunkMap.LOGGER.error("Couldn't load chunk {}", chunkPos, throwable);
        }

        return Either.left(this.createEmptyChunk(chunkPos));
    }

    private ChunkAccess createEmptyChunk(ChunkPos chunkPos) {
        this.markPositionReplaceable(chunkPos);
        return new ProtoChunk(chunkPos, UpgradeData.EMPTY, this.level, this.level.registryAccess().registryOrThrow(Registries.BIOME), (BlendingData) null);
    }

    private void markPositionReplaceable(ChunkPos pos) {
        this.chunkTypeCache.put(pos.toLong(), (byte) -1);
    }

    private byte markPosition(ChunkPos pos, ChunkStatus.ChunkType type) {
        return this.chunkTypeCache.put(pos.toLong(), (byte) (type == ChunkStatus.ChunkType.PROTOCHUNK ? -1 : 1));
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> scheduleChunkGeneration(ChunkHolder holder, ChunkStatus requiredStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected void releaseLightTicket(ChunkPos pos) {
        this.mainThreadExecutor.tell(Util.name(() -> {
            this.distanceManager.removeTicket(TicketType.LIGHT, pos, ChunkLevel.byStatus(ChunkStatus.LIGHT), pos);
        }, () -> {
            return "release light ticket " + pos;
        }));
    }

    public static ChunkStatus getDependencyStatus(ChunkStatus centerChunkTargetStatus, int distance) { // Paper -> public, static
        ChunkStatus chunkstatus1;

        if (distance == 0) {
            chunkstatus1 = centerChunkTargetStatus.getParent();
        } else {
            chunkstatus1 = ChunkStatus.getStatusAroundFullChunk(ChunkStatus.getDistance(centerChunkTargetStatus) + distance);
        }

        return chunkstatus1;
    }

    public static void postLoadProtoChunk(ServerLevel world, List<CompoundTag> nbt, ChunkPos position) { // Paper - public and add chunk position parameter
        if (!nbt.isEmpty()) {
            // CraftBukkit start - these are spawned serialized (DefinedStructure) and we don't call an add event below at the moment due to ordering complexities
            world.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(nbt, world).filter((entity) -> {
                boolean needsRemoval = false;
                net.minecraft.server.dedicated.DedicatedServer server = world.getCraftServer().getServer();
                if (!server.areNpcsEnabled() && entity instanceof net.minecraft.world.entity.npc.Npc) {
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause
                    needsRemoval = true;
                }
                if (!server.isSpawningAnimals() && (entity instanceof net.minecraft.world.entity.animal.Animal || entity instanceof net.minecraft.world.entity.animal.WaterAnimal)) {
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause
                    needsRemoval = true;
                }
                checkDupeUUID(world, entity); // Paper - duplicate uuid resolving
                return !needsRemoval;
            }), position); // Paper - rewrite chunk system
            // CraftBukkit end
        }

    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> protoChunkToFullChunk(ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    // Paper start - duplicate uuid resolving
    // rets true if to prevent the entity from being added
    public static boolean checkDupeUUID(ServerLevel level, Entity entity) {
        io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode mode = level.paperConfig().entities.spawning.duplicateUuid.mode;
        if (mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.WARN
            && mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.DELETE
            && mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.SAFE_REGEN) {
            return false;
        }
        Entity other = level.getEntity(entity.getUUID());

        if (other == null || other == entity) {
            return false;
        }

        if (mode == io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.SAFE_REGEN && other != null && !other.isRemoved()
            && Objects.equals(other.getEncodeId(), entity.getEncodeId())
            && entity.getBukkitEntity().getLocation().distance(other.getBukkitEntity().getLocation()) < level.paperConfig().entities.spawning.duplicateUuid.safeRegenDeleteRange
        ) {
            entity.discard(null);
            return true;
        }
        if (!other.isRemoved()) {
            switch (mode) {
                case SAFE_REGEN: {
                    entity.setUUID(java.util.UUID.randomUUID());
                    break;
                }
                case DELETE: {
                    entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
                    return true;
                }
                default:
                    break;
            }
        }
        return false;
    }
    // Paper end - duplicate uuid resolving
    public CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> prepareTickingChunk(ChunkHolder holder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void onChunkReadyToSend(LevelChunk chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite player chunk loader

    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> prepareAccessibleChunk(ChunkHolder holder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public int getTickingGenerated() {
        return this.tickingGenerated.get();
    }

    private boolean saveChunkIfNeeded(ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean save(ChunkAccess chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private boolean isExistingChunkFull(ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    // Paper start - replace player loader system
    public void setTickViewDistance(int distance) {
        this.level.playerChunkLoader.setTickDistance(distance);
    }

    public void setSendViewDistance(int distance) {
        this.level.playerChunkLoader.setSendDistance(distance);
    }
    // Paper end - replace player loader system

    public void setServerViewDistance(int watchDistance) { // Paper - public
        int j = Mth.clamp(watchDistance, 2, 32);

        if (j != this.serverViewDistance) {
            this.serverViewDistance = j;
            this.level.playerChunkLoader.setLoadDistance(this.serverViewDistance + 1); // Paper - replace player loader system
        }

    }

    public int getPlayerViewDistance(ServerPlayer player) { // Paper - public
        return io.papermc.paper.chunk.system.ChunkSystem.getSendViewDistance(player); // Paper - per player view distance
    }

    private void markChunkPendingToSend(ServerPlayer player, ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - per player view distance

    }

    private static void markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite player chunk loader
    }

    private static void dropChunk(ServerPlayer player, ChunkPos pos) {
        // Paper - rewrite player chunk loader
    }

    @Nullable
    public LevelChunk getChunkToSend(long pos) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(pos);

        return playerchunk == null ? null : playerchunk.getChunkToSend();
    }

    public int size() {
        return io.papermc.paper.chunk.system.ChunkSystem.getVisibleChunkHolderCount(this.level); // Paper
    }

    public DistanceManager getDistanceManager() {
        return this.distanceManager;
    }

    protected Iterable<ChunkHolder> getChunks() {
        return Iterables.unmodifiableIterable(io.papermc.paper.chunk.system.ChunkSystem.getVisibleChunkHolders(this.level)); // Paper
    }

    void dumpChunks(Writer writer) throws IOException {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private static String printFuture(CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> future) {
        try {
            Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> either = (Either) future.getNow(null); // CraftBukkit - decompile error

            return either != null ? (String) either.map((chunk) -> {
                return "done";
            }, (playerchunk_failure) -> {
                return "unloaded";
            }) : "not completed";
        } catch (CompletionException completionexception) {
            return "failed " + completionexception.getCause().getMessage();
        } catch (CancellationException cancellationexception) {
            return "cancelled";
        }
    }

    // Paper start - Asynchronous chunk io
    @Nullable
    @Override
    public CompoundTag readSync(ChunkPos chunkcoordintpair) throws IOException {
        // Paper start - rewrite chunk system
        if (!io.papermc.paper.chunk.system.io.RegionFileIOThread.isRegionFileThread()) {
            return io.papermc.paper.chunk.system.io.RegionFileIOThread.loadData(
                this.level, chunkcoordintpair.x, chunkcoordintpair.z, io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.CHUNK_DATA,
                io.papermc.paper.chunk.system.io.RegionFileIOThread.getIOBlockingPriorityForCurrentThread()
            );
        }
        // Paper end - rewrite chunk system
        return super.readSync(chunkcoordintpair);
    }

    @Override
    public void write(ChunkPos chunkcoordintpair, CompoundTag nbttagcompound) throws IOException {
        // Paper start - rewrite chunk system
        if (!io.papermc.paper.chunk.system.io.RegionFileIOThread.isRegionFileThread()) {
            io.papermc.paper.chunk.system.io.RegionFileIOThread.scheduleSave(
                this.level, chunkcoordintpair.x, chunkcoordintpair.z, nbttagcompound,
                io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.CHUNK_DATA);
            return;
        }
        // Paper end - rewrite chunk system
        super.write(chunkcoordintpair, nbttagcompound);
    }
    // Paper end

    private CompletableFuture<Optional<CompoundTag>> readChunk(ChunkPos chunkPos) {
        // Paper start - Cache chunk status on disk
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.readConvertChunkSync(chunkPos)));
        } catch (Throwable thr) {
            return CompletableFuture.failedFuture(thr);
        }
        // Paper end - Cache chunk status on disk
    }

    // CraftBukkit start
    private CompoundTag upgradeChunkTag(CompoundTag nbttagcompound, ChunkPos chunkcoordintpair) {
        return this.upgradeChunkTag(this.level.getTypeKey(), this.overworldDataStorage, nbttagcompound, this.generator.getTypeNameForDataFixer(), chunkcoordintpair, this.level);
        // CraftBukkit end
    }

    // Paper start - Cache chunk status on disk
    @Nullable
    public CompoundTag readConvertChunkSync(ChunkPos pos) throws IOException {
        CompoundTag nbttagcompound = this.readSync(pos);
        if (nbttagcompound == null) {
            return null;
        }

        nbttagcompound = this.upgradeChunkTag(nbttagcompound, pos); // CraftBukkit
        if (nbttagcompound == null) {
            return null;
        }

        this.updateChunkStatusOnDisk(pos, nbttagcompound);

        return nbttagcompound;
    }

    public ChunkStatus getChunkStatusOnDiskIfCached(ChunkPos chunkPos) {
        org.purpurmc.purpur.region.AbstractRegionFile regionFile = regionFileCache.getRegionFileIfLoaded(chunkPos); // LinearPurpur

        return regionFile == null ? null : regionFile.getStatusIfCached(chunkPos.x, chunkPos.z);
    }

    public ChunkStatus getChunkStatusOnDisk(ChunkPos chunkPos) throws IOException {
        org.purpurmc.purpur.region.AbstractRegionFile regionFile = regionFileCache.getRegionFile(chunkPos, true); // LinearPurpur

        if (regionFile == null || !regionFileCache.chunkExists(chunkPos)) {
            return null;
        }

        ChunkStatus status = regionFile.getStatusIfCached(chunkPos.x, chunkPos.z);

        if (status != null) {
            return status;
        }

        this.readChunk(chunkPos);

        return regionFile.getStatusIfCached(chunkPos.x, chunkPos.z);
    }

    public void updateChunkStatusOnDisk(ChunkPos chunkPos, @Nullable CompoundTag compound) throws IOException {
        org.purpurmc.purpur.region.AbstractRegionFile regionFile = regionFileCache.getRegionFile(chunkPos, false); // LinearPurpur

        regionFile.setStatus(chunkPos.x, chunkPos.z, ChunkSerializer.getStatus(compound));
    }

    public ChunkAccess getUnloadingChunk(int chunkX, int chunkZ) {
        ChunkHolder chunkHolder = io.papermc.paper.chunk.system.ChunkSystem.getUnloadingChunkHolder(this.level, chunkX, chunkZ);
        return chunkHolder == null ? null : chunkHolder.getAvailableChunkNow();
    }
    // Paper end - Cache chunk status on disk

    public boolean anyPlayerCloseEnoughForSpawning(ChunkPos pos) { // Paper - public
        // Spigot start
        return this.anyPlayerCloseEnoughForSpawning(pos, false);
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkcoordintpair, boolean reducedRange) {
        int chunkRange = this.level.spigotConfig.mobSpawnRange;
        chunkRange = (chunkRange > this.level.spigotConfig.viewDistance) ? (byte) this.level.spigotConfig.viewDistance : chunkRange;
        chunkRange = (chunkRange > 8) ? 8 : chunkRange;

        final int finalChunkRange = chunkRange; // Paper for lambda below
        //double blockRange = (reducedRange) ? Math.pow(chunkRange << 4, 2) : 16384.0D; // Paper - use from event
        double blockRange = 16384.0D; // Paper
        // Spigot end
        if (!this.distanceManager.hasPlayersNearby(chunkcoordintpair.toLong())) {
            return false;
        } else {
            Iterator iterator = this.playerMap.getAllPlayers().iterator();

            ServerPlayer entityplayer;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                entityplayer = (ServerPlayer) iterator.next();
                // Paper start - PlayerNaturallySpawnCreaturesEvent
                com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent event;
                blockRange = 16384.0D;
                if (reducedRange) {
                    event = entityplayer.playerNaturallySpawnedEvent;
                    if (event == null || event.isCancelled()) return false;
                    blockRange = (double) ((event.getSpawnRadius() << 4) * (event.getSpawnRadius() << 4));
                }
                // Paper end - PlayerNaturallySpawnCreaturesEvent
            } while (!this.playerIsCloseEnoughForSpawning(entityplayer, chunkcoordintpair, blockRange)); // Spigot

            return true;
        }
    }

    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos pos) {
        long i = pos.toLong();

        if (!this.distanceManager.hasPlayersNearby(i)) {
            return List.of();
        } else {
            Builder<ServerPlayer> builder = ImmutableList.builder();
            Iterator iterator = this.playerMap.getAllPlayers().iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (this.playerIsCloseEnoughForSpawning(entityplayer, pos, 16384.0D)) { // Spigot
                    builder.add(entityplayer);
                }
            }

            return builder.build();
        }
    }

    private boolean playerIsCloseEnoughForSpawning(ServerPlayer entityplayer, ChunkPos chunkcoordintpair, double range) { // Spigot
        if (entityplayer.isSpectator()) {
            return false;
        } else {
            double d0 = ChunkMap.euclideanDistanceSquared(chunkcoordintpair, entityplayer);

            return d0 < range; // Spigot
        }
    }

    private boolean skipPlayer(ServerPlayer player) {
        return player.isSpectator() && !this.level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
    }

    void updatePlayerStatus(ServerPlayer player, boolean added) {
        boolean flag1 = this.skipPlayer(player);
        boolean flag2 = this.playerMap.ignoredOrUnknown(player);

        if (added) {
            this.playerMap.addPlayer(player, flag1);
            this.updatePlayerPos(player);
            if (!flag1) {
                this.distanceManager.addPlayer(SectionPos.of((EntityAccess) player), player);
            }

            // Paper - handled by player chunk loader
            this.addPlayerToDistanceMaps(player); // Paper - distance maps
        } else {
            SectionPos sectionposition = player.getLastSectionPos();

            this.playerMap.removePlayer(player);
            if (!flag2) {
                this.distanceManager.removePlayer(sectionposition, player);
            }

            this.removePlayerFromDistanceMaps(player); // Paper - distance maps
            // Paper - handled by player chunk loader
        }

    }

    private void updatePlayerPos(ServerPlayer player) {
        SectionPos sectionposition = SectionPos.of((EntityAccess) player);

        player.setLastSectionPos(sectionposition);
    }

    public void move(ServerPlayer player) {
        // Paper - delay this logic for the entity tracker tick, no need to duplicate it

        SectionPos sectionposition = player.getLastSectionPos();
        SectionPos sectionposition1 = SectionPos.of((EntityAccess) player);
        boolean flag = this.playerMap.ignored(player);
        boolean flag1 = this.skipPlayer(player);
        boolean flag2 = sectionposition.asLong() != sectionposition1.asLong();

        if (flag2 || flag != flag1) {
            this.updatePlayerPos(player);
            if (!flag) {
                this.distanceManager.removePlayer(sectionposition, player);
            }

            if (!flag1) {
                this.distanceManager.addPlayer(sectionposition1, player);
            }

            if (!flag && flag1) {
                this.playerMap.ignorePlayer(player);
            }

            if (flag && !flag1) {
                this.playerMap.unIgnorePlayer(player);
            }

            // Paper - replaced by PlayerChunkLoader
        }

        this.updateMaps(player); // Paper - distance maps
    }

    private void updateChunkTracking(ServerPlayer player) {
        throw new UnsupportedOperationException(); // Paper - replaced by PlayerChunkLoader
    }

    private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkFilter) {
        throw new UnsupportedOperationException(); // Paper - replaced by PlayerChunkLoader
    }

    @Override
    public List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyOnWatchDistanceEdge) {
        // Paper start - per player view distance
        ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos.toLong());
        if (holder == null) {
            return new java.util.ArrayList<>();
        } else {
            return holder.getPlayers(onlyOnWatchDistanceEdge);
        }
        // Paper end - per player view distance
    }

    public void addEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity track"); // Spigot
        // Paper start - ignore and warn about illegal addEntity calls instead of crashing server
        if (!entity.valid || entity.level() != this.level || this.entityMap.containsKey(entity.getId())) {
            LOGGER.error("Illegal ChunkMap::addEntity for world " + this.level.getWorld().getName()
                + ": " + entity  + (this.entityMap.containsKey(entity.getId()) ? " ALREADY CONTAINED (This would have crashed your server)" : ""), new Throwable());
            return;
        }
        // Paper end - ignore and warn about illegal addEntity calls instead of crashing server
        if (entity instanceof ServerPlayer && ((ServerPlayer) entity).supressTrackerForLogin) return; // Paper - Fire PlayerJoinEvent when Player is actually ready; Delay adding to tracker until after list packets
        if (!(entity instanceof EnderDragonPart)) {
            EntityType<?> entitytypes = entity.getType();
            int i = entitytypes.clientTrackingRange() * 16;
            i = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, i); // Spigot

            if (i != 0) {
                int j = entitytypes.updateInterval();

                if (this.entityMap.containsKey(entity.getId())) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
                } else {
                    ChunkMap.TrackedEntity playerchunkmap_entitytracker = new ChunkMap.TrackedEntity(entity, i, j, entitytypes.trackDeltas());

                    entity.tracker = playerchunkmap_entitytracker; // Paper - Fast access to tracker
                    this.entityMap.put(entity.getId(), playerchunkmap_entitytracker);
                    playerchunkmap_entitytracker.updatePlayers(entity.getPlayersInTrackRange()); // Paper - don't search all players
                    if (entity instanceof ServerPlayer) {
                        ServerPlayer entityplayer = (ServerPlayer) entity;

                        this.updatePlayerStatus(entityplayer, true);
                        ObjectIterator objectiterator = this.entityMap.values().iterator();

                        while (objectiterator.hasNext()) {
                            ChunkMap.TrackedEntity playerchunkmap_entitytracker1 = (ChunkMap.TrackedEntity) objectiterator.next();

                            if (playerchunkmap_entitytracker1.entity != entityplayer) {
                                playerchunkmap_entitytracker1.updatePlayer(entityplayer);
                            }
                        }
                    }

                }
            }
        }
    }

    protected void removeEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity untrack"); // Spigot
        if (entity instanceof ServerPlayer) {
            ServerPlayer entityplayer = (ServerPlayer) entity;

            this.updatePlayerStatus(entityplayer, false);
            ObjectIterator objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();

                playerchunkmap_entitytracker.removePlayer(entityplayer);
            }
        }

        ChunkMap.TrackedEntity playerchunkmap_entitytracker1 = (ChunkMap.TrackedEntity) this.entityMap.remove(entity.getId());

        if (playerchunkmap_entitytracker1 != null) {
            playerchunkmap_entitytracker1.broadcastRemoved();
        }
        entity.tracker = null; // Paper - We're no longer tracked
    }

    // Leaf start - petal - multithreaded tracker
    private @Nullable org.dreeam.leaf.async.tracker.MultithreadedTracker multithreadedTracker;
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> trackerMainThreadTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private boolean tracking = false;

    public void runOnTrackerMainThread(final Runnable runnable) {
        if (this.tracking) {
            this.trackerMainThreadTasks.add(runnable);
        } else {
            runnable.run();
        }
    }

    // Paper start - optimised tracker
    private final void processTrackQueue() {
            if (org.dreeam.leaf.config.modules.async.MultithreadedTracker.enabled) {
                if (this.multithreadedTracker == null) {
                    this.multithreadedTracker = new org.dreeam.leaf.async.tracker.MultithreadedTracker(this.level.chunkSource.entityTickingChunks, this.trackerMainThreadTasks);
                }

                this.tracking = true;
                try {
                    this.multithreadedTracker.processTrackQueue();
                } finally {
                    this.tracking = false;
                }
                return;
            }
            // Leaf end - petal
            for (TrackedEntity tracker : this.entityMap.values()) {
                // update tracker entry
                tracker.updatePlayers(tracker.entity.getPlayersInTrackRange());
            }

            for (TrackedEntity tracker : this.entityMap.values()) {
                tracker.serverEntity.sendChanges();
            }
    }
    // Paper end - optimised tracker

    protected void tick() {
        // Paper start - optimized tracker
        if (true) {
            this.processTrackQueue();
            return;
        }
        // Paper end - optimized tracker
        List<ServerPlayer> list = Lists.newArrayList();
        List<ServerPlayer> list1 = this.level.players();
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        ChunkMap.TrackedEntity playerchunkmap_entitytracker;

        while (objectiterator.hasNext()) {
            playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();
            SectionPos sectionposition = playerchunkmap_entitytracker.lastSectionPos;
            SectionPos sectionposition1 = SectionPos.of((EntityAccess) playerchunkmap_entitytracker.entity);
            boolean flag = !Objects.equals(sectionposition, sectionposition1);

            if (flag) {
                playerchunkmap_entitytracker.updatePlayers(list1);
                Entity entity = playerchunkmap_entitytracker.entity;

                if (entity instanceof ServerPlayer) {
                    list.add((ServerPlayer) entity);
                }

                playerchunkmap_entitytracker.lastSectionPos = sectionposition1;
            }

            if (flag || this.distanceManager.inEntityTickingRange(sectionposition1.chunk().toLong())) {
                playerchunkmap_entitytracker.serverEntity.sendChanges();
            }
        }

        if (!list.isEmpty()) {
            objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) objectiterator.next();
                playerchunkmap_entitytracker.updatePlayers(list);
            }
        }

    }

    public void broadcast(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcast(packet);
        }

    }

    protected void broadcastAndSend(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity playerchunkmap_entitytracker = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcastAndSend(packet);
        }

    }

    public void resendBiomesForChunks(List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap();
        Iterator iterator = chunks.iterator();

        while (iterator.hasNext()) {
            ChunkAccess ichunkaccess = (ChunkAccess) iterator.next();
            ChunkPos chunkcoordintpair = ichunkaccess.getPos();
            LevelChunk chunk;

            if (ichunkaccess instanceof LevelChunk) {
                LevelChunk chunk1 = (LevelChunk) ichunkaccess;

                chunk = chunk1;
            } else {
                chunk = this.level.getChunk(chunkcoordintpair.x, chunkcoordintpair.z);
            }

            Iterator iterator1 = this.getPlayers(chunkcoordintpair, false).iterator();

            while (iterator1.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator1.next();

                ((List) map.computeIfAbsent(entityplayer, (entityplayer1) -> {
                    return new ArrayList();
                })).add(chunk);
            }
        }

        map.forEach((entityplayer1, list1) -> {
            entityplayer1.connection.send(ClientboundChunksBiomesPacket.forChunks(list1));
        });
    }

    public PoiManager getPoiManager() {
        return this.poiManager;
    }

    public String getStorageName() {
        return this.storageName;
    }

    void onFullChunkStatusChange(ChunkPos chunkPos, FullChunkStatus levelType) {
        this.chunkStatusListener.onChunkStatusChange(chunkPos, levelType);
    }

    public void waitForLightBeforeSending(ChunkPos centerPos, int radius) {
        // Paper - rewrite player chunk loader
    }

    public class ChunkDistanceManager extends DistanceManager { // Paper - public

        protected ChunkDistanceManager(Executor workerExecutor, Executor mainThreadExecutor) {
            super(workerExecutor, mainThreadExecutor, ChunkMap.this);
        }

        @Override
        protected boolean isChunkToRemove(long pos) {
            throw new UnsupportedOperationException(); // Paper - rewrite chunk system
        }

        @Nullable
        @Override
        protected ChunkHolder getChunk(long pos) {
            return ChunkMap.this.getUpdatingChunkIfPresent(pos);
        }

        @Nullable
        @Override
        protected ChunkHolder updateChunkScheduling(long pos, int level, @Nullable ChunkHolder holder, int k) {
            return ChunkMap.this.updateChunkScheduling(pos, level, holder, k);
        }
    }

    public class TrackedEntity {

        public final ServerEntity serverEntity;
        public final Entity entity; // Leaf - petal - public
        private final int range;
        SectionPos lastSectionPos;
        public final Set<ServerPlayerConnection> seenBy = Sets.newConcurrentHashSet(); // Paper - Perf: optimise map impl // Leaf - Fix tracker NPE

        public TrackedEntity(Entity entity, int i, int j, boolean flag) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, entity, j, flag, this::broadcast, this.seenBy); // CraftBukkit
            this.entity = entity;
            this.range = i;
            this.lastSectionPos = SectionPos.of((EntityAccess) entity);
        }

        // Paper start - use distance map to optimise tracker
        com.destroystokyo.paper.util.misc.PooledLinkedHashSets.PooledObjectLinkedOpenHashSet<ServerPlayer> lastTrackerCandidates;

        public final void updatePlayers(com.destroystokyo.paper.util.misc.PooledLinkedHashSets.PooledObjectLinkedOpenHashSet<ServerPlayer> newTrackerCandidates) { // Leaf - petal - public
            com.destroystokyo.paper.util.misc.PooledLinkedHashSets.PooledObjectLinkedOpenHashSet<ServerPlayer> oldTrackerCandidates = this.lastTrackerCandidates;
            this.lastTrackerCandidates = newTrackerCandidates;

            if (newTrackerCandidates != null) {
                Object[] rawData = newTrackerCandidates.getBackingSet();
                for (int i = 0, len = rawData.length; i < len; ++i) {
                    Object raw = rawData[i];
                    if (!(raw instanceof ServerPlayer)) {
                        continue;
                    }
                    ServerPlayer player = (ServerPlayer)raw;
                    this.updatePlayer(player);
                }
            }

            if (oldTrackerCandidates == newTrackerCandidates) {
                // this is likely the case.
                // means there has been no range changes, so we can just use the above for tracking.
                return;
            }

            // stuff could have been removed, so we need to check the trackedPlayers set
            // for players that were removed

            for (ServerPlayerConnection conn : this.seenBy.toArray(new ServerPlayerConnection[0])) { // avoid CME
                if (newTrackerCandidates == null || !newTrackerCandidates.contains(conn.getPlayer())) {
                    this.updatePlayer(conn.getPlayer());
                }
            }
        }
        // Paper end - use distance map to optimise tracker

        public boolean equals(Object object) {
            return object instanceof ChunkMap.TrackedEntity ? ((ChunkMap.TrackedEntity) object).entity.getId() == this.entity.getId() : false;
        }

        public int hashCode() {
            return this.entity.getId();
        }

        public void broadcast(Packet<?> packet) {
            // Leaf start - petal - avoid NPE
            for (ServerPlayerConnection serverplayerconnection : this.seenBy.toArray(new ServerPlayerConnection[0])) {
                serverplayerconnection.send(packet);
            }
            // Leaf end - petal
        }

        public void broadcastAndSend(Packet<?> packet) {
            this.broadcast(packet);
            if (this.entity instanceof ServerPlayer) {
                ((ServerPlayer) this.entity).connection.send(packet);
            }

        }

        public void broadcastRemoved() {
            // Leaf start - petal - avoid NPE
            for (ServerPlayerConnection serverplayerconnection : this.seenBy.toArray(new ServerPlayerConnection[0])) {
                this.serverEntity.removePairing(serverplayerconnection.getPlayer());
            }
            // Leaf end - petal
        }

        public void removePlayer(ServerPlayer player) {
            //org.spigotmc.AsyncCatcher.catchOp("player tracker clear"); // Spigot // Leaf - petal - We can remove async too
            if (this.seenBy.remove(player.connection)) {
                this.serverEntity.removePairing(player);
            }

        }

        public void updatePlayer(ServerPlayer player) {
            //org.spigotmc.AsyncCatcher.catchOp("player tracker update"); // Spigot // Leaf - petal - We can update async
            if (player != this.entity) {
                // Paper start - remove allocation of Vec3D here
                // Vec3 vec3d = player.position().subtract(this.entity.position());
                double vec3d_dx = player.getX() - this.entity.getX();
                double vec3d_dz = player.getZ() - this.entity.getZ();
                // Paper end - remove allocation of Vec3D here
                int i = ChunkMap.this.getPlayerViewDistance(player);
                double d0 = (double) Math.min(this.getEffectiveRange(), i * 16);
                double d1 = vec3d_dx * vec3d_dx + vec3d_dz * vec3d_dz; // Paper
                double d2 = d0 * d0;
                boolean flag = d1 <= d2 && this.entity.broadcastToPlayer(player) && ChunkMap.this.isChunkTracked(player, this.entity.chunkPosition().x, this.entity.chunkPosition().z);
                // Paper start - Configurable entity tracking range by Y
                if (flag && level.paperConfig().entities.trackingRangeY.enabled) {
                    double rangeY = level.paperConfig().entities.trackingRangeY.get(this.entity, -1);
                    if (rangeY != -1) {
                        double vec3d_dy = player.getY() - this.entity.getY();
                        flag = vec3d_dy * vec3d_dy <= rangeY * rangeY;
                    }
                }
                // Paper end - Configurable entity tracking range by Y

                // CraftBukkit start - respect vanish API
                if (flag && !player.getBukkitEntity().canSeeChunkMapUpdatePlayer(this.entity.getBukkitEntity())) { // Paper - only consider hits // SparklyPaper - optimize canSee checks
                    flag = false;
                }
                // CraftBukkit end
                if (flag) {
                    if (this.seenBy.add(player.connection)) {
                        // Paper start - entity tracking events
                        if (io.papermc.paper.event.player.PlayerTrackEntityEvent.getHandlerList().getRegisteredListeners().length == 0 || new io.papermc.paper.event.player.PlayerTrackEntityEvent(player.getBukkitEntity(), this.entity.getBukkitEntity()).callEvent()) {
                        this.serverEntity.addPairing(player);
                        }
                        // Paper end - entity tracking events
                    }
                } else if (this.seenBy.remove(player.connection)) {
                    this.serverEntity.removePairing(player);
                }

            }
        }

        private int scaledRange(int initialDistance) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(initialDistance);
        }

        // Gale start - Airplane - remove streams and iterators from range check
        private static int getHighestRange(Entity parent, int highest) {
            List<Entity> passengers = parent.getPassengers();

            for (int i = 0, size = passengers.size(); i < size; i++) {
                Entity entity = passengers.get(i);
                int range = entity.getType().clientTrackingRange() * 16;
                range = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, range); // Paper

                if (range > highest) { // Paper - we need the lowest range thanks to the fact that our tracker doesn't account for passenger logic // Tuinity - not anymore!
                    highest = range;
                }

                highest = getHighestRange(entity, highest);
            }

            return highest;
        }
        // Gale end - Airplane - remove streams and iterators from range check

        private int getEffectiveRange() {
            int i = this.range;
            // Gale start - Airplane - remove streams and iterators from range check
            /*
            Iterator iterator = this.entity.getIndirectPassengers().iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();
                int j = entity.getType().clientTrackingRange() * 16;
                j = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, j); // Paper

                if (j > i) {
                    i = j;
                }
            }
             */
            i = getHighestRange(this.entity, i);
            // Gale end - Airplane - remove streams and iterators from range check

            return this.scaledRange(i);
        }

        public void updatePlayers(List<ServerPlayer> players) {
            Iterator iterator = players.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                this.updatePlayer(entityplayer);
            }

        }
    }
}
