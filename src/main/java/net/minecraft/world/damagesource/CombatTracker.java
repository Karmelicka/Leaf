package net.minecraft.world.damagesource;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class CombatTracker {
    public static final int RESET_DAMAGE_STATUS_TIME = 100;
    public static final int RESET_COMBAT_STATUS_TIME = 300;
    private static final Style INTENTIONAL_GAME_DESIGN_STYLE = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://bugs.mojang.com/browse/MCPE-28723")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("MCPE-28723")));
    private final List<CombatEntry> entries = Lists.newArrayList();
    private final LivingEntity mob;
    private int lastDamageTime;
    private int combatStartTime;
    private int combatEndTime;
    private boolean inCombat;
    private boolean takingDamage;

    public CombatTracker(LivingEntity entity) {
        this.mob = entity;
    }

    public void recordDamage(DamageSource damageSource, float damage) {
        this.recheckStatus();
        FallLocation fallLocation = FallLocation.getCurrentFallLocation(this.mob);
        CombatEntry combatEntry = new CombatEntry(damageSource, damage, fallLocation, this.mob.fallDistance);
        this.entries.add(combatEntry);
        this.lastDamageTime = this.mob.tickCount;
        this.takingDamage = true;
        if (!this.inCombat && this.mob.isAlive() && shouldEnterCombat(damageSource)) {
            this.inCombat = true;
            this.combatStartTime = this.mob.tickCount;
            this.combatEndTime = this.combatStartTime;
            this.mob.onEnterCombat();
        }

    }

    private static boolean shouldEnterCombat(DamageSource damageSource) {
        return damageSource.getEntity() instanceof LivingEntity;
    }

    private Component getMessageForAssistedFall(Entity attacker, Component attackerDisplayName, String itemDeathTranslationKey, String deathTranslationKey) {
        ItemStack var10000;
        if (attacker instanceof LivingEntity livingEntity) {
            var10000 = livingEntity.getMainHandItem();
        } else {
            var10000 = ItemStack.EMPTY;
        }

        ItemStack itemStack = var10000;
        return !itemStack.isEmpty() && (org.purpurmc.purpur.PurpurConfig.playerDeathsAlwaysShowItem || itemStack.hasCustomHoverName()) ? Component.translatable(itemDeathTranslationKey, this.mob.getDisplayName(), attackerDisplayName, itemStack.getDisplayName()) : Component.translatable(deathTranslationKey, this.mob.getDisplayName(), attackerDisplayName);
    }

    private Component getFallMessage(CombatEntry damageRecord, @Nullable Entity attacker) {
        DamageSource damageSource = damageRecord.source();
        if (!damageSource.is(DamageTypeTags.IS_FALL) && !damageSource.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL)) {
            Component component = getDisplayName(attacker);
            Entity entity = damageSource.getEntity();
            Component component2 = getDisplayName(entity);
            if (component2 != null && !component2.equals(component)) {
                return this.getMessageForAssistedFall(entity, component2, "death.fell.assist.item", "death.fell.assist");
            } else {
                return (Component)(component != null ? this.getMessageForAssistedFall(attacker, component, "death.fell.finish.item", "death.fell.finish") : Component.translatable("death.fell.killer", this.mob.getDisplayName()));
            }
        } else {
            FallLocation fallLocation = Objects.requireNonNullElse(damageRecord.fallLocation(), FallLocation.GENERIC);
            return Component.translatable(fallLocation.languageKey(), this.mob.getDisplayName());
        }
    }

    @Nullable
    private static Component getDisplayName(@Nullable Entity entity) {
        return entity == null ? null : entity.getDisplayName();
    }

    public Component getDeathMessage() {
        if (this.entries.isEmpty()) {
            return Component.translatable("death.attack.generic", this.mob.getDisplayName());
        } else {
            CombatEntry combatEntry = this.entries.get(this.entries.size() - 1);
            DamageSource damageSource = combatEntry.source();
            CombatEntry combatEntry2 = this.getMostSignificantFall();
            DeathMessageType deathMessageType = damageSource.type().deathMessageType();
            if (deathMessageType == DeathMessageType.FALL_VARIANTS && combatEntry2 != null) {
                return this.getFallMessage(combatEntry2, damageSource.getEntity());
            } else if (deathMessageType == DeathMessageType.INTENTIONAL_GAME_DESIGN) {
                String string = "death.attack." + damageSource.getMsgId();
                Component component = ComponentUtils.wrapInSquareBrackets(Component.translatable(string + ".link")).withStyle(INTENTIONAL_GAME_DESIGN_STYLE);
                return Component.translatable(string + ".message", this.mob.getDisplayName(), component);
            } else {
                // Purpur start
                if (damageSource.isScissors()) {
                    return damageSource.getLocalizedDeathMessage(org.purpurmc.purpur.PurpurConfig.deathMsgRunWithScissors, this.mob);
                } else if (damageSource.isStonecutter()) {
                    return damageSource.getLocalizedDeathMessage(org.purpurmc.purpur.PurpurConfig.deathMsgStonecutter, this.mob);
                }
                // Purpur end
                return damageSource.getLocalizedDeathMessage(this.mob);
            }
        }
    }

    @Nullable
    private CombatEntry getMostSignificantFall() {
        CombatEntry combatEntry = null;
        CombatEntry combatEntry2 = null;
        float f = 0.0F;
        float g = 0.0F;

        for(int i = 0; i < this.entries.size(); ++i) {
            CombatEntry combatEntry3 = this.entries.get(i);
            CombatEntry combatEntry4 = i > 0 ? this.entries.get(i - 1) : null;
            DamageSource damageSource = combatEntry3.source();
            boolean bl = damageSource.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL);
            float h = bl ? Float.MAX_VALUE : combatEntry3.fallDistance();
            if ((damageSource.is(DamageTypeTags.IS_FALL) || bl) && h > 0.0F && (combatEntry == null || h > g)) {
                if (i > 0) {
                    combatEntry = combatEntry4;
                } else {
                    combatEntry = combatEntry3;
                }

                g = h;
            }

            if (combatEntry3.fallLocation() != null && (combatEntry2 == null || combatEntry3.damage() > f)) {
                combatEntry2 = combatEntry3;
                f = combatEntry3.damage();
            }
        }

        if (g > 5.0F && combatEntry != null) {
            return combatEntry;
        } else {
            return f > 5.0F && combatEntry2 != null ? combatEntry2 : null;
        }
    }

    public int getCombatDuration() {
        return this.inCombat ? this.mob.tickCount - this.combatStartTime : this.combatEndTime - this.combatStartTime;
    }

    public void recheckStatus() {
        int i = this.inCombat ? 300 : 100;
        if (this.takingDamage && (!this.mob.isAlive() || this.mob.tickCount - this.lastDamageTime > i)) {
            boolean bl = this.inCombat;
            this.takingDamage = false;
            this.inCombat = false;
            this.combatEndTime = this.mob.tickCount;
            if (bl) {
                this.mob.onLeaveCombat();
            }

            this.entries.clear();
        }

    }
}
