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

package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;

/**
 * 「本局待兑现的阵营卡」的客户端镜像（{@link BackpackManager#CARD_PART} 分区）。
 *
 * <p>
 * 只用来给界面显示「本局已生效 / 分不到会自动退卡」，不参与背包计数、不落库、也不影响判定：
 * 真正的裁决在 {@link FactionCardForcing}。字段用 {@link FactionCardType} 的名字存字符串，
 * 空串表示没有待生效的卡（缺字段的旧包同样按空处理）。
 */
public final class PendingCardState {
    /** 当场消耗的卡（{@link FactionCardType} 名）。 */
    public String card = "";
    /** 目标阵营（{@link FactionCardType} 名）。 */
    public String target = "";
    public long version;

    /** 没有待生效卡的快照。 */
    public static PendingCardState none(long version) {
        PendingCardState state = new PendingCardState();
        state.version = version;
        return state;
    }

    public static PendingCardState of(FactionCardType card, FactionCardType target, long version) {
        PendingCardState state = new PendingCardState();
        state.card = card == null ? "" : card.name();
        state.target = target == null ? "" : target.name();
        state.version = version;
        return state;
    }

    public FactionCardType cardType() {
        return FactionCardType.fromString(card);
    }

    public FactionCardType targetType() {
        return FactionCardType.fromString(target);
    }

    /** 本局是否有一张已使用、还没出结果的卡。 */
    public boolean active() {
        return cardType() != FactionCardType.NONE && targetType().isSelectableTarget();
    }
}
