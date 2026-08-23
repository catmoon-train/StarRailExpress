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
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.special.SuperLooseEndRoleData;

import java.awt.*;

public class SuperLooseEndHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(SpecialGameModeRoles.SUPER_LOOSE_END.identifier(), (guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight;
            // 渲染护盾数量
            var armorPlayerComponent = SREArmorPlayerComponent.KEY.get(client.player);
            var armorText = Component.translatable("hud.bartender.has_armor", armorPlayerComponent.armor)
                    .withStyle(ChatFormatting.GOLD);
            // 左下角渲染护盾数量文本
            guiGraphics.drawString(font, armorText, 10, yOffset - font.lineHeight - 4,
                    Color.WHITE.getRGB());
            // 渲染技能状态
            var superLooseEndData = RoleData.getOptional(SuperLooseEndRoleData.class, client.player);
            if (superLooseEndData.isEmpty()) return;
            var superLooseEndPlayerComponent = superLooseEndData.get();
            // 渲染位置 - 右下角
            int x = guiGraphics.guiWidth() - guiGraphics.guiWidth() / 4;
            int y = guiGraphics.guiHeight() - font.lineHeight * 5 - 5;
            yOffset = 0;
            Component text = Component.empty();
            Component consumeText = Component.empty();
            // 渲染技能切换提示
            text = Component.translatable("hud.super_loose_end.switch_tip").withStyle(ChatFormatting.GRAY);
            guiGraphics.drawString(font, text, x, y + yOffset, Color.WHITE.getRGB());

            if (superLooseEndPlayerComponent.getCurAbility() == null)
                return;
            if (superLooseEndPlayerComponent.getCurAbility().first.cooldown <= 0) {
                switch (superLooseEndPlayerComponent.curAbilityIdx) {
                    // 爆炸技能
                    case 0 -> {
                        text = Component.translatable("hud.super_loose_end.explode",
                                        superLooseEndPlayerComponent.getExplodeLvl(), superLooseEndPlayerComponent.getExplosionRange())
                                .withStyle(ChatFormatting.RED);
                        consumeText = Component.translatable("hud.super_loose_end.comsume.armor",
                                Math.max(armorPlayerComponent.getArmor(), 2));
                    }
                    // 召回技能
                    case 1 -> {
                        if (superLooseEndPlayerComponent.placed) {
                            text = Component.translatable("hud.super_loose_end.recall")
                                    .withStyle(ChatFormatting.AQUA);
                        } else {
                            text = Component.translatable("hud.super_loose_end.recall.place")
                                    .withStyle(ChatFormatting.AQUA);
                        }
                        consumeText = Component.translatable("hud.super_loose_end.comsume.armor",
                                SuperLooseEndRoleData.RECALL_COST);
                    }
                    // 交换技能
                    case 2 -> {
                        text = Component.translatable("hud.super_loose_end.swap",
                                        superLooseEndPlayerComponent.getExplodeLvl(), superLooseEndPlayerComponent.getExplosionRange())
                                .withStyle(ChatFormatting.LIGHT_PURPLE);
                        consumeText = Component.translatable("hud.super_loose_end.comsume.speed_swap");
                    }
                }
            }
            else {
                text = Component.translatable("hud.super_loose_end.cool_down",
                                superLooseEndPlayerComponent.getCurAbility().first.cooldown / 20)
                        .withStyle(ChatFormatting.GRAY);
            }
            yOffset += font.lineHeight + 1;
            guiGraphics.drawString(font, text, x, y + yOffset, Color.WHITE.getRGB());

            // 渲染技能消耗
            text = Component.translatable("hud.super_loose_end.consume", consumeText.getString())
                    .withStyle(ChatFormatting.RED);
            yOffset += font.lineHeight + 1;
            guiGraphics.drawString(font, text, x, y + yOffset, Color.WHITE.getRGB());
        });

    }
}
