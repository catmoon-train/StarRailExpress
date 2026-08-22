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

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.GuiGraphics;

/**
 * {@link LimitedInventoryScreen}（限位背包界面）生命周期事件。
 *
 * <p>纯客户端事件，与旧版 {@code @Mixin(LimitedInventoryScreen.class)} 的注入点一一对应：
 * <ul>
 *     <li>{@link #INIT} —— {@code init()} 开头（原 HEAD 注入）</li>
 *     <li>{@link #INIT_TAIL} —— {@code init()} 末尾（原 TAIL 注入）</li>
 *     <li>{@link #RENDER} —— {@code render()} 开头（原 HEAD 注入）</li>
 *     <li>{@link #RENDER_TAIL} —— {@code render()} 末尾（原 TAIL 注入）</li>
 * </ul>
 *
 * <p>职业相关的背包界面扩展优先使用 {@link io.wifi.starrailexpress.api.SRERole} 上的
 * {@code setInventoryScreenInitHandler} 等钩子；非职业（如 modifier）扩展请注册本事件。
 */
@Environment(EnvType.CLIENT)
public final class LimitedInventoryScreenEvents {

    /** {@code init()} 开头触发。 */
    public static final Event<Init> INIT = createArrayBacked(Init.class,
            listeners -> screen -> {
                for (Init listener : listeners) {
                    listener.onInit(screen);
                }
            });

    /** {@code init()} 末尾触发。 */
    public static final Event<Init> INIT_TAIL = createArrayBacked(Init.class,
            listeners -> screen -> {
                for (Init listener : listeners) {
                    listener.onInit(screen);
                }
            });

    /** {@code render()} 开头触发（每帧）。 */
    public static final Event<Render> RENDER = createArrayBacked(Render.class,
            listeners -> (screen, graphics, mouseX, mouseY, delta) -> {
                for (Render listener : listeners) {
                    listener.onRender(screen, graphics, mouseX, mouseY, delta);
                }
            });

    /** {@code render()} 末尾触发（每帧）。 */
    public static final Event<Render> RENDER_TAIL = createArrayBacked(Render.class,
            listeners -> (screen, graphics, mouseX, mouseY, delta) -> {
                for (Render listener : listeners) {
                    listener.onRender(screen, graphics, mouseX, mouseY, delta);
                }
            });

    private LimitedInventoryScreenEvents() {
    }

    @FunctionalInterface
    public interface Init {
        void onInit(LimitedInventoryScreen screen);
    }

    @FunctionalInterface
    public interface Render {
        void onRender(LimitedInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta);
    }
}
