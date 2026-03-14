package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.gambler.GamblerPlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 赌徒 HUD Mixin
 * 
 * 显示赌徒的技能状态：
 * - 赌徒职业
 */
@Mixin(Gui.class)
public class GamblerHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderGamblerAbilityStatus(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        if(client.player.isSpectator()) return;

        // 检查是否是赌徒
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.GAMBLER))
            return;

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(client.player))
            return;

        // 获取赌徒组件
        GamblerPlayerComponent gamblerComponent = GamblerPlayerComponent.KEY.get(client.player);

        // 渲染位置 - 右下角
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = screenWidth - 120; // 距离右边缘
        int y = screenHeight - 30; // 距离底部

        Font textRenderer = client.font;
        int roleDrawLeft = GamblerPlayerComponent.DRAW_INTERVAL - gamblerComponent.roleDrawTimer;
        if (gamblerComponent.selectedRole != null) {
            Component readyText = Component.translatable("hud.noellesroles.gambler.selected_tip",
                    RoleUtils.getRoleName(gamblerComponent.selectedRole));
            context.drawString(textRenderer, readyText, x, y - 40, CommonColors.GREEN);
            Component readyText2 = Component.translatable("hud.noellesroles.gambler.revolver_tip",
                    RoleUtils.getRoleName(gamblerComponent.selectedRole));
            context.drawString(textRenderer, readyText2, x, y - 60, CommonColors.GREEN);
        }
        if (gamblerComponent.availableRoles.size() > 0) {
            Component readyText = Component.translatable("hud.noellesroles.gambler.tip",
                    Component.keybind("key.noellesroles.ability"));
            context.drawString(textRenderer, readyText, x, y - 20, CommonColors.GREEN);
        }
        // 显示技能冷却
        int cdSeconds = roleDrawLeft / 20;
        Component cdText = Component.translatable("hud.noellesroles.gambler.cooldown",
                String.format("%d", cdSeconds));

        // 红色文字表示抽取间隔
        context.drawString(textRenderer, cdText, x, y, CommonColors.RED);
    }
}