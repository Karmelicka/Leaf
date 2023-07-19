package io.papermc.paper.plugin.lifecycle.event.types;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.handler.configuration.MonitorLifecycleEventHandlerConfiguration;
import io.papermc.paper.plugin.lifecycle.event.handler.configuration.MonitorLifecycleEventHandlerConfigurationImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public class MonitorableLifecycleEventType<O extends LifecycleEventOwner, E extends LifecycleEvent> extends AbstractLifecycleEventType<O, E, MonitorLifecycleEventHandlerConfiguration<O>, MonitorLifecycleEventHandlerConfigurationImpl<O, E>> implements LifecycleEventType.Monitorable<O, E> {

    final List<RegisteredHandler<O, E>> handlers = new ArrayList<>();
    int nonMonitorIdx = 0;

    public MonitorableLifecycleEventType(final String name, final Class<? extends O> ownerType) {
        super(name, ownerType);
    }

    @Override
    public MonitorLifecycleEventHandlerConfigurationImpl<O, E> newHandler(final LifecycleEventHandler<? super E> handler) {
        return new MonitorLifecycleEventHandlerConfigurationImpl<>(handler, this);
    }

    @Override
    protected void register(final O owner, final LifecycleEventHandler<? super E> handler, final MonitorLifecycleEventHandlerConfigurationImpl<O, E> config) {
        final RegisteredHandler<O, E> registeredHandler = new RegisteredHandler<>(owner, handler);
        if (!config.isMonitor()) {
            this.handlers.add(this.nonMonitorIdx, registeredHandler);
            this.nonMonitorIdx++;
        } else {
            this.handlers.add(registeredHandler);
        }
    }

    @Override
    public void forEachHandler(final Consumer<? super RegisteredHandler<O, E>> consumer, final Predicate<? super RegisteredHandler<O, E>> predicate) {
        for (final RegisteredHandler<O, E> handler : this.handlers) {
            if (predicate.test(handler)) {
                consumer.accept(handler);
            }
        }
    }

    @Override
    public void removeMatching(final Predicate<? super RegisteredHandler<O, E>> predicate) {
        this.handlers.removeIf(predicate);
    }
}
