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
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.player.Player;

/**
 * 职业阵营 —— <b>全项目唯一权威的阵营来源</b>。
 *
 * <p>
 * 所有「阵营 / 团队」判定都必须走这里，不要在别处再手写 if-else。
 *
 * <h2>三层概念</h2>
 * <ol>
 * <li><b>阵营（camp）</b>：{@link #matches(SRERole)}，职业<b>真实</b>属于哪个阵营，
 * 用于展示、筛选、胜利结算。</li>
 * <li><b>团队（team）</b>：{@link #isKillerTeam(SRERole)} / {@link #isInnocentTeam(SRERole)}，
 * 把若干阵营合并成一个「大团队」，并按 {@link SRERole#setHiddenTeam(RoleTeam.Team)} 生效
 * （隐藏身份优先于真实阵营）。</li>
 * <li><b>判定层 vs 真实层</b>：判定层（{@code isXxxTeam}）含隐藏身份，用于频道、报幕、透视、同伙可见性等；
 * 真实层（{@code isXxxTeamReal}）忽略隐藏身份，用于胜利结算、存活统计、小脑（误杀）判定。</li>
 * </ol>
 *
 * <h2>团队构成（{@link Team}）</h2>
 * <ul>
 * <li><b>好人团队</b>（{@link Team#INNOCENT}）：{@link #CIVILIAN}（乘客）+ {@link #SHERIFF}（警长）
 * + {@link #NEUTRAL_INNOCENT}（好人方中立）+ 隐藏身份为好人团队的职业。</li>
 * <li><b>杀手团队</b>（{@link Team#KILLER}）：{@link #KILLER}（杀手）+ {@link #NEUTRAL_KILLER}（杀手方中立）
 * + 隐藏身份为杀手团队的职业（如魔术师）。</li>
 * <li><b>中立团队</b>（{@link Team#NEUTRAL}）：{@link #NEUTRAL} 及全部中立细分
 * + 隐藏身份为中立团队的职业；隐藏为中立团队时，中立细分（偏好 / 杀手方 / 特殊 / 事件 / 独立胜利）
 * 仍按真实阵营细分判定。</li>
 * </ul>
 * <p>隐藏身份（{@link SRERole#setHiddenTeam(Team)}）只能伪装到「团队」这一层，
 * 不能指定团队内部的具体阵营。
 *
 * <h2>阵营细则</h2>
 * <ul>
 * <li>{@link #CIVILIAN} 平民：<b>好人阵营</b>（{@code isInnocent()}）、<b>非中立阵营</b>、
 * <b>非杀手阵营</b>且非警长；</li>
 * <li>{@link #SHERIFF} 警长：{@code isVigilanteTeam()}</li>
 * <li>{@link #NEUTRAL} 中立（泛）：任意中立都命中，包含下面全部中立细分</li>
 * <li>{@link #NEUTRAL_INNOCENT} 偏好中立（好人方中立）：随好人一同胜利的中立</li>
 * <li>{@link #NEUTRAL_KILLER} 杀手方中立：随杀手一同胜利的中立</li>
 * <li>{@link #NEUTRAL_SPECIAL} 特殊中立：显式打过 {@code setSpecialNeutral(true)} 标记的中立</li>
 * <li>{@link #NEUTRAL_EVENT} 事件中立：显式打过 {@code setEventNeutral(true)} 标记的中立</li>
 * <li>{@link #NEUTRAL_INDEPENDENT_WIN} 独立胜利中立：命中泛中立，但不属于偏好 / 杀手方 /
 * 事件 / 特殊中立的其余中立，由 {@link SRERole#isIndependentWinNeutral()} 自动归纳</li>
 * <li>{@link #KILLER} 杀手：<b>非好人阵营</b>（{@code !isInnocent()}）、<b>非中立阵营</b>
 * 且拥有杀手能力</li>
 * </ul>
 *
 * <p>
 * 注意：中立的判定前提是 {@link SRERole#isNeutralTeamBase()}（既不是好人阵营、也没有杀手能力，
 * 且带有任意中立标记），所以拥有杀手能力的中立会被归入 {@link #KILLER} 而不是中立。
 * 只设置了 {@link SRERole#isNeutralForInnocent()}（而没有 {@code isNeutrals()}）的职业
 * 也会被 {@link #NEUTRAL} 与 {@link #NEUTRAL_INNOCENT} 命中。
 * 各中立细分彼此**互斥**且判定有优先级（偏好 / 杀手方 → 事件 → 特殊 → 独立胜利）。
 */
public enum RoleTeam {
    /** 平民：好人阵营、非中立阵营、非杀手阵营且不属于警长阵营。 */
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
    /** 杀手：非好人阵营、非中立阵营且拥有杀手能力。 */
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

    // ───────────────────────── 阵营 → 团队 ─────────────────────────

    /**
     * 大团队。<b>唯一</b>用于 {@link SRERole#setHiddenTeam(RoleTeam.Team)} 的入参 ——
     * 隐藏身份只伪装到「团队」这一层，不区分团队内部的具体阵营（平民 / 警长 / 各中立细分）。
     */
    public enum Team {
        /** 好人团队：乘客 + 警长 + 好人方中立。 */
        INNOCENT,
        /** 杀手团队：杀手 + 杀手方中立。 */
        KILLER,
        /** 中立团队：全部中立细分。 */
        NEUTRAL;

        /** 该团队是否包含 {@code camp} 阵营。 */
        public boolean contains(RoleTeam camp) {
            return camp != null && camp.team() == this;
        }

        /** 阵营 → 团队；{@code camp} 为 {@code null} 时返回 {@code null}。 */
        public static RoleTeam.Team of(RoleTeam camp) {
            return camp == null ? null : camp.team();
        }

        /** 真实团队（不读隐藏身份）；未归类职业兜底为 {@link #NEUTRAL}。 */
        public static RoleTeam.Team actualOf(SRERole role) {
            RoleTeam camp = RoleTeam.of(role);
            return camp == null ? NEUTRAL : camp.team();
        }

        /** 判定团队（隐藏身份优先）；未设置隐藏身份时与 {@link #actualOf(SRERole)} 相同。 */
        public static RoleTeam.Team of(SRERole role) {
            if (role == null) {
                return NEUTRAL;
            }
            RoleTeam.Team hidden = role.getHiddenTeam();
            return hidden != null ? hidden : actualOf(role);
        }
    }

    /** 该阵营所属团队。 */
    public Team team() {
        return switch (this) {
            case CIVILIAN, SHERIFF, NEUTRAL_INNOCENT -> Team.INNOCENT;
            case KILLER, NEUTRAL_KILLER -> Team.KILLER;
            case NEUTRAL, NEUTRAL_SPECIAL, NEUTRAL_EVENT, NEUTRAL_INDEPENDENT_WIN -> Team.NEUTRAL;
        };
    }

    /**
     * 该阵营是否是「团队代表阵营」（{@link #CIVILIAN} / {@link #KILLER} / {@link #NEUTRAL}）。
     * <p>隐藏身份只到团队层，因此团队一致时代表阵营直接命中；团队内部细分（警长 / 各中立细分）
     * 仍按真实阵营判定，见 {@link #matchesJudged(SRERole)}。
     */
    public boolean isTeamRepresentative() {
        return this == CIVILIAN || this == KILLER || this == NEUTRAL;
    }

    /** 该阵营是否属于「杀手团队」：{@link #KILLER} 与 {@link #NEUTRAL_KILLER}。 */
    public boolean isKillerTeam() {
        return team() == Team.KILLER;
    }

    /** 该阵营是否属于「好人团队」：{@link #CIVILIAN}、{@link #SHERIFF} 与 {@link #NEUTRAL_INNOCENT}。 */
    public boolean isInnocentTeam() {
        return team() == Team.INNOCENT;
    }

    /** 该阵营是否属于「中立团队」（含全部中立细分）。 */
    public boolean isNeutralTeam() {
        return team() == Team.NEUTRAL;
    }

    // ───────────────────────── 阵营判定 ─────────────────────────

    /**
     * 该职业是否属于此阵营（<b>真实阵营</b>，忽略隐藏身份）。
     * {@code role} 为 {@code null} 时返回 false。
     */
    public boolean matches(SRERole role) {
        if (role == null) {
            return false;
        }
        return switch (this) {
            case CIVILIAN -> role.isInnocent() && !role.isNeutralTeamBase() && !KILLER.matches(role)
                    && !role.isVigilanteTeam();
            case SHERIFF -> role.isVigilanteTeam();
            case NEUTRAL -> role.isNeutralTeamBase();
            case NEUTRAL_INNOCENT -> role.isNeutralTeamBase() && role.isNeutralForInnocent();
            case NEUTRAL_KILLER -> role.isNeutralTeamBase() && role.isNeutralForKiller();
            case NEUTRAL_SPECIAL -> role.isNeutralTeamBase() && role.isSpecialNeutral();
            case NEUTRAL_EVENT -> role.isNeutralTeamBase() && role.isEventNeutral();
            case NEUTRAL_INDEPENDENT_WIN -> role.isIndependentWinNeutral();
            case KILLER -> !role.isInnocent() && !role.isNeutralTeamBase() && role.canUseKiller();
        };
    }

    /**
     * 该职业是否属于此阵营（<b>判定层</b>，隐藏身份优先）。
     * <ul>
     * <li>未设置隐藏身份：等价于真实阵营 {@link #matches(SRERole)}；</li>
     * <li>隐藏身份的团队与 {@link #team()} 不同：返回 false（伪装成别的团队后不再按本阵营算）；</li>
     * <li>团队相同：{@link #isTeamRepresentative()} 的代表阵营直接命中；
     * 团队内部细分（警长 / 各中立细分）仍按真实阵营 {@link #matches(SRERole)} 判定。</li>
     * </ul>
     */
    public boolean matchesJudged(SRERole role) {
        if (role == null) {
            return false;
        }
        RoleTeam.Team hidden = role.getHiddenTeam();
        if (hidden == null) {
            return this.matches(role);
        }
        if (hidden != this.team()) {
            return false;
        }
        return this.isTeamRepresentative() || this.matches(role);
    }

    /** 玩家维度的实际阵营判断；职业池筛选仍使用 {@link #matches(SRERole)}。 */
    public boolean matchesPlayer(Player player) {
        return player != null && matches(SREGameWorldComponent.getInstance(player).getAllegianceRole(player));
    }

    /** 取得玩家实际阵营，不改变复制职业本身的静态阵营。 */
    public static RoleTeam ofPlayer(Player player) {
        return player == null ? null : of(SREGameWorldComponent.getInstance(player).getAllegianceRole(player));
    }

    // ───────────────────────── 团队判定（权威入口） ─────────────────────────

    /** 真正的杀手（{@link #KILLER} 阵营，<b>不含</b>隐藏身份为杀手阵营的职业）。 */
    public static boolean isKiller(SRERole role) {
        return KILLER.matches(role);
    }

    /** 警长阵营。 */
    public static boolean isSheriff(SRERole role) {
        return SHERIFF.matches(role);
    }

    /**
     * <b>好人阵营本体</b>（等价于 {@code role.isInnocent()}）：乘客 + 警长，
     * 不含好人方中立，也不看隐藏身份。
     * <p>需要「好人团队」（含好人方中立、含隐藏身份）请用 {@link #isInnocentTeam(SRERole)}。
     */
    public static boolean isInnocentCamp(SRERole role) {
        return role != null && (CIVILIAN.matches(role) || SHERIFF.matches(role));
    }

    /**
     * <b>好人阵营（判定层）</b>：隐藏身份属于好人团队（{@link #isInnocentTeam()}）时按好人阵营对待，
     * 否则回退到真实好人阵营 {@link #isInnocentCamp(SRERole)}。
     * <p>用于「这个职业算不算好人」的判定（修饰符适用、播报、目标筛选等）；
     * 结算 / 数值仍请用 {@link #isInnocentCamp(SRERole)} 或 {@code role.isInnocent()}。
     */
    public static boolean isInnocentCampJudged(SRERole role) {
        if (role == null) {
            return false;
        }
        RoleTeam.Team hidden = role.getHiddenTeam();
        if (hidden != null) {
            return hidden == RoleTeam.Team.INNOCENT;
        }
        return isInnocentCamp(role);
    }

    /** <b>中立团队（判定层）</b>：隐藏身份为中立团队时命中，否则回退真实中立判定。 */
    public static boolean isNeutralTeam(SRERole role) {
        return NEUTRAL.matchesJudged(role);
    }

    /** 中立大类（任意中立细分，判定层：隐藏为中立团队也算）。 */
    public static boolean isNeutral(SRERole role) {
        return NEUTRAL.matchesJudged(role);
    }

    /** 杀手方中立（随杀手一同胜利的中立，判定层：隐藏为中立团队时按真实阵营细分）。 */
    public static boolean isNeutralForKiller(SRERole role) {
        return NEUTRAL_KILLER.matchesJudged(role);
    }

    /** 好人方中立（随好人一同胜利的中立，判定层：隐藏为中立团队时按真实阵营细分）。 */
    public static boolean isNeutralForInnocent(SRERole role) {
        return NEUTRAL_INNOCENT.matchesJudged(role);
    }

    /**
     * <b>杀手团队（判定层）</b>：杀手阵营 + 杀手方中立 + 隐藏身份为杀手团队（含杀手阵营）的职业。
     * <p>
     * 用于频道、死亡报幕、透视 / 本能、同伙可见性等「被当作杀手对待」的判定。
     * 胜利结算请用 {@link #isKillerTeamReal(SRERole)}。
     */
    public static boolean isKillerTeam(SRERole role) {
        if (role == null) {
            return false;
        }
        RoleTeam.Team hidden = role.getHiddenTeam();
        if (hidden != null) {
            // 隐藏身份优先：隐藏成杀手团队 → 算杀手团队；隐藏成别的团队 → 不再算杀手团队。
            return hidden == RoleTeam.Team.KILLER;
        }
        return isKillerTeamReal(role);
    }

    /**
     * <b>杀手团队（真实层）</b>：拥有杀手能力的职业（含「复制了好人能力、但本体仍是杀手」的克隆）
     * + 杀手方中立，<b>忽略</b>隐藏身份。
     * <p>
     * 用于胜利结算、存活人数统计与小脑（误杀）等必须按真实阵营结算的地方。
     */
    public static boolean isKillerTeamReal(SRERole role) {
        if (role == null) {
            return false;
        }
        return role.canUseKiller() || NEUTRAL_KILLER.matches(role);
    }

    /**
     * <b>好人团队（判定层）</b>：乘客阵营 + 警长阵营 + 好人方中立
     * + 隐藏身份为好人团队（含乘客 / 警长 / 好人方中立）的职业。
     */
    public static boolean isInnocentTeam(SRERole role) {
        if (role == null) {
            return false;
        }
        RoleTeam.Team hidden = role.getHiddenTeam();
        if (hidden != null) {
            return hidden == RoleTeam.Team.INNOCENT;
        }
        return isInnocentTeamReal(role);
    }

    /**
     * <b>好人团队（真实层）</b>：乘客阵营 + 警长阵营 + 好人方中立，<b>忽略</b>隐藏身份。
     */
    public static boolean isInnocentTeamReal(SRERole role) {
        if (role == null) {
            return false;
        }
        return CIVILIAN.matches(role) || SHERIFF.matches(role) || NEUTRAL_INNOCENT.matches(role);
    }

    /**
     * 取该职业「最具体的」阵营：中立细分优先，其次警长 / 杀手 / 平民。
     * 这是<b>真实阵营</b>（不读隐藏身份），用于展示与筛选；
     * 团队判定请用 {@link #isKillerTeam(SRERole)} / {@link #isInnocentTeam(SRERole)}。
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
