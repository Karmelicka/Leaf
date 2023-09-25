package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<SpawnerBlock> CODEC = simpleCodec(SpawnerBlock::new);

    @Override
    public MapCodec<SpawnerBlock> codec() {
        return SpawnerBlock.CODEC;
    }

    protected SpawnerBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntityType.MOB_SPAWNER, world.isClientSide ? SpawnerBlockEntity::clientTick : SpawnerBlockEntity::serverTick);
    }

    // Purpur start
    @Override
    public void playerDestroy(Level level, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack stack, boolean includeDrops, boolean dropExp) {
        if (level.purpurConfig.silkTouchEnabled && player.getBukkitEntity().hasPermission("purpur.drop.spawners") && isSilkTouch(level, stack)) {
            java.util.Optional<net.minecraft.world.entity.EntityType<?>> type = net.minecraft.world.entity.EntityType.by(((SpawnerBlockEntity) blockEntity).getSpawner().nextSpawnData.getEntityToSpawn());

            net.minecraft.world.entity.EntityType<?> entityType = type.orElse(null);
            final net.kyori.adventure.text.Component mobName = io.papermc.paper.adventure.PaperAdventure.asAdventure(entityType == null ? Component.empty() : entityType.getDescription());
            net.minecraft.nbt.CompoundTag display = new net.minecraft.nbt.CompoundTag();
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            net.minecraft.nbt.CompoundTag blockEntityTag = blockEntity.getUpdateTag();
            blockEntityTag.remove("Delay"); // remove this tag to allow stacking duplicate spawners
            tag.put("BlockEntityTag", blockEntityTag);

            String name = level.purpurConfig.silkTouchSpawnerName;
            if (name != null && !name.isEmpty() && !name.equals("Monster Spawner")) {
                net.kyori.adventure.text.Component displayName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name, net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("mob", mobName));
                if (name.startsWith("<reset>")) {
                    displayName = displayName.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                }
                display.put("Name", net.minecraft.nbt.StringTag.valueOf(io.papermc.paper.adventure.PaperAdventure.asJsonString(displayName, java.util.Locale.ROOT)));
                tag.put("display", display);
            }

            List<String> lore = level.purpurConfig.silkTouchSpawnerLore;
            if (lore != null && !lore.isEmpty()) {
                net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                for (String line : lore) {
                    net.kyori.adventure.text.Component lineComponent = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line, net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("mob", mobName));
                    if (line.startsWith("<reset>")) {
                        lineComponent = lineComponent.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                    }
                    list.add(net.minecraft.nbt.StringTag.valueOf(io.papermc.paper.adventure.PaperAdventure.asJsonString(lineComponent, java.util.Locale.ROOT)));
                }
                display.put("Lore", list);
                tag.put("display", display);
            }

            ItemStack item = new ItemStack(Blocks.SPAWNER.asItem());
            if (entityType != null) {
                tag.putDouble("HideFlags", ItemStack.TooltipPart.ADDITIONAL.getMask()); // hides the "Interact with Spawn Egg" tooltip
                item.setTag(tag);
            }

            popResource(level, pos, item);
        }
        super.playerDestroy(level, player, pos, state, blockEntity, stack, includeDrops, dropExp);
    }

    private boolean isSilkTouch(Level level, ItemStack stack) {
        return stack != null && level.purpurConfig.silkTouchTools.contains(stack.getItem()) && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, stack) >= level.purpurConfig.minimumSilkTouchSpawnerRequire;
    }
    // Purpur end

    @Override
    public void spawnAfterBreak(BlockState state, ServerLevel world, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, world, pos, tool, dropExperience);
        // CraftBukkit start - Delegate to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, ItemStack itemstack, boolean flag) {
        if (isSilkTouch(worldserver, itemstack)) return 0; // Purpur
        if (flag) {
            int i = 15 + worldserver.random.nextInt(15) + worldserver.random.nextInt(15);

            // this.popExperience(worldserver, blockposition, i);
            return i;
        }

        return 0;
        // CraftBukkit end
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter world, List<Component> tooltip, TooltipFlag options) {
        super.appendHoverText(stack, world, tooltip, options);
        Spawner.appendHoverText(stack, tooltip, "SpawnData");
    }
}
