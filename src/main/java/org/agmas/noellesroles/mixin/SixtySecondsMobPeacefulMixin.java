package org.agmas.noellesroles.mixin;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsWhisperSystem;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末日60秒：豁免游戏系统刷出的怪（夜袭僵尸 / 低语怪 Vex）在<b>和平难度</b>下被原版秒删。
 * <p>
 * 60s 开局会把服务器难度设为 PEACEFUL（{@code SixtySecondsGameSetup.prepareWorld}），
 * 而 {@link Mob#checkDespawn()} 对敌对怪（{@code shouldDespawnInPeaceful()=true}）会立即
 * {@code discard()}——夜袭怪刚在家门口刷出下一 tick 就消失，表现为「怪物不会刷新在家门口」。
 * {@code setPersistenceRequired()} 只防常规 despawn，防不住和平难度删除，故需在此按 tag 放行。
 */
@Mixin(Mob.class)
public abstract class SixtySecondsMobPeacefulMixin {

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void sixtyseconds$keepGameMobsInPeaceful(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self.level().getDifficulty() == Difficulty.PEACEFUL
                && (self.getTags().contains(SixtySecondsDefenseSystem.ASSAULT_TAG)
                        || self.getTags().contains(SixtySecondsWhisperSystem.WHISPER_TAG))) {
            ci.cancel();
        }
    }
}
