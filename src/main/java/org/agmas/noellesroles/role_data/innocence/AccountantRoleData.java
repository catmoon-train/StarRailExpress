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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.economy.MoneyFlow;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Optional;
import java.util.UUID;

/**
 * 会计的职业数据。
 * <p>
 * 两个技能共用同一套金币流水账本（{@link MoneyFlow}）：
 * <ul>
 * <li><b>收入</b>：查看目标玩家最近一次的金币收入来源。若目标有扒窃 / 偷窃 / 掠夺记录，
 * 优先显示该行为。</li>
 * <li><b>支出</b>：标记目标玩家，30 秒后得知其金币支出渠道（商店购买优先，带商品名）。</li>
 * </ul>
 */
public class AccountantRoleData extends SimpleRoleData {

    /** 被动收入金币数 */
    public static final int PASSIVE_INCOME_AMOUNT = 25;

    /** 被动收入间隔（60秒 = 1200 tick） */
    public static final int PASSIVE_INCOME_INTERVAL = 60 * 20;

    /** 技能花费金币数 */
    public static final int SKILL_COST = 175;

    /** 支出查询时间范围：标记后 30 秒出结果（30 * 20 = 600 tick） */
    public static final int EXPENSE_QUERY_TIME_RANGE = 30 * 20;

    /** 收入模式 */
    public static final int MODE_INCOME = 0;

    /** 支出模式 */
    public static final int MODE_EXPENSE = 1;

    /** 被动收入计时器 */
    private int passiveIncomeTimer = 0;

    /** 当前模式：0=收入, 1=支出 */
    private int currentMode = MODE_INCOME;

    /** 标记的玩家UUID */
    private UUID markedPlayerUUID = null;

    /** 标记时的时间戳（用于确定支出查询窗口） */
    private long markStartGameTime = 0;

    /** 标记计时器（tick） */
    private int markTimer = 0;

    public AccountantRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.passiveIncomeTimer = tag.getInt("PassiveIncomeTimer");
        this.currentMode = tag.getInt("CurrentMode");
        this.markTimer = tag.getInt("MarkTimer");
        if (tag.hasUUID("MarkedPlayerUUID")) {
            this.markedPlayerUUID = tag.getUUID("MarkedPlayerUUID");
        }
        this.markStartGameTime = tag.getLong("MarkStartGameTime");
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("PassiveIncomeTimer", this.passiveIncomeTimer);
        tag.putInt("CurrentMode", this.currentMode);
        tag.putInt("MarkTimer", this.markTimer);
        if (this.markedPlayerUUID != null) {
            tag.putUUID("MarkedPlayerUUID", this.markedPlayerUUID);
        }
        tag.putLong("MarkStartGameTime", this.markStartGameTime);
    }

    @Override
    public void init() {
        this.passiveIncomeTimer = 0;
        this.currentMode = MODE_INCOME;
        this.markedPlayerUUID = null;
        this.markStartGameTime = 0;
        this.markTimer = 0;
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null)
            return;
        if (!gameWorldComponent.isRunning())
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.ACCOUNTANT)) {
            return;
        }

        boolean shouldSync = false;

        // 被动收入
        if (passiveIncomeTimer > 0) {
            passiveIncomeTimer--;
            // 每10秒同步一次到客户端，确保HUD准确更新
            if (passiveIncomeTimer % 200 == 0) {
                shouldSync = true;
            }
        } else {
            givePassiveIncome();
            passiveIncomeTimer = PASSIVE_INCOME_INTERVAL;
            shouldSync = true;
        }

        // 支出查询倒计时
        if (markTimer > 0) {
            markTimer--;
            if (markTimer == 0 && markedPlayerUUID != null) {
                reportMarkedPlayerExpense();
            }
        }

        if (shouldSync) {
            sync();
        }
    }

    /**
     * 给予被动收入。
     * <p>
     * 走 {@code addToBalance} 以保持客户端同步；余额变动前先登记「被动收入」，
     * 这样账本不会把这笔记成「其它收入」。
     */
    private void givePassiveIncome() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        MoneyFlow.recordIncome(player, MoneyFlow.IncomeType.PASSIVE, PASSIVE_INCOME_AMOUNT);
        SREPlayerShopComponent.KEY.get(player).addToBalance(PASSIVE_INCOME_AMOUNT);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.accountant.passive_income", PASSIVE_INCOME_AMOUNT)
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    /**
     * 切换模式（蹲下按技能键）
     */
    public void toggleMode() {
        currentMode = currentMode == MODE_INCOME ? MODE_EXPENSE : MODE_INCOME;

        if (player instanceof ServerPlayer serverPlayer) {
            Component message;
            if (currentMode == MODE_INCOME) {
                message = Component.translatable("message.noellesroles.accountant.mode.income")
                        .withStyle(ChatFormatting.GOLD);
            } else {
                message = Component.translatable("message.noellesroles.accountant.mode.expense")
                        .withStyle(ChatFormatting.AQUA);
            }
            serverPlayer.displayClientMessage(message, true);
        }

        sync();
    }

    /**
     * 使用技能（直接按技能键）
     *
     * @return 是否成功释放技能
     */
    public boolean useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        if (currentMode == MODE_INCOME) {
            return executeIncomeSkill(serverPlayer);
        } else {
            return executeExpenseSkill(serverPlayer);
        }
    }

    /** 找到准星对准的玩家 */
    private Player getTargetPlayer(ServerPlayer player) {
        double minDistance = 5.0;
        Player target = null;

        for (Player otherPlayer : player.level().players()) {
            if (otherPlayer.isSpectator())
                continue;
            if (otherPlayer.getUUID().equals(player.getUUID()))
                continue;

            double distance = player.distanceTo(otherPlayer);
            if (distance <= minDistance) {
                net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
                net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle().normalize();
                net.minecraft.world.phys.Vec3 toTarget = otherPlayer.position().subtract(eyePos).normalize();
                double dotProduct = lookVec.dot(toTarget);

                if (dotProduct > 0.8) {
                    if (target == null || distance < player.distanceTo(target)) {
                        target = otherPlayer;
                    }
                }
            }
        }

        return target;
    }

    /** 检查金币并扣费；不足时提示并返回 false */
    private boolean tryPaySkillCost(ServerPlayer serverPlayer) {
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < SKILL_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.insufficient_funds", SKILL_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shopComponent.balance -= SKILL_COST;
        shopComponent.sync();
        return true;
    }

    /**
     * 收入技能：查看目标玩家最近一次的金币收入来源。
     */
    private boolean executeIncomeSkill(ServerPlayer serverPlayer) {
        Player target = getTargetPlayer(serverPlayer);
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!tryPaySkillCost(serverPlayer))
            return false;

        // 播放钟的声音
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.5F, 1.0F);

        Optional<MoneyFlow.IncomeEntry> entry = MoneyFlow.queryIncome(target);
        Component source = entry
                .map(e -> incomeSourceComponent(e.type()))
                .orElseGet(() -> incomeSourceComponent(MoneyFlow.IncomeType.NONE));

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.accountant.income.report",
                        target.getName(), source).withStyle(ChatFormatting.GREEN),
                true);
        return true;
    }

    /**
     * 支出技能：标记目标玩家，30 秒后得知其支出渠道。
     */
    private boolean executeExpenseSkill(ServerPlayer serverPlayer) {
        Player target = getTargetPlayer(serverPlayer);
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!tryPaySkillCost(serverPlayer))
            return false;

        // 播放翻书声
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);

        markedPlayerUUID = target.getUUID();
        markStartGameTime = player.level().getGameTime();
        markTimer = EXPENSE_QUERY_TIME_RANGE;

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.accountant.expense.marked", target.getName(),
                        EXPENSE_QUERY_TIME_RANGE / 20).withStyle(ChatFormatting.AQUA),
                true);

        sync();
        return true;
    }

    /**
     * 30 秒到期：报告被标记玩家的支出渠道。
     */
    private void reportMarkedPlayerExpense() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        UUID marked = markedPlayerUUID;
        Player target = marked == null ? null : serverPlayer.server.getPlayerList().getPlayer(marked);
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.expense.player_left")
                            .withStyle(ChatFormatting.GRAY),
                    true);
            clearMark();
            return;
        }

        Optional<MoneyFlow.ExpenseEntry> entry = MoneyFlow.queryExpense(target, markStartGameTime,
                markStartGameTime + EXPENSE_QUERY_TIME_RANGE);
        Component channel = entry
                .map(this::expenseChannelComponent)
                .orElseGet(() -> expenseChannelComponent(null));

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.accountant.expense.report",
                        target.getName(), channel).withStyle(ChatFormatting.AQUA),
                true);

        clearMark();
    }

    /** 收入来源的展示文本 */
    private Component incomeSourceComponent(MoneyFlow.IncomeType type) {
        return Component.translatable("message.noellesroles.accountant.income.type." + incomeKey(type));
    }

    private static String incomeKey(MoneyFlow.IncomeType type) {
        return switch (type) {
            case PASSIVE -> "passive";
            case TASK -> "task";
            case KILL -> "kill";
            case PICKPOCKET -> "pickpocket";
            case STEAL -> "steal";
            case PLUNDER -> "plunder";
            case PASSBOOK -> "passbook";
            case SKILL -> "skill";
            case OTHER -> "other";
            case NONE -> "none";
        };
    }

    /** 支出渠道的展示文本；商店购买会带上商品名 */
    private Component expenseChannelComponent(MoneyFlow.ExpenseEntry entry) {
        if (entry == null)
            return Component.translatable("message.noellesroles.accountant.expense.type.none");
        return switch (entry.type()) {
            case SHOP -> entry.detail() != null
                    ? Component.translatable("message.noellesroles.accountant.expense.type.shop", entry.detail())
                    : Component.translatable("message.noellesroles.accountant.expense.type.shop.unknown");
            case VENDING -> Component.translatable("message.noellesroles.accountant.expense.type.vending");
            case SKILL -> Component.translatable("message.noellesroles.accountant.expense.type.skill");
            case STOLEN -> Component.translatable("message.noellesroles.accountant.expense.type.stolen");
            case PASSBOOK -> Component.translatable("message.noellesroles.accountant.expense.type.passbook");
            case OTHER -> Component.translatable("message.noellesroles.accountant.expense.type.other");
            case NONE -> Component.translatable("message.noellesroles.accountant.expense.type.none");
        };
    }

    /** 清除标记 */
    private void clearMark() {
        this.markedPlayerUUID = null;
        this.markStartGameTime = 0;
        this.markTimer = 0;
        sync();
    }

    /** 获取当前模式 */
    public int getCurrentMode() {
        return currentMode;
    }

    /** 获取被动收入剩余时间（秒） */
    public int getPassiveIncomeRemainingSeconds() {
        return (passiveIncomeTimer + 19) / 20;
    }

    @Override
    public void clientTick() {
        if (passiveIncomeTimer > 1) {
            passiveIncomeTimer--;
        }
    }
}
