package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.StarPlayerShopComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 阴谋家组件
 *
 * 功能：
 * - 存储目标玩家和猜测的角色
 * - 管理死亡倒计时（40秒）
 * - 处理猜测结果
 */
public class ConspiratorPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<ConspiratorPlayerComponent> KEY = ModComponents.CONSPIRATOR;

    // 死亡倒计时：5秒 = 400 ticks
    public static final int DEATH_COUNTDOWN = 10 * 20;

    private final Player player;

    // 目标玩家信息类
    public static class TargetInfo {
        public UUID targetPlayer;
        public ResourceLocation guessedRole;
        public String targetName;
        public int deathCountdown;
        public boolean guessCorrect;

        public TargetInfo(UUID targetPlayer, ResourceLocation guessedRole, String targetName) {
            this.targetPlayer = targetPlayer;
            this.guessedRole = guessedRole;
            this.targetName = targetName;
            this.deathCountdown = 0;
            this.guessCorrect = false;
        }
    }

    // 目标玩家列表（支持多目标猜测）
    public List<TargetInfo> targetList = new ArrayList<>();

    // 是否已成功击杀（用于判断胜利）
    public boolean hasKilled = false;

    // 猜错次数（猜错3次会死亡）
    public int wrongGuessCount = 0;

    // 最大允许猜错次数
    public static final int MAX_WRONG_GUESSES = 3;

    public ConspiratorPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void reset() {
        this.targetList.clear();
        this.hasKilled = false;
        this.wrongGuessCount = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    /**
     * 是否已经被猜测过并且即将死亡
     * 
     * @param targetUuid 目标玩家 UUID
     */
    public boolean hasBeenGuessedToDie(UUID targetUuid) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        Player target = serverPlayer.level().getPlayerByUUID(targetUuid);
        if (target == null)
            return false;
        for (TargetInfo targetInfo : targetList) {
            if (targetInfo.targetPlayer.equals(targetUuid)) {
                if (targetInfo.guessCorrect && targetInfo.deathCountdown > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 进行猜测
     * 
     * @param targetUuid 目标玩家 UUID
     * @param roleId     猜测的角色 ID
     * @return true 如果猜测正确
     */
    public boolean makeGuess(UUID targetUuid, ResourceLocation roleId) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        Player target = player.level().getPlayerByUUID(targetUuid);
        if (target == null)
            return false;

        // 获取目标的实际角色
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
        Role actualRole = gameWorld.getRole(target);

        if (actualRole == null)
            return false;

        String targetName = target.getName().getString();

        // 检查目标是否已经在目标列表中
        TargetInfo existingTarget = null;
        for (TargetInfo targetInfo : targetList) {
            if (targetInfo.targetPlayer.equals(targetUuid)) {
                existingTarget = targetInfo;
                break;
            }
        }

        if (existingTarget != null) {
            // 更新现有目标的猜测
            existingTarget.guessedRole = roleId;
        } else {
            // 添加新目标到列表
            TargetInfo newTarget = new TargetInfo(targetUuid, roleId, targetName);
            targetList.add(newTarget);
        }

        // 检查是否猜测正确
        if (actualRole.identifier().equals(roleId)) {
            // 猜测正确！开始死亡倒计时
            if (existingTarget != null) {
                existingTarget.guessCorrect = true;
                existingTarget.deathCountdown = DEATH_COUNTDOWN;
            } else {
                TargetInfo newTarget = targetList.get(targetList.size() - 1); // 获取刚添加的目标
                newTarget.guessCorrect = true;
                newTarget.deathCountdown = DEATH_COUNTDOWN;
            }
            final var playerShopComponent = StarPlayerShopComponent.KEY.get(player);
            playerShopComponent.setBalance(100 + playerShopComponent.balance);

            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.conspirator.correct", targetName)
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    true);

            // 通知目标玩家他们被诅咒了（但不告诉是谁）
            if (target instanceof ServerPlayer targetServer) {
                targetServer.displayClientMessage(
                        Component.translatable("message.noellesroles.conspirator.cursed")
                                .withStyle(ChatFormatting.DARK_PURPLE),
                        true);
                // 触发私家侦探被动
                if (gameWorld.isRole(target, ModRoles.DETECTIVE)) {
                    target.displayClientMessage(
                            Component
                                    .translatable("message.noellesroles.conspirator.cursed.known",
                                            this.player.getDisplayName())
                                    .withStyle(ChatFormatting.DARK_PURPLE),
                            true);
                }
                for (ServerPlayer p : targetServer.serverLevel().players()) {
                    if (gameWorld.isRole(p, ModRoles.DETECTIVE)) {
                        DetectivePlayerComponent.KEY.get(p).triggerConspiratorInstinct(5 * 20);
                    }
                }
            }

            this.sync();
            return true;
        } else {
            // 猜测错误
            this.wrongGuessCount++;

            // 检查是否猜错3次
            if (this.wrongGuessCount >= MAX_WRONG_GUESSES) {
                // 猜错3次，阴谋家死亡
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.conspirator.too_many_wrong")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        true);

                // 使用自杀死因
                ResourceLocation deathReason = Noellesroles.id("conspiracy_backfire");
                GameFunctions.killPlayer(player, true, null, deathReason);

                // 重置状态
                this.targetList.clear();
                this.sync();
                return false;
            }

            int remainingGuesses = MAX_WRONG_GUESSES - this.wrongGuessCount;
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.conspirator.wrong_with_count", targetName,
                                    remainingGuesses)
                            .withStyle(ChatFormatting.RED),
                    true);

            this.sync();
            return false;
        }
    }

    /**
     * 获取剩余倒计时（秒）
     */
    public int getCountdownSeconds() {
        // 返回所有目标中剩余时间最少的倒计时
        int minCountdown = 0;
        for (TargetInfo targetInfo : targetList) {
            if (targetInfo.deathCountdown > 0 && (minCountdown == 0 || targetInfo.deathCountdown < minCountdown)) {
                minCountdown = targetInfo.deathCountdown;
            }
        }
        return minCountdown / 20;
    }

    /**
     * 检查目标是否存活
     */
    public boolean isTargetAlive(UUID targetUuid) {
        if (targetUuid == null)
            return false;
        Player target = player.level().getPlayerByUUID(targetUuid);
        return target != null && GameFunctions.isPlayerAliveAndSurvival(target);
    }

    /**
     * 检查是否有任何目标存活
     */
    public boolean hasAnyTargetAlive() {
        for (TargetInfo targetInfo : targetList) {
            if (isTargetAlive(targetInfo.targetPlayer)) {
                return true;
            }
        }
        return false;
    }

    public void sync() {
        ModComponents.CONSPIRATOR.sync(this.player);
    }

    @Override
    public void serverTick() {
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());

        // 只有阴谋家角色才处理
        if (!gameWorld.isRole(player, ModRoles.CONSPIRATOR))
            return;

        // 处理所有目标的倒计时
        for (int i = targetList.size() - 1; i >= 0; i--) {
            TargetInfo targetInfo = targetList.get(i);

            // 如果有正在进行的死亡倒计时
            if (targetInfo.deathCountdown > 0 && targetInfo.guessCorrect && targetInfo.targetPlayer != null) {
                targetInfo.deathCountdown--;

                // 每秒同步一次
                if (targetInfo.deathCountdown % 20 == 0) {
                    this.sync();

                    // 每10秒提醒目标玩家
                    if (targetInfo.deathCountdown % 200 == 0 && targetInfo.deathCountdown > 0) {
                        Player target = player.level().getPlayerByUUID(targetInfo.targetPlayer);
                        if (target instanceof ServerPlayer targetServer
                                && GameFunctions.isPlayerAliveAndSurvival(target)) {
                            targetServer.displayClientMessage(
                                    Component
                                            .translatable("message.noellesroles.conspirator.countdown",
                                                    targetInfo.deathCountdown / 20)
                                            .withStyle(ChatFormatting.DARK_PURPLE),
                                    true);
                        }
                    }
                }

                // 倒计时结束，目标死亡
                if (targetInfo.deathCountdown <= 0) {
                    Player target = player.level().getPlayerByUUID(targetInfo.targetPlayer);
                    if (target != null && GameFunctions.isPlayerAliveAndSurvival(target)) {
                        // 使用心脏麻痹死因（隐藏真实原因）
                        ResourceLocation deathReason = Noellesroles.id("heart_attack");
                        GameFunctions.killPlayer(target, true, player, deathReason);

                        this.hasKilled = true;

                        // 通知阴谋家
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.displayClientMessage(
                                    Component
                                            .translatable("message.noellesroles.conspirator.killed",
                                                    targetInfo.targetName)
                                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                                    true);
                        }
                    }

                    // 从列表中移除已完成的猜测
                    targetList.remove(i);
                    this.sync();
                }
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 序列化目标列表
        ListTag targetListTag = new ListTag();
        for (TargetInfo targetInfo : targetList) {
            CompoundTag targetTag = new CompoundTag();
            if (targetInfo.targetPlayer != null) {
                targetTag.putUUID("targetPlayer", targetInfo.targetPlayer);
            }
            if (targetInfo.guessedRole != null) {
                targetTag.putString("guessedRole", targetInfo.guessedRole.toString());
            }
            targetTag.putInt("deathCountdown", targetInfo.deathCountdown);
            targetTag.putBoolean("guessCorrect", targetInfo.guessCorrect);
            targetTag.putString("targetName", targetInfo.targetName);
            targetListTag.add(targetTag);
        }
        tag.put("targetList", targetListTag);

        tag.putBoolean("hasKilled", hasKilled);
        tag.putInt("wrongGuessCount", wrongGuessCount);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 反序列化目标列表
        targetList.clear();
        if (tag.contains("targetList")) {
            ListTag targetListTag = tag.getList("targetList", Tag.TAG_COMPOUND);
            for (int i = 0; i < targetListTag.size(); i++) {
                CompoundTag targetTag = targetListTag.getCompound(i);
                UUID targetPlayer = targetTag.contains("targetPlayer") ? targetTag.getUUID("targetPlayer") : null;
                ResourceLocation guessedRole = targetTag.contains("guessedRole")
                        ? ResourceLocation.tryParse(targetTag.getString("guessedRole"))
                        : null;
                String targetName = targetTag.getString("targetName");
                TargetInfo targetInfo = new TargetInfo(targetPlayer, guessedRole, targetName);
                targetInfo.deathCountdown = targetTag.getInt("deathCountdown");
                targetInfo.guessCorrect = targetTag.getBoolean("guessCorrect");
                targetList.add(targetInfo);
            }
        }

        this.hasKilled = tag.getBoolean("hasKilled");
        this.wrongGuessCount = tag.getInt("wrongGuessCount");
    }
}