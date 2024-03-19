package net.minecraft.world.entity.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

public class Giant extends Monster {
    public Giant(EntityType<? extends Giant> type, Level world) {
        super(type, world);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 10.440001F;
    }

    @Override
    protected float ridingOffset(Entity vehicle) {
        return -3.75F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 100.0D).add(Attributes.MOVEMENT_SPEED, 0.5D).add(Attributes.ATTACK_DAMAGE, 50.0D);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return world.getPathfindingCostFromLightLevels(pos);
    }
}
