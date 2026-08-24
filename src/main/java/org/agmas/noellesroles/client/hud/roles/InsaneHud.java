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
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.agmas.noellesroles.role.ModRoles;

public class InsaneHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(
                ModRoles.INSANE_KILLER_ID,
                (context, tickCounter) -> {
                    Minecraft client = Minecraft.getInstance();
                    if (SREClient.isPlayerSpectator())
                        return;

                    final var insaneKillerPlayerComponent = RoleData
                            .getNullable(InsaneKillerRoleData.class, client.player);
                    if (insaneKillerPlayerComponent == null)
                        return;
                    if (insaneKillerPlayerComponent.inNearDeath()) {
                        var text1 = Component.translatable("insane.tip.neardeath.line1")
                                .withStyle(ChatFormatting.YELLOW);
                        var text2 = Component
                                .translatable("insane.tip.neardeath.line2",
                                        insaneKillerPlayerComponent.deathState
                                                / 20)
                                .withStyle(ChatFormatting.RED);
                        var text3 = Component.translatable("insane.tip.neardeath.line3")
                                .withStyle(ChatFormatting.GRAY);
                        context.drawString(client.font, text1,
                                context.guiWidth() - client.font.width(text1) - 10,
                                context.guiHeight() - 40,
                                java.awt.Color.YELLOW.getRGB());
                        context.drawString(client.font, text2,
                                context.guiWidth() - client.font.width(text2) - 10,
                                context.guiHeight() - 30,
                                java.awt.Color.YELLOW.getRGB());
                        context.drawString(client.font, text3,
                                context.guiWidth() - client.font.width(text3) - 10,
                                context.guiHeight() - 20,
                                java.awt.Color.YELLOW.getRGB());
                    } else if (insaneKillerPlayerComponent.isActive) {
                        var text = Component.translatable("insane.tip.over",
                                NoellesrolesClient.abilityBind.getTranslatedKeyMessage()
                                        .getString())
                                .append(" ");
                        context.drawString(client.font, text,
                                context.guiWidth() - client.font.width(text),
                                context.guiHeight() - 20, ModRoles.MORPHLING.color());
                    } else {
                        final var morphTicks = insaneKillerPlayerComponent.cooldown;
                        if (morphTicks > 0) {
                            var text = Component
                                    .translatable("insane.tip",
                                            ((int) (morphTicks * 0.05)))
                                    .append(" ");
                            context.drawString(client.font, text,
                                    context.guiWidth() - client.font.width(text),
                                    context.guiHeight() - 20,
                                    ModRoles.MORPHLING.color());
                        } else {
                            var text = Component.translatable("insane.tip.ready",
                                    NoellesrolesClient.abilityBind
                                            .getTranslatedKeyMessage()
                                            .getString())
                                    .append(" ");
                            context.drawString(client.font, text,
                                    context.guiWidth() - client.font.width(text),
                                    context.guiHeight() - 20,
                                    ModRoles.MORPHLING.color());
                        }
                    }
                });
    }
}
