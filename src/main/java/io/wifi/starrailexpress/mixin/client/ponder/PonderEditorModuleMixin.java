package io.wifi.starrailexpress.mixin.client.ponder;

import com.nododiiiii.ponderer.editor.api.EditorModule;
import io.wifi.starrailexpress.client.ponder.PondererCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ponderer 1.12.0 的编辑器 mixin 使用了旧版 Minecraft 的 intermediary 字段名，
 * 在 1.21.1 上打开 PonderUI 时会抛 NoSuchFieldError 崩溃。
 * 这里在字段确实不存在时关闭 Ponderer 的场景编辑器模块，播放场景不受影响。
 *
 * <p>{@code EditorModule.isInstalled()} 是静态方法，注入处理器也必须是静态方法，
 * 否则 Mixin 会在准备阶段抛 InvalidInjectionException 并整体放弃这个 mixin
 * （日志：non-static callback method ... targets a static method which is not supported）。
 */
@Mixin(value = EditorModule.class, remap = false)
public class PonderEditorModuleMixin {
    @Inject(method = "isInstalled", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sre$disableBrokenPondererEditor(CallbackInfoReturnable<Boolean> cir) {
        if (PondererCompat.isPondererEditorBroken()) {
            cir.setReturnValue(false);
        }
    }
}
