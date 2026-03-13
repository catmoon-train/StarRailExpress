package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.cca.StarGameTimeComponent;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 怀表
 * - 右键使用查看当前局内游戏时间
 * - 使用后进入60秒冷却
 */
public class PocketWatchItem extends Item {

    /** 冷却时间（60秒 = 1200 tick） */
    public static final int COOLDOWN_TICKS = 1200;

    /** NBT标签：上次使用时间 */
    private static final String TAG_LAST_USE_TIME = "last_use_time";

    public PocketWatchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // 检查游戏是否正在进行
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning()) {
            return InteractionResultHolder.pass(itemStack);
        }

        // 检查玩家是否存活
        if (!GameFunctions.isPlayerAliveAndSurvival(player)) {
            return InteractionResultHolder.pass(itemStack);
        }

        // 检查冷却
        long currentTime = world.getGameTime();
        CustomData customData = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        long lastUseTime = tag.contains(TAG_LAST_USE_TIME) ? tag.getLong(TAG_LAST_USE_TIME) : 0;
        long cooldownRemaining = COOLDOWN_TICKS - (currentTime - lastUseTime);

        if (cooldownRemaining > 0) {
            int cooldownSeconds = (int) ((cooldownRemaining + 19) / 20);
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.pocket_watch.on_cooldown", cooldownSeconds)
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            } else {
                // 播放冷却音效（仅客户端）
                world.playSound(null, player.blockPosition(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 0.5F);
            }
            return InteractionResultHolder.success(itemStack);
        }

        // 获取游戏时间
        StarGameTimeComponent gameTime = StarGameTimeComponent.KEY.get(world);
        long gameTicks = gameTime.getTime();
        int gameSeconds = (int) (gameTicks / 20);
        int minutes = gameSeconds / 60;
        int seconds = gameSeconds % 60;

        // 显示游戏时间
        if (!world.isClientSide) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.pocket_watch.show_time", minutes, seconds)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    true);
        } else {
            // 播放使用音效（仅客户端）
            world.playSound(null, player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
        }

        // 更新冷却
        tag.putLong(TAG_LAST_USE_TIME, currentTime);
        itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return InteractionResultHolder.success(itemStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.pocket_watch.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.pocket_watch.tooltip2")
                .withStyle(ChatFormatting.GRAY));

        // 显示冷却剩余时间
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        long currentTime = System.currentTimeMillis() / 50; // 近似计算，实际应该从level获取
        long lastUseTime = tag.contains(TAG_LAST_USE_TIME) ? tag.getLong(TAG_LAST_USE_TIME) : 0;
        long cooldownRemaining = COOLDOWN_TICKS - (currentTime - lastUseTime);

        if (cooldownRemaining > 0) {
            int cooldownSeconds = (int) ((cooldownRemaining + 19) / 20);
            tooltip.add(Component.translatable("item.noellesroles.pocket_watch.cooldown", cooldownSeconds)
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("item.noellesroles.pocket_watch.ready")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
