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

package org.agmas.noellesroles.role_data.neutral;

import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role.touhou.roles.THYuyukoRole;
import org.agmas.noellesroles.role_data.innocence.SaltedFishRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREPlayerAFKComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

public class THYuyukoRoleData extends SimpleRoleData {
    public static final int AFK_THRESHOLD = 10 * 20;
    /** 吞玩家技能 id（用于失败时退还冷却）。 */
    private static final net.minecraft.resources.ResourceLocation EAT_PLAYER_SKILL_ID = SRE.id("yuyuko/eat_player");
    /** 发起击杀后等待几个 tick 再结算结果（死亡链经 server.execute() 排队，下一 tick 必然已完成）。 */
    private static final int PENDING_EAT_RESOLVE_TICKS = 1;

    public int ateCount = 0;
    public int winnerNeedCount = 0;
    public int instinctLeft = 0;

    /** 待结算的「吞玩家」目标（击杀已发起，结果待下一 tick 确认）。 */
    private java.util.UUID pendingEatTarget = null;
    private int pendingEatTicks = 0;

    public THYuyukoRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        tag.putInt("count", ateCount);
        tag.putInt("need", winnerNeedCount);
        tag.putInt("instinct", instinctLeft);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        ateCount = getIntTag(tag, "count", 0);
        winnerNeedCount = getIntTag(tag, "need", 0);
        instinctLeft = getIntTag(tag, "instinct", 0);
    }

    public void calcWinnerCount() {
        long c = player.level().players().stream().filter(GameUtils::isPlayerAliveAndSurvival).count();
        if (c >= 24) {
            winnerNeedCount = ((int) (c / 3f) + 2);
        } else {
            winnerNeedCount = ((int) (c / 1.5f) + 2);
        }
        sync();
    }

    public boolean tryEat(Player t) {
        if (t == null || !(t instanceof ServerPlayer target)) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target))
            return false;
        if (player.getVehicle() != null || player.isSleeping()) {
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.yuyuko.player.failed", target.getName(),
                            Component.translatable("message.noellesroles.yuyuko.player.failed.no_afk",
                                    AFK_THRESHOLD / 20))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (SREPlayerAFKComponent.KEY.get(target).getAFKTime() < AFK_THRESHOLD) {
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.yuyuko.player.failed", target.getName(),
                            Component.translatable("message.noellesroles.yuyuko.player.failed.no_afk",
                                    AFK_THRESHOLD / 20))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 先发起吞噬（非强制死亡）：目标可能被护盾、无敌、职业免死等原因挡下。
        //
        // GameUtils.killPlayer 走 server.execute()：在主线程的 tick 内它是「排队」而非内联执行
        // （BlockableEventLoop.executeIfPossible → scheduleExecutables() 在 reentrantCount != 0 时成立，
        // 只把任务塞进队列），所以这里读不到死亡结果。
        // 因此先返回 true 让技能正常进入冷却，再由 serverTick 在下一个 tick 结算：
        // 目标死了才算吃成功（计数 + 奖励），没死则退还冷却并提示被挡下。
        GameUtils.killPlayer(t, false, player, GameConstants.DeathReasons.YUYUKO_EATEN);
        pendingEatTarget = target.getUUID();
        pendingEatTicks = PENDING_EAT_RESOLVE_TICKS;
        return true;
    }

    /** 结算一次「吞玩家」的结果（由 {@link #serverTick()} 在下一 tick 调用）。 */
    private void resolvePendingEat() {
        UUID targetId = pendingEatTarget;
        pendingEatTarget = null;
        pendingEatTicks = 0;
        if (targetId == null) {
            return;
        }
        ServerPlayer target = player.getServer() == null ? null
                : player.getServer().getPlayerList().getPlayer(targetId);
        if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
            // 目标仍然存活 → 被护盾 / 无敌 / 职业免死挡下：退还冷却，不计胜利进度
            io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(player)
                    .setSkillCooldown(EAT_PLAYER_SKILL_ID, 0);
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.yuyuko.player.failed", target.getName(),
                            Component.translatable("message.noellesroles.yuyuko.player.failed.blocked"))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        // 目标已死亡（或已离场）→ 吞噬成功
        player.displayClientMessage(
                Component.translatable("message.noellesroles.yuyuko.success",
                        target != null ? target.getName() : Component.literal("?")),
                true);
        if (target != null) {
            SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.noellesroles.yuyuko.eat",
                    GameReplayUtils.getReplayPlayerDisplayText(player, true),
                    GameReplayUtils.getReplayPlayerDisplayText(target, true)));
        }
        ateCount++;
        endEat();
    }

    public boolean tryEat(PlayerBodyEntity body) {
        if (body == null) {
            player.displayClientMessage(
                    Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (body.isRemoved()) {
            return false;
        }
        // 待复活尸体不能被食用
        if (org.agmas.noellesroles.utils.AnatmanAsatyaCorpseGuard.denyInteraction(body, player)) {
            return false;
        }
        // 咸鱼「晒咸鱼」的假尸体不是真实尸体，不能拿来进食计数
        if (SaltedFishRoleData.isSaltedFishFakeBody(body)) {
            return false;
        }
        ateCount++;
        player.displayClientMessage(Component.translatable("message.noellesroles.yuyuko.success", body.getName()),
                true);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.noellesroles.yuyuko.eat",
                GameReplayUtils.getReplayPlayerDisplayText(player, true), body.getName()));
        body.discard();
        endEat();
        return true;
    }

    public void endEat() {

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BURP, SoundSource.MASTER, 0.9F, 0.65F);

        if (ateCount >= winnerNeedCount) {
            if (player.level() instanceof ServerLevel sl)
                RoleUtils.customWinnerWin(sl, "saigyouji_yuyuko", (THMiscRoles.YUYUKO.color()));
            return;
        }
        rewardInstinct(THYuyukoRole.INSTINCT_REWARD_TIME_PLAYER);
    }

    public void rewardInstinct(int ticks) {
        instinctLeft += ticks;
        sync();
    }

    @Override
    public void clientTick() {
        if (instinctLeft > 0) {
            instinctLeft--;
        }
    }

    @Override
    public void serverTick() {
        if (pendingEatTicks > 0) {
            pendingEatTicks--;
            if (pendingEatTicks == 0) {
                resolvePendingEat();
            }
        }
        if (instinctLeft > 0) {
            instinctLeft--;
            if (instinctLeft <= 0) {
                instinctLeft = 0;
                sync();
            }
        }
    }
}
