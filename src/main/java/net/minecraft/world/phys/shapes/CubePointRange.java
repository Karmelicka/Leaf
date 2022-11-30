package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;

public class CubePointRange extends AbstractDoubleList {
    private final int parts;
    private final double scale; // Gale - Lithium - replace division by multiplication in CubePointRange

    CubePointRange(int sectionCount) {
        if (sectionCount <= 0) {
            throw new IllegalArgumentException("Need at least 1 part");
        } else {
            this.parts = sectionCount;
        }
        this.scale = 1.0D / sectionCount; // Gale - Lithium - replace division by multiplication in CubePointRange
    }

    public double getDouble(int i) {
        return i * this.scale; // Gale - Lithium - replace division by multiplication in CubePointRange
    }

    public int size() {
        return this.parts + 1;
    }
}
