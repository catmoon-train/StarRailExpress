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

package io.wifi.starrailexpress.api;

import io.wifi.utils.RandomSelector;
import net.minecraft.server.level.ServerLevel;
import org.agmas.harpymodloader.SREDisableManager;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * 职业专属随机事件：每局开局掷一次的启用骰，以及由此派生的本局状态与「强制下一局」。
 * <p>
 * 每个职业实例通过 {@link SRERole#setEventEnableChance(BiConsumer, int)} 声明自己的事件，声明过的职业会被
 * {@link TMMRoles} 收录进事件职业列表（职业被注销时自动移出）。之后：
 * <ul>
 *   <li>每局正式开局（{@code OnGameTrueStarted}）由 {@link SRERole#rollAllEventEnableChances} 统一掷骰；</li>
 *   <li>局末（{@code OnGameEnd}）由 {@link SRERole#resetAllEventEnableStates} 清空本局状态。</li>
 * </ul>
 * 也就是说，事件监听器只有全局的两个，掷骰开销只与「声明过事件的职业数量」有关。
 *
 * <h2>维度通用 / Dimension-agnostic</h2>
 * 状态是<strong>职业级</strong>的：同一职业在所有维度共用一份「本局是否启用」，不按维度分别记录，
 * 因此查询与「强制下一局」都不需要世界参数。世界只在两个地方有意义——掷骰时的地图限制判定，
 * 以及回调的入参；未指定时以主世界为准（见 {@link SRERole#rollAllEventEnableChances}）。
 *
 * <h2>判定顺序</h2>
 * 掷骰依次检查：职业地图限制（{@link SRERole#isEventMapAllowed}，即 {@code setSpecialMapRole} /
 * {@code setSpecialMapRolesCondition} / {@code setCanSpawnInMap} 三项）→ 职业禁用状态
 * （{@link SREDisableManager#isRoleDisabled}，含地图 {@code disabledRoles}、配置禁用与轮选）→
 * 概率（已被 {@link #forceNextRound()} 强制时跳过概率）。
 *
 * <h2>回调契约</h2>
 * 回调在<strong>每一次掷骰后</strong>都会收到结果，且局末还会再收到一次 {@code (level, false)}，
 * 因此回调必须能处理「未启用」；只关心掷中的写法见 {@link SRERole#setEventEnableChance(Consumer, int)}。
 * 不需要回调、按需查询的写法见 {@link SRERole#setEventEnableChance(int)} 与 {@link #isEnabled()}。
 *
 * <h2>状态与跨局语义</h2>
 * {@link #enabled} 与 {@link #forcedByCommand} 每局开局重算、局末清零；{@link #forceRequested} 由
 * {@link #forceNextRound()} 写入后跨局保留，直到下一次开局掷骰时被消费——即使本局结束也不会丢失。
 * 注意请求只保证跳过概率判定，仍要经过地图限制与禁用状态检查；若那一局被这两项拦下，
 * 请求即被消费而不会顺延到再下一局。
 *
 * @see SRERole#setEventEnableChance(BiConsumer, int)
 */
public final class RoleRoundEvent {

    private final SRERole owner;
    /** 本局是否掷中，即 {@link #isEnabled()} 的结论。 */
    private boolean enabled;
    /** 本局是否由「强制下一局」请求掷中，用于区分命令强开与自然掷中。 */
    private boolean forcedByCommand;
    /** 是否有等待下一次开局生效的强制请求；唯一跨局的标志，局末清理不会动它。 */
    private boolean forceRequested;
    /** 掷骰结果回调；为 null 表示只通过 {@link #isEnabled()} 查询本局是否启用。 */
    private BiConsumer<ServerLevel, Boolean> handler;
    /**
     * 概率供应器，单位万分比；非 null 即表示本职业声明过事件（见 {@link #hasChance()}）。
     * <p>
     * 用供应器而不是固定值，是为了让概率能每次掷骰都读最新配置（见 {@code ModRoles.FAKE_STEVE}）。
     * 读取结果会被裁剪到 0–10000。
     */
    private IntSupplier chanceSupplier;

    /**
     * 创建某个职业的事件对象。由 {@link SRERole} 持有，每个职业实例一个。
     *
     * @param owner 拥有该事件的职业，不能为 null
     */
    RoleRoundEvent(SRERole owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * 声明事件并用固定概率掷骰。
     *
     * @param event  掷骰结果回调，可为 null（只查询、不需要通知）
     * @param chance 万分比概率，例如 6000 表示 60%；超出 0–10000 会被裁剪
     * @return this，便于链式调用
     */
    public RoleRoundEvent setChance(BiConsumer<ServerLevel, Boolean> event, int chance) {
        return setChance(event, () -> chance);
    }

    /**
     * 声明事件，概率在每次掷骰时动态读取，适合直接绑定配置项。
     *
     * @param event          掷骰结果回调，可为 null（只查询、不需要通知）
     * @param chanceSupplier 万分比概率供应器；读取值超出 0–10000 会被裁剪
     * @return this，便于链式调用
     * @throws NullPointerException {@code chanceSupplier} 为 null 时抛出
     */
    public RoleRoundEvent setChance(BiConsumer<ServerLevel, Boolean> event, IntSupplier chanceSupplier) {
        this.handler = event;
        this.chanceSupplier = Objects.requireNonNull(chanceSupplier, "chanceSupplier");
        // 注册之后再声明的职业在这里入列；注册之前声明的由 TMMRoles#registerRole 收录
        TMMRoles.markEventRole(owner);
        return this;
    }

    /**
     * 本职业是否声明过事件，即是否会参与每局开局的掷骰。
     *
     * @return 调用过任意 {@code setChance} 时为 true
     */
    public boolean hasChance() {
        return chanceSupplier != null;
    }

    /**
     * 本职业的事件是否通过了本局开局的掷骰。
     * <p>
     * 状态维度通用：未声明事件、本局未掷中、局末已清理、或职业当前处于禁用状态（含地图
     * {@code disabledRoles}、配置禁用与轮选）时都返回 false。禁用状态是每次查询时复查的，
     * 不只看掷骰那一刻。
     *
     * @return 本局该职业的专属事件是否启用
     */
    public boolean isEnabled() {
        return enabled && !SREDisableManager.isRoleDisabled(owner);
    }

    /**
     * 强制本职业的事件在下一局必定掷中（管理员命令用，见 {@code /sre:fake_steve next}）。
     * <p>
     * 请求写入后会一直保留到下一次开局掷骰时被消费——即使本局结束也不会丢失。注意请求只保证跳过
     * 概率判定，地图限制与禁用状态仍会生效；若那一局被这两项拦下，请求即被消费而不会顺延。
     *
     * @return 本次是否成功排队；本职业未声明事件或已有等待中的请求时返回 false
     */
    public boolean forceNextRound() {
        if (!hasChance() || forceRequested) {
            return false;
        }
        forceRequested = true;
        return true;
    }

    /**
     * 本局是否由「强制下一局」请求掷中，用于区分命令强开与自然掷中
     * （例如日志文案与 {@code ActivationSource}）。
     *
     * @return 本局该职业的事件是否由命令强制启用
     */
    public boolean wasForcedByCommand() {
        return forcedByCommand;
    }

    /**
     * 是否有「强制下一局」的请求在等待下一次开局。
     *
     * @return 是否已排队但尚未生效
     */
    public boolean isForcePending() {
        return forceRequested;
    }

    /**
     * 掷出本职业本局的启用状态（维度通用），并回调 {@link #handler}。
     * <p>
     * 由 {@link SRERole#rollAllEventEnableChances} 在每局正式开局时调用；调用方保证本职业声明过事件
     * （{@link #hasChance()}），这里仍做一次判空，使本方法自洽。
     *
     * @param level 判定地图限制用的服务端世界，同时作为回调入参（默认主世界）
     */
    void roll(ServerLevel level) {
        if (!hasChance()) {
            return;
        }
        boolean forced = forceRequested;
        forceRequested = false;

        boolean mapAllowed = owner.isEventMapAllowed(level);
        enabled = mapAllowed && !SREDisableManager.isRoleDisabled(owner)
                && (forced || RandomSelector.tryChance(
                        Math.max(0, Math.min(10000, chanceSupplier.getAsInt())), 10000));
        forcedByCommand = enabled && forced;
        if (handler != null) {
            handler.accept(level, enabled);
        }
    }

    /**
     * 清空本职业本局的启用状态，并回调 {@link #handler} 告知未启用。
     * <p>
     * 由 {@link SRERole#resetAllEventEnableStates} 在每局结束时调用。等待下一次开局的强制请求会保留
     * （见 {@link #forceRequested}）。
     *
     * @param level 作为回调入参的服务端世界（默认主世界）
     */
    void reset(ServerLevel level) {
        if (!hasChance()) {
            return;
        }
        enabled = false;
        forcedByCommand = false;
        if (handler != null) {
            handler.accept(level, false);
        }
    }
}
