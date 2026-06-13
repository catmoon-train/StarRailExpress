package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

/**
 * 通用自定义职业 HUD
 * 和其他职业 HUD 一样，通过 CommonHudRenderCallback 注册，渲染时自行判断角色
 */
@Environment(EnvType.CLIENT)
public class CustomRoleHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            // 判断当前玩家是否为启用了技能的自定义职业
            var role = SREClient.getCachedPlayerRole();
            if (role == null || !"customrole".equals(role.identifier().getNamespace())) return;
            if (!RoleSkill.isRegistered(role)) return; // enableAbility=false 的角色不会注册技能
            if (RoleSkill.hasUnifiedSkills(role)) return; // 统一技能 HUD 已负责显示

            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(client.player);
            int cooldownTicks = ability.cooldown;
            var font = client.font;

            // 渲染在屏幕右下角
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 30;

            if (cooldownTicks <= 0) {
                Component readyText = Component.translatable("hud.sre.custom_role.skill_ready");
                guiGraphics.drawString(font, readyText, x - font.width(readyText), y, 0x55FF55);
            } else {
                double seconds = cooldownTicks / 20.0;
                Component cooldownText = Component.translatable("hud.sre.custom_role.skill_cooldown",
                    String.format("%.1f", seconds));
                guiGraphics.drawString(font, cooldownText, x - font.width(cooldownText), y, 0xFF5555);
            }
        });
    }
}
