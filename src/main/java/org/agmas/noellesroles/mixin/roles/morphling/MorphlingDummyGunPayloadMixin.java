package org.agmas.noellesroles.mixin.roles.morphling;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 允许手枪命中变形者「举刀假人」：命中后假人按自身 hurt() 逻辑炸成一颗闪光弹。
 * 服务端 {@link GunShootPayload.Receiver} 原本只处理 ServerPlayer 目标，故在此提前拦截。
 */
@Mixin(GunShootPayload.Receiver.class)
public class MorphlingDummyGunPayloadMixin {

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void noe$handleMorphlingDummyTarget(GunShootPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        ItemStack mainHandStack = player.getMainHandItem();

        if (!mainHandStack.is(TMMItemTags.GUNS))
            return;
        if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
            return;

        if (player.serverLevel().getEntity(payload.target()) instanceof MorphlingKnifeDummyEntity dummy) {
            if (dummy.distanceTo(player) > 65.0)
                return;

            // 命中假人：交由其 hurt() 处理（炸成闪光弹）
            dummy.hurt(player.damageSources().playerAttack(player), 20.0F);

            // 与其它枪击一致：播放射击音效、枪口火光，并进入冷却
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
                ServerPlayNetworking.send(tracking, new ShootMuzzleS2CPayload(player.getId()));
            }
            ServerPlayNetworking.send(player, new ShootMuzzleS2CPayload(player.getId()));

            if (!player.isCreative() && mainHandStack.is(TMMItemTags.COOLDOWN_GUNS)) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(),
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 200));
            }

            ci.cancel();
        }
    }
}
