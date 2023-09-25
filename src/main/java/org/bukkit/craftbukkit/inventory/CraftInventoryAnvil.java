package org.bukkit.craftbukkit.inventory;

import com.google.common.base.Preconditions;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import org.bukkit.Location;
import org.bukkit.inventory.AnvilInventory;

public class CraftInventoryAnvil extends CraftResultInventory implements AnvilInventory {

    private final Location location;
    public final AnvilMenu container; // Purpur - private -> public

    public CraftInventoryAnvil(Location location, Container inventory, Container resultInventory, AnvilMenu container) {
        super(inventory, resultInventory);
        this.location = location;
        this.container = container;
    }

    @Override
    public Location getLocation() {
        return this.location;
    }

    @Override
    public String getRenameText() {
        return this.container.itemName;
    }

    @Override
    public int getRepairCostAmount() {
        return this.container.repairItemCountCost;
    }

    @Override
    public void setRepairCostAmount(int amount) {
        this.container.repairItemCountCost = amount;
    }

    @Override
    public int getRepairCost() {
        return this.container.cost.get();
    }

    @Override
    public void setRepairCost(int i) {
        this.container.cost.set(i);
    }

    @Override
    public int getMaximumRepairCost() {
        return this.container.maximumRepairCost;
    }

    @Override
    public void setMaximumRepairCost(int levels) {
        Preconditions.checkArgument(levels >= 0, "Maximum repair cost must be positive (or 0)");
        this.container.maximumRepairCost = levels;
    }

    // Purpur start
    @Override
    public boolean canBypassCost() {
        return container.bypassCost;
    }

    @Override
    public void setBypassCost(boolean bypassCost) {
        container.bypassCost = bypassCost;
    }

    @Override
    public boolean canDoUnsafeEnchants() {
        return container.canDoUnsafeEnchants;
    }

    @Override
    public void setDoUnsafeEnchants(boolean canDoUnsafeEnchants) {
        container.canDoUnsafeEnchants = canDoUnsafeEnchants;
    }
    // Purpur end
}
