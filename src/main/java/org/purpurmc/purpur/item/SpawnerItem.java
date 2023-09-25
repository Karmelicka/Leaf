package org.purpurmc.purpur.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerItem extends BlockItem {

    public SpawnerItem(Block block, Properties settings) {
        super(block, settings);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        boolean handled = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level.purpurConfig.silkTouchEnabled && player.getBukkitEntity().hasPermission("purpur.place.spawners")) {
            BlockEntity spawner = level.getBlockEntity(pos);
            if (spawner instanceof SpawnerBlockEntity && stack.hasTag()) {
                CompoundTag tag = stack.getTag();
                if (tag.contains("Purpur.mob_type")) {
                    EntityType.byString(tag.getString("Purpur.mob_type")).ifPresent(type ->
                            ((SpawnerBlockEntity) spawner).getSpawner().setEntityId(type, level, level.random, pos));
                } else if (tag.contains("BlockEntityTag")) {
                    spawner.load(tag.getCompound("BlockEntityTag"));
                }
            }
        }
        return handled;
    }
}
