package org.agmas.noellesroles.repack;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.GunDropPayload;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public record BanditRevolverShootPayload(int target) implements CustomPacketPayload {
    public static final Type<BanditRevolverShootPayload> ID = new Type<>(Noellesroles.id("banditgunshoot"));;
    public static final StreamCodec<FriendlyByteBuf, BanditRevolverShootPayload> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    static {
        CODEC = StreamCodec.composite(ByteBufCodecs.INT, BanditRevolverShootPayload::target,
                BanditRevolverShootPayload::new);
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<BanditRevolverShootPayload> {
        @Override
        public void receive(@NotNull BanditRevolverShootPayload payload,
                ServerPlayNetworking.@NotNull Context context) {
            final var player = context.player();
            extracted(player, player.serverLevel().getEntity(payload.target()));
        }
    }

    public static void extracted(ServerPlayer player, Entity var6) {

        ItemStack mainHandStack = player.getMainHandItem();
        if (mainHandStack.is(TMMItemTags.GUNS)) {
            if (!player.getCooldowns().isOnCooldown(mainHandStack.getItem())) {
                player.level().playSound((Player) null, player.getX(), player.getEyeY(), player.getZ(),
                        TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.5F,
                        1.0F + player.getRandom().nextFloat() * 0.1F - 0.05F);

                if (var6 instanceof Player) {
                    Player target = (Player) var6;
                    if ((double) target.distanceTo(player) < (double) 100) {
                        SREGameWorldComponent game = (SREGameWorldComponent) SREGameWorldComponent.KEY
                                .get(player.level());
                        boolean backfire = false;
                        if (game.isInnocent(target) && !player.isCreative()) {
                            // \
                            boolean shouldDrop = false;
                            if (game.isRole(player, ModRoles.BANDIT)) {
                                shouldDrop = player.getRandom().nextInt(0, 100) <= 90;
                            } else {
                                shouldDrop = player.getRandom().nextFloat() <= 0.2F;
                            }
                            if (shouldDrop) {
                                {
                                    if (player.getMainHandItem().is(TMMItemTags.GUNS)) {
                                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                        ItemEntity item = player.drop(TMMItems.REVOLVER.getDefaultInstance(), false,
                                                false);
                                        if (item != null) {
                                            item.setPickUpDelay(10);
                                            item.setThrower(player);
                                        }

                                        ServerPlayNetworking.send(player, new GunDropPayload());
                                    }
                                }
                            }
                        }
                        if (!backfire) {
                            GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.REVOLVER);
                        }
                    }
                }

                player.level().playSound((Player) null, player.getX(), player.getEyeY(), player.getZ(),
                        TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5.0F,
                        1.0F + player.getRandom().nextFloat() * 0.1F - 0.05F);

                for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
                    ServerPlayNetworking.send(tracking, new ShootMuzzleS2CPayload(player.getId()));
                }

                ServerPlayNetworking.send(player, new ShootMuzzleS2CPayload(player.getId()));
            }
        }
    }
}