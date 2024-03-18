package net.minecraft.world.level.block.entity;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.ticks.ContainerSingleItem;

// CraftBukkit start
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class DecoratedPotBlockEntity extends BlockEntity implements RandomizableContainer, ContainerSingleItem {

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return Arrays.asList(this.item);
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
       return this.maxStack;
    }

    @Override
    public void setMaxStackSize(int i) {
        this.maxStack = i;
    }

    @Override
    public Location getLocation() {
        if (this.level == null) return null;
        return CraftLocation.toBukkit(this.worldPosition, this.level.getWorld());
    }
    // CraftBukkit end

    public static final String TAG_SHERDS = "sherds";
    public static final String TAG_ITEM = "item";
    public static final int EVENT_POT_WOBBLES = 1;
    public long wobbleStartedAtTick;
    @Nullable
    public DecoratedPotBlockEntity.WobbleStyle lastWobbleStyle;
    public DecoratedPotBlockEntity.Decorations decorations;
    private ItemStack item;
    @Nullable
    protected ResourceLocation lootTable;
    protected long lootTableSeed;

    public DecoratedPotBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.DECORATED_POT, pos, state);
        this.item = ItemStack.EMPTY;
        this.decorations = DecoratedPotBlockEntity.Decorations.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        this.decorations.save(nbt);
        if (!this.trySaveLootTable(nbt) && !this.item.isEmpty()) {
            nbt.put("item", this.item.save(new CompoundTag()));
        }

    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.decorations = DecoratedPotBlockEntity.Decorations.load(nbt);
        if (!this.tryLoadLootTable(nbt)) {
            if (nbt.contains("item", 10)) {
                this.item = ItemStack.of(nbt.getCompound("item"));
            } else {
                this.item = ItemStack.EMPTY;
            }
        }

    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public Direction getDirection() {
        return (Direction) this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    public DecoratedPotBlockEntity.Decorations getDecorations() {
        return this.decorations;
    }

    public void setFromItem(ItemStack stack) {
        this.decorations = DecoratedPotBlockEntity.Decorations.load(BlockItem.getBlockEntityData(stack));
    }

    public ItemStack getPotAsItem() {
        return DecoratedPotBlockEntity.createDecoratedPotItem(this.decorations);
    }

    public static ItemStack createDecoratedPotItem(DecoratedPotBlockEntity.Decorations sherds) {
        ItemStack itemstack = Items.DECORATED_POT.getDefaultInstance();
        CompoundTag nbttagcompound = sherds.save(new CompoundTag());

        BlockItem.setBlockEntityData(itemstack, BlockEntityType.DECORATED_POT, nbttagcompound);
        return itemstack;
    }

    @Nullable
    @Override
    public ResourceLocation getLootTable() {
        return this.lootTable;
    }

    @Override
    public void setLootTable(@Nullable ResourceLocation lootTableId) {
        this.lootTable = lootTableId;
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long lootTableSeed) {
        this.lootTableSeed = lootTableSeed;
    }

    @Override
    public ItemStack getTheItem() {
        this.unpackLootTable((Player) null);
        return this.item;
    }

    @Override
    public ItemStack splitTheItem(int count) {
        this.unpackLootTable((Player) null);
        ItemStack itemstack = this.item.split(count);

        if (this.item.isEmpty()) {
            this.item = ItemStack.EMPTY;
        }

        return itemstack;
    }

    @Override
    public void setTheItem(ItemStack stack) {
        this.unpackLootTable((Player) null);
        this.item = stack;
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    public void wobble(DecoratedPotBlockEntity.WobbleStyle wobbleType) {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.blockEvent(this.getBlockPos(), this.getBlockState().getBlock(), 1, wobbleType.ordinal());
        }
    }

    @Override
    public boolean triggerEvent(int type, int data) {
        if (this.level != null && type == 1 && data >= 0 && data < DecoratedPotBlockEntity.WobbleStyle.values().length) {
            this.wobbleStartedAtTick = this.level.getGameTime();
            this.lastWobbleStyle = DecoratedPotBlockEntity.WobbleStyle.values()[data];
            return true;
        } else {
            return super.triggerEvent(type, data);
        }
    }

    public static record Decorations(Item back, Item left, Item right, Item front) {

        public static final DecoratedPotBlockEntity.Decorations EMPTY = new DecoratedPotBlockEntity.Decorations(Items.BRICK, Items.BRICK, Items.BRICK, Items.BRICK);

        public CompoundTag save(CompoundTag nbt) {
            if (this.equals(DecoratedPotBlockEntity.Decorations.EMPTY)) {
                return nbt;
            } else {
                ListTag nbttaglist = new ListTag();

                this.sorted().forEach((item) -> {
                    nbttaglist.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
                });
                nbt.put("sherds", nbttaglist);
                return nbt;
            }
        }

        public Stream<Item> sorted() {
            return Stream.of(this.back, this.left, this.right, this.front);
        }

        public static DecoratedPotBlockEntity.Decorations load(@Nullable CompoundTag nbt) {
            if (nbt != null && nbt.contains("sherds", 9)) {
                ListTag nbttaglist = nbt.getList("sherds", 8);

                return new DecoratedPotBlockEntity.Decorations(itemFromTag(nbttaglist, 0), itemFromTag(nbttaglist, 1), itemFromTag(nbttaglist, 2), itemFromTag(nbttaglist, 3));
            } else {
                return DecoratedPotBlockEntity.Decorations.EMPTY;
            }
        }

        private static Item itemFromTag(ListTag list, int index) {
            if (index >= list.size()) {
                return Items.BRICK;
            } else {
                Tag nbtbase = list.get(index);

                return (Item) BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(nbtbase.getAsString()));
            }
        }
    }

    public static enum WobbleStyle {

        POSITIVE(7), NEGATIVE(10);

        public final int duration;

        private WobbleStyle(int i) {
            this.duration = i;
        }
    }
}
