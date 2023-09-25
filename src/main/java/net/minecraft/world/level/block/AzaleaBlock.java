package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AzaleaBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<AzaleaBlock> CODEC = simpleCodec(AzaleaBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0.0D, 8.0D, 0.0D, 16.0D, 16.0D, 16.0D), Block.box(6.0D, 0.0D, 6.0D, 10.0D, 8.0D, 10.0D));

    @Override
    public MapCodec<AzaleaBlock> codec() {
        return CODEC;
    }

    protected AzaleaBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return floor.is(Blocks.CLAY) || super.mayPlaceOn(floor, world, pos);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        return world.getFluidState(pos.above()).isEmpty();
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return (double)world.random.nextFloat() < 0.45D;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        // Purpur start
        growTree(world, random, pos, state);
    }

    @Override
    public void randomTick(net.minecraft.world.level.block.state.BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        double chance = state.getBlock() == Blocks.FLOWERING_AZALEA ? world.purpurConfig.floweringAzaleaGrowthChance : world.purpurConfig.azaleaGrowthChance;
        if (chance > 0.0D && world.getMaxLocalRawBrightness(pos.above()) > 9 && random.nextDouble() < chance) {
            growTree(world, random, pos, state);
        }
    }

    private void growTree(ServerLevel world, RandomSource random, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        // Purpur end
        TreeGrower.AZALEA.growTree(world, world.getChunkSource().getGenerator(), pos, state, random);
    }

    // Paper start - Fix MC-224454
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
    // Paper end
}
