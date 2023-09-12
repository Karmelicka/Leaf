package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class ExplosionDamageCalculator {
    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState) {
        return blockState.isAir() && fluidState.isEmpty() ? Optional.empty() : Optional.of(Math.max(blockState.getBlock().getExplosionResistance(), fluidState.getExplosionResistance()));
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter world, BlockPos pos, BlockState state, float power) {
        return true;
    }

    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return true;
    }

    @io.papermc.paper.annotation.DoNotUse @Deprecated // Paper
    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
        // Paper start - actually optimise explosions
        return this.getEntityDamageAmount(explosion, entity, Explosion.getSeenPercent(explosion.center(), entity));
    }
    public float getEntityDamageAmount(Explosion explosion, Entity entity, double seenPercent) {
        // Paper end - actually optimise explosions
        float f = explosion.radius() * 2.0F;
        Vec3 vec3 = explosion.center();
        double d = Math.sqrt(entity.distanceToSqr(vec3)) / (double)f;
        double e = (1.0D - d) * seenPercent; // Paper - actually optimise explosions
        return (float)((e * e + e) / 2.0D * 7.0D * (double)f + 1.0D);
    }
}
