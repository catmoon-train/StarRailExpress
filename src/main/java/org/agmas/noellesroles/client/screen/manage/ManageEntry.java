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

package org.agmas.noellesroles.client.screen.manage;

import io.wifi.starrailexpress.client.gui.widget.SwitchState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 游戏设置中心里的一行（{@code docs/ui_style.md} §8.7）。
 *
 * <p>
 * 设置页是<b>数据驱动</b>的：分类里只放一串 {@code ManageEntry}，绘制与命中测试统一由
 * {@code GameManagementScreen} 负责。要加一项设置，只要在这里挑一个类型加进
 * {@code ManageCatalog}，不用碰布局代码。
 *
 * <p>
 * 行高只有三档（{@link #ROW_H} / {@link #LABEL_H} / {@link #DIVIDER_H}），全部落在 4px 栅格上；
 * 颜色只能取 {@code SRETheme} 或阵营色 / 角色色，不要就地写 8 位色值。
 */
@Environment(EnvType.CLIENT)
public sealed interface ManageEntry {

    /** 可点击的一行（按钮 / 导航 / 开关 / 数字）的标准高度。 */
    int ROW_H = 20;
    /** 分组标题行高度。 */
    int LABEL_H = 15;
    /** 分隔线高度。 */
    int DIVIDER_H = 5;

    /** 本行占用的高度。 */
    int height();

    /** 这一行用来做搜索匹配的文本（没有可匹配文本时返回 null）。 */
    default String searchText() {
        return null;
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. 分组标题 / 分隔线
    // ══════════════════════════════════════════════════════════════════

    /**
     * 分组标题：左侧 2px 分类色条 + 粗体标题。
     *
     * @param color 分类色（阵营色 / 功能色），标题文字与色条都用它
     */
    record Label(Component text, int color) implements ManageEntry {
        @Override
        public int height() {
            return LABEL_H;
        }
    }

    /** 一条分隔线。 */
    record Divider() implements ManageEntry {
        @Override
        public int height() {
            return DIVIDER_H;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 执行指令 / 打开界面
    // ══════════════════════════════════════════════════════════════════

    /**
     * 指令行：点一下就把 {@code command}（不带斜杠）发给服务端。
     *
     * <p>
     * 这是本页最老的形态（原来整页都是这个），也是唯一在客户端不知道服务端状态时
     * 仍然安全的形态 —— 服务端会自己校验权限并回一条消息。
     */
    record Command(Component text, String command) implements ManageEntry {
        @Override
        public int height() {
            return ROW_H;
        }

        @Override
        public String searchText() {
            return text.getString();
        }
    }

    /**
     * 导航行：点一下打开另一个界面（轮选名单、地图轮换、内容管理……）。
     *
     * <p>
     * 用它把「现成的界面」接进来，而不是在本页重写一遍它们的编辑逻辑。
     * 传 supplier 而不是 {@link Screen} 实例：列表每次重建都会重新构造行，
     * 直接持有实例会白白 new 一堆界面。
     */
    record Nav(Component text, Supplier<Screen> target) implements ManageEntry {
        @Override
        public int height() {
            return ROW_H;
        }

        @Override
        public String searchText() {
            return text.getString();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 开关 / 数字
    // ══════════════════════════════════════════════════════════════════

    /**
     * 开关行：右边一枚胶囊开关（{@code UiSurface.switchTrack}）。
     *
     * <p>
     * {@code state} 返回 {@link SwitchState#UNSET} 表示「本地还不知道服务端的取值」
     * （客户端镜像还没同步到）—— 滑块停在正中，点一下仍会下发开启指令。
     *
     * @param labelColor 标签颜色（阵营色 / 角色色）；关闭时自动降到 {@code SRETheme.MUTED}，
     *                   与 {@code SreSwitchButton} 的观感一致
     */
    record Toggle(Component text, int labelColor, Supplier<SwitchState> state, Consumer<Boolean> setter)
            implements ManageEntry {
        @Override
        public int height() {
            return ROW_H;
        }

        @Override
        public String searchText() {
            return text.getString();
        }
    }

    /**
     * 数字行：{@code [-] 值 [+] }（可选一个「自动」按钮）。
     *
     * <p>
     * 值取 {@code forced}：{@code -1} 表示服务端用自动公式，此时显示「自动」并把
     * {@code autoEstimate} 的估算值放在括号里；加减按钮会把 {@code -1} 变成具体数字。
     *
     * @param unit  数值后缀（例如「人」），可为 null；{@code min} / {@code max} 是可调范围（含端点）
     * @param apply 落到 {@code /setRoleCount <id> <n>} 这类指令上
     * @param reset 恢复自动；为 null 时不画「自动」按钮
     */
    record Counter(Component text, int labelColor, IntSupplier forced, IntSupplier autoEstimate,
            IntConsumer apply, Runnable reset, int min, int max, Component unit) implements ManageEntry {
        @Override
        public int height() {
            return ROW_H;
        }

        @Override
        public String searchText() {
            return text.getString();
        }
    }
}
