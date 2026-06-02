package org.agmas.noellesroles.game.roles.innocent.mortician;

import java.util.UUID;

import org.agmas.noellesroles.component.ModComponents;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.gui.PlayerBodyEntityContainer;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MorticianRole extends NormalRole {

    public MorticianRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean canSeeBodyItems(Player player, PlayerBodyEntity body) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;
        var cca = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (cca == null || cca.gameMode == null) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return false;
        }
        // 检查冷却
        var morticianComponent = org.agmas.noellesroles.component.ModComponents.MORTICIAN.get(serverPlayer);
        if (!morticianComponent.isCooldownReady()) {
            return false;
        }
        // 检查这具尸体是否已被打开过
        if (morticianComponent.hasOpenedCorpse(body.getUUID())) {
            return false;
        }
        // 检查是否在范围内（10格水平，3格垂直）
        double dx = serverPlayer.getX() - body.getX();
        double dy = serverPlayer.getY() - body.getY();
        double dz = serverPlayer.getZ() - body.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        return horizontalDist <= 10.0 && Math.abs(dy) <= 3.0;
    }

    @Override
    public void onClosedPlayerBodyChest(Player player, PlayerBodyEntity corpseEntity,
            PlayerBodyEntityContainer container) {
        if (corpseEntity != null) {
            UUID corpseUuid = corpseEntity.getUUID();
            MorticianPlayerComponent mortician = ModComponents.MORTICIAN.get(player);
            if (mortician != null) {
                mortician.onCorpseOpened(corpseUuid);
                // 发送消息提示
                if (player instanceof ServerPlayer serverPlayer) {
                    int itemsTaken = getMorticianItemsTaken(player);
                    if (itemsTaken > 0) {
                        serverPlayer.displayClientMessage(
                                Component.translatable("message.noellesroles.mortician.items_taken", itemsTaken)
                                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                                true);
                    }
                }
            }
        }
    }

    @Override
    public boolean canGetBodyContent(int slotId, int button, ClickType clickType, Player player,
            PlayerBodyEntityContainer container, int rows, NonNullList<Slot> slots) {
        MorticianPlayerComponent mortician = ModComponents.MORTICIAN.get(player);
        if (mortician == null || !mortician.isCooldownReady()) {
            // 冷却中，禁止任何操作
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.mortician.on_cooldown")
                                .withStyle(net.minecraft.ChatFormatting.RED),
                        true);
            }
            return false;
        }

        // 如果操作涉及容器槽位（索引 0 ~ rows*9-1）
        if (slotId >= 0 && slotId < rows * 9) {
            Slot slot = slots.get(slotId);

            switch (clickType) {
                case THROW: // 丢出（Q键）
                    return false; // 直接禁止
                case PICKUP: // 左键点击拿起物品
                case SWAP: // 数字键交换
                case CLONE: // 中键复制
                case QUICK_MOVE: // Shift+点击
                case QUICK_CRAFT: // Ctrl+点击
                    // 殡仪员物品限制检查（在 super.clicked 之前阻止）
                    if (slot != null && slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        if (isDerringer(stack) || isCommandBlock(stack)) {
                            return false; // 禁止拿取，不关闭页面
                        }
                        if (isRevolver(stack) && morticianHasRevolver(player)) {
                            return false; // 禁止拿取，不关闭页面
                        }
                    }

                    if (slot != null && !slot.hasItem()) {
                        morticianTookItem(player);

                        // 检查是否已经拿够了2个物品
                        if (!canMorticianTakeMore(player)) {
                            // 关闭菜单
                            if (player instanceof ServerPlayer serverPlayer) {
                                serverPlayer.closeContainer();
                            }
                        }
                    }
                    return true;
                default:
                    // 殡仪员物品限制检查
                    if (slot != null && slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        if (isDerringer(stack) || isCommandBlock(stack))
                            return false;
                        if (isRevolver(stack) && morticianHasRevolver(player))
                            return false;
                    }
                    return true;
            }
        }
        // 非容器槽位放行
        return true;
    }

    /**
     * 检查物品是否是命令方块
     */
    private boolean isCommandBlock(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.COMMAND_BLOCK) ||
                stack.is(Items.REPEATING_COMMAND_BLOCK) ||
                stack.is(Items.CHAIN_COMMAND_BLOCK);
    }

    /**
     * 检查物品是否是德林加手枪
     */
    private boolean isDerringer(ItemStack stack) {
        return !stack.isEmpty() && stack.is(io.wifi.starrailexpress.index.TMMItems.DERRINGER);
    }

    /**
     * 检查物品是否是手枪
     */
    private boolean isRevolver(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        return stack.is(TMMItemTags.GUNS);
    }

    /**
     * 检查殡仪员身上是否已有左轮手枪或巡警手枪
     */
    private boolean morticianHasRevolver(Player player) {
        for (var list : player.getInventory().compartments) {
            for (var stack : list) {
                if (isRevolver(stack))
                    return true;
            }
        }
        return false;
    }

    /**
     * 记录殡仪员拿取了一个物品
     */
    public MorticianPlayerComponent MoCCA(Player player) {
        return MorticianPlayerComponent.KEY.get(player);
    }

    public void morticianTookItem(Player player) {
        MoCCA(player).morticianItemsTaken++;
    }

    /**
     * 获取殡仪员已拿取的物品数量
     */
    public int getMorticianItemsTaken(Player player) {
        return MoCCA(player).morticianItemsTaken;
    }

    /**
     * 检查殡仪员是否还能拿取物品
     */
    public boolean canMorticianTakeMore(Player player) {
        return MoCCA(player).morticianItemsTaken < 1;
    }

    @Override
    public void startOpenPlayerBody(Player player) {
        MoCCA(player).morticianLooting = true;
        MoCCA(player).morticianItemsTaken = 0;
    }

    @Override
    public boolean canTakePlayerBodyItem(Player player, Container container, int slot, ItemStack stack) {

        // 检查是否已达到拿取上限（最多1个）
        if (MoCCA(player).morticianItemsTaken >= 1) {
            return false;
        }

        // 检查是否是命令方块
        if (isCommandBlock(stack)) {
            return false;
        }

        // 检查是否是德林加手枪
        if (isDerringer(stack)) {
            return false;
        }

        // 如果身上已有左轮手枪，无法再拿取左轮手枪或巡警手枪
        if (isRevolver(stack) && hasRevolverInInventory(player)) {
            return false;
        }

        return true;

    }
    // 殡仪员特殊检查

    @Override
    public void stopOpenPlayerBody(Player player) {
        MoCCA(player).morticianLooting = false;
        MoCCA(player).morticianItemsTaken = 0;
    }

    /**
     * 检查玩家物品栏中是否已有左轮手枪或巡警手枪
     */
    private boolean hasRevolverInInventory(Player player) {
        for (var list : player.getInventory().compartments) {
            for (var stack : list) {
                if (isRevolver(stack))
                    return true;
            }
        }
        return false;
    }
}
