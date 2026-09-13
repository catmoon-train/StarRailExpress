/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.hud.roles;

import java.util.Optional;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.anime.AnimeRoles;
import org.agmas.noellesroles.role.anime.chino.ChinoHeadRideManager;
import org.agmas.noellesroles.role_data.innocence.ChinoRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

/**
 * 卡布奇诺咖啡师 HUD：抱兔兔的冷却，以及头顶有兔兔时提示按技能键放下。
 */
public class ChinoHeadRideHud {

    /** 触发方式提示 / 冷却状态的 Y 偏移（相对底部）。 */
    private static final int LINE_HEIGHT = 12;

    public static void register() {
        RoleHudRenderCallback.EVENT.register(AnimeRoles.KAFU_CHINO.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            Optional<ChinoRoleData> roleData = RoleData.getOptional(ChinoRoleData.class, client.player);
            if (roleData.isEmpty()) {
                return;
            }

            Font font = client.font;
            int x = client.getWindow().getGuiScaledWidth() - 10;
            int y = client.getWindow().getGuiScaledHeight() - 20;

            long cooldownLeft = roleData.get().getRideCooldownLeft();
            Component text;
            int color;
            if (cooldownLeft > 0) {
                text = Component.translatable("hud.noellesroles.kafu_chino.cooldown",
                        String.format("%.1f", cooldownLeft / 20f));
                color = CommonColors.RED;
            } else {
                text = Component.translatable("hud.noellesroles.kafu_chino.ready");
                color = CommonColors.GREEN;
            }
            context.drawString(font, text, x - font.width(text), y, color);

            // 头顶有兔兔时，提示用技能键把人放下来
            if (ChinoHeadRideManager.isCarrying(client.player)) {
                Component hint = Component.translatable("hud.noellesroles.kafu_chino.release_hint",
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
                context.drawString(font, hint, x - font.width(hint), y - LINE_HEIGHT, 0xFFFFD24A);
            }
        });
    }
}
