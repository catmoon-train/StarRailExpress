package io.wifi.starrailexpress.client.render.hud.stamina.utils;

import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

/**
 * 在快捷栏上方显示物品冷却时间
 * <10s 显示1位小数，>=10s 不显示小数
 */
public class HotbarCooldownRenderer {

    /**
     * 主手冷却
     */
    private static float lastCooldown = 0f;
    private static boolean playedCooldownSound = false;
    private static ItemStack lastMainHandStack = ItemStack.EMPTY; // 用于跟踪上一次的主手物品

    private static final int SLOT_WIDTH = 20; // 每个快捷栏槽位宽度

    public static void render(GuiGraphics context, float delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        Font font = mc.font;
        ItemCooldowns cooldowns = player.getCooldowns();

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        // 快捷栏中心 X 坐标，槽位从 -4 到 +4
        int centerX = screenWidth / 2;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (!cooldowns.isOnCooldown(item)) continue;

            ItemCooldowns.CooldownInstance instance = cooldowns.cooldowns.get(item);
            if (instance == null) continue;

            int remainingTicks = instance.endTime - cooldowns.tickCount;
            if (remainingTicks <= 0) continue;

            float remainingSeconds = remainingTicks / 20.0f;

            String cooldownText;
            if (remainingSeconds < 10.0f) {
                cooldownText = String.format("%.1f", remainingSeconds);
            } else {
                cooldownText = String.format("%.0f", remainingSeconds);
            }

            // 该槽位中心 X
            int slotCenterX = centerX + (slot - 4) * SLOT_WIDTH;
            // 快捷栏槽位顶部约在 screenHeight - 22，文字放在槽位上方
            int textY = screenHeight - 32;

            // 文字宽度用于居中
            int textWidth = font.width(cooldownText);
            int textX = slotCenterX - textWidth / 2;

            context.pose().pushPose();
            context.pose().translate(0, 0, 200);

            // 阴影
            context.drawString(font, cooldownText, textX + 1, textY + 1, 0x80000000, false);
            // 主文字（金色）
            context.drawString(font, cooldownText, textX, textY, 0xFFFFD700, false);

            context.pose().popPose();
        }
    }
    
    /**
     * 渲染主手物品冷却提示
     */
    public static void renderMainHandCooldown(GuiGraphics context, LocalPlayer player, float delta) {
        ItemStack mainHandStack = player.getMainHandItem();
        ItemCooldowns cooldowns = player.getCooldowns();
        float cooldown = cooldowns.getCooldownPercent(mainHandStack.getItem(), delta);

        // 检查是否是同一个物品且冷却刚刚结束
        if (lastCooldown > 0 && cooldown == 0 && !playedCooldownSound
                && ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
            // 播放冷却结束音效
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.0f));
            playedCooldownSound = true;
        } else if (cooldown > 0 || !ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
            // 如果物品已切换，则重置冷却音效标志
            if (!ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
                // 如果切换到刀，则播放切刀音效
                // if (mainHandStack.getItem() instanceof KnifeItem
                //         && !(lastMainHandStack.getItem() instanceof KnifeItem)) {
                //     Minecraft.getInstance().getSoundManager().play(
                //             SimpleSoundInstance.forUI(SoundEvents.IRON_GOLEM_REPAIR, 0.4f, 2.1f));
                // }
                playedCooldownSound = false;
            }
            // 如果物品仍在冷却中，重置音效标志
            if (cooldown > 0) {
                playedCooldownSound = false;
            }
        }

        // 更新上一次冷却值和物品
        lastCooldown = cooldown;
        lastMainHandStack = mainHandStack.copy();

        // 如果物品在冷却中，显示冷却百分比
        if (cooldown > 0) {
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();

            // 在屏幕中心稍上方显示冷却文字
            int x = screenWidth / 2;
            int y = screenHeight - 48; // 物品栏上方

            String cooldownText = String.format("%d%%", (int) (cooldown * 100));

            // 根据冷却百分比改变颜色：红色->橙色->绿色
            int textColor;
            if (cooldown > 0.7f) {
                textColor = 0xFFFF0000; // 红色
            } else if (cooldown > 0.3f) {
                textColor = 0xFFFFA500; // 橙色
            } else {
                textColor = 0xFF00FF00; // 绿色
            }

            // 绘制文字背景（半透明黑色）
            // int textWidth = Minecraft.getInstance().font.width(cooldownText);
            // int padding = 4;
            // context.fill(
            // x - textWidth / 2 - padding,
            // y - padding,
            // x + textWidth / 2 + padding,
            // y + 9 + padding,
            // 0x80000000
            // );

            // 绘制冷却文字
            context.drawCenteredString(
                    Minecraft.getInstance().font,
                    cooldownText,
                    x,
                    y,
                    textColor);

        }
    }

}
