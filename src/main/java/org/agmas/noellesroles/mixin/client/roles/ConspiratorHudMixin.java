package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.component.ConspiratorPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阴谋家 HUD 显示
 * 显示当前目标和倒计时
 */
@Mixin(RoleNameRenderer.class)
public class ConspiratorHudMixin {
    
    @Inject(method = "renderHud", at = @At("HEAD"))
    private static void renderConspiratorHud(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if(client.player.isSpectator()) return;
        
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(client.player.level());
        if (!gameWorld.isRole(client.player, ModRoles.CONSPIRATOR)) return;
        if (!SREClient.isPlayerAliveAndInSurvival()) return;
        
        ConspiratorPlayerComponent component = ConspiratorPlayerComponent.KEY.get(client.player);
        
        context.pose().pushPose();
        
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int yOffset = screenHeight - 28;  // 右下角
        int xOffset = screenWidth - 200;  // 距离右边缘
        
        // 检查是否有正在进行的诅咒（目标列表中至少有一个猜测正确的目标）
        boolean hasActiveCurse = false;
        String targetName = "";
        for (ConspiratorPlayerComponent.TargetInfo targetInfo : component.targetList) {
            if (targetInfo.guessCorrect && targetInfo.deathCountdown > 0) {
                hasActiveCurse = true;
                // 显示第一个有倒计时的目标
                targetName = targetInfo.targetName;
                break;
            }
        }
        
        // 如果有正在进行的诅咒
        if (hasActiveCurse && !targetName.isEmpty()) {
            Component targetText = Component.translatable("tip.noellesroles.conspirator.target",
                targetName, component.getCountdownSeconds())
                .withStyle(ChatFormatting.DARK_PURPLE);
            context.drawString(renderer, targetText, xOffset, yOffset, ModRoles.CONSPIRATOR.color());
        } else {
            // 显示提示
            Component hintText = Component.translatable("tip.noellesroles.conspirator.no_target")
                .withStyle(ChatFormatting.GRAY);
            context.drawString(renderer, hintText, xOffset, yOffset, 0x888888);
        }
        
        context.pose().popPose();
    }
}