package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.block.CapturedBlockState;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public class SaplingBlock extends BushBlock implements BonemealableBlock {

    public static final MapCodec<SaplingBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(TreeGrower.CODEC.fieldOf("tree").forGetter((blocksapling) -> {
            return blocksapling.treeGrower;
        }), propertiesCodec()).apply(instance, SaplingBlock::new);
    });
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    protected static final float AABB_OFFSET = 6.0F;
    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);
    protected final TreeGrower treeGrower;
    public static TreeType treeType; // CraftBukkit

    @Override
    public MapCodec<? extends SaplingBlock> codec() {
        return SaplingBlock.CODEC;
    }

    protected SaplingBlock(TreeGrower generator, BlockBehaviour.Properties settings) {
        super(settings);
        this.treeGrower = generator;
        this.registerDefaultState((net.minecraft.world.level.block.state.BlockState) ((net.minecraft.world.level.block.state.BlockState) this.stateDefinition.any()).setValue(SaplingBlock.STAGE, 0));
    }

    @Override
    public VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SaplingBlock.SHAPE;
    }

    @Override
    public void randomTick(net.minecraft.world.level.block.state.BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextFloat() < (world.spigotConfig.saplingModifier / (100.0f * 7))) { // Spigot - SPIGOT-7159: Better modifier resolution
            this.advanceTree(world, pos, state, random);
        }

    }

    public void advanceTree(ServerLevel world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, RandomSource random) {
        if ((Integer) state.getValue(SaplingBlock.STAGE) == 0) {
            world.setBlock(pos, (net.minecraft.world.level.block.state.BlockState) state.cycle(SaplingBlock.STAGE), 4);
        } else {
            // CraftBukkit start
            if (world.captureTreeGeneration) {
                this.treeGrower.growTree(world, world.getChunkSource().getGenerator(), pos, state, random);
            } else {
                world.captureTreeGeneration = true;
                this.treeGrower.growTree(world, world.getChunkSource().getGenerator(), pos, state, random);
                world.captureTreeGeneration = false;
                if (world.capturedBlockStates.size() > 0) {
                    TreeType treeType = SaplingBlock.treeType;
                    SaplingBlock.treeType = null;
                    Location location = CraftLocation.toBukkit(pos, world.getWorld());
                    java.util.List<BlockState> blocks = new java.util.ArrayList<>(world.capturedBlockStates.values());
                    world.capturedBlockStates.clear();
                    StructureGrowEvent event = null;
                    if (treeType != null) {
                        event = new StructureGrowEvent(location, treeType, false, null, blocks);
                        org.bukkit.Bukkit.getPluginManager().callEvent(event);
                    }
                    if (event == null || !event.isCancelled()) {
                        for (BlockState blockstate : blocks) {
                            CapturedBlockState.setBlockState(blockstate);
                        }
                    }
                }
            }
            // CraftBukkit end
        }

    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return (double) world.random.nextFloat() < 0.45D;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.advanceTree(world, pos, state, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {
        builder.add(SaplingBlock.STAGE);
    }
}
