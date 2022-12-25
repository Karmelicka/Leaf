package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TradeWithVillager extends Behavior<Villager> {
    private static final int INTERACT_DIST_SQR = 5;
    private static final float SPEED_MODIFIER = 0.5F;
    // Gale start - optimize villager data storage
    private static final Item[] WHEAT_SINGLETON_ARRAY = {Items.WHEAT};
    private @NotNull Item @Nullable [] trades = null;
    // Gale end - optimize villager data storage

    public TradeWithVillager() {
        super(ImmutableMap.of(MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Villager entity) {
        return BehaviorUtils.targetIsValid(entity.getBrain(), MemoryModuleType.INTERACTION_TARGET, EntityType.VILLAGER);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Villager entity, long time) {
        return this.checkExtraStartConditions(world, entity);
    }

    @Override
    protected void start(ServerLevel serverLevel, Villager villager, long l) {
        Villager villager2 = (Villager)villager.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(villager, villager2, 0.5F);
        this.trades = figureOutWhatIAmWillingToTrade(villager, villager2);
    }

    @Override
    protected void tick(ServerLevel world, Villager entity, long time) {
        Villager villager = (Villager)entity.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        if (!(entity.distanceToSqr(villager) > 5.0D)) {
            BehaviorUtils.lockGazeAndWalkToEachOther(entity, villager, 0.5F);
            entity.gossip(world, villager, time);
            if (entity.hasExcessFood() && (entity.getVillagerData().getProfession() == VillagerProfession.FARMER || villager.wantsMoreFood())) {
                throwHalfStack(entity, Villager.FOOD_POINTS_KEY_ARRAY, villager); // Gale - optimize villager data storage
            }

            if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER && entity.getInventory().countItem(Items.WHEAT) > Items.WHEAT.getMaxStackSize() / 2) {
                throwHalfStack(entity, WHEAT_SINGLETON_ARRAY, villager); // Gale - optimize villager data storage
            }

            // Gale start - optimize villager data storage
            if (this.trades != null && entity.getInventory().hasAnyOf(this.trades)) {
                throwHalfStack(entity, this.trades, villager);
                // Gale end - optimize villager data storage
            }

        }
    }

    @Override
    protected void stop(ServerLevel serverLevel, Villager villager, long l) {
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    // Gale start - optimize villager data storage
    private static @NotNull Item @Nullable [] figureOutWhatIAmWillingToTrade(Villager entity, Villager target) {
        @NotNull Item @Nullable [] immutableSet = target.getVillagerData().getProfession().requestedItems();
        if (immutableSet == null) {
            return null;
        }
        @NotNull Item @Nullable [] immutableSet2 = entity.getVillagerData().getProfession().requestedItems();
        if (immutableSet2 == null) {
            return immutableSet;
        }
        if (immutableSet == immutableSet2) {
            return null;
        }
        Item[] willingToTrade = new Item[immutableSet.length];
        int willingToTradeSize = 0;
        forImmutableSet: for (Item item : immutableSet) {
            for (Item item2 : immutableSet2) {
                if (item == item2) {
                    continue forImmutableSet;
                }
            }
            willingToTrade[willingToTradeSize] = item;
            willingToTradeSize++;
        }
        return Arrays.copyOf(willingToTrade, willingToTradeSize);
        // Gale end - optimize villager data storage
    }

    private static void throwHalfStack(Villager villager, @NotNull Item @NotNull [] validItems, LivingEntity target) { // Gale - optimize villager data storage
        SimpleContainer simpleContainer = villager.getInventory();
        ItemStack itemStack = ItemStack.EMPTY;
        int i = 0;

        while(i < simpleContainer.getContainerSize()) {
            ItemStack itemStack2;
            Item item;
            int j;
            label28: {
                itemStack2 = simpleContainer.getItem(i);
                if (!itemStack2.isEmpty()) {
                    item = itemStack2.getItem();
                    // Gale start - optimize villager data storage
                    boolean inValidItems = false;
                    for (Item validItem : validItems) {
                        if (validItem == item) {
                            inValidItems = true;
                            break;
                        }
                    }
                    if (inValidItems) {
                        // Gale end - optimize villager data storage
                        if (itemStack2.getCount() > itemStack2.getMaxStackSize() / 2) {
                            j = itemStack2.getCount() / 2;
                            break label28;
                        }

                        if (itemStack2.getCount() > 24) {
                            j = itemStack2.getCount() - 24;
                            break label28;
                        }
                    }
                }

                ++i;
                continue;
            }

            itemStack2.shrink(j);
            itemStack = new ItemStack(item, j);
            break;
        }

        if (!itemStack.isEmpty()) {
            BehaviorUtils.throwItem(villager, itemStack, target.position());
        }

    }
}
