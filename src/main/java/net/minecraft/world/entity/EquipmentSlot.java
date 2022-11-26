package net.minecraft.world.entity;

import net.minecraft.util.StringRepresentable;

public enum EquipmentSlot implements StringRepresentable {
    MAINHAND(EquipmentSlot.Type.HAND, 0, 0, "mainhand"),
    OFFHAND(EquipmentSlot.Type.HAND, 1, 5, "offhand"),
    FEET(EquipmentSlot.Type.ARMOR, 0, 1, "feet"),
    LEGS(EquipmentSlot.Type.ARMOR, 1, 2, "legs"),
    CHEST(EquipmentSlot.Type.ARMOR, 2, 3, "chest"),
    HEAD(EquipmentSlot.Type.ARMOR, 3, 4, "head");

    public static final StringRepresentable.EnumCodec<EquipmentSlot> CODEC = StringRepresentable.fromEnum(EquipmentSlot::values);
    private final EquipmentSlot.Type type;
    private final int index;
    private final int filterFlag;
    private final String name;
    public static final EquipmentSlot[] VALUES = EquipmentSlot.values(); // Gale - JettPack - reduce array allocations

    private EquipmentSlot(EquipmentSlot.Type type, int entityId, int armorStandId, String name) {
        this.type = type;
        this.index = entityId;
        this.filterFlag = armorStandId;
        this.name = name;
    }

    public EquipmentSlot.Type getType() {
        return this.type;
    }

    public int getIndex() {
        return this.index;
    }

    public int getIndex(int offset) {
        return offset + this.index;
    }

    public int getFilterFlag() {
        return this.filterFlag;
    }

    public String getName() {
        return this.name;
    }

    public boolean isArmor() {
        return this.type == EquipmentSlot.Type.ARMOR;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static EquipmentSlot byName(String name) {
        EquipmentSlot equipmentSlot = CODEC.byName(name);
        if (equipmentSlot != null) {
            return equipmentSlot;
        } else {
            throw new IllegalArgumentException("Invalid slot '" + name + "'");
        }
    }

    public static EquipmentSlot byTypeAndIndex(EquipmentSlot.Type type, int index) {
        for(EquipmentSlot equipmentSlot : values()) {
            if (equipmentSlot.getType() == type && equipmentSlot.getIndex() == index) {
                return equipmentSlot;
            }
        }

        throw new IllegalArgumentException("Invalid slot '" + type + "': " + index);
    }

    public static enum Type {
        HAND,
        ARMOR;
    }
}
