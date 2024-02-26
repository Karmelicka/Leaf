package io.papermc.paper.configuration.serializer.registry;

import io.leangen.geantyref.TypeToken;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

/**
 * Use {@link RegistryHolderSerializer} for datapack-configurable things.
 */
public final class RegistryValueSerializer<T> extends RegistryEntrySerializer<T, T> {

    public RegistryValueSerializer(TypeToken<T> type, final RegistryAccess registryAccess, ResourceKey<? extends Registry<T>> registryKey, boolean omitMinecraftNamespace) {
        super(type, registryAccess, registryKey, omitMinecraftNamespace);
    }

    public RegistryValueSerializer(Class<T> type, final RegistryAccess registryAccess, ResourceKey<? extends Registry<T>> registryKey, boolean omitMinecraftNamespace) {
        super(type, registryAccess, registryKey, omitMinecraftNamespace);
    }

    @Override
    protected T convertFromResourceKey(ResourceKey<T> key) {
        final T value = this.registry().get(key);
        if (value == null) {
            // Leaf start - Don't throw exception on missing ResourceKey value
            //throw new SerializationException("Missing value in " + this.registry() + " with key " + key.location());
            com.mojang.logging.LogUtils.getClassLogger().error("Missing value in {} with key {}", this.registry(), key.location());
            return null;
            // Leaf end
        }
        return value;
    }

    @Override
    protected ResourceKey<T> convertToResourceKey(T value) {
        return this.registry().getResourceKey(value).orElseThrow();
    }
}
