package io.wifi.starrailexpress.mixin.compat.cobblemon;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 已有内容保持可保存，关闭时只暂停其主动玩法；原版实体和方块不进入这些分支。 */
public final class ContentMechanicsMixins {
    @Mixin(Mob.class)
    public static class MobAi {
        @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
        private void sre$ai(CallbackInfo ci) {
            if (!CobblemonMechanics.enabled()
                    && CobblemonMechanics.isCobblemon(BuiltInRegistries.ENTITY_TYPE.getKey(((Mob) (Object) this).getType()))) ci.cancel();
        }
    }

    @Mixin(BlockBehaviour.BlockStateBase.class)
    public static class Plants {
        @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
        private void sre$growth(CallbackInfo ci) {
            if (!CobblemonMechanics.enabled()
                    && CobblemonMechanics.isCobblemon(BuiltInRegistries.BLOCK.getKey(((BlockState) (Object) this).getBlock()))) ci.cancel();
        }
    }

    @Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
    public static class Machines {
        @Shadow @Final private BlockEntity blockEntity;
        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void sre$machine(CallbackInfo ci) {
            if (!CobblemonMechanics.enabled()
                    && CobblemonMechanics.isCobblemon(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()))) ci.cancel();
        }
    }
}
