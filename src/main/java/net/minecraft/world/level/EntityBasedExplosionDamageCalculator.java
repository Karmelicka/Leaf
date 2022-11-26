package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class EntityBasedExplosionDamageCalculator extends ExplosionDamageCalculator {
    private final Entity source;

    public EntityBasedExplosionDamageCalculator(Entity entity) {
        this.source = entity;
    }

    @Override
    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState) {
        // Gale start - Lithium - reduce lambda and Optional allocation in EntityBasedExplosionDamageCalculator
        Optional<Float> optionalBlastResistance = super.getBlockExplosionResistance(explosion, world, pos, blockState, fluidState);
        if (optionalBlastResistance.isPresent()) {
            float blastResistance = optionalBlastResistance.get();
            float effectiveExplosionResistance = this.source.getBlockExplosionResistance(explosion, world, pos, blockState, fluidState, blastResistance);
            if (effectiveExplosionResistance != blastResistance) {
                return Optional.of(effectiveExplosionResistance);
            }
        }
        return optionalBlastResistance;
        // Gale end - Lithium - reduce lambda and Optional allocation in EntityBasedExplosionDamageCalculator
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter world, BlockPos pos, BlockState state, float power) {
        return this.source.shouldBlockExplode(explosion, world, pos, state, power);
    }
}
