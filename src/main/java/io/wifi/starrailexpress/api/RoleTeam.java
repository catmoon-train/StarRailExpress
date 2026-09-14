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

/**
 * 职业阵营（用于「按阵营」限制 / 筛选，例如修饰符的 {@code setCannotAppliedToTeam}）。
 *
 * <p>
 * 判定全部基于 {@link SRERole} 上的阵营标记。其中「中立」系阵营额外要求
 * {@code !isInnocent() && !canUseKiller()}（既不是好人阵营、也没有杀手能力），
 * 「杀手」额外要求 {@code !isInnocent()}：
 * <ul>
 * <li>{@link #CIVILIAN} 平民：{@code isInnocent() && !isVigilanteTeam()}</li>
 * <li>{@link #SHERIFF} 警长：{@code isVigilanteTeam()}</li>
 * <li>{@link #NEUTRAL} 中立：{@code !isInnocent() && !canUseKiller()} 且为任意中立（好人方中立 / 杀手方中立 / 特殊中立）</li>
 * <li>{@link #NEUTRAL_INNOCENT} 好人方中立：{@code !isInnocent() && !canUseKiller() && isNeutralForInnocent()}</li>
 * <li>{@link #NEUTRAL_KILLER} 杀手方中立：{@code !isInnocent() && !canUseKiller() && isNeutralForKiller()}</li>
 * <li>{@link #NEUTRAL_SPECIAL} 特殊中立：{@code !isInnocent() && !canUseKiller()} 且为除好人方中立、杀手方中立以外的中立</li>
 * <li>{@link #KILLER} 杀手：{@code !isInnocent() && canUseKiller()}</li>
 * </ul>
 *
 * <p>
 * 注意：只设置了 {@link SRERole#isNeutralForInnocent()}（而没有 {@code isNeutrals()}）的职业
 * 也会被 {@link #NEUTRAL} 与 {@link #NEUTRAL_INNOCENT} 命中；完全没有任何阵营标记的职业则不属于以上任一。
 */
public enum RoleTeam {
    /** 平民：好人阵营且不属于警长阵营。 */
    CIVILIAN,
    /** 警长阵营。 */
    SHERIFF,
    /** 中立（任意中立，含好人方中立、杀手方中立、特殊中立）。 */
    NEUTRAL,
    /** 好人方中立：与好人一同胜利的中立。 */
    NEUTRAL_INNOCENT,
    /** 杀手方中立：与杀手一同胜利的中立。 */
    NEUTRAL_KILLER,
    /** 特殊中立：除好人方中立、杀手方中立以外的中立。 */
    NEUTRAL_SPECIAL,
    /** 杀手：拥有杀手能力。 */
    KILLER;

    /** 该职业是否属于此阵营。{@code role} 为 {@code null} 时返回 false。 */
    public boolean matches(SRERole role) {
        if (role == null) {
            return false;
        }
        return switch (this) {
            case CIVILIAN -> role.isInnocent() && !role.isVigilanteTeam();
            case SHERIFF -> role.isVigilanteTeam();
            case NEUTRAL -> isNeutralBase(role)
                    && (role.isNeutrals() || role.isNeutralForInnocent() || role.isNeutralForKiller());
            case NEUTRAL_INNOCENT -> isNeutralBase(role) && role.isNeutralForInnocent();
            case NEUTRAL_KILLER -> isNeutralBase(role) && role.isNeutralForKiller();
            case NEUTRAL_SPECIAL -> isNeutralBase(role) && role.isNeutrals()
                    && !role.isNeutralForInnocent() && !role.isNeutralForKiller();
            case KILLER -> !role.isInnocent() && role.canUseKiller();
        };
    }

    /** 中立系阵营的公共前置条件：既不是好人阵营，也没有杀手能力。 */
    private static boolean isNeutralBase(SRERole role) {
        return !role.isInnocent() && !role.canUseKiller();
    }
}
