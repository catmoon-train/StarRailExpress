package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.vigilante.ghost_eye.GhostEyePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 鬼眼·杨间 HUD：右下角显示被动扫描倒计时 + 主动技能「诡域」冷却。
 */
public class GhostEyeHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GHOST_EYE_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;

            Font font = client.font;
            GhostEyePlayerComponent ghostComp = GhostEyePlayerComponent.KEY.get(client.player);
            int x = context.guiWidth() - 12;
            int color = ModRoles.GHOST_EYE.color();

            // 第 1 行：被动扫描倒计时（上方）
            MutableComponent scanLine;
            if (ghostComp.revealTicks > 0) {
                scanLine = Component.translatable("hud.ghost_eye.scanning",
                        ghostComp.revealTicks / 20.0);
            } else {
                scanLine = Component.translatable("hud.ghost_eye.scan_countdown",
                        ghostComp.scanCountdown / 20);
            }
            int y = context.guiHeight() - 20 - font.lineHeight - 2;
            context.drawString(font, scanLine, x - font.width(scanLine), y, color);

            // 第 2 行：主动技能「诡域」冷却（下方，原位置）
            final var abilityComp = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY.get(client.player);
            MutableComponent abilityLine;
            if (abilityComp.cooldown > 0) {
                abilityLine = Component.translatable("hud.ghost_eye.cooldown", abilityComp.cooldown / 20);
            } else {
                abilityLine = Component.translatable("hud.ghost_eye.ready");
            }
            context.drawString(font, abilityLine,
                    x - font.width(abilityLine),
                    context.guiHeight() - 20, color);
        });
    }
}
