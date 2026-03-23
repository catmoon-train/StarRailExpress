package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：用于覆盖客户端的环境光照亮度（用于特殊视觉效果）。
 * 首个返回非负值的监听器结果优先；返回 -1 表示不覆盖。
 *
 * <p>Event interface to override the client-side ambient light level (for special visual effects).
 * The first listener returning a non-negative value wins; returning -1 means no override.
 */
public interface RenderClientLightLevel {

    /**
     * 覆盖客户端环境光照亮度的事件。
     * 首个返回非负值（&ge;0）的监听器结果生效；返回负值表示不覆盖。
     *
     * <p>Event to override the client-side ambient light level.
     * The first listener returning a value &ge; 0 takes effect; negative values indicate no override.
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

    /**
     * 计算客户端应使用的环境光照亮度。
     *
     * <p>Calculates the ambient light level to be used on the client side.
     *
     * @param instance 当前光照颜色向量 / the current light color vector
     * @param other    另一光照颜色向量（用于插值） / another light color vector (used for interpolation)
     * @param t        插值系数 / the interpolation factor
     * @return 覆盖的光照亮度值（&ge;0），或 -1 表示不覆盖 /
     *         the overriding light level (&ge;0), or -1 for no override
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    float renderClientLightLevel(Vector3f instance, Vector3fc other, float t);
}