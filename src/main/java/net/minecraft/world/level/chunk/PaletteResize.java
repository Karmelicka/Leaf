package net.minecraft.world.level.chunk;

public interface PaletteResize<T> {  // Gale - Lithium - faster chunk serialization - package -> public
    int onResize(int newBits, T object);
}
