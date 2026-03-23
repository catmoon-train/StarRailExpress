package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：判断是否允许渲染玩家的名称标签。
 * 若任意监听器返回 {@code false}，则不渲染名称标签。
 *
 * <p>Event interface to determine whether a player's name tag should be rendered.
 * If any listener returns {@code false}, the name tag will not be rendered.
 */
public interface AllowNameRender {

    /**
     * 判断是否允许渲染玩家名称标签的事件。
     * 任意监听器返回 {@code false} 即阻止渲染。
     *
     * <p>Event callback to determine if a player's name tag is allowed to be rendered.
     * Any listener returning {@code false} prevents rendering.
     */
    Event<AllowNameRender> EVENT = createArrayBacked(AllowNameRender.class, listeners -> (player) -> {
        for (AllowNameRender listener : listeners) {
            if (!listener.allowRenderName(player)) {
                return false;
            }
        }
        return true;
    });

    /**
     * 判断是否允许渲染指定玩家的名称标签。
     *
     * <p>Determines whether the given player's name tag is allowed to be rendered.
     *
     * @param player 需要判断的玩家 / the player whose name tag rendering is being checked
     * @return {@code true} 若允许渲染名称标签 / {@code true} if rendering the name tag is allowed
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean allowRenderName(Player player);
}