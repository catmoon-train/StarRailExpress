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

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.content.block_entity.EffectGeneratorBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class EffectGeneratorServerNetwork {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EffectGeneratorPayload.SaveConfig.TYPE,
                EffectGeneratorServerNetwork::handleSave);
    }

    private static void handleSave(EffectGeneratorPayload.SaveConfig payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.getServer().execute(() -> {
            if (!player.isCreative()) {
                return;
            }
            var be = player.level().getBlockEntity(payload.pos());
            if (be instanceof EffectGeneratorBlockEntity generator) {
                generator.loadConfig(payload.data());
                player.displayClientMessage(Component.translatable("message.starrailexpress.effect_generator.saved"),
                        true);
            }
        });
    }
}
