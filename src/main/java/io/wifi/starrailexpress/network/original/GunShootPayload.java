package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.IsShootBackFire;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record GunShootPayload(int target) implements CustomPacketPayload {
    public static final Type<GunShootPayload> ID = new Type<>(SRE.id("gunshoot"));
    public static final StreamCodec<FriendlyByteBuf, GunShootPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT,
            GunShootPayload::target, GunShootPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<GunShootPayload> {
        @Override
        public void receive(@NotNull GunShootPayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            ItemStack mainHandStack = player.getMainHandItem();
            if (!mainHandStack.is(TMMItemTags.GUNS))
                return;
            if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                return;

            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            // cancel if derringer has been shot
            Boolean isUsed = mainHandStack.getOrDefault(SREDataComponentTypes.USED, false);
            if (mainHandStack.is(TMMItems.DERRINGER)) {
                if (isUsed == null) {
                    isUsed = false;
                }

                if (isUsed) {
                    return;
                }

                if (!player.isCreative()) {
                    mainHandStack.set(SREDataComponentTypes.USED, true);
                }
            }

            if (player.serverLevel().getEntity(payload.target()) instanceof ServerPlayer target
                    && target.distanceTo(player) < 70.0) {
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
                Item revolver = TMMItems.REVOLVER;
                boolean isDerringer = mainHandStack.is(TMMItems.DERRINGER);
                ResourceLocation deathReason = isDerringer ? GameConstants.DeathReasons.DERRINGER
                        : GameConstants.DeathReasons.REVOLVER;

                boolean backfire = false;
                final var role = game.getRole(player);
                if (role != null) {
                    if (!role.onGunHit(player, target)) {
                        return;
                    }
                }
                backfire = IsShootBackFire.EVENT.invoker().isShootBackFire(player, target);
                boolean shouldDropRevolver = game.isInnocent(target) && !player.isCreative()
                        && mainHandStack.is(revolver);
                var dropresult = AllowShootRevolverDrop.EVENT.invoker().allowDrop(player, target);
                if (dropresult.equals(AllowShootRevolverDrop.ShouldDropResult.FALSE)) {
                    shouldDropRevolver = false;
                } else if (dropresult.equals(AllowShootRevolverDrop.ShouldDropResult.TRUE)) {
                    shouldDropRevolver = true;
                }
                if (backfire) {
                    GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.BACKFIRE);
                } else if (shouldDropRevolver) {
                    {
                        {
                            if (player.getMainHandItem().is(TMMItemTags.GUNS)) {
                                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                ItemEntity item = player.drop(revolver.getDefaultInstance(), false, false);
                                if (item != null) {
                                    item.setPickUpDelay(10);
                                    item.setThrower(player);
                                }
                                PacketTracker.sendToClient(player, new GunDropPayload());
                                SREPlayerMoodComponent.KEY.get(player).setMood(0);
                            }
                        }
                    }
                }

                if (!backfire) {
                    mainHandStack.set(SREDataComponentTypes.USED, false);
                    GameUtils.killPlayer(target, true, player, deathReason);
                }
                OnRevolverUsed.EVENT.invoker().onPlayerShoot(player, target);

            } else {
                OnRevolverUsed.EVENT.invoker().onPlayerShoot(player, null);
            }

            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            for (ServerPlayer tracking : PlayerLookup.tracking(player))
                PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));
            PacketTracker.sendToClient(player, new ShootMuzzleS2CPayload(player.getId()));
            if (!player.isCreative())
                player.getCooldowns().addCooldown(mainHandStack.getItem(),
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(),
                                GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0)));
        }
    }
}