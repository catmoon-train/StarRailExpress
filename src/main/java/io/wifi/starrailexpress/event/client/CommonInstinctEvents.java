/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.event.client;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;

/**
 * 获取直觉高亮颜色的事件。
 * 首个返回非 {@link TrueFalseAndCustomResult#pass()} 的监听器结果生效。<br/>
 * <li>{@link TrueFalseAndCustomResult#pass()} 不修改颜色（走默认逻辑）</li>
 * <li>{@link TrueFalseAndCustomResult#custom()} 返回自定义颜色</li>
 * <li>{@link TrueFalseAndCustomResult#disallow()} 或
 * {@link TrueFalseAndCustomResult#no()} 阻止显示</li>
 */
public class CommonInstinctEvents {

    /**
     * 获取活着的玩家的直觉高亮颜色的事件。最后触发<br/>
     * 首个返回非 {@link TrueFalseAndCustomResult#pass()} 的监听器结果生效。<br/>
     * <li>{@link TrueFalseAndCustomResult#pass()} 不修改颜色（走默认逻辑）</li>
     * <li>{@link TrueFalseAndCustomResult#custom()} 返回自定义颜色</li>
     * <li>{@link TrueFalseAndCustomResult#disallow()} 或
     * {@link TrueFalseAndCustomResult#no()} 阻止显示</li>
     */
    public static Event<InnerOnGetInstinctHighlight> ALIVE_COMMON_AFTER_EVENT = createArrayBacked(
            InnerOnGetInstinctHighlight.class,
            listeners -> (self, target, isInstinctEnabled) -> {
                if (target == null)
                    return TrueFalseAndCustomResult.pass();
                for (InnerOnGetInstinctHighlight listener : listeners) {
                    var color = listener.getInstinctHighlight(self, target, isInstinctEnabled);
                    if (color != null && !color.isPass()) {
                        return color;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });

    /**
     * 获取活着的玩家的直觉高亮颜色的事件。在被观察的条件后观察条件前触发<br/>
     * 首个返回非 {@link TrueFalseAndCustomResult#pass()} 的监听器结果生效。<br/>
     * <li>{@link TrueFalseAndCustomResult#pass()} 不修改颜色（走默认逻辑）</li>
     * <li>{@link TrueFalseAndCustomResult#custom()} 返回自定义颜色</li>
     * <li>{@link TrueFalseAndCustomResult#disallow()} 或
     * {@link TrueFalseAndCustomResult#no()} 阻止显示</li>
     */
    public static Event<InnerOnGetInstinctHighlight> ALIVE_COMMON_MIDDLE_EVENT = createArrayBacked(
            InnerOnGetInstinctHighlight.class,
            listeners -> (self, target, isInstinctEnabled) -> {
                if (target == null)
                    return TrueFalseAndCustomResult.pass();
                for (InnerOnGetInstinctHighlight listener : listeners) {
                    var color = listener.getInstinctHighlight(self, target, isInstinctEnabled);
                    if (color != null && !color.isPass()) {
                        return color;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });
    /**
     * 获取活着的玩家的直觉高亮颜色的事件。<br/>
     * 首个返回非 {@link TrueFalseAndCustomResult#pass()} 的监听器结果生效。<br/>
     * <li>{@link TrueFalseAndCustomResult#pass()} 不修改颜色（走默认逻辑）</li>
     * <li>{@link TrueFalseAndCustomResult#custom()} 返回自定义颜色</li>
     * <li>{@link TrueFalseAndCustomResult#disallow()} 或
     * {@link TrueFalseAndCustomResult#no()} 阻止显示</li>
     */
    public static Event<InnerOnGetInstinctHighlight> ALIVE_COMMON_BEFORE_EVENT = createArrayBacked(
            InnerOnGetInstinctHighlight.class,
            listeners -> (self, target, isInstinctEnabled) -> {
                if (target == null)
                    return TrueFalseAndCustomResult.pass();
                for (InnerOnGetInstinctHighlight listener : listeners) {
                    var color = listener.getInstinctHighlight(self, target, isInstinctEnabled);
                    if (color != null && !color.isPass()) {
                        return color;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });

    /**
     * 获取旁观玩家直觉高亮颜色的事件。<br/>
     * 首个返回非 {@link TrueFalseAndCustomResult#pass()} 的监听器结果生效。<br/>
     * <li>{@link TrueFalseAndCustomResult#pass()} 不修改颜色（走默认逻辑）</li>
     * <li>{@link TrueFalseAndCustomResult#custom()} 返回自定义颜色</li>
     * <li>{@link TrueFalseAndCustomResult#disallow()} 或
     * {@link TrueFalseAndCustomResult#no()} 阻止显示</li>
     */
    public static Event<InnerOnGetInstinctHighlight> SPECTATOR_COMMON_EVENT = createArrayBacked(
            InnerOnGetInstinctHighlight.class,
            listeners -> (self, target, isInstinctEnabled) -> {
                if (target == null)
                    return TrueFalseAndCustomResult.pass();
                for (InnerOnGetInstinctHighlight listener : listeners) {
                    var color = listener.getInstinctHighlight(self, target, isInstinctEnabled);
                    if (color != null && !color.isPass()) {
                        return color;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });

    /**
     * 获取指定实体在直觉视野下的高亮颜色。
     *
     * <p>
     * Returns the instinct highlight color for the given target entity.
     *
     * @param target            需要高亮的目标实体 / the target entity to highlight
     * @param isInstinctEnabled 直觉功能当前是否开启 / whether the instinct ability is
     *                          currently active
     * @return 高亮颜色值；-1 表示不改变，-2 表示禁用直觉高亮 /
     *         the highlight color; -1 for no change, -2 to disable instinct
     *         highlight
     */
    public interface InnerOnGetInstinctHighlight {
        TrueFalseAndCustomResult<Integer> getInstinctHighlight(LocalPlayer self, Entity target,
                boolean isInstinctEnabled);
    }
}
