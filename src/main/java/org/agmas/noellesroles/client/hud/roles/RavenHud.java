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

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.neutral.RavenRoleData;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.Locale;

public final class RavenHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RAVEN_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            var ravenOpt = RoleData.getOptional(RavenRoleData.class, player);
            if (ravenOpt.isEmpty()) return;
            RavenRoleData raven = ravenOpt.get();
            int x = context.guiWidth() - 180;
            int y = context.guiHeight() - 55;

            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.raven.charge_progress",
                            String.format(Locale.ROOT, "%.2f", raven.moodProgress),
                            String.format(Locale.ROOT, "%.2f", raven.moodProgressThreshold)),
                    x, y, 0x8B5EB8);

            // Hunt charges
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.raven.charges", raven.charges, RavenRoleData.MAX_CHARGES),
                    x, y + 11, 0x6B4B9E);

            // Kill progress
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.raven.kills", raven.kills, raven.requiredKills),
                    x, y + 22, 0xA66DCC);

            // Cooldown countdown
            if (raven.cooldownTicks > 0) {
                int seconds = (raven.cooldownTicks + 19) / 20;
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.cooldown", seconds),
                        x, y + 33, 0xAAAAAA);
            }

            // Hunt time remaining during hunt
            if (raven.isHunting()) {
                int seconds = (raven.huntTicks + 19) / 20;
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.hunt_time", seconds),
                        x, y - 11, 0xCC8844);
            }

            // Target role during hunt
            if (raven.isHunting() && raven.targetRoleId != null) {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.target", roleDisplayName(raven.targetRoleId)),
                        x, y - 22, 0xFF5555);
            }

            // Return hint during hunt
            if (raven.isHunting()) {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.return_hint"),
                        x, y - 33, 0xFFD700);
            }
        });
    }

    /**
     * 职业显示名：统一走 {@link RoleUtils#getRoleName(net.minecraft.resources.ResourceLocation)}。
     *
     * <p>
     * 自定义职业会拿到配置里的自定义名字（已注册时走 {@code CustomNormalRole#getName()}，
     * 未注册时回退查自定义职业配置），不会再退成 {@code announcement.star.role.<englishId>}
     * 这种没人填过的翻译键。
     */
    private static Component roleDisplayName(ResourceLocation roleId) {
        MutableComponent name = RoleUtils.getRoleName(roleId);
        return name != null ? name : Component.empty();
    }
}
