package net.minecraft.world.item.enchantment;

import net.minecraft.world.entity.EquipmentSlot;

public class LootBonusEnchantment extends Enchantment {
    protected LootBonusEnchantment(Enchantment.Rarity rarity, EnchantmentCategory target, EquipmentSlot... slotTypes) {
        super(rarity, target, slotTypes);
    }

    // Purpur start
    @Override
    public boolean canEnchant(net.minecraft.world.item.ItemStack stack) {
        // we have to cheat the system because this class is loaded before purpur's config is loaded
        return (org.purpurmc.purpur.PurpurConfig.allowShearsLooting && this.category == EnchantmentCategory.WEAPON ? EnchantmentCategory.WEAPON_AND_SHEARS : this.category).canEnchant(stack.getItem());
    }
    // Purpur end

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 9;
    }

    @Override
    public int getMaxCost(int level) {
        return super.getMinCost(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other) && other != Enchantments.SILK_TOUCH;
    }
}
