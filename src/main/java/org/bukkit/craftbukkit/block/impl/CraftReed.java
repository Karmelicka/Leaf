/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftReed extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.Ageable {

    public CraftReed() {
        super();
    }

    public CraftReed(net.minecraft.world.level.block.state.BlockState state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.CraftAgeable

    private static final net.minecraft.world.level.block.state.properties.IntegerProperty AGE = getInteger(net.minecraft.world.level.block.SugarCaneBlock.class, "age");

    @Override
    public int getAge() {
        return this.get(CraftReed.AGE);
    }

    @Override
    public void setAge(int age) {
        this.set(CraftReed.AGE, age);
    }

    @Override
    public int getMaximumAge() {
        return getMax(CraftReed.AGE);
    }
}
