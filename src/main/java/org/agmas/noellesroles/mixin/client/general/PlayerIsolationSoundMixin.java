package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class PlayerIsolationSoundMixin {

    /** 声音被判定为“来自其他玩家”的最大距离（方块），平方值。 */
    private static final double NL$OTHER_PLAYER_RADIUS_SQR = 4.0D; // 2 格

    /** 自身声音的判定半径（方块），平方值。位于该半径内的声音视为本人发出，不屏蔽。 */
    private static final double NL$SELF_RADIUS_SQR = 2.25D; // 1.5 格

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void blockPlayerSound(SoundInstance sound, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) return;
        if (!self.hasEffect(ModEffects.PLAYER_ISOLATION)) return;

        double sx = sound.getX();
        double sy = sound.getY();
        double sz = sound.getZ();

        // 与自身的距离：本人发出的声音（脚步/受伤/吃东西等）保留，避免隔离时听不到自己的脚步。
        double dxSelf = sx - self.getX();
        double dySelf = sy - self.getY();
        double dzSelf = sz - self.getZ();
        double selfDistSqr = dxSelf * dxSelf + dySelf * dySelf + dzSelf * dzSelf;
        boolean nearSelf = selfDistSqr <= NL$SELF_RADIUS_SQR;

        // 1) 玩家类声音（脚步、受伤、升级、吃喝等）只要不是本人发出的就屏蔽。
        if (sound.getSource() == SoundSource.PLAYERS && !nearSelf) {
            ci.cancel();
            return;
        }

        // 2) 任何在其他玩家附近发出的声音（破坏/放置方块、使用物品等）同样屏蔽，
        //    这样“听不见其他玩家”才是完整的。本人附近的声音不受影响。
        if (nearSelf) return;
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == self) continue;
            double dx = sx - other.getX();
            double dy = sy - other.getY();
            double dz = sz - other.getZ();
            if (dx * dx + dy * dy + dz * dz <= NL$OTHER_PLAYER_RADIUS_SQR) {
                ci.cancel();
                return;
            }
        }
    }
}
