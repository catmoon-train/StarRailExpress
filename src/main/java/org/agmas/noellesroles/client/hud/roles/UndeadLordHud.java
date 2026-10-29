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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.killer.UndeadLordRoleData;
import org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordRole;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 亡灵之主 HUD：沿用右下角文字风格，显示魂/亡灵、两主动技能与独立复苏冷却。
 */
public class UndeadLordHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.UNDEAD_LORD.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            var comp = RoleData.getNullable(UndeadLordRoleData.class, client.player);
            var ability = SREAbilityPlayerComponent.KEY.maybeGet(client.player).orElse(null);
            if (comp == null || ability == null) {
                return;
            }

            Font font = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;
            int step = font.lineHeight + 2;

            Component souls = Component.translatable("hud.noellesroles.undead_lord.souls",
                    comp.getSouls(), comp.maxSouls());
            context.drawString(font, souls, x - font.width(souls), y - step * 4, 0xB8E6FF);

            Component count = Component.translatable("hud.noellesroles.undead_lord.count",
                    comp.syncedUndeadCount, comp.ignoreUndeadCap ? "∞" : comp.syncedMaxUndead);
            context.drawString(font, count, x - font.width(count), y - step * 3, 0x9457FF);

            int commandCd = ability.getSkillState(UndeadLordRole.COMMAND_SKILL_ID).cooldown;
            Component command = Component.translatable("hud.noellesroles.undead_lord.command",
                    Component.keybind("key.noellesroles.ability"), status(commandCd));
            context.drawString(font, command, x - font.width(command), y - step * 2,
                    commandCd > 0 ? CommonColors.RED : CommonColors.GREEN);

            int summonCd = ability.getSkillState(UndeadLordRole.SUMMON_SKILL_ID).cooldown;
            Component summon = Component.translatable("hud.noellesroles.undead_lord.summon",
                    Component.keybind("key.sneak"), Component.keybind("key.noellesroles.ability"), status(summonCd));
            context.drawString(font, summon, x - font.width(summon), y - step,
                    summonCd > 0 || comp.getSouls() < 1 ? CommonColors.RED : CommonColors.GREEN);

            int raiseCd = comp.getRaiseCooldownTicks();
            Component raise = raiseCd > 0
                    ? Component.translatable("hud.noellesroles.undead_lord.cooldown", (raiseCd + 19) / 20)
                    : Component.translatable("hud.noellesroles.undead_lord.ready");
            context.drawString(font, raise, x - font.width(raise), y,
                    raiseCd > 0 || comp.getSouls() < 1 ? CommonColors.RED : CommonColors.GREEN);

            if (comp.syncedAmpActive) {
                Component amp = Component.translatable("hud.noellesroles.undead_lord.amp");
                context.drawString(font, amp, x - font.width(amp), y - step * 5, 0xFFAA00);
            }
        });
    }

    private static Component status(int ticks) {
        return ticks > 0
                ? Component.translatable("hud.noellesroles.undead_lord.skill_cooldown", (ticks + 19) / 20)
                : Component.translatable("hud.noellesroles.undead_lord.skill_ready");
    }
}
