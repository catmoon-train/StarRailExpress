package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：获取玩家击杀者阵营的展示组件（用于 UI 渲染）。
 * 首个返回非 null 结果的监听器决定最终展示内容。
 *
 * <p>Event interface to obtain the display component for a player's killer cohort (for UI rendering).
 * The first listener returning a non-null result determines the display.
 */
public interface OnKillerCohortDisplay {

    /**
     * 获取击杀者阵营展示组件的事件。
     * 首个返回非 null {@link MutableComponent} 的监听器生效。
     *
     * <p>Event to obtain the killer cohort display component.
     * The first listener returning a non-null {@link MutableComponent} wins.
     */
    Event<OnKillerCohortDisplay> EVENT = createArrayBacked(OnKillerCohortDisplay.class,
            listeners -> (p) -> {
                MutableComponent result = null;
                for (OnKillerCohortDisplay listener : listeners) {
                    result = listener.onCohortRender(p);
                    if (result != null) {
                        return result;
                    }
                }
                return null;
            });

    /**
     * 获取指定玩家击杀者阵营的展示组件。
     *
     * <p>Returns the display component for the given player's killer cohort.
     *
     * @param target 需要展示阵营信息的玩家 / the player whose cohort display is requested
     * @return 阵营展示组件，若不提供则返回 null / the cohort display component, or null to not provide one
     */
    MutableComponent onCohortRender(Player target);
}
