package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class WorldGenerationContext {
    private final int minY;
    private final int height;
    private final @javax.annotation.Nullable net.minecraft.world.level.Level level; // Paper - Flat bedrock generator settings

    public WorldGenerationContext(ChunkGenerator generator, LevelHeightAccessor world) { this(generator, world, null); } // Paper - Flat bedrock generator settings
    public WorldGenerationContext(ChunkGenerator generator, LevelHeightAccessor world, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level) { // Paper - Flat bedrock generator settings
        this.minY = Math.max(world.getMinBuildHeight(), generator.getMinY());
        this.height = Math.min(world.getHeight(), generator.getGenDepth());
        this.level = level; // Paper - Flat bedrock generator settings
    }

    public int getMinGenY() {
        return this.minY;
    }

    public int getGenDepth() {
        return this.height;
    }

    // Paper start - Flat bedrock generator settings
    public net.minecraft.world.level.Level getWorld() {
        if (this.level == null) {
            throw new NullPointerException("WorldGenerationContext was initialized without a Level, but WorldGenerationContext#getWorld was called");
        }
        return this.level;
    }
    // Paper end - Flat bedrock generator settings
}
