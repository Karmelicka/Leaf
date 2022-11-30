package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CubeVoxelShape extends VoxelShape {

    private @NotNull DoubleList @Nullable [] list; // Gale - Lithium - cache CubeVoxelShape shape array

    protected CubeVoxelShape(DiscreteVoxelShape voxels) {
        super(voxels);
        this.initCache(); // Paper - optimise collisions
    }

    @Override
    protected DoubleList getCoords(Direction.Axis axis) {
        // Gale start - Lithium - cache CubeVoxelShape shape array
        if (this.list == null) {
            this.list = new DoubleList[Direction.Axis.VALUES.length];
            for (Direction.Axis existingAxis : Direction.Axis.VALUES) {
                this.list[existingAxis.ordinal()] = new CubePointRange(this.shape.getSize(axis));
            }
        }
        return this.list[axis.ordinal()];
        // Gale end - Lithium - cache CubeVoxelShape shape array
    }

    @Override
    protected int findIndex(Direction.Axis axis, double coord) {
        int i = this.shape.getSize(axis);
        return Mth.floor(Mth.clamp(coord * (double)i, -1.0D, (double)i));
    }
}
