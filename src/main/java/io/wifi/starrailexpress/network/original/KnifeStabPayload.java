package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

public record KnifeStabPayload(int target) implements CustomPacketPayload {
    public static final Type<KnifeStabPayload> ID = new Type<>(SRE.id("knifestab"));
    public static final StreamCodec<FriendlyByteBuf, KnifeStabPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT,
            KnifeStabPayload::target, KnifeStabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<KnifeStabPayload> {
        @Override
        public void receive(@NotNull KnifeStabPayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            if (!(player.serverLevel().getEntity(payload.target()) instanceof ServerPlayer target))
                return;
            if (target.distanceTo(player) > 3.0)
                return;
            StarGameWorldComponent game = StarGameWorldComponent.KEY.get(player.level());
            final var role = game.getRole(player);
            if (role != null) {
                if (!role.onUseKnifeHit(player, target)) {
                    return;
                }
            }
            GameFunctions.killPlayer(target, true, player, GameConstants.DeathReasons.KNIFE);
            target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            player.swing(InteractionHand.MAIN_HAND);
            if (!player.isCreative()
                    && !StarGameWorldComponent.KEY.get(player.level()).isRole(player, TMMRoles.LOOSE_END)) {
                player.getCooldowns().addCooldown(TMMItems.KNIFE, GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE));
            }
        }
    }
}