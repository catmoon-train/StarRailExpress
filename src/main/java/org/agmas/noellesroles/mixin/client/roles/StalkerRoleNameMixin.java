package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.component.AdmirerPlayerComponent;
import org.agmas.noellesroles.component.StalkerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 跟踪者和爱慕者角色名称显示 Mixin
 * 
 * 功能：
 * 1. 在跟踪者名称后显示阶段信息（一阶/二阶/三阶）
 * 2. 在爱慕者名称后显示能量信息
 */
@Mixin(RoleNameRenderer.class)
public class StalkerRoleNameMixin {

    @Shadow
    private static float nametagAlpha;

    /**
     * 在角色名称渲染后添加阶段/能量信息
     */
    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I", ordinal = 0, shift = At.Shift.AFTER))
    private static void renderPhaseInfo(Font renderer, LocalPlayer player, GuiGraphics context,
            DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.player.level());

        // 只有旁观者或创作模式能看到详细信息
        if (!SREClient.isPlayerSpectatingOrCreative())
            return;

        // 获取当前查看的玩家（通过射线追踪或其他方式）
        // 这里使用当前玩家本身来检查角色
        Player localPlayer = client.player;

        // =============== 跟踪者阶段显示 ===============
        if (gameWorld.isRole(localPlayer, ModRoles.STALKER)) {
            StalkerPlayerComponent stalkerComp = StalkerPlayerComponent.KEY.get(localPlayer);
            if (stalkerComp.isStalkerMarked && stalkerComp.phase > 0) {
                Component phaseText = getPhaseText(stalkerComp.phase);
                // 在角色名称下方显示阶段信息
                context.drawString(renderer, phaseText, -renderer.width(phaseText) / 2, 12,
                        ModRoles.STALKER.color() | (int) (nametagAlpha * 255.0F) << 24);
            }
        }

        // =============== 爱慕者能量显示 ===============
        if (gameWorld.isRole(localPlayer, ModRoles.ADMIRER)) {
            AdmirerPlayerComponent admirerComp = AdmirerPlayerComponent.KEY.get(localPlayer);
            if (admirerComp.isAdmirerMarked && !admirerComp.hasTransformed) {
                Component energyText = Component
                        .literal(String.format("[%d/%d]", admirerComp.energy, AdmirerPlayerComponent.MAX_ENERGY))
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
                // 在角色名称下方显示能量信息
                context.drawString(renderer, energyText, -renderer.width(energyText) / 2, 12,
                        ModRoles.ADMIRER.color() | (int) (nametagAlpha * 255.0F) << 24);
            }
        }
    }

    /**
     * 获取阶段文本
     */
    private static Component getPhaseText(int phase) {
        return switch (phase) {
            case 1 ->
                Component.translatable("hud.noellesroles.stalker.phase1_short").withStyle(ChatFormatting.DARK_PURPLE);
            case 2 -> Component.translatable("hud.noellesroles.stalker.phase2_short").withStyle(ChatFormatting.RED);
            case 3 ->
                Component.translatable("hud.noellesroles.stalker.phase3_short").withStyle(ChatFormatting.DARK_RED);
            default -> Component.empty();
        };
    }
}