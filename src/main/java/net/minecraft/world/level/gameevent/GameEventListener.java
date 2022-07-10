package net.minecraft.world.level.gameevent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public interface GameEventListener {
    PositionSource getListenerSource();

    int getListenerRadius();

    boolean handleGameEvent(ServerLevel world, GameEvent event, GameEvent.Context emitter, Vec3 emitterPos);

    default GameEventListener.DeliveryMode getDeliveryMode() {
        return GameEventListener.DeliveryMode.UNSPECIFIED;
    }

    public static enum DeliveryMode {
        UNSPECIFIED,
        BY_DISTANCE;
    }

    public interface Holder<T extends GameEventListener> {
        T getListener();
    }

    // Leaf start - petal - Add check for seeing if this listener cares about an event
    default boolean listensToEvent(GameEvent gameEvent, GameEvent.Context context) {
        return true;
    }
    // Leaf end - petal
}
