package io.wifi.starrailexpress.mixin.entity.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {
    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    public void tmm$disableSleepMessage(ServerPlayer instance, Component message, boolean overlay,
            Operation<Void> original) {
        if (SRE.isLobby) {
            original.call();
        }
    }

    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FZZ)V"))
    public void tmm$disableSetSpawnpoint(ServerPlayer instance, ResourceKey<Level> dimension, @Nullable BlockPos pos, float angle, boolean forced, boolean sendMessage, Operation<Void> original) {
        if(SRE.isLobby){
            original.call();
        }
    }

    @Inject(method = "attack", at = @At("HEAD"))
    public void attack(Entity target, CallbackInfo ci) {
        if (SRE.isLobby) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;

        if (self.getMainHandItem().is(TMMItems.BAT) && target instanceof ServerPlayer playerTarget
                && self.getAttackStrengthScale(0.5F) >= 1f) {
            if (playerTarget instanceof ServerPlayer) {
                GameUtils.killPlayer(playerTarget, true, self, GameConstants.DeathReasons.BAT);
            }
            CrosshairaddonsCompat.onAttack(target);
            self.getCommandSenderWorld().playSound(self,
                    playerTarget.getX(), playerTarget.getEyeY(), playerTarget.getZ(),
                    TMMSounds.ITEM_BAT_HIT, SoundSource.PLAYERS,
                    3f, 1f);
            ci.cancel();
            return;
        }

        // 双节棍左键和Shift+左键攻击处理
        if (self.getMainHandItem().is(TMMItems.NUNCHUCK) && target instanceof ServerPlayer playerTarget
                && GameUtils.isPlayerAliveAndSurvival(playerTarget) && self instanceof ServerPlayer spself) {
            boolean isShiftLeftClick = self.isShiftKeyDown();
            int direction = isShiftLeftClick ? 2 : 1; // Shift+左键=2(向后), 左键=1(向右)
            io.wifi.starrailexpress.network.original.NunchuckHitPayload
                    .onHurt(spself, playerTarget, direction);
            CrosshairaddonsCompat.onAttack(target);
            ci.cancel();
            return;
        }
    }
}