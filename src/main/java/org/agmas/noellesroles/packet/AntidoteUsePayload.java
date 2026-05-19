package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.jetbrains.annotations.NotNull;

public record AntidoteUsePayload(int target) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AntidoteUsePayload> ID = new CustomPacketPayload.Type<>(Noellesroles.id("antidoteuse"));
    public static final StreamCodec<FriendlyByteBuf, AntidoteUsePayload> CODEC;

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    static {
        CODEC = StreamCodec.composite(ByteBufCodecs.INT, AntidoteUsePayload::target, AntidoteUsePayload::new);
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<AntidoteUsePayload> {
        public void receive(@NotNull AntidoteUsePayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            Entity var5 = player.serverLevel().getEntity(payload.target());
            if (var5 instanceof Player target) {
                if (!((double)target.distanceTo(player) > (double)3.0F)) {
                    ((SREPlayerPoisonComponent)SREPlayerPoisonComponent.KEY.get(target)).init();
                    target.playSound(NRSounds.SYRINGE_STAB, 0.4F, 1.0F);
                    player.swing(InteractionHand.MAIN_HAND);
                    if (!player.isCreative()) {
                        player.getCooldowns().addCooldown(ModItems.ANTIDOTE, (Integer) ModItems.ITEM_COOLDOWNS.get(ModItems.ANTIDOTE));
                    }

                }
            }
        }
    }
}
