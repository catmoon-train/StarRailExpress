package org.agmas.noellesroles.mixin.client.roles.dio;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.DIOPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 迪奥 HUD Mixin
 * 
 * 显示迪奥的技能状态：
 * - The World: 时间停止可用次数、冷却进度
 * - 最后的狂欢：是否已解锁、是否激活中、剩余时间
 * - 总吸食尸体次数
 */
@Mixin(Gui.class)
public class DIOHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderDIOStatus(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;

        if (client.player.isSpectator())
            return;

        // 检查是否是迪奥
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.DIO))
            return;

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(client.player))
            return;

        // 获取迪奥组件
        DIOPlayerComponent dioComponent = DIOPlayerComponent.KEY.get(client.player);

        // 渲染位置 - 右下角（在设陷者下方或其他位置）
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = screenWidth - 120; // 距离右边缘（比特拉普更靠左）
        int y = screenHeight - 100; // 距离底部（比特拉普更高）

        Font textRenderer = client.font;

        // ==================== 显示 The World 技能状态 ====================
        int timeStopCharges = dioComponent.getTimeStopCharges();
        int maxCharges = DIOPlayerComponent.MAX_TIME_STOP_CHARGES;
        int cooldown = dioComponent.timeStopCooldown;

        // 标题
        Component timeStopTitle = Component.translatable("hud.noellesroles.dio.the_world")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        context.drawString(textRenderer, timeStopTitle, x, y, 0xFFD700); // 金色

        // 时间停止使用次数
        int chargesColor;
        if (timeStopCharges == 0) {
            chargesColor = CommonColors.RED;
        } else if (timeStopCharges >= maxCharges) {
            chargesColor = CommonColors.GREEN;
        } else {
            chargesColor = 0xFFFF00; // 黄色
        }

        Component chargesText = Component.translatable(
                "hud.noellesroles.dio.charges",
                timeStopCharges,
                maxCharges);
        context.drawString(textRenderer, chargesText, x, y + 12, chargesColor);

        // 冷却时间显示
        if (cooldown > 0) {
            float cooldownSeconds = cooldown / 20.0f;
            Component cooldownText = Component.translatable(
                    "hud.noellesroles.dio.cooldown",
                    String.format("%.0f", cooldownSeconds));
            context.drawString(textRenderer, cooldownText, x, y + 24, CommonColors.RED);
        } else if (timeStopCharges > 0) {
            Component readyText = Component.translatable("hud.noellesroles.dio.ready",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
            context.drawString(textRenderer, readyText, x, y + 24, CommonColors.GREEN);
        }

        // ==================== 显示最后的狂欢状态 ====================
        int carnivalY = y + 40;

        Component carnivalTitle = Component.translatable("hud.noellesroles.dio.final_carnival")
                .withStyle(ChatFormatting.DARK_RED);
        context.drawString(textRenderer, carnivalTitle, x, carnivalY, 0x8B0000); // 深红色

        if (!dioComponent.hasFinalCarnival()) {
            // 未解锁，显示进度
            int feedCount = dioComponent.getTotalFeedCount();
            int threshold = DIOPlayerComponent.FINAL_CARNIVAL_THRESHOLD;
            Component progressText = Component.translatable(
                    "hud.noellesroles.dio.carnival_locked",
                    feedCount,
                    threshold);
            context.drawString(textRenderer, progressText, x, carnivalY + 12, 0xAAAAAA);
        } else if (dioComponent.isFinalCarnivalActive) {
            // 激活中，显示剩余时间
            float remainingSeconds = dioComponent.tempLifeRemaining / 20.0f;
            Component activeText = Component.translatable(
                    "hud.noellesroles.dio.carnival_active",
                    String.format("%.1f", remainingSeconds));
            context.drawString(textRenderer, activeText, x, carnivalY + 12, CommonColors.RED);
        } else {
            // 已解锁但未激活
            Component unlockedText = Component.translatable("hud.noellesroles.dio.carnival_ready");
            context.drawString(textRenderer, unlockedText, x, carnivalY + 12, CommonColors.GREEN);
        }

        // ==================== 显示总吸食次数 ====================
        int feedY = carnivalY + 30;
        int feedCount = dioComponent.getTotalFeedCount();
        Component feedText = Component.translatable(
                "hud.noellesroles.dio.total_feeds",
                feedCount);
        context.drawString(textRenderer, feedText, x, feedY, 0xDDDDDD);
    }
}
