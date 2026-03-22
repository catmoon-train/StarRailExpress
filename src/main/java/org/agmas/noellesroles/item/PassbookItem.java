package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 存折
 * - 用于存储和取出金币
 * - 右键存钱：最多存入300金币，并扣除实际存入金额
 * - 右键取钱：将存折中的金币全部取出到玩家身上，存折消失
 */
public class PassbookItem extends Item {

    /** NBT标签：存储的金币数量 */
    private static final String TAG_BALANCE = "balance";
    /** 存折可存储的金币上限 */
    private static final int MAX_STORED_BALANCE = 300;

    public PassbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (!world.isClientSide) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.pass(itemStack);
            }

            // 检查存折中是否有金币
            int storedBalance = getStoredBalance(itemStack);

            if (storedBalance > 0) {
                // 取钱逻辑：取出存折中的金币到玩家身上，存折消失
                withdrawMoney(serverPlayer, itemStack, storedBalance, hand);
            } else {
                // 存钱逻辑：最多存入300金币，并扣除对应金额
                depositMoney(serverPlayer, itemStack);
            }

            // 播放翻书声
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        return InteractionResultHolder.success(itemStack);
    }

    /**
     * 存钱：存入不超过上限的金币，并仅扣除实际存入的金额
     */
    private void depositMoney(ServerPlayer player, ItemStack itemStack) {
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        int balance = shopComponent.balance;

        if (balance <= 0) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.passbook.no_money_to_deposit")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        int depositAmount = Math.min(balance, MAX_STORED_BALANCE);

        // 仅扣除实际存入的金币
        shopComponent.balance -= depositAmount;
        shopComponent.sync();

        // 存入存折
        setStoredBalance(itemStack, depositAmount);

        // 通知玩家
        player.displayClientMessage(
            Component.translatable("message.noellesroles.passbook.deposited", depositAmount)
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    /**
     * 取钱：取出存折中的金币到玩家身上，存折消失
     */
    private void withdrawMoney(ServerPlayer player, ItemStack itemStack, int balance, InteractionHand hand) {
        // 增加玩家金币
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        shopComponent.balance += balance;
        shopComponent.sync();

        // 通知玩家
        player.displayClientMessage(
                Component.translatable("message.noellesroles.passbook.withdrawn", balance)
                        .withStyle(ChatFormatting.GREEN),
                true);

        // 移除存折
        player.setItemInHand(hand, ItemStack.EMPTY);
    }

    /**
     * 获取存折中存储的金币数量
     */
    private static int getStoredBalance(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.getInt(TAG_BALANCE);
    }

    /**
     * 设置存折中存储的金币数量
     */
    private static void setStoredBalance(ItemStack stack, int balance) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_BALANCE, balance);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        int balance = getStoredBalance(stack);

        if (balance > 0) {
            tooltip.add(Component.translatable("item.noellesroles.passbook.balance", balance)
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.noellesroles.passbook.tooltip.withdraw")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("item.noellesroles.passbook.tooltip.empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.noellesroles.passbook.tooltip.deposit")
                    .withStyle(ChatFormatting.AQUA));
        }
        super.appendHoverText(stack, context, tooltip, type);
    }
}
