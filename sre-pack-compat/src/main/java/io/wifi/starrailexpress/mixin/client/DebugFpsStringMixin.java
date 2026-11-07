package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Locale;

/** F3 关闭时不格式化调试 FPS 行，但保留原版 FPS 数值与计时更新。 */
@Mixin(Minecraft.class)
public abstract class DebugFpsStringMixin {
    @WrapOperation(method = "runTick", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private String sre$formatFpsOnlyWithDebugOverlay(Locale locale, String format, Object[] args,
                                                    Operation<String> original) {
        Minecraft client = (Minecraft) (Object) this;
        // 仅跳过格式化，不能取消 runTick 或 FPS 更新循环，否则会破坏 HUD 模组的数值统计。
        // 直接读取 fpsString 的第三方模组在 F3 关闭时会得到上一次的调试行。
        return client.gui.getDebugOverlay().showDebugScreen()
                ? original.call(locale, format, args)
                : client.fpsString;
    }
}
