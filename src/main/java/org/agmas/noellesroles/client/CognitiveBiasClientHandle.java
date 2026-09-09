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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.init.ModEffects;

import java.util.UUID;

/**
 * 人类认知偏差：other players' skins shuffle and names obfuscate like low mood.
 */
@Environment(EnvType.CLIENT)
public final class CognitiveBiasClientHandle {
    private CognitiveBiasClientHandle() {
    }

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            if (!viewerHasEffect() || player == Minecraft.getInstance().player) {
                return PlayerSkinResult.SKIP;
            }
            UUID shuffled = NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
            if (shuffled == null) {
                return PlayerSkinResult.SKIP;
            }
            PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(shuffled);
            if (info == null || info.getSkin() == null) {
                return PlayerSkinResult.SKIP;
            }
            return PlayerSkinResult.playerSkin(info.getSkin());
        });
        OnRenderRoleName.RENDER_PLAYER_NAME.register((self, target, context, delta, font) -> {
            if (self == null || target == null || !self.hasEffect(ModEffects.COGNITIVE_BIAS)
                    || self.getUUID().equals(target.getUUID())) {
                return TrueFalseAndCustomResult.pass();
            }
            return TrueFalseAndCustomResult.custom(Component.literal("??!?!")
                    .withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.GRAY));
        });
    }

    private static boolean viewerHasEffect() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(ModEffects.COGNITIVE_BIAS);
    }
}
