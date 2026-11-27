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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阵营卡「使用 → 兑现 / 退款」的统一规则（{@code docs/faction-card-unified.md}）。
 *
 * <p>
 * 只测不碰 Minecraft 运行时的部分：退款退哪张卡、算不算兑现、登记与加成、
 * 以及推给客户端的 {@link PendingCardState} 线上格式。
 */
class FactionCardForcingTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    @AfterEach
    void clearRegistrations() {
        FactionCardForcing.clearAll();
    }

    // ==================== 退款退哪张卡 ====================

    @Test
    void refundPrefersTheConsumedCardSoCustomCardsNeverComeBackAsAnotherFaction() {
        // 自定义卡选了杀手：退的必须是自定义卡本身
        assertEquals(FactionCardType.CUSTOM, FactionCardForcing.refundCardFor(FactionCardType.CUSTOM, 4));
        assertEquals(FactionCardType.CUSTOM, FactionCardForcing.refundCardFor(FactionCardType.CUSTOM, 1));
        // 固定卡照旧退自己
        assertEquals(FactionCardType.KILLER, FactionCardForcing.refundCardFor(FactionCardType.KILLER, 4));
        // 没有登记（旧路径直接写 ForcePlayerTeam）时按目标扮演类型反推
        assertEquals(FactionCardType.KILLER, FactionCardForcing.refundCardFor(null, 4));
        assertEquals(FactionCardType.CIVILIAN, FactionCardForcing.refundCardFor(null, 1));
        assertEquals(FactionCardType.NEUTRAL, FactionCardForcing.refundCardFor(null, 2));
        assertEquals(FactionCardType.NEUTRAL_FOR_KILLER, FactionCardForcing.refundCardFor(null, 3));
        assertEquals(FactionCardType.NONE, FactionCardForcing.refundCardFor(null, 0));
        // NONE 不算「已登记的卡」，仍然走反推
        assertEquals(FactionCardType.KILLER, FactionCardForcing.refundCardFor(FactionCardType.NONE, 4));
    }

    // ==================== 算不算兑现 ====================

    @Test
    void deliveredMeansTheActualRoleTypeIsExactlyTheTarget() {
        assertTrue(FactionCardForcing.matchesTarget(4, 4));
        assertTrue(FactionCardForcing.matchesTarget(1, 1));
        assertFalse(FactionCardForcing.matchesTarget(4, 1));
        // 目标 0（自定义卡没登记目标）永远不算兑现；实际 -1（无职业）也不算
        assertFalse(FactionCardForcing.matchesTarget(0, 0));
        assertFalse(FactionCardForcing.matchesTarget(1, -1));
    }

    // ==================== 登记与选人加成 ====================

    @Test
    void registeringACardAlsoGivesThePickBoostAndBothDisappearTogether() {
        assertEquals(0, FactionCardForcing.pendingCount());
        FactionCardForcing.register(PLAYER, FactionCardType.CUSTOM, 4);

        assertEquals(1, FactionCardForcing.pendingCount());
        assertTrue(FactionCardForcing.hasPending(PLAYER));
        assertEquals(new FactionCardForcing.Pending(FactionCardType.CUSTOM, 4), FactionCardForcing.pending(PLAYER));
        assertEquals(FactionCardForcing.PICK_BOOST_MULTIPLIER,
                PlayerRoleWeightManager.getCardFactionBoost(PLAYER, 4));
        // 只对目标阵营加成，别的阵营不受影响
        assertEquals(1.0F, PlayerRoleWeightManager.getCardFactionBoost(PLAYER, 1));

        FactionCardForcing.clearAll();
        assertEquals(0, FactionCardForcing.pendingCount());
        assertFalse(FactionCardForcing.hasPending(PLAYER));
        assertEquals(1.0F, PlayerRoleWeightManager.getCardFactionBoost(PLAYER, 4));
    }

    @Test
    void registeringRejectsUnknownCardsAndTargets() {
        FactionCardForcing.register(PLAYER, FactionCardType.NONE, 4);
        FactionCardForcing.register(PLAYER, FactionCardType.KILLER, 0);
        assertEquals(0, FactionCardForcing.pendingCount());
    }

    // ==================== 自定义卡的目标阵营 ====================

    @Test
    void customCardIsATypeButNeverATarget() {
        assertEquals(FactionCardType.CUSTOM, FactionCardType.fromString("custom"));
        assertFalse(FactionCardType.CUSTOM.isSelectableTarget());
        assertEquals(0, FactionCardType.CUSTOM.getTypeRoleId());

        assertEquals(FactionCardType.KILLER, FactionCardType.targetFromString("killer"));
        assertEquals(FactionCardType.NEUTRAL_FOR_KILLER, FactionCardType.targetFromString("neutral_for_killer"));
        // 自定义卡不能作为目标，NONE / 空 / 拼错都按「没选」处理
        assertEquals(FactionCardType.NONE, FactionCardType.targetFromString("custom"));
        assertEquals(FactionCardType.NONE, FactionCardType.targetFromString("none"));
        assertEquals(FactionCardType.NONE, FactionCardType.targetFromString(""));
        assertEquals(FactionCardType.NONE, FactionCardType.targetFromString(null));
    }

    // ==================== 客户端镜像 ====================

    @Test
    void pendingStateRoundTripsThroughTheClientWireFormat() {
        Gson gson = new GsonBuilder().create();
        PendingCardState state = gson.fromJson(
                gson.toJson(PendingCardState.of(FactionCardType.CUSTOM, FactionCardType.KILLER, 7)),
                PendingCardState.class);

        assertEquals(FactionCardType.CUSTOM, state.cardType());
        assertEquals(FactionCardType.KILLER, state.targetType());
        assertEquals(7L, state.version);
        assertTrue(state.active());
    }

    @Test
    void emptyOrLegacyPayloadsCountAsNoPendingCard() {
        assertFalse(PendingCardState.none(1L).active());
        assertFalse(new PendingCardState().active());
        // 缺字段 / 空串（老客户端或空包）不能崩，也不能显示成「已使用」
        Gson gson = new GsonBuilder().create();
        assertFalse(gson.fromJson("{}", PendingCardState.class).active());
        assertFalse(gson.fromJson("{\"card\":\"custom\",\"target\":\"\"}", PendingCardState.class).active());
    }
}
