package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DecoratedPotBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<DecoratedPotBlock> CODEC = simpleCodec(DecoratedPotBlock::new);
    public static final ResourceLocation SHERDS_DYNAMIC_DROP_ID = new ResourceLocation("sherds");
    private static final VoxelShape BOUNDING_BOX = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
    private static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CRACKED = BlockStateProperties.CRACKED;
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    @Override
    public MapCodec<DecoratedPotBlock> codec() {
        return DecoratedPotBlock.CODEC;
    }

    protected DecoratedPotBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DecoratedPotBlock.HORIZONTAL_FACING, Direction.NORTH)).setValue(DecoratedPotBlock.WATERLOGGED, false)).setValue(DecoratedPotBlock.CRACKED, false));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(DecoratedPotBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());

        return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(DecoratedPotBlock.HORIZONTAL_FACING, ctx.getHorizontalDirection())).setValue(DecoratedPotBlock.WATERLOGGED, fluid.getType() == Fluids.WATER)).setValue(DecoratedPotBlock.CRACKED, false);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof DecoratedPotBlockEntity) {
            DecoratedPotBlockEntity decoratedpotblockentity = (DecoratedPotBlockEntity) tileentity;

            if (world.isClientSide) {
                return InteractionResult.CONSUME;
            } else {
                ItemStack itemstack = player.getItemInHand(hand);
                ItemStack itemstack1 = decoratedpotblockentity.getTheItem();

                if (!itemstack.isEmpty() && (itemstack1.isEmpty() || ItemStack.isSameItemSameTags(itemstack1, itemstack) && itemstack1.getCount() < itemstack1.getMaxStackSize())) {
                    decoratedpotblockentity.wobble(DecoratedPotBlockEntity.WobbleStyle.POSITIVE);
                    player.awardStat(Stats.ITEM_USED.get(itemstack.getItem()));
                    ItemStack itemstack2 = player.isCreative() ? itemstack.copyWithCount(1) : itemstack.split(1);
                    float f;

                    if (decoratedpotblockentity.isEmpty()) {
                        decoratedpotblockentity.setTheItem(itemstack2);
                        f = (float) itemstack2.getCount() / (float) itemstack2.getMaxStackSize();
                    } else {
                        itemstack1.grow(1);
                        f = (float) itemstack1.getCount() / (float) itemstack1.getMaxStackSize();
                    }

                    world.playSound((Player) null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F * f);
                    if (world instanceof ServerLevel) {
                        ServerLevel worldserver = (ServerLevel) world;

                        worldserver.sendParticles(ParticleTypes.DUST_PLUME, (double) pos.getX() + 0.5D, (double) pos.getY() + 1.2D, (double) pos.getZ() + 0.5D, 7, 0.0D, 0.0D, 0.0D, 0.0D);
                    }

                    decoratedpotblockentity.setChanged();
                } else {
                    world.playSound((Player) null, pos, SoundEvents.DECORATED_POT_INSERT_FAIL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    decoratedpotblockentity.wobble(DecoratedPotBlockEntity.WobbleStyle.NEGATIVE);
                }

                world.gameEvent((Entity) player, GameEvent.BLOCK_CHANGE, pos);
                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        if (world.isClientSide) {
            world.getBlockEntity(pos, BlockEntityType.DECORATED_POT).ifPresent((decoratedpotblockentity) -> {
                decoratedpotblockentity.setFromItem(itemStack);
            });
        }

    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return DecoratedPotBlock.BOUNDING_BOX;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DecoratedPotBlock.HORIZONTAL_FACING, DecoratedPotBlock.WATERLOGGED, DecoratedPotBlock.CRACKED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecoratedPotBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        Containers.dropContentsOnDestroy(state, newState, world, pos);
        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity tileentity = (BlockEntity) builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);

        if (tileentity instanceof DecoratedPotBlockEntity) {
            DecoratedPotBlockEntity decoratedpotblockentity = (DecoratedPotBlockEntity) tileentity;

            builder.withDynamicDrop(DecoratedPotBlock.SHERDS_DYNAMIC_DROP_ID, (consumer) -> {
                decoratedpotblockentity.getDecorations().sorted().map(Item::getDefaultInstance).forEach(consumer);
            });
        }

        return super.getDrops(state, builder);
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        ItemStack itemstack = player.getMainHandItem();
        BlockState iblockdata1 = state;

        if (itemstack.is(ItemTags.BREAKS_DECORATED_POTS) && !EnchantmentHelper.hasSilkTouch(itemstack)) {
            iblockdata1 = (BlockState) state.setValue(DecoratedPotBlock.CRACKED, true);
            world.setBlock(pos, iblockdata1, 4);
        }

        return super.playerWillDestroy(world, pos, iblockdata1, player);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(DecoratedPotBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public SoundType getSoundType(BlockState state) {
        return (Boolean) state.getValue(DecoratedPotBlock.CRACKED) ? SoundType.DECORATED_POT_CRACKED : SoundType.DECORATED_POT;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter world, List<Component> tooltip, TooltipFlag options) {
        super.appendHoverText(stack, world, tooltip, options);
        DecoratedPotBlockEntity.Decorations decoratedpotblockentity_decoration = DecoratedPotBlockEntity.Decorations.load(BlockItem.getBlockEntityData(stack));

        if (!decoratedpotblockentity_decoration.equals(DecoratedPotBlockEntity.Decorations.EMPTY)) {
            tooltip.add(CommonComponents.EMPTY);
            Stream.of(decoratedpotblockentity_decoration.front(), decoratedpotblockentity_decoration.left(), decoratedpotblockentity_decoration.right(), decoratedpotblockentity_decoration.back()).forEach((item) -> {
                tooltip.add((new ItemStack(item, 1)).getHoverName().plainCopy().withStyle(ChatFormatting.GRAY));
            });
        }
    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockposition = hit.getBlockPos();

        if (!world.isClientSide && projectile.mayInteract(world, blockposition) && projectile.mayBreak(world)) {
            // CraftBukkit start - call EntityChangeBlockEvent
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(projectile, blockposition, this.getFluidState(state).createLegacyBlock())) {
                return;
            }
            // CraftBukkit end
            world.setBlock(blockposition, (BlockState) state.setValue(DecoratedPotBlock.CRACKED, true), 4);
            world.destroyBlock(blockposition, true, projectile);
        }

    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof DecoratedPotBlockEntity) {
            DecoratedPotBlockEntity decoratedpotblockentity = (DecoratedPotBlockEntity) tileentity;

            return decoratedpotblockentity.getPotAsItem();
        } else {
            return super.getCloneItemStack(world, pos, state);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(world.getBlockEntity(pos));
    }
}
