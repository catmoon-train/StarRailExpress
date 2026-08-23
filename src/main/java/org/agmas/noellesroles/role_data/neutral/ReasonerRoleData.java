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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ReasonerOpenScreenS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

public class ReasonerRoleData extends SimpleRoleData {


    public static final int GIVE_COMPASS_TICKS = 2 * 60 * 20;
    public static final int KILLER_QUESTION_TICKS = 3 * 60 * 20;


    private boolean compassGiven;
    private long compassStartWorldTick = -1;
    private int activeTicks;
    private UUID roleQuestionTarget;
    private UUID bodyQuestionTarget;
    private UUID taskQuestionTarget;
    private boolean solvedAliveCount;
    private boolean solvedRole;
    private boolean solvedDeathReason;
    private boolean solvedTask;
    private boolean solvedKillerCount;
    /** 「杀手数量」问题是否已解锁（解锁即锁定，直至游戏结束不再消失） */
    private boolean killerQuestionUnlocked;

    public ReasonerRoleData(RoleDataContext context) {
        super(context);
    }


    @Override
    public void init() {
        compassGiven = false;
        compassStartWorldTick = -1;
        activeTicks = 0;
        roleQuestionTarget = null;
        bodyQuestionTarget = null;
        taskQuestionTarget = null;
        solvedAliveCount = false;
        solvedRole = false;
        solvedDeathReason = false;
        solvedTask = false;
        solvedKillerCount = false;
        killerQuestionUnlocked = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }


    @Override
    public void serverTick() {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRole(player, ModRoles.REASONER)) {
            return;
        }
        if (!game.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        activeTicks++;
        boolean changed = false;

        // 开局基准：无论罗盘是否已发放都记录（稳定的计时基准，不受击杀加时影响）
        if (compassStartWorldTick < 0) {
            compassStartWorldTick = ((ServerLevel) player.level()).getGameTime();
            changed = true;
        }

        if (!compassGiven) {
            if (player.level().getGameTime() - compassStartWorldTick >= GIVE_COMPASS_TICKS
                    && player instanceof ServerPlayer serverPlayer) {
                giveCompass(serverPlayer);
                compassGiven = true;
                changed = true;
            }
        }

        // 「杀手数量」问题：使用 level.getGameTime() 与稳定基准计时；
        // 一旦达到解锁时间即永久解锁，直至游戏结束都不会再消失
        if (!killerQuestionUnlocked
                && player.level().getGameTime() - compassStartWorldTick >= KILLER_QUESTION_TICKS) {
            killerQuestionUnlocked = true;
            changed = true;
        }

        if (changed) {
            sync();
        }
    }

    private ReasonerOpenScreenS2CPacket buildOpenScreenPacket(ServerPlayer serverPlayer) {
        ServerLevel level = serverPlayer.serverLevel();
        refreshQuestionTargets(level);
        String roleTargetName = getRoleQuestionTargetName(level);
        String bodyTargetName = getBodyQuestionTargetName(level);
        String taskTargetName = getTaskQuestionTargetName(level);
        boolean deathAvailable = deadPlayerCount(level) >= 3
                && bodyQuestionTarget != null
                && !"?".equals(bodyTargetName);
        // 目标为 "?" 时当作已解答隐藏该问题，避免死局（组件实际 solved 状态不变）
        boolean hideRole = solvedRole || "?".equals(roleTargetName);
        boolean hideTask = solvedTask || "?".equals(taskTargetName);
        // 原版物品冷却状态
        int vanillaCooldown = serverPlayer.getCooldowns().isOnCooldown(ModItems.REASONER_COMPASS) ? 1 : 0;
        return new ReasonerOpenScreenS2CPacket(
                roleTargetName,
                bodyTargetName,
                taskTargetName,
                deathAvailable,
                killerQuestionUnlocked,
                solvedAliveCount,
                hideRole,
                solvedDeathReason,
                hideTask,
                solvedKillerCount,
                vanillaCooldown);
    }

    public void openCompass(ServerPlayer serverPlayer) {
        if (serverPlayer.getCooldowns().isOnCooldown(ModItems.REASONER_COMPASS)) {
            return;
        }
        ServerPlayNetworking.send(serverPlayer, buildOpenScreenPacket(serverPlayer));
    }

    public void submitAnswer(ServerPlayer serverPlayer, int question, String answer) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (!game.isRole(serverPlayer, ModRoles.REASONER) || !game.isRunning() || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }
        if (serverPlayer.getCooldowns().isOnCooldown(ModItems.REASONER_COMPASS)) {
            return;
        }
        if (isSolved(question)) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.already_solved").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        refreshQuestionTargets(serverPlayer.serverLevel());
        boolean correct = switch (question) {
            case 1 -> checkAliveCount(answer);
            case 2 -> checkRoleAnswer(serverPlayer.serverLevel(), answer);
            case 3 -> checkDeathReasonAnswer(serverPlayer.serverLevel(), answer);
            case 4 -> checkTaskAnswer(serverPlayer.serverLevel(), answer);
            case 5 -> checkKillerCountAnswer(serverPlayer.serverLevel(), answer);
            default -> false;
        };

        // 原版物品冷却系统
        serverPlayer.getCooldowns().addCooldown(ModItems.REASONER_COMPASS, 35 * 20);
        if (correct) {
            markSolved(question);
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.correct", solvedCount(), 5).withStyle(ChatFormatting.GREEN), true);
            checkWin(serverPlayer.serverLevel());
            // 回放记录：成功推理出线索
            SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.reasoner.deduce_clue",
                    GameReplayUtils.getReplayPlayerDisplayText(serverPlayer, true)));
        } else {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.incorrect").withStyle(ChatFormatting.RED), true);
        }
        sync();
    }

    private void giveCompass(ServerPlayer serverPlayer) {
        if (hasCompass(serverPlayer)) {
            return;
        }
        if (RoleUtils.insertStackInFreeSlot(serverPlayer, ModItems.REASONER_COMPASS.getDefaultInstance())) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.compass_given").withStyle(ChatFormatting.GOLD), false);
        }
    }

    private boolean hasCompass(ServerPlayer serverPlayer) {
        for (ItemStack stack : serverPlayer.getInventory().items) {
            if (stack.is(ModItems.REASONER_COMPASS)) {
                return true;
            }
        }
        return false;
    }

    private void refreshQuestionTargets(ServerLevel level) {
        UUID self = player.getUUID();
        List<ServerPlayer> alive = alivePlayers(level);
        // 角色问题候选：排除推理师本人
        List<ServerPlayer> roleCandidates = alive.stream()
                .filter(p -> !p.getUUID().equals(self))
                .toList();
        if (roleQuestionTarget == null || roleCandidates.stream().noneMatch(p -> p.getUUID().equals(roleQuestionTarget))) {
            roleQuestionTarget = pickRandomUuid(roleCandidates);
        }

        // 死因问题：仅选取已死亡但仍在线玩家、且非推理师本人的尸体
        List<PlayerBodyEntity> bodies = getBodyTargets(level).stream()
                .filter(b -> b.getPlayerUuid() != null
                        && !b.getPlayerUuid().equals(self)
                        && level.getServer().getPlayerList().getPlayer(b.getPlayerUuid()) != null
                        && !GameUtils.isPlayerAliveAndSurvival(level.getServer().getPlayerList().getPlayer(b.getPlayerUuid())))
                .toList();
        if (bodyQuestionTarget == null || bodies.stream().noneMatch(b -> b.getPlayerUuid() != null && b.getPlayerUuid().equals(bodyQuestionTarget))) {
            bodyQuestionTarget = pickRandomBodyOwner(bodies);
        }

        List<ServerPlayer> taskTargets = alive.stream()
                .filter(p -> !p.getUUID().equals(self))
                .filter(this::isInnocentNonNeutral)
                .filter(p -> !SREPlayerTaskComponent.KEY.get(p).tasks.isEmpty())
                .toList();
        if (taskQuestionTarget == null || taskTargets.stream().noneMatch(p -> p.getUUID().equals(taskQuestionTarget))) {
            taskQuestionTarget = pickRandomUuid(taskTargets);
        }

        // 二次校验：目标离线时持续尝试重新选择，直到找到有效目标或池子耗尽
        while (roleQuestionTarget != null
                && level.getServer().getPlayerList().getPlayer(roleQuestionTarget) == null) {
            UUID next = pickRandomUuid(roleCandidates);
            if (next == null || next.equals(roleQuestionTarget)) {
                roleQuestionTarget = null;
                break;
            }
            roleQuestionTarget = next;
        }
        while (bodyQuestionTarget != null && "?".equals(getBodyQuestionTargetName(level))) {
            UUID next = pickRandomBodyOwner(bodies);
            if (next == null || next.equals(bodyQuestionTarget)) {
                bodyQuestionTarget = null;
                break;
            }
            bodyQuestionTarget = next;
        }
        while (taskQuestionTarget != null
                && level.getServer().getPlayerList().getPlayer(taskQuestionTarget) == null) {
            UUID next = pickRandomUuid(taskTargets);
            if (next == null || next.equals(taskQuestionTarget)) {
                taskQuestionTarget = null;
                break;
            }
            taskQuestionTarget = next;
        }
    }

    private boolean checkAliveCount(String answer) {
        Integer guessed = parseNonNegativeInt(answer);
        return guessed != null && guessed == alivePlayers((ServerLevel) player.level()).size();
    }

    private boolean checkRoleAnswer(ServerLevel level, String answer) {
        if (solvedRole || roleQuestionTarget == null) {
            return false;
        }
        Player target = level.getServer().getPlayerList().getPlayer(roleQuestionTarget);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SRERole role = SREGameWorldComponent.KEY.get(level).getRole(target);
        return role != null && role.identifier().toString().equals(answer);
    }

    private boolean checkDeathReasonAnswer(ServerLevel level, String answer) {
        if (solvedDeathReason || deadPlayerCount(level) < 3 || bodyQuestionTarget == null) {
            return false;
        }
        PlayerBodyEntity body = findBody(level, bodyQuestionTarget);
        return body != null && answer.equals(body.getDeathReason());
    }

    private boolean checkTaskAnswer(ServerLevel level, String answer) {
        if (solvedTask || taskQuestionTarget == null) {
            return false;
        }
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(taskQuestionTarget);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target) || !isInnocentNonNeutral(target)) {
            return false;
        }
        try {
            SREPlayerTaskComponent.Task task = SREPlayerTaskComponent.Task.valueOf(answer);
            return SREPlayerTaskComponent.KEY.get(target).tasks.containsKey(task);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean checkKillerCountAnswer(ServerLevel level, String answer) {
        if (solvedKillerCount || !killerQuestionUnlocked) {
            return false;
        }
        Integer guessed = parseNonNegativeInt(answer);
        return guessed != null && guessed == aliveKillerCount(level);
    }

    private int aliveKillerCount(ServerLevel level) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        List<UUID> killerTeam = game.getAllKillerTeamPlayers();
        int count = 0;
        for (ServerPlayer p : alivePlayers(level)) {
            SRERole role = game.getRole(p);
            if (role != null && (role.canUseKiller() || killerTeam.contains(p.getUUID()))) {
                count++;
            }
        }
        return count;
    }

    private int deadPlayerCount(ServerLevel level) {
        return (int) level.players().stream().filter(player -> !GameUtils.isPlayerAliveAndSurvival(player)).count();
    }

    private boolean isInnocentNonNeutral(ServerPlayer target) {
        SRERole role = SREGameWorldComponent.KEY.get(target.level()).getRole(target);
        return role != null && role.isInnocent() && !role.canUseKiller() && !role.isNeutrals();
    }

    private List<ServerPlayer> alivePlayers(ServerLevel level) {
        return level.players().stream().filter(GameUtils::isPlayerAliveAndSurvival).toList();
    }

    private List<PlayerBodyEntity> getBodyTargets(ServerLevel level) {
        AABB allWorld = new AABB(-30000000, level.getMinBuildHeight(), -30000000, 30000000, level.getMaxBuildHeight(), 30000000);
        return level.getEntitiesOfClass(PlayerBodyEntity.class, allWorld,
                body -> body.getPlayerUuid() != null
                        && !org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(body));
    }

    private PlayerBodyEntity findBody(ServerLevel level, UUID owner) {
        return getBodyTargets(level).stream()
                .filter(body -> owner.equals(body.getPlayerUuid()))
                .findFirst()
                .orElse(null);
    }

    private UUID pickRandomUuid(List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return null;
        }
        return players.get(player.level().random.nextInt(players.size())).getUUID();
    }

    private UUID pickRandomBodyOwner(List<PlayerBodyEntity> bodies) {
        if (bodies.isEmpty()) {
            return null;
        }
        return bodies.get(player.level().random.nextInt(bodies.size())).getPlayerUuid();
    }

    private String getRoleQuestionTargetName(ServerLevel level) {
        ServerPlayer target = roleQuestionTarget == null ? null : level.getServer().getPlayerList().getPlayer(roleQuestionTarget);
        return target == null ? "?" : target.getGameProfile().getName();
    }

    private String getBodyQuestionTargetName(ServerLevel level) {
        PlayerBodyEntity body = bodyQuestionTarget == null ? null : findBody(level, bodyQuestionTarget);
        if (body == null || body.getPlayerUuid() == null) {
            return "?";
        }
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(body.getPlayerUuid());
        if (target != null) {
            return target.getGameProfile().getName();
        }
        return "?";
    }

    private String getTaskQuestionTargetName(ServerLevel level) {
        ServerPlayer target = taskQuestionTarget == null ? null : level.getServer().getPlayerList().getPlayer(taskQuestionTarget);
        return target == null ? "?" : target.getGameProfile().getName();
    }

    private void markSolved(int question) {
        switch (question) {
            case 1 -> solvedAliveCount = true;
            case 2 -> solvedRole = true;
            case 3 -> solvedDeathReason = true;
            case 4 -> solvedTask = true;
            case 5 -> solvedKillerCount = true;
            default -> {
            }
        }
    }

    private boolean isSolved(int question) {
        return switch (question) {
            case 1 -> solvedAliveCount;
            case 2 -> solvedRole;
            case 3 -> solvedDeathReason;
            case 4 -> solvedTask;
            case 5 -> solvedKillerCount;
            default -> false;
        };
    }

    public int getSolvedCount() {
        return solvedCount();
    }

    /**
     * 罗盘立即完成一条未回答的随机问题（领袖追随者效果）。
     */
    public void forceCompleteRandomQuestion() {
        java.util.List<Integer> unsolved = new java.util.ArrayList<>();
        for (int q = 1; q <= 5; q++) {
            if (!isSolved(q)) {
                unsolved.add(q);
            }
        }
        if (unsolved.isEmpty()) {
            return;
        }
        int question = unsolved.get(player.getRandom().nextInt(unsolved.size()));
        markSolved(question);
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.reasoner.correct", solvedCount(), 5)
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
        sync();
        if (player instanceof ServerPlayer sp2) {
            checkWin(sp2.serverLevel());
        }
    }

    /**
     * 领袖追随者效果：立即获得罗盘（视作已拥有，之后不再重复发放，HUD 同步显示已拥有），
     * 并立即解决一个罗盘中的随机问题。
     */
    public void forceGiveCompassAndSolveOne() {
        compassGiven = true;
        if (player instanceof ServerPlayer sp) {
            giveCompass(sp);
        }
        sync();
        forceCompleteRandomQuestion();
    }

    /** 是否已发放罗盘。 */
    public boolean isCompassGiven() {
        return compassGiven;
    }

    /**
     * 拿取罗盘的剩余倒计时（tick）。
     * 基于 serverLevel.getGameTime() 与开局基准计算，不受击杀加时影响；
     * 若已发放或尚未开始则返回 0。
     */
    public int getCompassRemainingTicks() {
        if (compassGiven || compassStartWorldTick < 0) {
            return 0;
        }
        long elapsed = player.level().getGameTime() - compassStartWorldTick;
        return (int) Math.max(0, GIVE_COMPASS_TICKS - elapsed);
    }

    private int solvedCount() {
        int count = 0;
        if (solvedAliveCount) count++;
        if (solvedRole) count++;
        if (solvedDeathReason) count++;
        if (solvedTask) count++;
        if (solvedKillerCount) count++;
        return count;
    }

    private void checkWin(ServerLevel level) {
        if (solvedAliveCount && solvedRole && solvedDeathReason && solvedTask && solvedKillerCount) {
            RoleUtils.customWinnerWin(level, GameUtils.WinStatus.CUSTOM,
                    ModRoles.REASONER_ID.getPath(), OptionalInt.of(ModRoles.REASONER.color()));
        }
    }

    private Integer parseNonNegativeInt(String answer) {
        try {
            int value = Integer.parseInt(answer.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("compassGiven", compassGiven);
        tag.putLong("compassStartWorldTick", compassStartWorldTick);
        tag.putInt("activeTicks", activeTicks);
        tag.putBoolean("solvedAliveCount", solvedAliveCount);
        tag.putBoolean("solvedRole", solvedRole);
        tag.putBoolean("solvedDeathReason", solvedDeathReason);
        tag.putBoolean("solvedTask", solvedTask);
        tag.putBoolean("solvedKillerCount", solvedKillerCount);
        tag.putBoolean("killerQuestionUnlocked", killerQuestionUnlocked);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        compassGiven = tag.getBoolean("compassGiven");
        compassStartWorldTick = tag.getLong("compassStartWorldTick");
        activeTicks = tag.getInt("activeTicks");
        solvedAliveCount = tag.getBoolean("solvedAliveCount");
        solvedRole = tag.getBoolean("solvedRole");
        solvedDeathReason = tag.getBoolean("solvedDeathReason");
        solvedTask = tag.getBoolean("solvedTask");
        solvedKillerCount = tag.getBoolean("solvedKillerCount");
        killerQuestionUnlocked = tag.getBoolean("killerQuestionUnlocked");
    }


}
