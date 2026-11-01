package io.wifi.starrailexpress.mixin.compat.supplementaries;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carpet 已安装真实的搬运实体，并将原调用替换为空占位与空操作。
 * 让占位继续经过原操作链，避免 Supplementaries 的空值断言误报；绝不创建第二个实体覆盖容器数据。
 * 此 Mixin 仅在 Carpet 与 Supplementaries 同时存在时启用。
 */
@Mixin(value = PistonBaseBlock.class, priority = 1500)
public class CarpetPistonPlaceholderMixin {
    @TargetHandler(mixin = "net.mehvahdjukaar.supplementaries.mixins.PistonBaseBlockMixin",
            name = "supp$captureBeForPistonMove", prefix = "wrapOperation")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void sre$forwardCarpetPlaceholder(Level level, BlockEntity movingPiston, Operation<Void> original,
                                               Direction facing, boolean extending, CallbackInfo ci) {
        if (movingPiston == null) {
            // Carpet 的原操作为空操作；其他真实异常继续传播，不能统一跳过 setBlockEntity。
            original.call(level, movingPiston);
            ci.cancel();
        }
    }
}
