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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public record ZeroOneFiveShootPayload(int target) implements CustomPacketPayload {
    public static final Type<ZeroOneFiveShootPayload> ID = new Type<>(SRE.id("zero_one_five_shoot"));
    public static final StreamCodec<FriendlyByteBuf, ZeroOneFiveShootPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ZeroOneFiveShootPayload::target,
            ZeroOneFiveShootPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<ZeroOneFiveShootPayload> {
        @Override
        public void receive(@NotNull ZeroOneFiveShootPayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            ItemStack mainHandStack = player.getMainHandItem();

            if (player.isSpectator()) {
                return;
            }
            if (player.hasEffect(ModEffects.USED_BANED)) {
                return;
            }
            if (!mainHandStack.is(ModItems.ZERO_ONE_FIVE_GUN)) {
                return;
            }
            if (!ZeroOneFiveGunItem.tryConsumeShot(player)) {
                return;
            }

            if (player.serverLevel().getEntity(payload.target()) instanceof ServerPlayer target
                    && GameUtils.isPlayerAliveAndSurvival(target)
                    && target.distanceToSqr(player) < 30 * 30) {
                ZeroOneFiveGunItem.onHit(player, target);
            }

            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            org.agmas.noellesroles.gunfx.GunTracers.broadcast(player,
                    payload.target() >= 0 ? player.serverLevel().getEntity(payload.target()) : null, 30.0D);

            for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
                PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));
            }
            PacketTracker.sendToClient(player, new ShootMuzzleS2CPayload(player.getId()));
        }
    }
}
