package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.potion.CraftPotionEffectType;
import org.bukkit.craftbukkit.potion.CraftPotionUtil;
import org.bukkit.entity.MushroomCow;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CraftMushroomCow extends CraftCow implements MushroomCow {
    public CraftMushroomCow(CraftServer server, net.minecraft.world.entity.animal.MushroomCow entity) {
        super(server, entity);
    }

    @Override
    public boolean hasEffectsForNextStew() {
        return this.getHandle().stewEffects != null && !this.getHandle().stewEffects.isEmpty();
    }

    @Override
    public List<PotionEffect> getEffectsForNextStew() {
        if (this.hasEffectsForNextStew()) {
            return this.getHandle().stewEffects.stream().map(recordSuspiciousEffect -> CraftPotionUtil.toBukkit(recordSuspiciousEffect.createEffectInstance())).toList();
        }
        return ImmutableList.of();
    }

    @Override
    public boolean addEffectToNextStew(PotionEffect potionEffect, boolean overwrite) {
        Preconditions.checkArgument(potionEffect != null, "PotionEffect cannot be null");
        MobEffectInstance minecraftPotionEffect = CraftPotionUtil.fromBukkit(potionEffect);
        if (!overwrite && this.hasEffectForNextStew(potionEffect.getType())) {
            return false;
        }
        if (this.getHandle().stewEffects == null) {
            this.getHandle().stewEffects = new ArrayList<>();
        }
        SuspiciousEffectHolder.EffectEntry recordSuspiciousEffect = new SuspiciousEffectHolder.EffectEntry(minecraftPotionEffect.getEffect(), minecraftPotionEffect.getDuration());
        this.removeEffectFromNextStew(potionEffect.getType()); // Avoid duplicates of effects
        return this.getHandle().stewEffects.add(recordSuspiciousEffect);
    }

    @Override
    public boolean removeEffectFromNextStew(PotionEffectType potionEffectType) {
        Preconditions.checkArgument(potionEffectType != null, "potionEffectType cannot be null");
        if (!this.hasEffectsForNextStew()) {
            return false;
        }
        MobEffect minecraftPotionEffectType = CraftPotionEffectType.bukkitToMinecraft(potionEffectType);
        return this.getHandle().stewEffects.removeIf(recordSuspiciousEffect -> recordSuspiciousEffect.effect().equals(minecraftPotionEffectType));
    }

    @Override
    public boolean hasEffectForNextStew(PotionEffectType potionEffectType) {
        Preconditions.checkArgument(potionEffectType != null, "potionEffectType cannot be null");
        if (!this.hasEffectsForNextStew()) {
            return false;
        }
        MobEffect minecraftPotionEffectType = CraftPotionEffectType.bukkitToMinecraft(potionEffectType);
        return this.getHandle().stewEffects.stream().anyMatch(recordSuspiciousEffect -> recordSuspiciousEffect.effect().equals(minecraftPotionEffectType));
    }

    @Override
    public void clearEffectsForNextStew() {
        this.getHandle().stewEffects = null;
    }

    @Override
    public net.minecraft.world.entity.animal.MushroomCow getHandle() {
        return (net.minecraft.world.entity.animal.MushroomCow) this.entity;
    }

    @Override
    public Variant getVariant() {
        return Variant.values()[this.getHandle().getVariant().ordinal()];
    }

    @Override
    public void setVariant(Variant variant) {
        Preconditions.checkArgument(variant != null, "Variant cannot be null");

        this.getHandle().setVariant(net.minecraft.world.entity.animal.MushroomCow.MushroomType.values()[variant.ordinal()]);
    }

    @Override
    public String toString() {
        return "CraftMushroomCow";
    }
}
