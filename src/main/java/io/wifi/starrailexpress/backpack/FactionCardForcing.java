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

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.commands.BroadcastCommand;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阵营卡「使用 → 兑现 / 退款」的<b>唯一裁决处</b>，所有模式共用同一套规则。
 *
 * <h2>为什么要有这个类</h2>
 * 以前每个模式各写一份「分不到就退款」：{@code SREMurderGameMode} / {@code SREHideAndSeekGameMode} /
 * {@code SRECustomRoleGameMode} 各抄一遍，轮选 / 海选的 draft state 又各写一份，
 * 还有若干模式直接 {@code PlayerRoleWeightManager.ForcePlayerTeam.clear()} 把卡吞掉、
 * 或整支循环因为集合没填而永远不退款。结果同一张卡在不同模式里的命运完全不同，玩家无从预期。
 *
 * <h2>统一后的生命周期</h2>
 * <ol>
 * <li><b>使用</b>：{@link BackpackManager#activateCardResult} 扣掉卡 → {@link #register} 登记
 * 「本局待兑现的卡」（含自定义卡选中的目标阵营），并同时给出<b>硬强制</b>
 * （{@link PlayerRoleWeightManager#ForcePlayerTeam}）与<b>选人加成</b>
 * （{@link PlayerRoleWeightManager#setCardFactionBoost}）。</li>
 * <li><b>兑现</b>：模式把目标阵营真的分给了玩家 → {@link #markDelivered}，卡不再退回。</li>
 * <li><b>退款</b>：模式当场判定分不到（职业池没有该阵营 / 轮选配额满）→
 * {@link #handleForceFailure}；模式分完职业清场时仍未兑现 → {@link #settleRound}
 * 按本局实际职业兜底结算。<b>分不到就一定退卡，且只退一次</b>。</li>
 * </ol>
 *
 * <p>
 * 退款退的是「当时消耗的那张卡」：自定义卡靠 {@link Pending#card()} 记住，
 * 不会因为目标阵营是别的阵营就退成别的卡（见 {@link #refundCardFor}）。
 */
public final class FactionCardForcing {

    /**
     * 选人加成倍率：持卡的玩家在本局按权重抽人的环节（{@code PlayerRoleAssigner}）
     * 明显更容易被抽中去扮演目标阵营，效果接近硬强制但不占用强制名额。
     */
    public static final float PICK_BOOST_MULTIPLIER = 6.0F;

    /** 分不到目标阵营时的默认退款文案（带一个卡名参数）。 */
    public static final String REFUND_MESSAGE_KEY = "message.sre.pass.faction.card_refunded";

    /** 本局待兑现的卡：UUID → 当场消耗的卡类型 + 目标扮演类型。 */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private FactionCardForcing() {
    }

    /**
     * 本局待兑现的卡。
     *
     * @param card           当场消耗掉的那张卡（自定义卡就是 {@link FactionCardType#CUSTOM}）
     * @param targetRoleType 目标扮演类型（口径同 {@code PlayerRoleWeightManager.getRoleType}）
     */
    public record Pending(FactionCardType card, int targetRoleType) {
    }

    // ====================== 使用 ======================

    /** 使用卡后的登记：记录消耗的是哪张卡、目标是谁，并叠加本局选人加成。 */
    public static void register(UUID uuid, FactionCardType card, int targetRoleType) {
        if (uuid == null || card == null || card == FactionCardType.NONE || targetRoleType <= 0) {
            return;
        }
        PENDING.put(uuid, new Pending(card, targetRoleType));
        PlayerRoleWeightManager.setCardFactionBoost(uuid, targetRoleType, PICK_BOOST_MULTIPLIER);
    }

    public static Pending pending(UUID uuid) {
        return uuid == null ? null : PENDING.get(uuid);
    }

    public static boolean hasPending(UUID uuid) {
        return pending(uuid) != null;
    }

    /** 当前有多少张卡还没结算（诊断 / 测试用）。 */
    public static int pendingCount() {
        return PENDING.size();
    }

    // ====================== 判定 ======================

    /**
     * 退款应当退哪张卡：优先用登记里的「当场消耗的卡」（自定义卡只能靠它），
     * 其次按目标扮演类型反推（兼容历史上直接写 {@code ForcePlayerTeam} 的旧路径）。
     */
    public static FactionCardType refundCardFor(FactionCardType consumed, int roleType) {
        if (consumed != null && consumed != FactionCardType.NONE) {
            return consumed;
        }
        return FactionCardType.fromRoleType(roleType);
    }

    /** 本局实际扮演类型是否兑现了这张卡的目标。 */
    public static boolean matchesTarget(int targetRoleType, int actualRoleType) {
        return targetRoleType > 0 && targetRoleType == actualRoleType;
    }

    // ====================== 兑现 / 退款 ======================

    /** 兑现成功：抹掉登记与选人加成，卡不再退回。 */
    public static void markDelivered(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        PENDING.remove(uuid);
        PlayerRoleWeightManager.clearCardFactionBoost(uuid);
        BackpackManager.sendPendingCard(player);
    }

    /**
     * 退还一张卡并抹掉登记与加成。
     *
     * @return 实际退还的卡类型；{@link FactionCardType#NONE} 表示这次没有可退的卡
     */
    public static FactionCardType refund(UUID uuid, int roleType, ServerPlayer player, String messageKey) {
        Pending pending = uuid == null ? null : PENDING.remove(uuid);
        if (uuid != null) {
            PlayerRoleWeightManager.clearCardFactionBoost(uuid);
        }
        FactionCardType card = refundCardFor(pending == null ? null : pending.card(), roleType);
        if (card == FactionCardType.NONE || player == null) {
            return FactionCardType.NONE;
        }
        BackpackManager.addCard(player, card, 1);
        notify(player, Component.translatable(messageKey, Component.translatable(card.displayName)));
        BackpackManager.sendPendingCard(player);
        return card;
    }

    /**
     * 模式当场判定「这张强制分不到目标阵营」时的统一处理：撤掉强制、扣一次失败补偿、
     * 卡牌来源退卡，命令 / 保底等非卡牌来源只提示。
     */
    public static void handleForceFailure(ServerPlayer player, UUID uuid, int roleType, ForceTeamType forceType) {
        if (uuid != null) {
            PlayerRoleWeightManager.ForcePlayerTeam.remove(uuid);
            PlayerRoleWeightManager.boostKillerSideAfterForceFailure(uuid);
        }
        Harpymodloader.LOGGER.warn(
                "Couldn't force player [{}]'s role to {} because there are no roles available for him.",
                uuid, roleType);
        if (forceType == ForceTeamType.CARD
                && refund(uuid, roleType, player, REFUND_MESSAGE_KEY) != FactionCardType.NONE) {
            return;
        }
        if (player != null) {
            notify(player, Component.translatable("message.sre.force_team.assign_failed"));
        }
    }

    // ====================== 局末结算 ======================

    /**
     * 模式分完职业、清场前的兜底结算：登记的卡若已兑现（本局职业就是目标阵营）就消耗掉，
     * 没兑现就退卡。之后再把本局所有强制阵营清空。
     *
     * <p>
     * 所有「以前直接 {@code ForcePlayerTeam.clear()}」的位置都应改调本方法。
     */
    public static void settleRound(ServerLevel level) {
        settle(level);
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    /**
     * 只结算登记的卡、不动其它强制来源（保底 / 命令）的安全网，
     * 用于没有清场步骤的模式（在 {@code OnGameTrueStarted} 时调用）。
     */
    public static void settlePending(ServerLevel level) {
        settle(level);
    }

    private static void settle(ServerLevel level) {
        if (level == null || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        for (UUID uuid : List.copyOf(PENDING.keySet())) {
            Pending pending = PENDING.get(uuid);
            if (pending == null) {
                continue;
            }
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                // 离线玩家退不了卡（背包写入需要在线玩家），登记留到他下局再结算
                continue;
            }
            PlayerRoleWeightManager.ForcePlayerTeam.remove(uuid);
            int actualRoleType = PlayerRoleWeightManager.getRoleType(game.getRole(player));
            if (matchesTarget(pending.targetRoleType(), actualRoleType)) {
                markDelivered(player);
            } else {
                refund(uuid, pending.targetRoleType(), player, REFUND_MESSAGE_KEY);
            }
        }
    }

    /** 关服时清空登记与加成（本局不会再有结算时机）。 */
    public static void clearAll() {
        PENDING.clear();
        PlayerRoleWeightManager.clearAllCardFactionBoosts();
    }

    private static void notify(ServerPlayer player, Component message) {
        BroadcastCommand.BroadcastMessage(player, message.copy().withStyle(ChatFormatting.RED));
    }
}
