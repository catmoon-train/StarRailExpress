package org.agmas.noellesroles.roles.thief;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.OptionalInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.repack.HSRItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;

import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 小偷玩家组件
 *
 * 中立阵营，假心情，无限冲刺时间
 *
 * 技能：
 * - 蹲下按技能键切换偷钱/偷物品模式
 * - 按技能键释放技能（冷却30s，偷取失败不进入冷却）
 * - 偷钱：偷取目标100金币（目标必须至少有100金币）
 * - 偷物品：仿照StupidExpress2的小偷机制
 *
 * 被动：
 * - 杀一人获得100金币
 *
 * 独立胜利条件：
 * - 场上存在小偷时游戏不结束
 * - 手持小偷的荣誉（金锭）回房间睡觉则独立胜利
 * - 小偷的荣誉所需金币数 = 游戏开始总人数 * 75
 */
public class ThiefPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public int honorCost = 0;
    /** 组件键 */
    public static final ComponentKey<ThiefPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "thief"),
            ThiefPlayerComponent.class);

    /** 技能冷却时间（30秒 = 600 tick） */
    public static final int ABILITY_COOLDOWN = 30 * 20;

    /** 每次偷钱金额 */
    public static final int STEAL_MONEY_AMOUNT = 100;

    /** 购买小偷的荣誉所需的金币基数 */
    public static final int HONOR_COST_PER_PLAYER = 60;

    /** 偷钱模式 */
    public static final int MODE_STEAL_MONEY = 0;

    /** 偷物品模式 */
    public static final int MODE_STEAL_ITEM = 1;

    /** 卖物品模式 */
    public static final int MODE_SELL_ITEM = 2;

    /** 通知延迟时间（10秒 = 200 tick） */
    public static final int NOTIFICATION_DELAY = 10 * 20;

    private final Player player;

    /** 技能冷却 */
    public int cooldown = 0;

    /** 当前模式：0=偷钱, 1=偷物品 */
    public int currentMode = MODE_STEAL_MONEY;

    /** 是否在偷取选择界面（蹲下状态） */
    public boolean isInSelectionMode = false;

    /** 待通知的被偷取信息列表 */
    public java.util.List<PendingNotification> pendingNotifications = new java.util.ArrayList<>();

    /**
     * 待通知的被偷取信息
     */
    public static class PendingNotification {
        public ServerPlayer targetPlayer;
        public String messageKey;
        public Object[] messageArgs;
        public int delayTicks;

        public PendingNotification(ServerPlayer targetPlayer, String messageKey, Object[] messageArgs, int delayTicks) {
            this.targetPlayer = targetPlayer;
            this.messageKey = messageKey;
            this.messageArgs = messageArgs;
            this.delayTicks = delayTicks;
        }
    }

    /**
     * 构造函数
     */
    public ThiefPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void reset() {
        this.cooldown = ABILITY_COOLDOWN;
        this.currentMode = MODE_STEAL_MONEY;
        this.isInSelectionMode = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
        this.pendingNotifications.clear();
    }

    public void updateHonorCost(int allPlayer) {
        this.honorCost = getHonorCost(allPlayer);
        sync();
    }

    /**
     * 切换偷取模式（蹲下按技能键）
     */
    public void toggleMode() {
        if (this.cooldown > 0) {
            return;
        }

        if (this.currentMode == MODE_STEAL_MONEY) {
            this.currentMode = MODE_STEAL_ITEM;
        } else if (this.currentMode == MODE_STEAL_ITEM) {
            this.currentMode = MODE_SELL_ITEM;
        } else {
            this.currentMode = MODE_STEAL_MONEY;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            Component message;
            if (this.currentMode == MODE_STEAL_MONEY) {
                message = Component.translatable("message.noellesroles.thief.mode.money")
                        .withStyle(ChatFormatting.GOLD);
            } else if (this.currentMode == MODE_STEAL_ITEM) {
                message = Component.translatable("message.noellesroles.thief.mode.item")
                        .withStyle(ChatFormatting.AQUA);
            } else {
                message = Component.translatable("message.noellesroles.thief.mode.sell")
                        .withStyle(ChatFormatting.GREEN);
            }
            serverPlayer.displayClientMessage(message, true);
        }

        this.sync();
    }

    /**
     * 尝试使用技能（按技能键释放）
     *
     * @return 是否成功释放技能
     */
    public boolean useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            return false;
        }
        // 检查冷却（卖物品模式不需要冷却）
        if (this.cooldown > 0 && this.currentMode != MODE_SELL_ITEM) {
            return false;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());

        // 检查角色
        if (!gameWorld.isRole(player, ModRoles.THIEF)) {
            return false;
        }

        if (this.currentMode == MODE_SELL_ITEM) {
            return sellItem();
        }

        // 获取当前看向的目标玩家
        Player target = getLookedAtPlayer();
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.thief.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return true; // 失败不进入冷却
        }

        if (this.currentMode == MODE_STEAL_MONEY) {
            return stealMoney(target);
        } else {
            return stealItem(target);
        }
    }

    /**
     * 偷钱
     */
    private boolean stealMoney(Player target) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof ServerPlayer targetPlayer)) {
            return false;
        }

        // 检查目标是否被淘汰
        if (GameUtils.isPlayerEliminated(target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.thief.target_eliminated")
                            .withStyle(ChatFormatting.RED),
                    true);
            return true; // 失败不进入冷却
        }

        // 获取目标金钱
        SREPlayerShopComponent targetShop = SREPlayerShopComponent.KEY.get(target);
        int targetBalance = targetShop.balance;

        // 检查目标金币是否足够
        if (targetBalance < STEAL_MONEY_AMOUNT) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.thief.not_enough_money",
                            target.getDisplayName())
                            .withStyle(ChatFormatting.RED),
                    true);
            return true; // 失败不进入冷却
        }

        // 偷取金币
        targetShop.balance -= STEAL_MONEY_AMOUNT;
        targetShop.sync();

        SREPlayerShopComponent thiefShop = SREPlayerShopComponent.KEY.get(player);
        thiefShop.balance += STEAL_MONEY_AMOUNT;
        thiefShop.sync();

        // 通知小偷
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.thief.stole_money",
                        target.getDisplayName(),
                        STEAL_MONEY_AMOUNT)
                        .withStyle(ChatFormatting.GOLD),
                true);

        // 延迟10秒通知被偷者
        pendingNotifications.add(new PendingNotification(
                targetPlayer,
                "message.noellesroles.thief.money_stolen",
                new Object[] { STEAL_MONEY_AMOUNT },
                NOTIFICATION_DELAY));

        // 成功偷取，进入冷却
        this.cooldown = ABILITY_COOLDOWN;
        this.sync();

        return true;
    }

    /**
     * 偷物品（仿照StupidExpress2的小偷）
     */
    public static class StolenableItemInfo {
        public int slot;
        public ItemStack itemStack;

        public StolenableItemInfo(int slot, ItemStack itemStack) {
            this.slot = slot;
            this.itemStack = itemStack;
        }
    }

    private boolean stealItem(Player target) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof ServerPlayer targetPlayer)) {
            return false;
        }

        // 检查目标是否被淘汰
        if (GameUtils.isPlayerEliminated(target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.thief.target_eliminated")
                            .withStyle(ChatFormatting.RED),
                    true);
            return true;
        }

        // 统计可偷取物品数量
        var gameWorldComponent = SREGameWorldComponent.KEY.get(target.level());

        ArrayList<StolenableItemInfo> arr = new ArrayList<>();
        for (int i = 0; i < target.getInventory().items.size(); i++) {
            ItemStack stack = target.getInventory().items.get(i);
            if (!stack.isEmpty() && canStealItem(stack, target, gameWorldComponent)) {
                arr.add(new StolenableItemInfo(i, stack));
            }
        }

        // 如果没有可偷物品
        if (arr.size() <= 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.thief.no_stealable_items",
                            target.getDisplayName())
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return true; // 没有物品可偷，不进入冷却
        }
        Collections.shuffle(arr);

        StolenableItemInfo stoleninfo = arr.getFirst();

        if (stoleninfo.slot == -1 || stoleninfo.itemStack.isEmpty()) {
            return true;
        }

        // 先获取物品名称（在移除之前）
        Component itemName = stoleninfo.itemStack.getDisplayName();

        // 检查是否是球棒（需要特殊处理）
        boolean isBat = stoleninfo.itemStack.is(TMMItems.BAT);

        // 检查小偷背包是否有空间
        if (isBat) {
            stoleninfo.itemStack = ItemStack.EMPTY;
        }
        boolean canAdd = RoleUtils.insertStackInFreeSlot(serverPlayer, stoleninfo.itemStack.copy());

        if (!canAdd) {
            // 背包满了，归还物品给目标
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.thief.inventory_full")
                            .withStyle(ChatFormatting.RED),
                    true);
            return true; // 失败不进入冷却
        }

        // 检查是否真正偷到了物品（不是EMPTY）
        if (stoleninfo.itemStack.isEmpty()) {
            // 没有真正偷到物品
            if (isBat) {
                // 偷到了球棒，显示球棒氧化消息
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.thief.stole_item_failed",
                                target.getDisplayName(),
                                itemName)
                                .withStyle(ChatFormatting.AQUA),
                        true);
            } else {
                // 其他情况显示偷取失败
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.thief.steal_failed",
                                target.getDisplayName())
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return true; // 失败不进入冷却，不通知被偷者
        }

        // 成功从目标背包移除物品
        target.getInventory().items.set(stoleninfo.slot, ItemStack.EMPTY);
        // 通知小偷
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.thief.stole_item",
                        target.getDisplayName(),
                        itemName)
                        .withStyle(ChatFormatting.AQUA),
                true);

        // 延迟10秒通知被偷者
        pendingNotifications.add(new PendingNotification(
                targetPlayer,
                "message.noellesroles.thief.item_stolen",
                new Object[] { itemName },
                NOTIFICATION_DELAY));

        // 成功偷取，进入冷却
        this.cooldown = ABILITY_COOLDOWN;
        this.sync();

        return true;
    }

    /**
     * 判断物品是否可以被偷取
     * 只允许偷取指定的武器和道具
     */
    private boolean canStealItem(ItemStack stack, Player target, SREGameWorldComponent gameWorldComponent) {
        if (stack.isEmpty())
            return false;

        // 禁止偷取的物品
        // 金锭（小偷的荣誉）
        if (stack.is(Items.GOLD_INGOT))
            return false;
        // 只允许偷取以下物品：

        // 枪械类

        if (stack.is(HSRItems.BANDIT_REVOLVER))
            return true; // 匪徒手枪
        if (stack.is(ModItems.ONCE_REVOLVER))
            return true; // 一次性手枪
        if (stack.is(TMMItemTags.GUNS)) {
            if (gameWorldComponent.isKillerTeam(target)) {
                return true;
            }
            return false;
        }

        // 武器类
        if (stack.is(TMMItems.KNIFE))
            return true; // 匕首
        if (stack.is(TMMItems.BAT))
            return true; // 球棒（小巧思）

        // 投掷物类
        if (stack.is(TMMItems.GRENADE))
            return true; // 手榴弹
        if (stack.is(TMMItems.FIRECRACKER))
            return true; // 鞭炮
        if (stack.is(ModItems.BOMB))
            return true; // 炸弹

        // 道具类
        if (stack.is(TMMItems.SCORPION))
            return true; // 蝎子
        if (stack.is(TMMItems.POISON_VIAL))
            return true; // 毒药瓶
        if (stack.is(TMMItems.CROWBAR))
            return true; // 撬棍
        if (stack.is(TMMItems.LOCKPICK))
            return true; // 开锁器
        if (stack.is(TMMItems.BODY_BAG))
            return true; // 裹尸袋
        if (stack.is(TMMItems.NOTE))
            return true; // 纸条
        if (stack.is(ModItems.HANDCUFFS))
            return true; // 手铐

        // 特殊物品类（来自HSRItems）
        if (stack.is(HSRItems.TOXIN))
            return true; // 毒针
        if (stack.is(HSRItems.ANTIDOTE))
            return true; // 解药

        // NoellesRoles 特殊物品
        if (stack.is(ModItems.BOXING_GLOVE))
            return true; // 拳套
        if (stack.is(ModItems.DEFIBRILLATOR))
            return true; // 除颤仪
        if (stack.is(ModItems.DELUSION_VIAL))
            return true; // 幻觉试剂
        if (stack.is(ModItems.ANTIDOTE_REAGENT))
            return true; // 解药试剂
        if (stack.is(ModItems.BLANK_CARTRIDGE))
            return true; // 空包弹
        if (stack.is(ModItems.SMOKE_GRENADE))
            return true; // 烟雾弹
        if (stack.is(ModItems.SCREWDRIVER))
            return true; // 加固门道具
        if (stack.is(ModItems.REINFORCEMENT))
            return true; // 加固门道具
        if (stack.is(ModItems.ALARM_TRAP))
            return true; // 警报陷阱
        if (stack.is(ModItems.LOCK_ITEM))
            return true; // 锁
        if (stack.is(ModItems.DELIVERY_BOX))
            return true; // 传递盒
        if (stack.is(ModItems.HALLUCINATION_BOTTLE))
            return true; // 迷幻瓶
        if (stack.is(ModItems.NIGHT_VISION_GLASSES))
            return true; // 夜视镜
        if (stack.is(ModItems.WHEELCHAIR))
            return true; // 轮椅

        // 投掷物
        if (stack.is(ModItems.CHLORINE_BOMB))
            return true; // 毒气弹
        if (stack.is(ModItems.PURIFY_BOMB))
            return true; // 净化弹
        if (stack.is(ModItems.FLASH_GRENADE))
            return true; // 闪光弹
        if (stack.is(ModItems.DECOY_GRENADE))
            return true; // 诱饵弹

        // 护盾试剂（来自TMM）
        if (stack.is(TMMItems.DEFENSE_VIAL))
            return true; // 护盾试剂

        if (stack.is(TMMItems.KEY))
            return true;
        // 万能钥匙和乘务员钥匙
        if (stack.is(ModItems.MASTER_KEY))
            return true;
        if (stack.is(ModItems.MASTER_KEY_P))
            return true;

        // 其他物品不可偷取
        return false;
    }

    /**
     * 卖物品（手上的物品卖50金币，只能卖可偷取的物品）
     */
    private boolean sellItem() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        // 获取手持物品
        ItemStack heldItem = player.getMainHandItem();

        // 检查是否空手
        if (heldItem.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.thief.no_item_to_sell")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 检查物品是否可以出售（使用与偷物品相同的逻辑）
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!canSellItem(heldItem, gameWorld)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.thief.cannot_sell_item")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 移除物品
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // 给予50金币
        SREPlayerShopComponent thiefShop = SREPlayerShopComponent.KEY.get(player);
        thiefShop.balance += 50;
        thiefShop.sync();

        // 获取物品名称
        Component itemName = heldItem.getDisplayName();

        // 通知小偷
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.thief.item_sold", itemName, 50)
                        .withStyle(ChatFormatting.GREEN),
                true);

        return true;
    }

    /**
     * 判断物品是否可以出售
     * 只允许出售可偷取的物品（不检查目标阵营）
     */
    private boolean canSellItem(ItemStack stack, SREGameWorldComponent gameWorldComponent) {
        if (stack.isEmpty())
            return false;

        // 禁止偷取/出售的物品
        // 金锭（小偷的荣誉）
        if (stack.is(Items.GOLD_INGOT))
            return false;

        // 枪械类
        if (stack.is(HSRItems.BANDIT_REVOLVER))
            return true; // 匪徒手枪
        if (stack.is(ModItems.ONCE_REVOLVER))
            return true; // 一次性手枪
        if (stack.is(TMMItemTags.GUNS))
            return true; // 枪械（小偷可以偷任何枪械来卖）

        // 武器类
        if (stack.is(TMMItems.KNIFE))
            return true; // 匕首
        if (stack.is(TMMItems.BAT))
            return true; // 球棒

        // 投掷物类
        if (stack.is(TMMItems.GRENADE))
            return true; // 手榴弹
        if (stack.is(TMMItems.FIRECRACKER))
            return true; // 鞭炮
        if (stack.is(ModItems.BOMB))
            return true; // 炸弹

        // 道具类
        if (stack.is(TMMItems.SCORPION))
            return true; // 蝎子
        if (stack.is(TMMItems.POISON_VIAL))
            return true; // 毒药瓶
        if (stack.is(TMMItems.CROWBAR))
            return true; // 撬棍
        if (stack.is(TMMItems.LOCKPICK))
            return true; // 开锁器
        if (stack.is(TMMItems.BODY_BAG))
            return true; // 裹尸袋
        if (stack.is(TMMItems.NOTE))
            return true; // 纸条
        if (stack.is(ModItems.HANDCUFFS))
            return true; // 手铐

        // 特殊物品类（来自HSRItems）
        if (stack.is(HSRItems.TOXIN))
            return true; // 毒针
        if (stack.is(HSRItems.ANTIDOTE))
            return true; // 解药

        // NoellesRoles 特殊物品
        if (stack.is(ModItems.BOXING_GLOVE))
            return true; // 拳套
        if (stack.is(ModItems.DEFIBRILLATOR))
            return true; // 除颤仪
        if (stack.is(ModItems.DELUSION_VIAL))
            return true; // 幻觉试剂
        if (stack.is(ModItems.ANTIDOTE_REAGENT))
            return true; // 解药试剂
        if (stack.is(ModItems.BLANK_CARTRIDGE))
            return true; // 空包弹
        if (stack.is(ModItems.SMOKE_GRENADE))
            return true; // 烟雾弹
        if (stack.is(ModItems.REINFORCEMENT))
            return true; // 加固门道具
        if (stack.is(ModItems.ALARM_TRAP))
            return true; // 警报陷阱
        if (stack.is(ModItems.LOCK_ITEM))
            return true; // 锁
        if (stack.is(ModItems.DELIVERY_BOX))
            return true; // 传递盒
        if (stack.is(ModItems.HALLUCINATION_BOTTLE))
            return true; // 迷幻瓶
        if (stack.is(ModItems.NIGHT_VISION_GLASSES))
            return true; // 夜视镜
        if (stack.is(ModItems.WHEELCHAIR))
            return true; // 轮椅

        // 投掷物
        if (stack.is(ModItems.CHLORINE_BOMB))
            return true; // 毒气弹
        if (stack.is(ModItems.PURIFY_BOMB))
            return true; // 净化弹
        if (stack.is(ModItems.FLASH_GRENADE))
            return true; // 闪光弹
        if (stack.is(ModItems.DECOY_GRENADE))
            return true; // 诱饵弹

        // 护盾试剂（来自TMM）
        if (stack.is(TMMItems.DEFENSE_VIAL))
            return true; // 护盾试剂

        if (stack.is(TMMItems.KEY))
            return true; // 钥匙

        // 万能钥匙和乘务员钥匙
        if (stack.is(ModItems.MASTER_KEY))
            return true;
        if (stack.is(ModItems.MASTER_KEY_P))
            return true;

        // 铁门钥匙
        if (stack.is(io.wifi.starrailexpress.index.TMMItems.IRON_DOOR_KEY))
            return true;

        // 其他物品不可出售
        return false;
    }

    /**
     * 获取当前看向的玩家
     */
    private Player getLookedAtPlayer() {
        double maxDistance = 4.0;
        Player closestPlayer = null;
        double closestDistance = maxDistance;

        for (Player otherPlayer : player.level().players()) {
            if (otherPlayer == player)
                continue;
            if (GameUtils.isPlayerEliminated(otherPlayer))
                continue;

            double distance = player.distanceTo(otherPlayer);
            if (distance < closestDistance && player.hasLineOfSight(otherPlayer)) {
                closestDistance = distance;
                closestPlayer = otherPlayer;
            }
        }

        return closestPlayer;
    }

    /**
     * 处理小偷击杀目标（获得200金币）
     */
    public void handleKilledVictim(Player victim) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.THIEF)) {
            return;
        }

        // 给予200金币
        SREPlayerShopComponent thiefShop = SREPlayerShopComponent.KEY.get(player);
        thiefShop.balance += 200;
        thiefShop.sync();

        // 通知小偷
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.thief.kill_reward", 200)
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    /**
     * 检查小偷独立胜利条件
     * 手持小偷的荣誉（金锭）回房间睡觉则胜利
     */
    public static boolean checkThiefVictory(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);

        // 检查是否有小偷存活
        boolean hasThiefAlive = false;
        ServerPlayer thief = null;

        for (ServerPlayer p : serverLevel.players()) {
            if (gameWorld.isRole(p, ModRoles.THIEF)
                    && !GameUtils.isPlayerEliminated(p)) {
                hasThiefAlive = true;
                thief = p;
                break;
            }
        }

        if (!hasThiefAlive || thief == null) {
            return false;
        }

        // 检查小偷是否手持小偷的荣誉（金锭）
        ItemStack heldItem = thief.getMainHandItem();
        if (!heldItem.is(Items.GOLD_INGOT)) {
            return false;
        }

        // 检查小偷是否在睡觉
        if (!thief.isSleeping()) {
            return false;
        }

        // 小偷胜利！
        RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM, "thief",
                OptionalInt.of(new java.awt.Color(255, 215, 0).getRGB()));

        return true;
    }

    /**
     * 获取购买小偷的荣誉所需金币数
     */
    public static int getHonorCost(int totalPlayers) {
        return totalPlayers * HONOR_COST_PER_PLAYER;
    }

    @Override
    public void serverTick() {
        // 减少冷却
        var gwc = SREGameWorldComponent.KEY.get(player.level());
        if (!gwc.isRole(player, ModRoles.THIEF))
            return;
        var psc = SREPlayerShopComponent.KEY.get(player);
        if (player.level().getGameTime() % 20 == 0) {
            if (psc.balance >= this.honorCost) {
                if (RoleUtils.insertStackInFreeSlot(player, Items.GOLD_INGOT.getDefaultInstance())) {
                    psc.addToBalance(-honorCost);
                    player.displayClientMessage(
                            Component.translatable("message.thief.honor_got").withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 60 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 处理延迟通知
        if (!pendingNotifications.isEmpty()) {
            java.util.Iterator<PendingNotification> iterator = pendingNotifications.iterator();
            while (iterator.hasNext()) {
                PendingNotification notification = iterator.next();
                notification.delayTicks--;
                if (notification.delayTicks <= 0) {
                    if (notification.targetPlayer != null
                            && !GameUtils.isPlayerEliminated(notification.targetPlayer)) {
                        notification.targetPlayer.displayClientMessage(
                                Component.translatable(notification.messageKey, notification.messageArgs)
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }
                    iterator.remove();
                }
            }
        }
    }

    public void clientTick() {
        if (this.cooldown > 1) {
            this.cooldown--;
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putInt("Cooldown", this.cooldown);
        tag.putInt("CurrentMode", this.currentMode);
        tag.putBoolean("IsInSelectionMode", this.isInSelectionMode);

        tag.putInt("honorCost", honorCost);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        honorCost = tag.contains("honorCost") ? tag.getInt("honorCost") : -1;
        this.cooldown = tag.getInt("Cooldown");
        this.currentMode = tag.getInt("CurrentMode");
        this.isInSelectionMode = tag.getBoolean("IsInSelectionMode");
    }
}
