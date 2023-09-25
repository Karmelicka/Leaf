package net.minecraft.world.item.enchantment;

import net.minecraft.world.entity.EquipmentSlot;

public class ArrowInfiniteEnchantment extends Enchantment {
    public ArrowInfiniteEnchantment(Enchantment.Rarity weight, EquipmentSlot... slotTypes) {
        super(weight, EnchantmentCategory.BOW, slotTypes);
    }

    // Purpur start
    @Override
    public boolean canEnchant(net.minecraft.world.item.ItemStack stack) {
        // we have to cheat the system because this class is loaded before purpur's config is loaded
        return (org.purpurmc.purpur.PurpurConfig.allowCrossbowInfinity ? EnchantmentCategory.BOW_AND_CROSSBOW : EnchantmentCategory.BOW).canEnchant(stack.getItem());
    }
    // Purpur end

    @Override
    public int getMinCost(int level) {
        return 20;
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        return other instanceof MendingEnchantment ? org.purpurmc.purpur.PurpurConfig.allowInfinityMending : super.checkCompatibility(other);
    }
}
