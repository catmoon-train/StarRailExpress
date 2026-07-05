package io.wifi.starrailexpress.client.render.hud.stamina.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

/**
 * 在快捷栏上方显示物品冷却时间
 * <10s 显示1位小数，>=10s 不显示小数
 */
public class HotbarCooldownRenderer {

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
}
