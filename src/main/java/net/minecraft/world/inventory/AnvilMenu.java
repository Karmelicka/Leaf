package net.minecraft.world.inventory;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

// Purpur start
import net.minecraft.nbt.IntTag;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
// Purpur end

public class AnvilMenu extends ItemCombinerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_COST = false;
    public static final int MAX_NAME_LENGTH = 50;
    public int repairItemCountCost;
    @Nullable
    public String itemName;
    public final DataSlot cost;
    private static final int COST_FAIL = 0;
    private static final int COST_BASE = 1;
    private static final int COST_ADDED_BASE = 1;
    private static final int COST_REPAIR_MATERIAL = 1;
    private static final int COST_REPAIR_SACRIFICE = 2;
    private static final int COST_INCOMPATIBLE_PENALTY = 1;
    private static final int COST_RENAME = 1;
    private static final int INPUT_SLOT_X_PLACEMENT = 27;
    private static final int ADDITIONAL_SLOT_X_PLACEMENT = 76;
    private static final int RESULT_SLOT_X_PLACEMENT = 134;
    private static final int SLOT_Y_PLACEMENT = 47;
    // CraftBukkit start
    public static final int DEFAULT_DENIED_COST = -1;
    public int maximumRepairCost = 40;
    private CraftInventoryView bukkitEntity;
    // CraftBukkit end
    public boolean bypassCost = false; // Purpur
    public boolean canDoUnsafeEnchants = false; // Purpur

    public AnvilMenu(int syncId, Inventory inventory) {
        this(syncId, inventory, ContainerLevelAccess.NULL);
    }

    public AnvilMenu(int syncId, Inventory inventory, ContainerLevelAccess context) {
        super(MenuType.ANVIL, syncId, inventory, context);
        this.cost = DataSlot.standalone();
        this.addDataSlot(this.cost);
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create().withSlot(0, 27, 47, (itemstack) -> {
            return true;
        }).withSlot(1, 76, 47, (itemstack) -> {
            return true;
        }).withResultSlot(2, 134, 47).build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(BlockTags.ANVIL);
    }

    @Override
    protected boolean mayPickup(Player player, boolean present) {
        return (player.getAbilities().instabuild || player.experienceLevel >= this.cost.get()) && (bypassCost || this.cost.get() > AnvilMenu.DEFAULT_DENIED_COST) && present; // CraftBukkit - allow cost 0 like a free item // Purpur
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        ItemStack itemstack = activeQuickItem == null ? stack : activeQuickItem; // Purpur
        if (org.purpurmc.purpur.event.inventory.AnvilTakeResultEvent.getHandlerList().getRegisteredListeners().length > 0) new org.purpurmc.purpur.event.inventory.AnvilTakeResultEvent(player.getBukkitEntity(), getBukkitView(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack)).callEvent(); // Purpur
        if (!player.getAbilities().instabuild) {
            if (bypassCost) ((ServerPlayer) player).lastSentExp = -1; else // Purpur
            player.giveExperienceLevels(-this.cost.get());
        }

        this.inputSlots.setItem(0, ItemStack.EMPTY);
        if (this.repairItemCountCost > 0) {
            ItemStack itemstack1 = this.inputSlots.getItem(1);

            if (!itemstack1.isEmpty() && itemstack1.getCount() > this.repairItemCountCost) {
                itemstack1.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, itemstack1);
            } else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        } else {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        this.access.execute((world, blockposition) -> {
            BlockState iblockdata = world.getBlockState(blockposition);

            if (!player.getAbilities().instabuild && iblockdata.is(BlockTags.ANVIL) && player.getRandom().nextFloat() < 0.12F) {
                BlockState iblockdata1 = AnvilBlock.damage(iblockdata);

                // Paper start - AnvilDamageEvent
                com.destroystokyo.paper.event.block.AnvilDamagedEvent event = new com.destroystokyo.paper.event.block.AnvilDamagedEvent(getBukkitView(), iblockdata1 != null ? org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(iblockdata1) : null);
                if (!event.callEvent()) {
                    return;
                } else if (event.getDamageState() == com.destroystokyo.paper.event.block.AnvilDamagedEvent.DamageState.BROKEN) {
                    iblockdata1 = null;
                } else {
                    iblockdata1 = ((org.bukkit.craftbukkit.block.data.CraftBlockData) event.getDamageState().getMaterial().createBlockData()).getState().setValue(AnvilBlock.FACING, iblockdata.getValue(AnvilBlock.FACING));
                }
                // Paper end - AnvilDamageEvent
                if (iblockdata1 == null) {
                    world.removeBlock(blockposition, false);
                    world.levelEvent(1029, blockposition, 0);
                } else {
                    world.setBlock(blockposition, iblockdata1, 2);
                    world.levelEvent(1030, blockposition, 0);
                }
            } else {
                world.levelEvent(1030, blockposition, 0);
            }

        });
    }

    @Override
    public void createResult() {
        // Purpur start
        bypassCost = false;
        canDoUnsafeEnchants = false;
        if (org.purpurmc.purpur.event.inventory.AnvilUpdateResultEvent.getHandlerList().getRegisteredListeners().length > 0) new org.purpurmc.purpur.event.inventory.AnvilUpdateResultEvent(getBukkitView()).callEvent();
        // Purpur end

        ItemStack itemstack = this.inputSlots.getItem(0);

        this.cost.set(1);
        int i = 0;
        byte b0 = 0;
        byte b1 = 0;

        if (itemstack.isEmpty()) {
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
            this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        } else {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.inputSlots.getItem(1);
            Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(itemstack1);
            int j = b0 + itemstack.getBaseRepairCost() + (itemstack2.isEmpty() ? 0 : itemstack2.getBaseRepairCost());

            this.repairItemCountCost = 0;
            if (!itemstack2.isEmpty()) {
                boolean flag = itemstack2.is(Items.ENCHANTED_BOOK) && !EnchantedBookItem.getEnchantments(itemstack2).isEmpty();
                int k;
                int l;
                int i1;

                if (itemstack1.isDamageableItem() && itemstack1.getItem().isValidRepairItem(itemstack, itemstack2)) {
                    k = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    if (k <= 0) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    for (i1 = 0; k > 0 && i1 < itemstack2.getCount(); ++i1) {
                        l = itemstack1.getDamageValue() - k;
                        itemstack1.setDamageValue(l);
                        ++i;
                        k = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = i1;
                } else {
                    if (!flag && (!itemstack1.is(itemstack2.getItem()) || !itemstack1.isDamageableItem())) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    if (itemstack1.isDamageableItem() && !flag) {
                        k = itemstack.getMaxDamage() - itemstack.getDamageValue();
                        i1 = itemstack2.getMaxDamage() - itemstack2.getDamageValue();
                        l = i1 + itemstack1.getMaxDamage() * 12 / 100;
                        int j1 = k + l;
                        int k1 = itemstack1.getMaxDamage() - j1;

                        if (k1 < 0) {
                            k1 = 0;
                        }

                        if (k1 < itemstack1.getDamageValue()) {
                            itemstack1.setDamageValue(k1);
                            i += 2;
                        }
                    }

                    Map<Enchantment, Integer> map1 = EnchantmentHelper.getEnchantments(itemstack2);
                    boolean flag1 = false;
                    boolean flag2 = false;
                    Iterator iterator = map1.keySet().iterator();

                    while (iterator.hasNext()) {
                        Enchantment enchantment = (Enchantment) iterator.next();

                        if (enchantment != null) {
                            int l1 = (Integer) map.getOrDefault(enchantment, 0);
                            int i2 = (Integer) map1.get(enchantment);

                            i2 = l1 == i2 ? i2 + 1 : Math.max(i2, l1);
                            boolean flag3 = canDoUnsafeEnchants || (org.purpurmc.purpur.PurpurConfig.allowUnsafeEnchants && org.purpurmc.purpur.PurpurConfig.allowInapplicableEnchants) || enchantment.canEnchant(itemstack); // Purpur
                            boolean flag4 = true; // Purpur

                            if (this.player.getAbilities().instabuild || itemstack.is(Items.ENCHANTED_BOOK)) {
                                flag3 = true;
                            }

                            Iterator iterator1 = map.keySet().iterator();

                            while (iterator1.hasNext()) {
                                Enchantment enchantment1 = (Enchantment) iterator1.next();

                                if (enchantment1 != enchantment && !enchantment.isCompatibleWith(enchantment1)) {
                                    flag4 = canDoUnsafeEnchants || (org.purpurmc.purpur.PurpurConfig.allowUnsafeEnchants && org.purpurmc.purpur.PurpurConfig.allowIncompatibleEnchants); // Purpur flag3 -> flag4
                                    if (!flag4 && org.purpurmc.purpur.PurpurConfig.replaceIncompatibleEnchants) {
                                        iterator1.remove();
                                        flag4 = true;
                                    }
                                    ++i;
                                }
                            }

                            if (!flag3 || !flag4) { // Purpur
                                flag2 = true;
                            } else {
                                flag1 = true;
                                if ((!org.purpurmc.purpur.PurpurConfig.allowUnsafeEnchants || !org.purpurmc.purpur.PurpurConfig.allowHigherEnchantsLevels) && i2 > enchantment.getMaxLevel()) { // Purpur
                                    i2 = enchantment.getMaxLevel();
                                }

                                map.put(enchantment, i2);
                                int j2 = 0;

                                switch (enchantment.getRarity()) {
                                    case COMMON:
                                        j2 = 1;
                                        break;
                                    case UNCOMMON:
                                        j2 = 2;
                                        break;
                                    case RARE:
                                        j2 = 4;
                                        break;
                                    case VERY_RARE:
                                        j2 = 8;
                                }

                                if (flag) {
                                    j2 = Math.max(1, j2 / 2);
                                }

                                i += j2 * i2;
                                if (itemstack.getCount() > 1) {
                                    i = 40;
                                }
                            }
                        }
                    }

                    if (flag2 && !flag1) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }
                }
            }

            if (this.itemName != null && !Util.isBlank(this.itemName)) {
                if (!this.itemName.equals(itemstack.getHoverName().getString())) {
                    b1 = 1;
                    i += b1;
                    // Purpur start
                    if (this.player != null) {
                        org.bukkit.craftbukkit.entity.CraftHumanEntity player = this.player.getBukkitEntity();
                        String name = this.itemName;
                        boolean removeItalics = false;
                        if (player.hasPermission("purpur.anvil.remove_italics")) {
                            if (name.startsWith("&r")) {
                                name = name.substring(2);
                                removeItalics = true;
                            } else if (name.startsWith("<r>")) {
                                name = name.substring(3);
                                removeItalics = true;
                            } else if (name.startsWith("<reset>")) {
                                name = name.substring(7);
                                removeItalics = true;
                            }
                        }
                        if (this.player.level().purpurConfig.anvilAllowColors) {
                            if (player.hasPermission("purpur.anvil.color")) {
                                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)&([0-9a-fr])").matcher(name);
                                while (matcher.find()) {
                                    String match = matcher.group(1);
                                    name = name.replace("&" + match, "\u00a7" + match.toLowerCase(java.util.Locale.ROOT));
                                }
                                //name = name.replaceAll("(?i)&([0-9a-fr])", "\u00a7$1");
                            }
                            if (player.hasPermission("purpur.anvil.format")) {
                                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)&([k-or])").matcher(name);
                                while (matcher.find()) {
                                    String match = matcher.group(1);
                                    name = name.replace("&" + match, "\u00a7" + match.toLowerCase(java.util.Locale.ROOT));
                                }
                                //name = name.replaceAll("(?i)&([l-or])", "\u00a7$1");
                            }
                        }
                        net.kyori.adventure.text.Component component;
                        if (this.player.level().purpurConfig.anvilColorsUseMiniMessage && player.hasPermission("purpur.anvil.minimessage")) {
                            component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(org.bukkit.ChatColor.stripColor(name));
                        } else {
                            component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(name);
                        }
                        if (removeItalics) {
                            component = component.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                        }
                        itemstack1.setHoverName(io.papermc.paper.adventure.PaperAdventure.asVanilla(component));
                    }
                    else
                    // Purpur end
                    itemstack1.setHoverName(Component.literal(this.itemName));
                }
            } else if (itemstack.hasCustomHoverName()) {
                b1 = 1;
                i += b1;
                itemstack1.resetHoverName();
            }

            this.cost.set(j + i);
            if (i <= 0) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (b1 == i && b1 > 0 && this.cost.get() >= this.maximumRepairCost) { // CraftBukkit
                this.cost.set(this.maximumRepairCost - 1); // CraftBukkit
            }

            // Purpur start
            if (bypassCost && cost.get() >= maximumRepairCost) {
                itemstack.addTagElement("Purpur.realCost", IntTag.valueOf(cost.get()));
                cost.set(maximumRepairCost - 1);
            }
            // Purpur end

            if (this.cost.get() >= this.maximumRepairCost && !this.player.getAbilities().instabuild) { // CraftBukkit
                itemstack1 = ItemStack.EMPTY;
            }

            if (!itemstack1.isEmpty()) {
                int k2 = itemstack1.getBaseRepairCost();

                if (!itemstack2.isEmpty() && k2 < itemstack2.getBaseRepairCost()) {
                    k2 = itemstack2.getBaseRepairCost();
                }

                if (b1 != i || b1 == 0) {
                    k2 = AnvilMenu.calculateIncreasedRepairCost(k2);
                }

                itemstack1.setRepairCost(k2);
                EnchantmentHelper.setEnchantments(map, itemstack1);
            }

            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), itemstack1); // CraftBukkit
            this.sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686: Always send completed inventory to stay in sync with client
            this.broadcastChanges();
            // Purpur start
            if ((canDoUnsafeEnchants || org.purpurmc.purpur.PurpurConfig.allowUnsafeEnchants) && itemstack1 != ItemStack.EMPTY) {
                ((ServerPlayer) player).connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 2, itemstack1));
                ((ServerPlayer) player).connection.send(new ClientboundContainerSetDataPacket(containerId, 0, cost.get()));
            }
            // Purpur end
        }
    }

    public static int calculateIncreasedRepairCost(int cost) {
        return org.purpurmc.purpur.PurpurConfig.anvilCumulativeCost ? cost * 2 + 1 : 0;
    }

    public boolean setItemName(String newItemName) {
        String s1 = AnvilMenu.validateName(newItemName);

        if (s1 != null && !s1.equals(this.itemName)) {
            this.itemName = s1;
            if (this.getSlot(2).hasItem()) {
                ItemStack itemstack = this.getSlot(2).getItem();

                if (Util.isBlank(s1)) {
                    itemstack.resetHoverName();
                } else {
                    itemstack.setHoverName(Component.literal(s1));
                }
            }

            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private static String validateName(String name) {
        String s1 = SharedConstants.filterText(name);

        return s1.length() <= 50 ? s1 : null;
    }

    public int getCost() {
        return this.cost.get();
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventory inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryAnvil(
                this.access.getLocation(), this.inputSlots, this.resultSlots, this);
        this.bukkitEntity = new CraftInventoryView(this.player.getBukkitEntity(), inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end
}
