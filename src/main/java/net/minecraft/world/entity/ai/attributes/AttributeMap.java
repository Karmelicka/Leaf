package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AttributeMap {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Gale start - Lithium - replace AI attributes with optimized collections
    private final Map<Attribute, AttributeInstance> attributes = new Reference2ReferenceOpenHashMap<>(0);
    private final Set<AttributeInstance> dirtyAttributes = new ReferenceOpenHashSet<>(0);
    // Gale end - Lithium - replace AI attributes with optimized collections
    private final AttributeSupplier supplier;
    private final java.util.function.Function<Attribute, AttributeInstance> createInstance; // Gale - Airplane - reduce entity allocations
    private final net.minecraft.world.entity.LivingEntity entity; // Purpur

    public AttributeMap(AttributeSupplier defaultAttributes) {
        // Purpur start
        this(defaultAttributes, null);
    }

    public AttributeMap(AttributeSupplier defaultAttributes, net.minecraft.world.entity.LivingEntity entity) {
        this.entity = entity;
        // Purpur end
        this.supplier = defaultAttributes;
        this.createInstance = attribute -> this.supplier.createInstance(this::onAttributeModified, attribute); // Gale - Airplane - reduce entity allocations
    }

    private void onAttributeModified(AttributeInstance instance) {
        if (instance.getAttribute().isClientSyncable() && (entity == null || entity.shouldSendAttribute(instance.getAttribute()))) { // Purpur
            this.dirtyAttributes.add(instance);
        }

    }

    public Set<AttributeInstance> getDirtyAttributes() {
        return this.dirtyAttributes;
    }

    public Collection<AttributeInstance> getSyncableAttributes() {
        return this.attributes.values().stream().filter((attribute) -> {
            return attribute.getAttribute().isClientSyncable() && (entity == null || entity.shouldSendAttribute(attribute.getAttribute())); // Purpur
        }).collect(Collectors.toList());
    }


    @Nullable
    public AttributeInstance getInstance(Attribute attribute) {
        return this.attributes.computeIfAbsent(attribute, this.createInstance); // Gale - Airplane - reduce entity allocations - cache lambda, as for some reason java allocates it anyways
    }

    @Nullable
    public AttributeInstance getInstance(Holder<Attribute> attribute) {
        return this.getInstance(attribute.value());
    }

    public boolean hasAttribute(Attribute attribute) {
        return this.attributes.get(attribute) != null || this.supplier.hasAttribute(attribute);
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.hasAttribute(attribute.value());
    }

    public boolean hasModifier(Attribute attribute, UUID uuid) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(uuid) != null : this.supplier.hasModifier(attribute, uuid);
    }

    public boolean hasModifier(Holder<Attribute> attribute, UUID uuid) {
        return this.hasModifier(attribute.value(), uuid);
    }

    public double getValue(Attribute attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getValue() : this.supplier.getValue(attribute);
    }

    public double getBaseValue(Attribute attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getBaseValue() : this.supplier.getBaseValue(attribute);
    }

    public double getModifierValue(Attribute attribute, UUID uuid) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(uuid).getAmount() : this.supplier.getModifierValue(attribute, uuid);
    }

    public double getModifierValue(Holder<Attribute> attribute, UUID uuid) {
        return this.getModifierValue(attribute.value(), uuid);
    }

    public void removeAttributeModifiers(Multimap<Attribute, AttributeModifier> attributeModifiers) {
        attributeModifiers.asMap().forEach((attribute, modifiers) -> {
            AttributeInstance attributeInstance = this.attributes.get(attribute);
            if (attributeInstance != null) {
                modifiers.forEach((modifier) -> {
                    attributeInstance.removeModifier(modifier.getId());
                });
            }

        });
    }

    public void addTransientAttributeModifiers(Multimap<Attribute, AttributeModifier> attributeModifiers) {
        attributeModifiers.forEach((attribute, attributeModifier) -> {
            AttributeInstance attributeInstance = this.getInstance(attribute);
            if (attributeInstance != null) {
                attributeInstance.removeModifier(attributeModifier.getId());
                attributeInstance.addTransientModifier(attributeModifier);
            }

        });
    }

    public void assignValues(AttributeMap other) {
        other.attributes.values().forEach((attributeInstance) -> {
            AttributeInstance attributeInstance2 = this.getInstance(attributeInstance.getAttribute());
            if (attributeInstance2 != null) {
                attributeInstance2.replaceFrom(attributeInstance);
            }

        });
    }

    public ListTag save() {
        ListTag listTag = new ListTag();

        for(AttributeInstance attributeInstance : this.attributes.values()) {
            listTag.add(attributeInstance.save());
        }

        return listTag;
    }

    public void load(ListTag nbt) {
        for(int i = 0; i < nbt.size(); ++i) {
            CompoundTag compoundTag = nbt.getCompound(i);
            String string = compoundTag.getString("Name");
            Util.ifElse(BuiltInRegistries.ATTRIBUTE.getOptional(ResourceLocation.tryParse(string)), (attribute) -> {
                AttributeInstance attributeInstance = this.getInstance(attribute);
                if (attributeInstance != null) {
                    attributeInstance.load(compoundTag);
                }

            }, () -> {
                LOGGER.warn("Ignoring unknown attribute '{}'", (Object)string);
            });
        }

    }

    // Paper - start - living entity allow attribute registration
    public void registerAttribute(Attribute attributeBase) {
        AttributeInstance attributeModifiable = new AttributeInstance(attributeBase, AttributeInstance::getAttribute);
        attributes.put(attributeBase, attributeModifiable);
    }
    // Paper - end - living entity allow attribute registration

}
