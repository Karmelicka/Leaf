package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public abstract class RandomizableContainerBlockEntity extends BaseContainerBlockEntity implements RandomizableContainer {
    @Nullable
    public ResourceLocation lootTable;
    public long lootTableSeed;
    public final com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData = new com.destroystokyo.paper.loottable.PaperLootableInventoryData(new com.destroystokyo.paper.loottable.PaperTileEntityLootableInventory(this)); // Paper

    protected RandomizableContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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

    // Paper start
    @Override
    public boolean tryLoadLootTable(final net.minecraft.nbt.CompoundTag nbt) {
        // Copied from super with changes, always check the original method
        this.lootableData.loadNbt(nbt); // Paper
        if (nbt.contains("LootTable", 8)) {
            this.setLootTable(ResourceLocation.tryParse(nbt.getString("LootTable")));
            try { org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(this.lootTable); } catch (IllegalArgumentException ex) { this.lootTable = null; } // Paper - validate
            this.setLootTableSeed(nbt.getLong("LootTableSeed"));
            return false; // Paper - always load the items, table may still remain
        } else {
            return false;
        }
    }

    @Override
    public boolean trySaveLootTable(final net.minecraft.nbt.CompoundTag nbt) {
        this.lootableData.saveNbt(nbt);
        RandomizableContainer.super.trySaveLootTable(nbt);
        return false;
    }

    @Override
    public void unpackLootTable(@org.jetbrains.annotations.Nullable final Player player) {
        if (player == null) return; // Gale - EMC - don't trigger lootable refresh for non-player interaction
        // Copied from super with changes, always check the original method
        net.minecraft.world.level.Level level = this.getLevel();
        BlockPos blockPos = this.getBlockPos();
        ResourceLocation resourceLocation = this.getLootTable();
        if (this.lootableData.shouldReplenish(player) && level != null) { // Paper
            net.minecraft.world.level.storage.loot.LootTable lootTable = level.getServer().getLootData().getLootTable(resourceLocation);
            if (player instanceof net.minecraft.server.level.ServerPlayer) {
                net.minecraft.advancements.CriteriaTriggers.GENERATE_LOOT.trigger((net.minecraft.server.level.ServerPlayer)player, resourceLocation);
            }

            this.lootableData.processRefill(player); // Paper
            net.minecraft.world.level.storage.loot.LootParams.Builder builder = (new net.minecraft.world.level.storage.loot.LootParams.Builder((net.minecraft.server.level.ServerLevel)level)).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(blockPos));
            if (player != null) {
                builder.withLuck(player.getLuck()).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, player);
            }

            lootTable.fill(this, builder.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST), this.getLootTableSeed());
        }

    }
    // Paper end

    @Override
    public boolean isEmpty() {
        this.unpackLootTable((Player)null);
        // Paper start - Perf: Optimize Hoppers
        return this.isCompletelyEmpty(null); // Gale - Airplane - improve container checking with a bitset - use super
        // Paper end - Perf: Optimize Hoppers
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == 0) this.unpackLootTable((Player) null); // Paper - Perf: Optimize Hoppers
        return this.getItems().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        this.unpackLootTable((Player)null);
        ItemStack itemStack = ContainerHelper.removeItem(this.getItems(), slot, amount);
        if (!itemStack.isEmpty()) {
            this.setChanged();
        }

        return itemStack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        this.unpackLootTable((Player)null);
        return ContainerHelper.takeItem(this.getItems(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.unpackLootTable((Player)null);
        this.getItems().set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.getItems().clear();
    }

    protected abstract NonNullList<ItemStack> getItems();

    protected abstract void setItems(NonNullList<ItemStack> list);

    @Override
    public boolean canOpen(Player player) {
        return super.canOpen(player) && (this.lootTable == null || !player.isSpectator());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        if (this.canOpen(player)) {
            this.unpackLootTable(playerInventory.player);
            return this.createMenu(syncId, playerInventory);
        } else {
            return null;
        }
    }
}
