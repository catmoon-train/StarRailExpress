package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 亡灵之主 HUD：右下角显示现存亡灵数量、复苏冷却、感染增幅状态。
 */
public class UndeadLordHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.UNDEAD_LORD.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            var comp = UndeadLordPlayerComponent.KEY.maybeGet(client.player).orElse(null);
            var ability = SREAbilityPlayerComponent.KEY.maybeGet(client.player).orElse(null);
            if (comp == null || ability == null) {
                return;
            }

            Font font = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;

            // 现存亡灵数量
            Component countText = Component.translatable("hud.noellesroles.undead_lord.count",
                    comp.syncedUndeadCount, Math.max(1, comp.syncedMaxUndead));
            context.drawString(font, countText, x - font.width(countText), y - font.lineHeight - 2, 0x9457FF);

            // 复苏冷却 / 就绪
            Component skillText;
            int color;
            if (ability.cooldown > 0) {
                int seconds = (ability.cooldown + 19) / 20;
                skillText = Component.translatable("hud.noellesroles.undead_lord.cooldown", seconds);
                color = CommonColors.RED;
            } else {
                skillText = Component.translatable("hud.noellesroles.undead_lord.ready");
                color = CommonColors.GREEN;
            }
            context.drawString(font, skillText, x - font.width(skillText), y, color);

            // 感染增幅
            if (comp.syncedAmpActive) {
                Component ampText = Component.translatable("hud.noellesroles.undead_lord.amp");
                context.drawString(font, ampText, x - font.width(ampText), y - (font.lineHeight + 2) * 2, 0xFFAA00);
            }
        });
    }
}
