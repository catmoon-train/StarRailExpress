package org.agmas.noellesroles.roles.fool;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public record FoolExecutionerGunShootC2SPacket(int targetId) implements CustomPacketPayload {
    public static final Type<FoolExecutionerGunShootC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "fool_executioner_gun_shoot"));
    public static final StreamCodec<FriendlyByteBuf, FoolExecutionerGunShootC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            FoolExecutionerGunShootC2SPacket::targetId,
            FoolExecutionerGunShootC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<FoolExecutionerGunShootC2SPacket> {
        @Override
        public void receive(@NotNull FoolExecutionerGunShootC2SPacket payload,
                ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer shooter = context.player();
            ItemStack mainHandStack = shooter.getMainHandItem();
            if (!mainHandStack.is(ModItems.EXECUTIONER_GUN)) {
                return;
            }
            if (shooter.getCooldowns().isOnCooldown(mainHandStack.getItem())) {
                return;
            }

            shooter.level().playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.5f,
                    1f + shooter.getRandom().nextFloat() * .1f - .05f);

            ServerPlayer target = null;
            if (shooter.serverLevel().getEntity(payload.targetId()) instanceof ServerPlayer foundTarget
                    && foundTarget.distanceTo(shooter) < 70.0) {
                target = foundTarget;
            }

            if (target != null) {
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(shooter.level());
                if (game.isRole(shooter, ModRoles.THE_FOOL)) {
                    final var role = game.getRole(shooter);
                    if (role != null && role.onGunHit(shooter, target)) {
                        GameUtils.killPlayer(target, true, shooter, GameConstants.DeathReasons.EXECUTE);
                    }
                }
            }

            shooter.level().playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f,
                    1f + shooter.getRandom().nextFloat() * .1f - .05f);

            for (ServerPlayer tracking : PlayerLookup.tracking(shooter)) {
                PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(shooter.getId()));
            }
            PacketTracker.sendToClient(shooter, new ShootMuzzleS2CPayload(shooter.getId()));
        }
    }
}