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

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.neutral.AmonRoleData;
import org.agmas.noellesroles.role.ModRoles;

public final class AmonHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.AMON_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            AmonRoleData amon = RoleData.getNullable(AmonRoleData.class, player);
            if (amon == null)
                return;
            int x = context.guiWidth() - 190;
            int y = context.guiHeight() - 57;

            // 终幕阶段：显示倒计时与备用能力，戏耍众人。
            if (amon.finalePhase) {
                int seconds = (amon.finaleTicks + 19) / 20;
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.amon.finale_time", seconds),
                        x, y, 0xFFD700);
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.amon.reserve", amon.reserveLives),
                        x, y + 11, 0xC79BE6);
                return;
            }

            // 潜伏中的时之虫数 / 上限
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.amon.seeds", amon.clientSeeds, amon.seedCap),
                    x, y, 0x8A6FB0);

            // 已成熟（可夺舍）宿主数
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.amon.matured", amon.clientMatured),
                    x, y + 11, 0xC79BE6);

            // 夺舍就绪提示
            if (amon.clientMatured > 0) {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.amon.usurp_ready"),
                        x, y + 22, 0x55FF55);
            }

            // 已夺舍次数
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.amon.usurped", amon.usurpCount),
                    x, y + 33, amon.hasUsurped ? 0xFFD700 : 0xAAAAAA);

            // 「种时之虫」技能冷却
            var ability = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY.get(player);
            int cd = ability.getSkillState(SRE.id("amon_plant_seed")).cooldown;
            if (cd > 0) {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.amon.cooldown", (cd + 19) / 20),
                        x, y + 44, 0xFF7755);
            } else {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.amon.ready"),
                        x, y + 44, 0x55FF55);
            }
        });
    }
}
