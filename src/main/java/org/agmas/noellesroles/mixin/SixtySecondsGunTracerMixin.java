package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.exmo.sre.sixtyseconds.logic.GunTracers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 枪械开火（左轮/德林加/处刑枪等 {@code GunShootPayload}）的两处增强：
 * <ul>
 *   <li><b>降噪</b>：原枪声音量硬编码 5f 过响刺耳，封顶到 2f（上膛声 0.5f 不受影响）。</li>
 *   <li><b>射击轨迹</b>：开火成功（到达方法尾部）后广播弹道轨迹（{@link GunTracers}）。</li>
 * </ul>
 */
@Mixin(GunShootPayload.Receiver.class)
public abstract class SixtySecondsGunTracerMixin {

    @ModifyArg(method = "receive(Lio/wifi/starrailexpress/network/original/GunShootPayload;"
            + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;"
                            + "DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"),
            index = 6)
    private float sixtySeconds$capGunVolume(float volume) {
        return Math.min(volume, 2.0F);
    }

    @Inject(method = "receive(Lio/wifi/starrailexpress/network/original/GunShootPayload;"
            + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("TAIL"))
    private void sixtySeconds$tracer(GunShootPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (!player.getMainHandItem().is(TMMItemTags.GUNS)) {
            return;
        }
        Entity hit = payload.target() >= 0 ? player.serverLevel().getEntity(payload.target()) : null;
        GunTracers.broadcast(player, hit, 30.0D);
        // 60s 模式：枪击低语怪/夜袭者 → 立即击杀（doc §6「射击怪物→立即死亡」）
        if (hit instanceof net.minecraft.world.entity.Mob mob
                && net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(player.level())
                && mob.distanceToSqr(player) < 30 * 30
                && (mob.getTags().contains(net.exmo.sre.sixtyseconds.logic.SixtySecondsWhisperSystem.WHISPER_TAG)
                        || mob.getTags().contains(
                                net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.ASSAULT_TAG))) {
            mob.hurt(player.damageSources().playerAttack(player), 1000.0F);
        }
    }
}
