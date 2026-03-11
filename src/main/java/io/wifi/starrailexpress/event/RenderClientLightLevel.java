package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public interface RenderClientLightLevel {

    /**
     * Event callback to determine if a game is allowed to stop for a specific
     * win status.
     * The game currently has the following death type names defined:
     * NONE, KILLERS, PASSENGERS, TIME, LOOSE_END, GAMBLER, RECORDER
     * 
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<RenderClientLightLevel> EVENT = createArrayBacked(RenderClientLightLevel.class,
            listeners -> (instance, other, t) -> {
                for (RenderClientLightLevel listener : listeners) {
                    float result = listener.renderClientLightLevel(instance, other, t);
                    if (result >= 0)
                        return result;
                }
                return -1;
            });

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    float renderClientLightLevel(Vector3f instance, Vector3fc other, float t);
}