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

import net.minecraft.network.chat.Component;

/**
 * 职业阵营（用于「按阵营」限制 / 筛选，例如修饰符的 {@code setCannotAppliedToTeam}）。
 *
 * <p>
 * 判定全部基于 {@link SRERole} 上的阵营标记。其中「中立」系阵营额外要求修饰前提
 * {@link SRERole#isNeutralTeamBase()}（既不是好人阵营、也没有杀手能力，且带有任意中立标记），
 * 「杀手」额外要求 {@code !isInnocent()}：
 * <ul>
 * <li>{@link #CIVILIAN} 平民：{@code isInnocent() && !isVigilanteTeam()}</li>
 * <li>{@link #SHERIFF} 警长：{@code isVigilanteTeam()}</li>
 * <li>{@link #NEUTRAL} 中立（泛）：任意中立都命中，包含下面全部中立细分</li>
 * <li>{@link #NEUTRAL_INNOCENT} 偏好中立（好人方中立）：随好人一同胜利的中立</li>
 * <li>{@link #NEUTRAL_KILLER} 杀手方中立：随杀手一同胜利的中立</li>
 * <li>{@link #NEUTRAL_SPECIAL} 特殊中立：显式打过 {@code setSpecialNeutral(true)} 标记的中立</li>
 * <li>{@link #NEUTRAL_EVENT} 事件中立：显式打过 {@code setEventNeutral(true)} 标记的中立</li>
 * <li>{@link #NEUTRAL_INDEPENDENT_WIN} 独立胜利中立：命中泛中立，但不属于偏好 / 杀手方 /
 * 事件 / 特殊中立的其余中立，由 {@link SRERole#isIndependentWinNeutral()} 自动归纳</li>
 * <li>{@link #KILLER} 杀手：拥有杀手能力</li>
 * </ul>
 *
 * <p>
 * 注意：只设置了 {@link SRERole#isNeutralForInnocent()}（而没有 {@code isNeutrals()}）的职业
 * 也会被 {@link #NEUTRAL} 与 {@link #NEUTRAL_INNOCENT} 命中；完全没有任何阵营标记的职业则不属于以上任一。
 * 各中立细分彼此**互斥**且判定有优先级（偏好 / 杀手方 → 事件 → 特殊 → 独立胜利），
 * 取「最具体的那一个」时应按此顺序判断。
 */
public enum RoleTeam {
    /** 平民：好人阵营且不属于警长阵营。 */
    CIVILIAN("display.type.role.innocent", 0xFF55FF55),
    /** 警长阵营。 */
    SHERIFF("display.type.role.vigilante", 0xFF55FFFF),
    /** 中立（泛）：任意中立，含偏好中立、杀手方中立、特殊中立、事件中立、独立胜利中立。 */
    NEUTRAL("display.type.role.neutral_all", 0xFFCCAA22),
    /** 偏好中立（好人方中立）：与好人一同胜利的中立。 */
    NEUTRAL_INNOCENT("display.type.role.neutral_innocent", 0xFF44BB66),
    /** 杀手方中立：与杀手一同胜利的中立。 */
    NEUTRAL_KILLER("display.type.role.neutral_for_killer", 0xFFFE55FE),
    /** 特殊中立：显式标记为特殊中立的中立职业。 */
    NEUTRAL_SPECIAL("display.type.role.neutral_special", 0xFFC8A882),
    /** 事件中立：显式标记为事件中立的中立职业（由局内随机事件决定是否登场）。 */
    NEUTRAL_EVENT("display.type.role.neutral_event", 0xFF555555),
    /** 独立胜利中立：不属于偏好 / 杀手方 / 事件 / 特殊中立的其余中立，自动归纳。 */
    NEUTRAL_INDEPENDENT_WIN("display.type.role.neutral_independent_win", 0xFFFFFF55),
    /** 杀手：拥有杀手能力。 */
    KILLER("display.type.role.killer", 0xFFFF5555);

    /** 该阵营的展示名翻译键（与阵营一对一）。 */
    private final String displayKey;
    /** 该阵营的展示色 ARGB（与阵营一对一）。 */
    private final int color;

    RoleTeam(String displayKey, int color) {
        this.displayKey = displayKey;
        this.color = color;
    }

    /** 该阵营的展示名翻译键。 */
    public String displayKey() {
        return displayKey;
    }

    /**
     * 该阵营的展示色（ARGB）。
     * 这是「阵营 → 颜色」的**唯一**权威来源，各处按阵营着色都应取这里，不要另建一套映射。
     */
    public int color() {
        return color;
    }

    /** 该阵营的展示名（已带阵营色）。 */
    public Component displayName() {
        return Component.translatable(displayKey).withStyle(s -> s.withColor(color));
    }

    /** 该职业是否属于此阵营。{@code role} 为 {@code null} 时返回 false。 */
    public boolean matches(SRERole role) {
        if (role == null) {
            return false;
        }
        return switch (this) {
            case CIVILIAN -> role.isInnocent() && !role.isVigilanteTeam();
            case SHERIFF -> role.isVigilanteTeam();
            case NEUTRAL -> role.isNeutralTeamBase();
            case NEUTRAL_INNOCENT -> role.isNeutralTeamBase() && role.isNeutralForInnocent();
            case NEUTRAL_KILLER -> role.isNeutralTeamBase() && role.isNeutralForKiller();
            case NEUTRAL_SPECIAL -> role.isNeutralTeamBase() && role.isSpecialNeutral();
            case NEUTRAL_EVENT -> role.isNeutralTeamBase() && role.isEventNeutral();
            case NEUTRAL_INDEPENDENT_WIN -> role.isIndependentWinNeutral();
            case KILLER -> !role.isInnocent() && role.canUseKiller();
        };
    }

    /**
     * 取该职业「最具体的」阵营：中立细分优先，其次警长 / 杀手 / 平民。
     * 都不匹配（完全没有任何阵营标记）时返回 {@code null}。
     * <p>
     * 这是所有「按阵营取色 / 取展示名」的**统一入口**，配合 {@link #color()} 与
     * {@link #displayName()} 使用，避免各处再手写 if-else 判断阵营。
     */
    public static RoleTeam of(SRERole role) {
        if (role == null) {
            return null;
        }
        RoleTeam sub = getNeutralSubTeam(role);
        if (sub != null) {
            return sub;
        }
        if (SHERIFF.matches(role)) {
            return SHERIFF;
        }
        if (KILLER.matches(role)) {
            return KILLER;
        }
        if (CIVILIAN.matches(role)) {
            return CIVILIAN;
        }
        return null;
    }

    /**
     * 该职业命中的「最具体的中立细分阵营」（偏好 / 杀手方 / 事件 / 特殊 / 独立胜利）。
     * 不属于任何中立时返回 {@code null}。用于需要区分中立细分的 UI 展示。
     */
    public static RoleTeam getNeutralSubTeam(SRERole role) {
        if (role == null) {
            return null;
        }
        if (NEUTRAL_INNOCENT.matches(role)) {
            return NEUTRAL_INNOCENT;
        }
        if (NEUTRAL_KILLER.matches(role)) {
            return NEUTRAL_KILLER;
        }
        if (NEUTRAL_EVENT.matches(role)) {
            return NEUTRAL_EVENT;
        }
        if (NEUTRAL_SPECIAL.matches(role)) {
            return NEUTRAL_SPECIAL;
        }
        if (NEUTRAL_INDEPENDENT_WIN.matches(role)) {
            return NEUTRAL_INDEPENDENT_WIN;
        }
        return null;
    }

    /**
     * 统一的「大阵营顺序 + 中立细分顺序」展示排序键，数值越小越靠前。
     * 所有按阵营展示职业列表的地方都应复用此方法，保证顺序一致：
     * 平民 → 警长 → 杀手 → 中立（偏好 → 杀手方 → 事件 → 特殊 → 独立胜利）→ 其它。
     * 注意：「中立」这个大阵营不会整体提前，只是在轮到展示中立那一段时内部再细排。
     */
    public static int factionDisplayOrder(SRERole role) {
        RoleTeam team = of(role);
        if (team == null) {
            return 400; // 未归类职业置于最后
        }
        return switch (team) {
            case CIVILIAN -> 0; // 平民
            case SHERIFF -> 1; // 警长
            case KILLER -> 2; // 杀手
            // 中立内部细分：偏好 → 杀手方 → 事件 → 特殊 → 独立胜利
            case NEUTRAL_INNOCENT -> 310;
            case NEUTRAL_KILLER -> 320;
            case NEUTRAL_EVENT -> 330;
            case NEUTRAL_SPECIAL -> 340;
            case NEUTRAL_INDEPENDENT_WIN -> 350;
            case NEUTRAL -> 360; // 其它未细分中立兜底
            default -> 400;
        };
    }
}
