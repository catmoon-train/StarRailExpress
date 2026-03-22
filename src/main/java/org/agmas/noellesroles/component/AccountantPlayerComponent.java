package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 会计玩家组件
 *
 * 平民阵营，真实心情，默认冲刺时间
 *
 * 被动：每60秒获得25金币
 *
 * 技能：
 * - 蹲下按技能键：切换模式（收入模式 / 支出模式）
 * - 直接按技能键：花费175金币发动技能
 *
 * 收入模式：对玩家按下技能键查看其金币量是否超过300，如果是则给会计消息提示
 * 支出模式：标记一名玩家，20秒后对比金币数变化（上升/下降），提示给会计（被标记玩家无提示）
 *
 * 商店：可花费100金币购买存折
 */
public class AccountantPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 */
    public static final ComponentKey<AccountantPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "accountant"),
            AccountantPlayerComponent.class);

    /** 被动收入金币数 */
    public static final int PASSIVE_INCOME_AMOUNT = 25;

    /** 被动收入间隔（60秒 = 1200 tick） */
    public static final int PASSIVE_INCOME_INTERVAL = 60 * 20;

    /** 技能花费金币数 */
    public static final int SKILL_COST = 175;

    /** 收入金币查询阈值 */
    public static final int INCOME_QUERY_THRESHOLD = 300;

    /** 支出查询半径（4格） */
    public static final double EXPENSE_QUERY_RADIUS = 4.0;

    /** 支出查询时间范围（20秒 = 400 tick） */
    public static final int EXPENSE_QUERY_TIME_RANGE = 20 * 20;

    /** 收入模式 */
    public static final int MODE_INCOME = 0;

    /** 支出模式 */
    public static final int MODE_EXPENSE = 1;

    private final Player player;

    /** 被动收入计时器 */
    private int passiveIncomeTimer = 0;

    /** 当前模式：0=收入, 1=支出 */
    private int currentMode = MODE_INCOME;

    /** 标记的玩家UUID */
    private java.util.UUID markedPlayerUUID = null;

    /** 标记时的金币数 */
    private int markedPlayerInitialBalance = 0;

    /** 标记计时器（tick） */
    private int markTimer = 0;

    /**
     * 构造函数
     */
    public AccountantPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.passiveIncomeTimer = tag.getInt("PassiveIncomeTimer");
        this.currentMode = tag.getInt("CurrentMode");
        this.markTimer = tag.getInt("MarkTimer");
        if (tag.hasUUID("MarkedPlayerUUID")) {
            this.markedPlayerUUID = tag.getUUID("MarkedPlayerUUID");
        }
        this.markedPlayerInitialBalance = tag.getInt("MarkedPlayerInitialBalance");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("PassiveIncomeTimer", this.passiveIncomeTimer);
        tag.putInt("CurrentMode", this.currentMode);
        tag.putInt("MarkTimer", this.markTimer);
        if (this.markedPlayerUUID != null) {
            tag.putUUID("MarkedPlayerUUID", this.markedPlayerUUID);
        }
        tag.putInt("MarkedPlayerInitialBalance", this.markedPlayerInitialBalance);
    }

    @Override
    public void init() {
        this.passiveIncomeTimer = 0;
        this.currentMode = MODE_INCOME;
        this.markedPlayerUUID = null;
        this.markedPlayerInitialBalance = 0;
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
        // 检查玩家是否是会计角色
        if (!gameWorldComponent.isRole(player, ModRoles.ACCOUNTANT)) {
            return;
        }
        // 处理被动收入 - 只在游戏开始后且玩家是会计时生效
        boolean shouldSync = false;
        if (passiveIncomeTimer > 0) {
            passiveIncomeTimer--;
            // 每10秒同步一次到客户端，确保HUD准确更新
            if (passiveIncomeTimer % 200 == 0) {
                shouldSync = true;
            }
        } else {
            // 检查游戏是否正在运行
            givePassiveIncome();
            passiveIncomeTimer = PASSIVE_INCOME_INTERVAL;
            shouldSync = true;
        }

        // 处理标记计时器
        if (markTimer > 0) {
            markTimer--;
            if (markTimer == 0 && markedPlayerUUID != null) {
                // 20秒到期，检查金币变化
                checkMarkedPlayerBalance();
            }
        }

        // 如果需要同步，发送数据到客户端
        if (shouldSync) {
            sync();
        }
    }

    /**
     * 给予被动收入
     */
    private void givePassiveIncome() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        shopComponent.balance += PASSIVE_INCOME_AMOUNT;
        shopComponent.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.accountant.passive_income", PASSIVE_INCOME_AMOUNT)
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    /**
     * 切换模式（蹲下按技能键）
     */
    public void toggleMode() {
        if (currentMode == MODE_INCOME) {
            currentMode = MODE_EXPENSE;
        } else {
            currentMode = MODE_INCOME;
        }

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
     */
    public void useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 根据当前模式执行技能
        if (currentMode == MODE_INCOME) {
            executeIncomeSkill(serverPlayer);
        } else {
            executeExpenseSkill(serverPlayer);
        }
    }

    /**
     * 执行收入模式技能
     * 查看准星对准的玩家的金币量是否超过300
     */
    private void executeIncomeSkill(ServerPlayer serverPlayer) {
        // 获取准星对准的玩家
        Player target = getTargetPlayer(serverPlayer);
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查金币是否足够
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < SKILL_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.insufficient_funds", SKILL_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 扣除金币
        shopComponent.balance -= SKILL_COST;
        shopComponent.sync();

        // 播放钟的声音
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.5F, 1.0F);

        // 检查目标玩家金币数量
        SREPlayerShopComponent targetShop = SREPlayerShopComponent.KEY.get(target);
        int targetBalance = targetShop.balance;

        if (targetBalance >= INCOME_QUERY_THRESHOLD) {
            // 金币超过300，给予提示
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.accountant.income.rich", target.getDisplayName(),
                                    targetBalance)
                            .withStyle(ChatFormatting.GREEN),
                    true);
        } else {
            // 金币未超过300，给予提示
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.accountant.income.poor", target.getDisplayName(),
                                    targetBalance)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
    }

    /**
     * 执行支出模式技能
     * 标记一名玩家，20秒后对比其金币数变化
     */
    private void executeExpenseSkill(ServerPlayer serverPlayer) {
        // 获取准星对准的玩家
        Player target = getTargetPlayer(serverPlayer);
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查金币是否足够
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < SKILL_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.insufficient_funds", SKILL_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 扣除金币
        shopComponent.balance -= SKILL_COST;
        shopComponent.sync();

        // 播放翻书声
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 标记玩家
        markedPlayerUUID = target.getUUID();
        SREPlayerShopComponent targetShop = SREPlayerShopComponent.KEY.get(target);
        markedPlayerInitialBalance = targetShop.balance;
        markTimer = EXPENSE_QUERY_TIME_RANGE;

        // 给会计提示
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.accountant.expense.marked", target.getDisplayName())
                        .withStyle(ChatFormatting.AQUA),
                true);

        sync();
    }

    /**
     * 检查被标记玩家的金币数变化
     * 判断是否上升/下降超过100金币
     */
    private void checkMarkedPlayerBalance() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 查找被标记的玩家
        Player target = null;
        for (Player p : player.level().players()) {
            if (p.getUUID().equals(markedPlayerUUID)) {
                target = p;
                break;
            }
        }

        if (target == null) {
            // 玩家已离线
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.expense.player_left")
                            .withStyle(ChatFormatting.GRAY),
                    true);
            clearMark();
            return;
        }

        // 对比金币数
        SREPlayerShopComponent targetShop = SREPlayerShopComponent.KEY.get(target);
        int currentBalance = targetShop.balance;
        int difference = currentBalance - markedPlayerInitialBalance;

        if (difference > 100) {
            // 金币上升超过100
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.expense.increased_over_100",
                            target.getDisplayName(), difference)
                            .withStyle(ChatFormatting.GOLD),
                    true);
        } else if (difference < -100) {
            // 金币下降超过100
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.expense.decreased_over_100",
                            target.getDisplayName(), -difference)
                            .withStyle(ChatFormatting.DARK_RED),
                    true);
        } else if (difference > 0) {
            // 金币上升但未超过100
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.expense.increased",
                            target.getDisplayName(), difference)
                            .withStyle(ChatFormatting.GREEN),
                    true);
        } else if (difference < 0) {
            // 金币下降但未超过100
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.expense.decreased",
                            target.getDisplayName(), -difference)
                            .withStyle(ChatFormatting.RED),
                    true);
        } else {
            // 金币不变
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.accountant.expense.unchanged",
                            target.getDisplayName())
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }

        clearMark();
    }

    /**
     * 清除标记
     */
    private void clearMark() {
        this.markedPlayerUUID = null;
        this.markedPlayerInitialBalance = 0;
        this.markTimer = 0;
        sync();
    }

    /**
     * 获取准星对准的玩家
     */
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
                // 检查是否在准星方向
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

    /**
     * 获取当前模式
     */
    public int getCurrentMode() {
        return currentMode;
    }

    /**
     * 获取被动收入剩余时间（秒）
     */
    public int getPassiveIncomeRemainingSeconds() {
        return (passiveIncomeTimer + 19) / 20;
    }

    /**
     * 同步组件数据到客户端
     */
    private void sync() {
        if (!player.level().isClientSide) {
            KEY.sync(player);
        }
    }

    @Override
    public void clientTick() {
        if (passiveIncomeTimer > 1) {
            passiveIncomeTimer--;
        }
    }
}
