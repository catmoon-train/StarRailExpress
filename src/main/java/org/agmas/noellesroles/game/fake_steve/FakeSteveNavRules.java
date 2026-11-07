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

package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 地图配置对寻路提出的硬约束：跳跃、游泳、落差。
 *
 * <h2>为什么需要单独一层</h2>
 * 这几个开关原本分散在「客户端按键抑制」「环境致死检测」「摔落致死 mixin」三处，
 * 寻路完全不知道它们的存在，于是必然产出三类错误路线：
 * <ul>
 * <li><b>禁跳地图</b>（{@code canJump = false}）：A* 仍按玩家跳跃上限 1.25 格放行，
 * 给出「跳上一整格高台」的路线，身体只会对着方块原地跳；</li>
 * <li><b>禁游地图</b>：A* 把水面当成「代价低、又不受落差限制」的捷径，身体一头扎进水里
 * 被判 {@code DeathReasons.CANNOT_SWIM}；</li>
 * <li><b>设置了 {@code fallToDeathHeight}</b>：A* 仍按固定 3 格落差放行，身体直接摔死。</li>
 * </ul>
 *
 * <p>
 * 收敛成不可变记录之后，{@link FakeSteveNavigator} 的搜索内核只认「这一步到底能不能走」，
 * 而这层映射本身是纯函数（{@link #of(boolean, AreasSettings, Integer)}），可以被单元测试直接断言。
 *
 * <p>
 * <b>边界</b>：本类只回答「AI 允不允许这么做」，不重复实现致死判定。
 * 真正的死亡仍然由 {@code SREGameWorldComponent#playerEnvironmentDetection} 与
 * {@code FallDamageCheckMixin} 负责，本类只保证 AI 不会主动把自己送进去。
 *
 * @param canJump         是否允许跳跃输入，等价 {@code SREGameWorldComponent#isJumpAvailable()}
 * @param canSwim         是否允许下水，四项游泳配置<b>全部</b>开启才算允许
 * @param maxFallDistance 单步允许的最大下落高度（格），由 {@code fallToDeathHeight} 折算
 */
record FakeSteveNavRules(boolean canJump, boolean canSwim, double maxFallDistance) {

    /** 地图没有配置摔落致死高度时的兜底落差，与原 {@link FakeSteveNavigator#MAX_FALL_DISTANCE} 保持一致。 */
    static final double DEFAULT_MAX_FALL_DISTANCE = 3.0D;

    /**
     * 距致死高度保留的安全余量。
     *
     * <p>
     * {@code FallDamageCheckMixin} 的判据是 {@code fallDistance >= fallToDeathHeight}，
     * 而 A* 里的「落差」只是相邻两格站立高度的差，两者并不完全等价
     * （贴墙下滑、被推动等都会额外累计 {@code fallDistance}）。
     * 留半格余量，保证「严格按路线走」不会踩到阈值。
     */
    static final double FALL_SAFETY_MARGIN = 0.5D;

    /** 没有任何限制：网格单元测试、以及读不到世界信息时的默认值。 */
    static final FakeSteveNavRules PERMISSIVE =
            new FakeSteveNavRules(true, true, DEFAULT_MAX_FALL_DISTANCE);

    /** 什么都不能做：拿不到地图配置时的保守值，宁可原地不动也不要主动送死。 */
    static final FakeSteveNavRules RESTRICTIVE =
            new FakeSteveNavRules(false, false, DEFAULT_MAX_FALL_DISTANCE);

    FakeSteveNavRules {
        maxFallDistance = Math.max(0.0D, maxFallDistance);
    }

    /** 读当前世界的地图配置（不叠加职业覆盖）。 */
    static FakeSteveNavRules of(ServerLevel level) {
        return of(level, null);
    }

    /**
     * 读当前世界的地图配置，并叠加职业对「摔落致死高度」的覆盖。
     *
     * <p>
     * {@code FallDamageCheckMixin} 里职业覆盖优先于地图设置，寻路必须用同一个值，
     * 否则会给出一条「地图允许、但该职业会摔死」的路线。
     *
     * @param body 被夺舍的身体；为 {@code null} 时只看地图配置
     */
    static FakeSteveNavRules of(ServerLevel level, ServerPlayer body) {
        if (level == null) {
            return PERMISSIVE;
        }
        boolean jumps;
        try {
            jumps = SREGameWorldComponent.KEY.get(level).isJumpAvailable();
        } catch (RuntimeException unregistered) {
            jumps = false;
        }
        return of(jumps, areas(level), fallOverride(body));
    }

    /**
     * 纯映射：地图配置 → 寻路约束。不读世界，可被单元测试直接断言。
     *
     * @param jumpAvailable 跳跃输入是否被放行（取实际生效值，而不是 {@code canJump} 字段本身，
     *                      因为 {@code CommandBlockDirector} 会临时关闭跳跃）
     * @param areas         地图配置；{@code null} 表示拿不到，按保守值处理
     * @param fallOverride  职业覆盖的摔落致死高度；{@code null} 表示使用地图设置
     */
    static FakeSteveNavRules of(boolean jumpAvailable, AreasSettings areas, Integer fallOverride) {
        if (areas == null) {
            return new FakeSteveNavRules(jumpAvailable, false, DEFAULT_MAX_FALL_DISTANCE);
        }
        // 四项必须全部开启：任一关闭都会让落水变成致死事件
        // （canSwim 控制水中上浮，另外三项分别对应眼睛入水 / 深水 / 三格水体的致死判定）。
        boolean swim = areas.canSwim && areas.canSimpleSwim
                && areas.canUnderWater && areas.allowInDeepWater;
        return new FakeSteveNavRules(jumpAvailable, swim,
                maxFallDistance(areas.fallToDeathHeight, fallOverride));
    }

    /**
     * 把 {@code fallToDeathHeight}（0 表示不启用）折算成「单步允许落差」。
     *
     * <p>
     * 下落 {@code h} 格时 {@code fallDistance ≈ h}，所以单步落差必须严格小于阈值；
     * 扣掉 {@link #FALL_SAFETY_MARGIN} 后向下取整，避免「刚好踩线」。
     * 例如阈值为 1 时不允许任何整格下落，为 2 时允许 1 格，为 3 时允许 2 格。
     */
    static double maxFallDistance(int mapHeight, Integer fallOverride) {
        int height = fallOverride != null ? fallOverride : mapHeight;
        if (height <= 0) {
            return DEFAULT_MAX_FALL_DISTANCE;
        }
        return Math.max(0.0D, Math.floor(height - FALL_SAFETY_MARGIN));
    }

    /** 地图禁止游泳：水面既不能作为路线，也不能作为落点。 */
    boolean swimmingBanned() {
        return !canSwim;
    }

    private static AreasSettings areas(ServerLevel level) {
        try {
            return AreasWorldComponent.KEY.get(level).areasSettings;
        } catch (RuntimeException unregistered) {
            return null;
        }
    }

    private static Integer fallOverride(ServerPlayer body) {
        if (body == null) {
            return null;
        }
        try {
            SRERole role = RoleUtils.getPlayerRole(body);
            return role == null ? null : role.getFallToDeathHeightOverride();
        } catch (RuntimeException unregistered) {
            return null;
        }
    }
}
