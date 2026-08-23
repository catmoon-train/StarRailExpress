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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.neutral.VoiceChangerRoleData;
import org.agmas.noellesroles.role.ModRoles;

public class VoiceChangerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.VOICE_CHANGER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            var vcData = RoleData.getOptional(VoiceChangerRoleData.class, client.player);
            if (vcData.isEmpty()) return;
            var vc = vcData.get();
            MobEffect effect = VoiceChangerRoleData.VOICE_EFFECTS.get(vc.currentVoiceType).value();
            int level = vc.currentVoiceLevel + 1; // 0 级 = 实际 1 级
            Component line = Component.translatable("tip.voice_changer",
                    Component.translatable(effect.getDescriptionId()), level);
            int drawY = context.guiHeight() - client.font.wordWrapHeight(line, 999999) - 8;
            context.drawString(client.font, line, context.guiWidth() - client.font.width(line), drawY,
                    0xADD8E6); // 淡蓝色
        });
    }
}
