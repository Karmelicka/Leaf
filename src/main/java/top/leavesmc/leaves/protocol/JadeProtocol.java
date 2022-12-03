package top.leavesmc.leaves.protocol;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CalibratedSculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.leavesmc.leaves.protocol.core.LeavesProtocol;
import top.leavesmc.leaves.protocol.core.ProtocolHandler;
import top.leavesmc.leaves.protocol.core.ProtocolUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@LeavesProtocol(namespace = "jade")
public class JadeProtocol {

    public static final String PROTOCOL_ID = "jade";

    // send
    public static final ResourceLocation PACKET_SERVER_PING = id("server_ping");
    public static final ResourceLocation PACKET_RECEIVE_DATA = id("receive_data");

    private static final HierarchyLookup<IJadeDataProvider<Entity>> entityDataProviders = new HierarchyLookup<>(Entity.class);
    private static final HierarchyLookup<IJadeDataProvider<BlockEntity>> tileDataProviders = new HierarchyLookup<>(BlockEntity.class);

    private static final HierarchyLookup<IServerExtensionProvider<Entity, ItemStack>> entityItemProviders = new HierarchyLookup<>(Entity.class);
    private static final HierarchyLookup<IServerExtensionProvider<BlockEntity, ItemStack>> tileItemProviders = new HierarchyLookup<>(BlockEntity.class);

    public static final Cache<Object, ItemCollector<?>> targetCache = CacheBuilder.newBuilder().weakKeys().expireAfterAccess(60, TimeUnit.SECONDS).build();

    public static final int MAX_DISTANCE_SQR = 900;

    @Contract("_ -> new")
    public static @NotNull ResourceLocation id(String path) {
        return new ResourceLocation(PROTOCOL_ID, path);
    }

    @ProtocolHandler.Init
    public static void init() {
        entityItemProviders.register(Entity.class, (player, world, target) -> {
            if (target instanceof ContainerEntity containerEntity && containerEntity.getLootTable() != null) {
                return List.of();
            }

            ItemCollector<?> itemCollector;
            try {
                itemCollector = targetCache.get(target, () -> {
                    if (target instanceof AbstractHorse) {
                        return new ItemCollector<>(new ItemIterator.ContainerItemIterator(o -> {
                            if (o instanceof AbstractHorse horse) {
                                return horse.inventory;
                            }
                            return null;
                        }, 2));
                    }
                    return ItemCollector.EMPTY;
                });
            } catch (ExecutionException e) {
                return null;
            }

            if (itemCollector == ItemCollector.EMPTY) {
                return null;
            }
            return itemCollector.update(target, world.getGameTime());
        });

        tileItemProviders.register(CampfireBlockEntity.class, (player, world, target) -> {
            CampfireBlockEntity campfire = (CampfireBlockEntity) target;
            List<ItemStack> list = Lists.newArrayList();
            for (int i = 0; i < campfire.cookingTime.length; i++) {
                ItemStack stack = campfire.getItems().get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                stack = stack.copy();
                stack.getOrCreateTag().putInt("jade:cooking", campfire.cookingTime[i] - campfire.cookingProgress[i]);
                list.add(stack);
            }
            return List.of(new ViewGroup<>(list));
        });
        tileItemProviders.register(BlockEntity.class, (player, world, target) -> {
            if (target instanceof RandomizableContainer te && te.getLootTable() != null) {
                return List.of();
            }

            if (!player.isCreative() && !player.isSpectator() && target instanceof BaseContainerBlockEntity te) {
                if (te.lockKey != LockCode.NO_LOCK) {
                    return List.of();
                }
            }
            if (target instanceof EnderChestBlockEntity) {
                PlayerEnderChestContainer inventory = player.getEnderChestInventory();
                return new ItemCollector<>(new ItemIterator.ContainerItemIterator(0)).update(inventory, world.getGameTime());
            }
            ItemCollector<?> itemCollector;
            try {
                itemCollector = targetCache.get(target, () -> {
                    if (target instanceof Container) {
                        if (target instanceof ChestBlockEntity) {
                            return new ItemCollector<>(new ItemIterator.ContainerItemIterator(o -> {
                                if (o instanceof ChestBlockEntity be) {
                                    if (be.getBlockState().getBlock() instanceof ChestBlock chestBlock) {
                                        Container compound = ChestBlock.getContainer(chestBlock, be.getBlockState(), world, be.getBlockPos(), false);
                                        if (compound != null) {
                                            return compound;
                                        }
                                    }
                                    return be;
                                }
                                return null;
                            }, 0));
                        }
                        return new ItemCollector<>(new ItemIterator.ContainerItemIterator(0));
                    }
                    return ItemCollector.EMPTY;
                });
            } catch (ExecutionException e) {
                return null;
            }

            if (itemCollector == ItemCollector.EMPTY) {
                return null;
            }
            return itemCollector.update(target, world.getGameTime());
        });

        entityDataProviders.register(OwnableEntity.class, ((data, player, world, entity, showDetails) -> {
            UUID ownerUUID = ((OwnableEntity) entity).getOwnerUUID();
            if (ownerUUID != null) {
                GameProfileCache cache = MinecraftServer.getServer().getProfileCache();
                if (cache != null) {
                    cache.get(ownerUUID).map(GameProfile::getName).ifPresent(name -> data.putString("OwnerName", name));
                }
            }
        }));
        entityDataProviders.register(LivingEntity.class, ((data, player, world, entity, showDetails) -> {
            LivingEntity living = (LivingEntity) entity;
            Collection<MobEffectInstance> effects = living.getActiveEffects();
            if (effects.isEmpty()) {
                return;
            }
            ListTag list = new ListTag();
            for (MobEffectInstance effect : effects) {
                CompoundTag compound = new CompoundTag();
                compound.putString("Name", Component.Serializer.toJson(getEffectName(effect)));
                if (effect.isInfiniteDuration()) {
                    compound.putBoolean("Infinite", true);
                } else {
                    compound.putInt("Duration", effect.getDuration());
                }
                compound.putBoolean("Bad", effect.getEffect().getCategory() == MobEffectCategory.HARMFUL);
                list.add(compound);
            }
            data.put("StatusEffects", list);
        }));
        entityDataProviders.register(AgeableMob.class, ((data, player, world, entity, showDetails) -> {
            int time = -((AgeableMob) entity).getAge();
            if (time > 0) {
                data.putInt("GrowingTime", time);
            }
        }));
        entityDataProviders.register(Tadpole.class, ((data, player, world, entity, showDetails) -> {
            int time = ((Tadpole) entity).getTicksLeftUntilAdult();
            if (time > 0) {
                data.putInt("GrowingTime", time);
            }
        }));
        entityDataProviders.register(Animal.class, ((data, player, world, entity, showDetails) -> {
            int time = ((Animal) entity).getAge();
            if (time > 0) {
                data.putInt("BreedingCD", time);
            }
        }));
        entityDataProviders.register(Allay.class, ((data, player, world, entity, showDetails) -> {
            int time = 0;
            Allay allay = (Allay) entity;
            if (allay.duplicationCooldown > 0 && allay.duplicationCooldown < Integer.MAX_VALUE) {
                time = (int) allay.duplicationCooldown;
            }
            if (time > 0) {
                data.putInt("BreedingCD", time);
            }
        }));
        entityDataProviders.register(Chicken.class, ((data, player, world, entity, showDetails) -> {
            data.putInt("NextEggIn", ((Chicken) entity).eggTime);
        }));
        entityDataProviders.register(Entity.class, ((tag, player, world, object, showDetails) -> {
            for (var provider : entityItemProviders.get(object)) {
                var groups = provider.getGroups(player, world, object);
                if (groups == null) {
                    continue;
                }
                if (ViewGroup.saveList(tag, "JadeItemStorage", groups, item -> {
                    CompoundTag itemTag = new CompoundTag();
                    int count = item.getCount();
                    if (count > 64) {
                        item.setCount(1);
                    }
                    item.save(itemTag);
                    if (count > 64) {
                        itemTag.putInt("NewCount", count);
                        item.setCount(count);
                    }
                    return itemTag;
                })) {
                    tag.putString("JadeItemStorageUid", "minecraft:item_storage");
                }
                break;
            }
        }));
        entityDataProviders.register(ZombieVillager.class, (data, player, world, object, showDetails) -> {
            ZombieVillager entity = (ZombieVillager) object;
            if (entity.villagerConversionTime > 0) {
                data.putInt("ConversionTime", entity.villagerConversionTime);
            }
        });

        tileDataProviders.register(BrewingStandBlockEntity.class, ((data, player, world, object, showDetails) -> {
            if (object instanceof BrewingStandBlockEntity brewingStand) {
                CompoundTag compound = new CompoundTag();
                compound.putInt("Time", brewingStand.brewTime);
                compound.putInt("Fuel", brewingStand.fuel);
                data.put("BrewingStand", compound);
            }
        }));
        tileDataProviders.register(BeehiveBlockEntity.class, ((data, player, world, object, showDetails) -> {
            data.getAllKeys().clear();
            BeehiveBlockEntity beehive = (BeehiveBlockEntity) object;
            data.putByte("Bees", (byte) beehive.getOccupantCount());
            data.putBoolean("Full", beehive.isFull());
        }));
        tileDataProviders.register(CommandBlockEntity.class, ((data, player, world, object, showDetails) -> {
            if (!player.canUseGameMasterBlocks()) {
                return;
            }
            BaseCommandBlock logic = ((CommandBlockEntity) object).getCommandBlock();
            String command = logic.getCommand();
            if (command.isEmpty()) {
                return;
            }
            if (command.length() > 40) {
                command = command.substring(0, 37) + "...";
            }
            data.putString("Command", command);
        }));
        tileDataProviders.register(JukeboxBlockEntity.class, ((data, player, world, object, showDetails) -> {
            if (object instanceof JukeboxBlockEntity jukebox) {
                ItemStack stack = jukebox.getTheItem();
                if (!stack.isEmpty()) {
                    data.put("Record", stack.save(new CompoundTag()));
                }
            }
        }));
        tileDataProviders.register(LecternBlockEntity.class, ((data, player, world, object, showDetails) -> {
            ItemStack stack = ((LecternBlockEntity) object).getBook();
            if (!stack.isEmpty()) {
                if (stack.hasCustomHoverName() || stack.getItem() != Items.WRITABLE_BOOK) {
                    data.put("Book", stack.save(new CompoundTag()));
                }
            }
        }));
        tileDataProviders.register(ComparatorBlockEntity.class, ((data, player, world, object, showDetails) -> {
            data.putInt("Signal", ((ComparatorBlockEntity) object).getOutputSignal());
        }));
        tileDataProviders.register(HopperBlockEntity.class, ((data, player, world, object, showDetails) -> {
            BlockState state = object.getBlockState();
            if (state.hasProperty(BlockStateProperties.ENABLED) && !state.getValue(BlockStateProperties.ENABLED)) {
                data.putBoolean("HopperLocked", true);
            }
        }));
        tileDataProviders.register(AbstractFurnaceBlockEntity.class, ((data, player, world, object, showDetails) -> {
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) object;
            ListTag items = new ListTag();
            for (int i = 0; i < 3; i++) {
                items.add(furnace.getItem(i).save(new CompoundTag()));
            }
            data.put("furnace", items);
            CompoundTag furnaceTag = furnace.saveWithoutMetadata();
            data.putInt("progress", furnaceTag.getInt("CookTime"));
            data.putInt("total", furnaceTag.getInt("CookTimeTotal"));
        }));
        tileDataProviders.register(BlockEntity.class, ((data, player, world, object, showDetails) -> {
            if (object instanceof Nameable nameable) {
                Component name = null;
                if (nameable instanceof ChestBlockEntity chestBlock) {
                    MenuProvider menuProvider = chestBlock.getBlockState().getMenuProvider(world, chestBlock.getBlockPos());
                    if (menuProvider != null) {
                        name = menuProvider.getDisplayName();
                    }
                } else if (nameable.hasCustomName()) {
                    name = nameable.getDisplayName();
                }

                if (name != null) {
                    data.putString("givenName", Component.Serializer.toJson(name));
                }
            }
        }));
        tileDataProviders.register(ChiseledBookShelfBlockEntity.class, ((data, player, world, object, showDetails) -> {
            ChiseledBookShelfBlockEntity bookShelf = (ChiseledBookShelfBlockEntity) object;
            if (!bookShelf.isEmpty()) {
                data.put("Bookshelf", bookShelf.saveWithoutMetadata());
            }
        }));
        tileDataProviders.register(BlockEntity.class, ((tag, player, world, object, showDetails) -> {
            if (object instanceof AbstractFurnaceBlockEntity) {
                return;
            }

            for (var provider : tileItemProviders.get(object)) {
                var groups = provider.getGroups(player, world, object);
                if (groups == null) {
                    continue;
                }

                if (ViewGroup.saveList(tag, "JadeItemStorage", groups, item -> {
                    CompoundTag itemTag = new CompoundTag();
                    int count = item.getCount();
                    if (count > 64) {
                        item.setCount(1);
                    }
                    item.save(itemTag);
                    if (count > 64) {
                        itemTag.putInt("NewCount", count);
                        item.setCount(count);
                    }
                    return itemTag;
                })) {
                    tag.putString("JadeItemStorageUid", "minecraft:item_storage");
                } else if (object instanceof RandomizableContainer containerEntity && containerEntity.getLootTable() != null) {
                    tag.putBoolean("Loot", true);
                } else if (!player.isCreative() && !player.isSpectator() && object instanceof BaseContainerBlockEntity te) {
                    if (te.lockKey != LockCode.NO_LOCK) {
                        tag.putBoolean("Locked", true);
                    }
                }
                break;
            }
        }));
        tileDataProviders.register(TrialSpawnerBlockEntity.class, (data, player, world, object, showDetails) -> {
            TrialSpawnerBlockEntity spawner = (TrialSpawnerBlockEntity) object;
            TrialSpawnerData spawnerData = spawner.getTrialSpawner().getData();
            if (spawner.getTrialSpawner().canSpawnInLevel(world) && world.getGameTime() < spawnerData.cooldownEndsAt) {
                data.putInt("Cooldown", (int) (spawnerData.cooldownEndsAt - world.getGameTime()));
            }
        });
        tileDataProviders.register(CalibratedSculkSensorBlockEntity.class, ((data, player, world, object, showDetails) -> {
            Direction direction = object.getBlockState().getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
            int signal = world.getSignal(object.getBlockPos().relative(direction), direction);
            data.putInt("Signal", signal);
        }));
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerJoin(ServerPlayer player) {
        if (org.dreeam.leaf.config.modules.network.ProtocolSupport.jadeProtocol) {
            ProtocolUtils.sendPayloadPacket(player, PACKET_SERVER_PING, buf -> buf.writeUtf("{}"));
        }
    }

    @ProtocolHandler.PayloadReceiver(payload = RequestEntityPayload.class, payloadId = "request_entity")
    public static void requestEntityData(ServerPlayer player, RequestEntityPayload payload) {
        if (!org.dreeam.leaf.config.modules.network.ProtocolSupport.jadeProtocol) {
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();
        server.execute(() -> {
            Level world = player.level();
            boolean showDetails = payload.showDetails;
            Entity entity = world.getEntity(payload.entityId);
            if (entity == null || player.distanceToSqr(entity) > MAX_DISTANCE_SQR) {
                return;
            }
            if (payload.partIndex >= 0 && entity instanceof EnderDragon dragon) {
                EnderDragonPart[] parts = dragon.getSubEntities();
                if (payload.partIndex < parts.length) {
                    entity = parts[payload.partIndex];
                }
            }

            var providers = entityDataProviders.get(entity);
            if (providers.isEmpty()) {
                return;
            }

            CompoundTag tag = new CompoundTag();
            for (IJadeDataProvider<Entity> provider : providers) {
                try {
                    provider.saveData(tag, player, world, entity, showDetails);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            tag.putInt("WailaEntityID", entity.getId());

            ProtocolUtils.sendPayloadPacket(player, PACKET_RECEIVE_DATA, buf -> buf.writeNbt(tag));
        });
    }

    @ProtocolHandler.PayloadReceiver(payload = RequestTilePayload.class, payloadId = "request_tile")
    public static void requestTileData(ServerPlayer player, RequestTilePayload payload) {
        if (!org.dreeam.leaf.config.modules.network.ProtocolSupport.jadeProtocol) {
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();
        boolean showDetails = payload.showDetails;
        BlockHitResult result = payload.hitResult;
        BlockPos pos = result.getBlockPos();
        Level world = player.level();
        if (pos.distSqr(player.blockPosition()) > MAX_DISTANCE_SQR || !world.isLoaded(pos)) {
            return;
        }

        server.execute(() -> {
            BlockEntity tile = world.getBlockEntity(pos);
            if (tile == null) return;

            List<IJadeDataProvider<BlockEntity>> providers = tileDataProviders.get(tile);
            if (providers.isEmpty()) {
                return;
            }

            CompoundTag tag = new CompoundTag();
            for (IJadeDataProvider<BlockEntity> provider : providers) {
                try {
                    provider.saveData(tag, player, world, tile, showDetails);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            tag.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(tile.getType()).toString());

            ProtocolUtils.sendPayloadPacket(player, PACKET_RECEIVE_DATA, buf -> buf.writeNbt(tag));
        });
    }

    @ProtocolHandler.ReloadServer
    public static void onServerReload() {
        if (org.dreeam.leaf.config.modules.network.ProtocolSupport.jadeProtocol) {
            enableAllPlayer();
        }
    }

    public static void enableAllPlayer() {
        for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().players) {
            onPlayerJoin(player);
        }
    }

    public record RequestEntityPayload(boolean showDetails, int entityId, int partIndex, float hitX, float hitY, float hitZ) implements CustomPacketPayload {

        private static final ResourceLocation PACKET_REQUEST_ENTITY = JadeProtocol.id("request_entity");

        public RequestEntityPayload(ResourceLocation id, FriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(showDetails);
            buf.writeVarInt(entityId);
            buf.writeVarInt(partIndex);
            buf.writeFloat(hitX);
            buf.writeFloat(hitY);
            buf.writeFloat(hitZ);
        }

        @Override
        @NotNull
        public ResourceLocation id() {
            return PACKET_REQUEST_ENTITY;
        }
    }

    public record RequestTilePayload(boolean showDetails, BlockHitResult hitResult, int blockState, ItemStack fakeBlock) implements CustomPacketPayload {

        private static final ResourceLocation PACKET_REQUEST_TILE = JadeProtocol.id("request_tile");

        public RequestTilePayload(ResourceLocation id, FriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readBlockHitResult(), buf.readVarInt(), buf.readItem());
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(showDetails);
            buf.writeBlockHitResult(hitResult);
            buf.writeVarInt(blockState);
            buf.writeItem(fakeBlock);
        }

        @Override
        @NotNull
        public ResourceLocation id() {
            return PACKET_REQUEST_TILE;
        }
    }

    // Power by Jade

    public static Component getEffectName(MobEffectInstance mobEffectInstance) {
        MutableComponent mutableComponent = mobEffectInstance.getEffect().getDisplayName().copy();
        if (mobEffectInstance.getAmplifier() >= 1 && mobEffectInstance.getAmplifier() <= 9) {
            mutableComponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (mobEffectInstance.getAmplifier() + 1)));
        }
        return mutableComponent;
    }

    public interface IJadeProvider {
    }

    public interface IJadeDataProvider<T> {
        void saveData(CompoundTag data, ServerPlayer player, Level world, T object, boolean showDetails);
    }

    public interface IServerExtensionProvider<IN, OUT> {
        List<ViewGroup<OUT>> getGroups(ServerPlayer player, Level world, IN target);
    }

    public static class ItemCollector<T> {
        public static final int MAX_SIZE = 54;
        public static final ItemCollector<?> EMPTY = new ItemCollector<>(null);
        private static final Predicate<ItemStack> NON_EMPTY = stack -> {
            if (stack.isEmpty()) {
                return false;
            }
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("CustomModelData")) {
                for (String key : stack.getTag().getAllKeys()) {
                    if (key.toLowerCase(Locale.ENGLISH).endsWith("clear") && stack.getTag().getBoolean(key)) {
                        return false;
                    }
                }
            }
            return true;
        };
        private final Object2IntLinkedOpenHashMap<ItemDefinition> items = new Object2IntLinkedOpenHashMap<>();
        private final ItemIterator<T> iterator;
        public long version;
        public long lastTimeFinished;
        public List<ViewGroup<ItemStack>> mergedResult;

        public ItemCollector(ItemIterator<T> iterator) {
            this.iterator = iterator;
        }

        public List<ViewGroup<ItemStack>> update(Object target, long gameTime) {
            if (iterator == null) {
                return null;
            }
            T container = iterator.find(target);
            if (container == null) {
                return null;
            }
            long currentVersion = iterator.getVersion(container);
            if (mergedResult != null && iterator.isFinished()) {
                if (version == currentVersion) {
                    return mergedResult; // content not changed
                }
                if (lastTimeFinished + 5 > gameTime) {
                    return mergedResult; // avoid update too frequently
                }
                iterator.reset();
            }
            AtomicInteger count = new AtomicInteger();
            iterator.populate(container).forEach(stack -> {
                count.incrementAndGet();
                if (NON_EMPTY.test(stack)) {
                    ItemDefinition def = new ItemDefinition(stack);
                    items.addTo(def, stack.getCount());
                }
            });
            iterator.afterPopulate(count.get());
            if (mergedResult != null && !iterator.isFinished()) {
                updateCollectingProgress(mergedResult.get(0));
                return mergedResult;
            }
            List<ItemStack> partialResult = items.object2IntEntrySet().stream().limit(54).map(entry -> {
                ItemDefinition def = entry.getKey();
                return def.toStack(entry.getIntValue());
            }).toList();
            List<ViewGroup<ItemStack>> groups = List.of(updateCollectingProgress(new ViewGroup<>(partialResult)));
            if (iterator.isFinished()) {
                mergedResult = groups;
                version = currentVersion;
                lastTimeFinished = gameTime;
                items.clear();
            }
            return groups;
        }

        protected ViewGroup<ItemStack> updateCollectingProgress(ViewGroup<ItemStack> group) {
            float progress = iterator.getCollectingProgress();
            CompoundTag data = group.getExtraData();
            if (Float.isNaN(progress)) {
                progress = 0;
            }
            if (progress >= 1) {
                data.remove("Collecting");
            } else {
                data.putFloat("Collecting", progress);
            }
            return group;
        }

        public record ItemDefinition(Item item, @Nullable CompoundTag tag) {
            ItemDefinition(ItemStack stack) {
                this(stack.getItem(), stack.getTag());
            }

            public ItemStack toStack(int count) {
                ItemStack stack = new ItemStack(item);
                stack.setCount(count);
                stack.setTag(tag);
                return stack;
            }
        }
    }

    public static abstract class ItemIterator<T> {
        public static final AtomicLong version = new AtomicLong();
        protected final Function<Object, @Nullable T> containerFinder;
        protected final int fromIndex;
        protected boolean finished;
        protected int currentIndex;

        protected ItemIterator(Function<Object, @Nullable T> containerFinder, int fromIndex) {
            this.containerFinder = containerFinder;
            this.currentIndex = this.fromIndex = fromIndex;
        }

        public @Nullable T find(Object target) {
            return containerFinder.apply(target);
        }

        public final boolean isFinished() {
            return finished;
        }

        public long getVersion(T container) {
            return version.getAndIncrement();
        }

        public abstract Stream<ItemStack> populate(T container);

        public void reset() {
            currentIndex = fromIndex;
            finished = false;
        }

        public void afterPopulate(int count) {
            currentIndex += count;
            if (count == 0 || currentIndex >= 10000) {
                finished = true;
            }
        }

        public float getCollectingProgress() {
            return Float.NaN;
        }

        public static abstract class SlottedItemIterator<T> extends ItemIterator<T> {
            protected float progress;

            public SlottedItemIterator(Function<Object, @Nullable T> containerFinder, int fromIndex) {
                super(containerFinder, fromIndex);
            }

            protected abstract int getSlotCount(T container);

            protected abstract ItemStack getItemInSlot(T container, int slot);

            @Override
            public Stream<ItemStack> populate(T container) {
                int slotCount = getSlotCount(container);
                int toIndex = currentIndex + ItemCollector.MAX_SIZE * 2;
                if (toIndex >= slotCount) {
                    toIndex = slotCount;
                    finished = true;
                }
                progress = (float) (currentIndex - fromIndex) / (slotCount - fromIndex);
                return IntStream.range(currentIndex, toIndex).mapToObj(slot -> getItemInSlot(container, slot));
            }

            @Override
            public float getCollectingProgress() {
                return progress;
            }
        }

        public static class ContainerItemIterator extends SlottedItemIterator<Container> {
            public ContainerItemIterator(int fromIndex) {
                this(Container.class::cast, fromIndex);
            }

            public ContainerItemIterator(Function<Object, @Nullable Container> containerFinder, int fromIndex) {
                super(containerFinder, fromIndex);
            }

            @Override
            protected int getSlotCount(Container container) {
                return container.getContainerSize();
            }

            @Override
            protected ItemStack getItemInSlot(Container container, int slot) {
                return container.getItem(slot);
            }
        }
    }

    public static class ViewGroup<T> {

        public final List<T> views;
        @Nullable
        public String id;
        @Nullable
        protected CompoundTag extraData;

        public ViewGroup(List<T> views) {
            this.views = views;
        }

        public void save(CompoundTag tag, Function<T, CompoundTag> writer) {
            ListTag list = new ListTag();
            for (var view : views) {
                list.add(writer.apply(view));
            }
            tag.put("Views", list);
            if (id != null) {
                tag.putString("Id", id);
            }
            if (extraData != null) {
                tag.put("Data", extraData);
            }
        }

        public static <T> boolean saveList(CompoundTag tag, String key, List<ViewGroup<T>> groups, Function<T, CompoundTag> writer) {
            if (groups == null || groups.isEmpty()) {
                return false;
            }

            ListTag groupList = new ListTag();
            for (ViewGroup<T> group : groups) {
                if (group.views.isEmpty()) {
                    continue;
                }
                CompoundTag groupTag = new CompoundTag();
                group.save(groupTag, writer);
                groupList.add(groupTag);
            }
            if (!groupList.isEmpty()) {
                tag.put(key, groupList);
                return true;
            }
            return false;
        }

        public CompoundTag getExtraData() {
            if (extraData == null) {
                extraData = new CompoundTag();
            }
            return extraData;
        }
    }

    public static class HierarchyLookup<T> {

        private final Class<?> baseClass;
        private final Cache<Class<?>, List<T>> resultCache = CacheBuilder.newBuilder().build();
        private final boolean singleton;
        private ListMultimap<Class<?>, T> objects = ArrayListMultimap.create();

        public HierarchyLookup(Class<?> baseClass) {
            this(baseClass, false);
        }

        public HierarchyLookup(Class<?> baseClass, boolean singleton) {
            this.baseClass = baseClass;
            this.singleton = singleton;
        }

        public void register(Class<?> clazz, T provider) {
            Objects.requireNonNull(clazz);
            objects.put(clazz, provider);
        }

        public List<T> get(Object obj) {
            if (obj == null) {
                return List.of();
            }
            return get(obj.getClass());
        }

        public List<T> get(Class<?> clazz) {
            try {
                return resultCache.get(clazz, () -> {
                    List<T> list = Lists.newArrayList();
                    getInternal(clazz, list);
                    if (singleton && !list.isEmpty()) {
                        return ImmutableList.of(list.get(0));
                    }
                    return list;
                });
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return List.of();
        }

        private void getInternal(Class<?> clazz, List<T> list) {
            if (clazz != baseClass && clazz != Object.class) {
                getInternal(clazz.getSuperclass(), list);
            }
            list.addAll(objects.get(clazz));
        }

        public Multimap<Class<?>, T> getObjects() {
            return objects;
        }
    }
}
