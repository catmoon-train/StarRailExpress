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

package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import org.agmas.noellesroles.client.NoellesrolesClient;
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
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null)
                return;
            if (!SREClient.isPlayerAliveAndInSurvival())
                return;

            // 判断当前玩家是否为启用了技能的自定义职业
            var role = SREClient.getCachedPlayerRole();
            if (role == null || !"customrole".equals(role.identifier().getNamespace())
                    || !(role instanceof CustomNormalRole))
                return;
            if (!RoleSkill.isRegistered(role))
                return; // enableAbility=false 的角色不会注册技能

            CustomRoleData data = CustomRoleLoader.getCustomRoleData(role.identifier().getPath());
            if (data == null)
                return;

            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(client.player);
            int cooldownTicks = ability.cooldown;
            var font = client.font;

            // 渲染在屏幕右下角
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 30;

            // 计算当前选中技能名称。
            // 关键：必须使用与「释放 / 切换」完全相同的权威列表 getSelectableDefinitions(role)，
            // 再用选中技能的定义 id 反查模块显示名。这样 HUD 名字必定与真正释放/切换到的技能一致，
            // 不会再出现「HUD 显示一技能、实际释放/切换却对应二技能」的错位。
            var selectable = RoleSkill.getSelectableDefinitions(role);
            int selected = selectable.isEmpty() ? 0 : Math.floorMod(ability.getSelectedSkill(), selectable.size());
            String skillName = selectable.isEmpty() ? ""
                    : CustomRoleLoader.getSkillDisplayName(selectable.get(selected).id());
            Component nameComponent = skillName.isEmpty()
                    ? Component.translatable("hud.sre.custom_role.skill_default")
                    : Component.literal(skillName);

            if (data.enableSkillSwitch && !data.skillModules.isEmpty()) {
                // 启用切换技能：显示「按 <切换键> 切换技能: <技能名>」
                Component switchHint = Component.translatable("hud.sre.custom_role.skill_switch_hint",
                        NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage(),
                        nameComponent);
                guiGraphics.drawString(font, switchHint, x - font.width(switchHint), y - 12, 0xFFFFAA);
            } else {
                // 单技能：直接在冷却上方显示技能名称
                guiGraphics.drawString(font, nameComponent, x - font.width(nameComponent), y - 12, 0xFFFFFF);
            }

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
