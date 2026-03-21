package org.agmas.noellesroles.mixin.roles.puppeteer;

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
import org.agmas.noellesroles.entity.PuppeteerBodyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 允许手枪攻击傀儡本体实体
 */
@Mixin(GunShootPayload.Receiver.class)
public class PuppeteerBodyGunPayloadMixin {

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void handlePuppeteerBodyTarget(GunShootPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        ItemStack mainHandStack = player.getMainHandItem();

        // 检查玩家是否持有枪
        if (!mainHandStack.is(TMMItemTags.GUNS))
            return;
        if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
            return;

        // 检查目标是否是傀儡本体实体
        if (player.serverLevel().getEntity(payload.target()) instanceof PuppeteerBodyEntity bodyEntity) {
            if (bodyEntity.distanceTo(player) > 65.0)
                return;

            // 播放枪声
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            // 对傀儡本体造成致命伤害
            bodyEntity.playerHurt(player, GameConstants.DeathReasons.REVOLVER);

            // 播放射击音效和粒子效果
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
                ServerPlayNetworking.send(tracking, new ShootMuzzleS2CPayload(player.getId()));
            }
            ServerPlayNetworking.send(player, new ShootMuzzleS2CPayload(player.getId()));

            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(),
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 200));
            }

            ci.cancel();
        }
    }
}