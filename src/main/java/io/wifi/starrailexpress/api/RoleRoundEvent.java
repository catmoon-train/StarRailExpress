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
 * 每个职业实例通过 {@link SRERole#setEventEnableChance} 声明自己的事件，声明过的职业会被
 * {@link TMMRoles} 收录进事件职业列表（职业被注销时自动移出）。之后：
 * <ul>
 *   <li>每局正式开局（{@code OnGameTrueStarted}）由 {@link SRERole#rollAllEventEnableChances} 统一掷骰；</li>
 *   <li>局末（{@code OnGameEnd}）由 {@link SRERole#resetAllEventEnableStates} 清理本局状态。</li>
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
 * <h2>两个回调 / Two callbacks</h2>
 * 声明时一次给出两个互不干扰的回调：
 * <ul>
 *   <li><strong>掷骰回调</strong>：每局开局掷骰后调用一次。想连「没掷中」一起处理（例如启用失败时
 *       复位、公告），用带 {@code boolean} 的 {@link BiConsumer}，未启用时会收到 {@code false}；
 *       只关心掷中的话用 {@link Consumer}，它只在本局启用时调用。</li>
 *   <li><strong>局末回调</strong>：{@link #reset} 时调用，<strong>只在本局掷中过时才调用</strong>，
 *       用于收尾（开场与收尾一一对应）。它<strong>先于状态清空</strong>执行，所以回调里
 *       {@link #isEnabled()} 仍然反映本局结果。</li>
 * </ul>
 * 两者可以一起给，也可以只给一个；只挂局末回调（或配合纯查询式声明）时用
 * {@link #setRoundEndHandler(Consumer)} 单独注册即可。
 * 不需要回调、按需查询的写法见 {@link #setChance(IntSupplier)} 与 {@link #isEnabled()}。
 *
 * <h2>状态与跨局语义</h2>
 * {@link #enabled} 与 {@link #forcedByCommand} 每局开局重算、局末清零；{@link #forceRequested} 由
 * {@link #forceNextRound()} 写入后跨局保留，直到下一次开局掷骰时被消费——即使本局结束也不会丢失。
 * 注意请求只保证跳过概率判定，仍要经过地图限制与禁用状态检查；若那一局被这两项拦下，
 * 请求即被消费而不会顺延到再下一局。
 *
 * @see SRERole#setEventEnableChance(BiConsumer, Consumer, int)
 */
public final class RoleRoundEvent {

    private final SRERole owner;
    /** 本局是否掷中，即 {@link #isEnabled()} 的结论。 */
    private boolean enabled;
    /** 本局是否由「强制下一局」请求掷中，用于区分命令强开与自然掷中。 */
    private boolean forcedByCommand;
    /** 是否有等待下一次开局生效的强制请求；唯一跨局的标志，局末清理不会动它。 */
    private boolean forceRequested;
    /**
     * 掷骰回调（带结果）：每次掷骰都调用，未启用时收到 false。
     * <p>
     * 与 {@link #enabledHandler} 互斥——两个都声明时以最后声明的那个为准。
     */
    private BiConsumer<ServerLevel, Boolean> resultHandler;
    /** 掷骰回调（仅启用）：只在本局掷中时调用。 */
    private Consumer<ServerLevel> enabledHandler;
    /** 局末回调：只在本局掷中过时调用，且先于状态清空执行。 */
    private Consumer<ServerLevel> roundEndHandler;
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
     * 声明事件：掷骰回调（带结果）+ 局末回调 + 固定概率。
     *
     * @param resultHandler   掷骰回调，每次掷骰都调用，未启用时收到 {@code (level, false)}；
     *                        不需要时可传 null（此时应改用 {@link Consumer} 重载）
     * @param roundEndHandler 局末回调，只在本局掷中过时调用；可为 null
     * @param chance          万分比概率，例如 6000 表示 60%；超出 0–10000 会被裁剪
     * @return this，便于链式调用
     */
    public RoleRoundEvent setChance(BiConsumer<ServerLevel, Boolean> resultHandler,
            Consumer<ServerLevel> roundEndHandler, int chance) {
        return setChance(resultHandler, roundEndHandler, () -> chance);
    }

    /**
     * 同上，但概率在每次掷骰时动态读取，适合直接绑定配置项。
     *
     * @param resultHandler   掷骰回调（带结果），可为 null
     * @param roundEndHandler 局末回调（只在本局掷中过时调用），可为 null
     * @param chanceSupplier  万分比概率供应器；读取值超出 0–10000 会被裁剪
     * @return this，便于链式调用
     * @throws NullPointerException {@code chanceSupplier} 为 null 时抛出
     */
    public RoleRoundEvent setChance(BiConsumer<ServerLevel, Boolean> resultHandler,
            Consumer<ServerLevel> roundEndHandler, IntSupplier chanceSupplier) {
        return apply(resultHandler, null, roundEndHandler, chanceSupplier);
    }

    /**
     * 声明事件：掷骰回调（仅启用）+ 局末回调 + 固定概率。
     *
     * @param enabledHandler  掷骰回调，只在本局掷中时调用；为 null 表示不注册掷骰回调
     *                        （字面量 null 需显式转型，只挂局末回调更推荐 {@link #setRoundEndHandler(Consumer)}）
     * @param roundEndHandler 局末回调，只在本局掷中过时调用；可为 null
     * @param chance          万分比概率，超出 0–10000 会被裁剪
     * @return this，便于链式调用
     */
    public RoleRoundEvent setChance(Consumer<ServerLevel> enabledHandler,
            Consumer<ServerLevel> roundEndHandler, int chance) {
        return setChance(enabledHandler, roundEndHandler, () -> chance);
    }

    /**
     * 同上，但概率在每次掷骰时动态读取。
     *
     * @param enabledHandler  掷骰回调（仅启用），可为 null
     * @param roundEndHandler 局末回调（只在本局掷中过时调用），可为 null
     * @param chanceSupplier  万分比概率供应器；读取值超出 0–10000 会被裁剪
     * @return this，便于链式调用
     * @throws NullPointerException {@code chanceSupplier} 为 null 时抛出
     */
    public RoleRoundEvent setChance(Consumer<ServerLevel> enabledHandler,
            Consumer<ServerLevel> roundEndHandler, IntSupplier chanceSupplier) {
        return apply(null, enabledHandler, roundEndHandler, chanceSupplier);
    }

    /**
     * 声明事件：只要掷骰回调（带结果），不要局末回调。
     *
     * @param resultHandler 掷骰回调，每次掷骰都调用，可为 null
     * @param chance        万分比概率，超出 0–10000 会被裁剪
     * @return this，便于链式调用
     */
    public RoleRoundEvent setChance(BiConsumer<ServerLevel, Boolean> resultHandler, int chance) {
        return setChance(resultHandler, () -> chance);
    }

    /**
     * 同上，但概率在每次掷骰时动态读取。
     *
     * @param resultHandler  掷骰回调（带结果），可为 null
     * @param chanceSupplier 万分比概率供应器
     * @return this，便于链式调用
     * @throws NullPointerException {@code chanceSupplier} 为 null 时抛出
     */
    public RoleRoundEvent setChance(BiConsumer<ServerLevel, Boolean> resultHandler,
            IntSupplier chanceSupplier) {
        return apply(resultHandler, null, null, chanceSupplier);
    }

    /**
     * 声明事件：只要掷骰回调（仅启用），不要局末回调。
     *
     * @param enabledHandler 掷骰回调，只在本局掷中时调用，可为 null
     * @param chance         万分比概率，超出 0–10000 会被裁剪
     * @return this，便于链式调用
     */
    public RoleRoundEvent setChance(Consumer<ServerLevel> enabledHandler, int chance) {
        return setChance(enabledHandler, () -> chance);
    }

    /**
     * 同上，但概率在每次掷骰时动态读取。
     *
     * @param enabledHandler 掷骰回调（仅启用），可为 null
     * @param chanceSupplier 万分比概率供应器
     * @return this，便于链式调用
     * @throws NullPointerException {@code chanceSupplier} 为 null 时抛出
     */
    public RoleRoundEvent setChance(Consumer<ServerLevel> enabledHandler,
            IntSupplier chanceSupplier) {
        return apply(null, enabledHandler, null, chanceSupplier);
    }

    /**
     * 声明事件但不要任何回调：本局是否启用通过 {@link #isEnabled()} 按需查询，
     * 概率每次掷骰动态读取，适合直接绑定配置项。
     *
     * @param chanceSupplier 万分比概率供应器；读取值超出 0–10000 会被裁剪
     * @return this，便于链式调用
     * @throws NullPointerException {@code chanceSupplier} 为 null 时抛出
     */
    public RoleRoundEvent setChance(IntSupplier chanceSupplier) {
        return apply(null, null, null, chanceSupplier);
    }

    /**
     * 单独注册局末回调，不改变已声明的掷骰回调与概率。
     * <p>
     * 与 {@code setChance(..., roundEndHandler, ...)} 等价，只是把收尾拆出来单独挂：
     * 可配合任意声明形式使用（包括 {@code setChance(6000)} / {@code setChance(IntSupplier)}
     * 这种纯查询式），先挂还是后挂都行——已注册的局末回调不会被后续的 {@code setChance} 覆盖。
     *
     * @param roundEndHandler 局末回调，只在本局掷中过时调用，且先于状态清空执行；不能为 null
     * @return this，便于链式调用
     * @throws NullPointerException {@code roundEndHandler} 为 null 时抛出
     */
    public RoleRoundEvent setRoundEndHandler(Consumer<ServerLevel> roundEndHandler) {
        this.roundEndHandler = Objects.requireNonNull(roundEndHandler, "roundEndHandler");
        return this;
    }

    /**
     * 所有 {@code setChance} 重载的公共落点：写入回调与概率，并让职业进入事件职业列表。
     *
     * @param resultHandler   掷骰回调（带结果），与 {@code enabledHandler} 互斥
     * @param enabledHandler  掷骰回调（仅启用），与 {@code resultHandler} 互斥
     * @param roundEndHandler 局末回调；为 null 表示不改动已挂的那个（见 {@link #setRoundEndHandler}）
     * @param chanceSupplier  概率供应器，不能为 null
     * @return this，便于链式调用
     */
    private RoleRoundEvent apply(BiConsumer<ServerLevel, Boolean> resultHandler,
            Consumer<ServerLevel> enabledHandler, Consumer<ServerLevel> roundEndHandler,
            IntSupplier chanceSupplier) {
        this.resultHandler = resultHandler;
        this.enabledHandler = enabledHandler;
        if (roundEndHandler != null) {
            this.roundEndHandler = roundEndHandler;
        }
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
     * 状态维度通用：未声明事件、本局未掷中、局末清理后、或职业当前处于禁用状态（含地图
     * {@code disabledRoles}、配置禁用与轮选）时都返回 false。禁用状态是每次查询时复查的，
     * 不只看掷骰那一刻。局末回调执行期间仍返回本局结果（见 {@link #reset(ServerLevel)}）。
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
     * 掷出本职业本局的启用状态（维度通用），并按声明方式回调。
     * <p>
     * 由 {@link SRERole#rollAllEventEnableChances} 在每局正式开局时调用；调用方保证本职业声明过事件
     * （{@link #hasChance()}），这里仍做一次判空，使本方法自洽。回调能看到刚写入的本局状态。
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
        if (resultHandler != null) {
            // 带结果的回调：没掷中也通知一次，便于处理「启用失败」
            resultHandler.accept(level, enabled);
        } else if (enabled && enabledHandler != null) {
            enabledHandler.accept(level);
        }
    }

    /**
     * 收尾本局：先回调局末处理，再清空本局状态。
     * <p>
     * 由 {@link SRERole#resetAllEventEnableStates} 在每局结束时调用。局末回调只在
     * <strong>本局掷中过</strong>时调用，且此刻 {@link #enabled} 仍为 true——回调里可以用
     * {@link #isEnabled()} 判断「本局事件跑过」，做对应的收尾；回调返回后才清空状态。
     * 等待下一次开局的强制请求会保留（见 {@link #forceRequested}）。
     *
     * @param level 作为回调入参的服务端世界（默认主世界）
     */
    void reset(ServerLevel level) {
        if (!hasChance()) {
            return;
        }
        if (enabled && roundEndHandler != null) {
            roundEndHandler.accept(level);
        }
        enabled = false;
        forcedByCommand = false;
    }
}
