package net.minecraft.world.inventory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface ContainerLevelAccess {

    // CraftBukkit start
    default Level getWorld() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    default BlockPos getPosition() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    default org.bukkit.Location getLocation() {
        return new org.bukkit.Location(this.getWorld().getWorld(), this.getPosition().getX(), this.getPosition().getY(), this.getPosition().getZ());
    }
    // CraftBukkit end

    ContainerLevelAccess NULL = new ContainerLevelAccess() {
        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> getter) {
            return Optional.empty();
        }
    };

    static ContainerLevelAccess create(final Level world, final BlockPos pos) {
        return new ContainerLevelAccess() {
            // CraftBukkit start
            @Override
            public Level getWorld() {
                return world;
            }

            @Override
            public BlockPos getPosition() {
                return pos;
            }
            // CraftBukkit end

            @Override
            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> getter) {
                return Optional.of(getter.apply(world, pos));
            }
        };
    }

    <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> getter);

    default <T> T evaluate(BiFunction<Level, BlockPos, T> getter, T defaultValue) {
        return this.evaluate(getter).orElse(defaultValue);
    }

    default void execute(BiConsumer<Level, BlockPos> function) {
        this.evaluate((world, blockposition) -> {
            function.accept(world, blockposition);
            return Optional.empty();
        });
    }
}
