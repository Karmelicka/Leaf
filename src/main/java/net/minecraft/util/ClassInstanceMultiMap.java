package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClassInstanceMultiMap<T> extends AbstractCollection<T> {
    private final Map<Class<?>, List<T>> byClass = new Reference2ReferenceOpenHashMap<>(2); // Gale - Lithium - replace class map with optimized collection
    private final Class<T> baseClass;
    private final List<T> allInstances = Lists.newArrayList();

    public ClassInstanceMultiMap(Class<T> elementType) {
        this.baseClass = elementType;
        this.byClass.put(elementType, this.allInstances);
    }

    @Override
    public boolean add(T object) {
        boolean bl = false;

        for(Map.Entry<Class<?>, List<T>> entry : this.byClass.entrySet()) {
            if (entry.getKey().isInstance(object)) {
                bl |= entry.getValue().add(object);
            }
        }

        return bl;
    }

    @Override
    public boolean remove(Object object) {
        boolean bl = false;

        for(Map.Entry<Class<?>, List<T>> entry : this.byClass.entrySet()) {
            if (entry.getKey().isInstance(object)) {
                List<T> list = entry.getValue();
                bl |= list.remove(object);
            }
        }

        return bl;
    }

    @Override
    public boolean contains(Object object) {
        return this.find(object.getClass()).contains(object);
    }

    public <S> Collection<S> find(Class<S> type) {
        // Gale start - Lithium - avoid Class#isAssignableFrom call in ClassInstanceMultiMap
        /*
        Only perform the slow Class#isAssignableFrom(Class) if a list doesn't exist for the type, otherwise
        we can assume it's already valid. The slow-path code is moved to a separate method to help the JVM inline this.
         */
        Collection<T> collection = this.byClass.get(type);

        if (collection == null) {
            collection = this.createAllOfType(type);
        }

        return (Collection<S>) Collections.unmodifiableCollection(collection);
    }

    private <S> Collection<T> createAllOfType(Class<S> type) {
        List<T> list = new java.util.ArrayList<>(1);

        for (T allElement : this.allInstances) {
            if (type.isInstance(allElement)) {
                list.add(allElement);
            }
        }

        this.byClass.put(type, list);

        return list;
        // Gale end - Lithium - avoid Class#isAssignableFrom call in ClassInstanceMultiMap
    }

    @Override
    public Iterator<T> iterator() {
        return (Iterator<T>)(this.allInstances.isEmpty() ? Collections.emptyIterator() : Iterators.unmodifiableIterator(this.allInstances.iterator()));
    }

    public List<T> getAllInstances() {
        return ImmutableList.copyOf(this.allInstances);
    }

    @Override
    public int size() {
        return this.allInstances.size();
    }
}
