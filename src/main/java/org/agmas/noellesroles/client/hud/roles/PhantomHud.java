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

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

/**
 * Phantom-specific HUD overlay.
 *
 * Since the unified skill HUD ({@link io.wifi.starrailexpress.api.RoleSkill}) already
 * renders the skill card (name + cooldown/ready state), this overlay only adds the
 * Phantom-specific detail: the remaining invisibility duration when the effect is active,
 * plus a hint about the toggle key.
 */
public class PhantomHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PHANTOM_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            if (client.player.hasEffect(ModEffects.SKILL_BANED))
                return;

            var ability = SREAbilityPlayerComponent.KEY.get(client.player);
            int cooldownTicks = ability.cooldown;
            var font = client.font;
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();

            // 渲染技能名称 + 状态（就绪 / 冷却中）
            Component skillName = Component.translatable("skill.noellesroles.phantom.invisibility");
            Component skillLine;
            int skillColor;
            if (cooldownTicks <= 0) {
                skillLine = skillName.copy().append(Component.literal("  ")).append(
                        Component.translatable("hud.sre.skill.ready"));
                skillColor = 0xFF55FF55;
            } else {
                skillLine = skillName.copy().append(Component.literal("  ")).append(
                        Component.translatable("hud.sre.skill.cooldown",
                                String.format("%.1f", cooldownTicks / 20.0F)));
                skillColor = 0xFFFF7755;
            }
            int baseY = screenHeight - font.lineHeight - 12;
            context.drawString(font, skillLine,
                    screenWidth - font.width(skillLine) - 12, baseY, skillColor);

            // 隐身状态额外信息
            var invisibility = client.player.getEffect(MobEffects.INVISIBILITY);
            if (invisibility == null || invisibility.getDuration() <= 0) {
                return;
            }

            Component line = Component.translatable("tip.phantom.activing", invisibility.getDuration() / 20,
                    Component.keybind("key.noellesroles.ability"));

            int drawY = baseY - font.lineHeight - 2;
            context.drawString(font, line, context.guiWidth() - font.width(line) - 12,
                    drawY, 0xFFAA4444);
        });
    }
}
