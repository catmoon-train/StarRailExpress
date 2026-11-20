package io.wifi.starrailexpress.mixin.client.ponder;

import com.nododiiiii.ponderer.editor.api.EditorModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ponderer 1.12.0 的编辑器 mixin 使用了旧版 Minecraft 的 intermediary 字段名，
 * 在 1.21.1 上打开 PonderUI 时会抛 NoSuchFieldError 崩溃。
 * 这里在字段确实不存在时关闭 Ponderer 的场景编辑器模块，播放场景不受影响。
 */
@Mixin(value = EditorModule.class, remap = false)
public class PonderEditorModuleMixin {
    @Inject(method = "isInstalled", at = @At("HEAD"), cancellable = true, remap = false)
    private void sre$disableBrokenPondererEditor(CallbackInfoReturnable<Boolean> cir) {
        if (PonderCompat.isPondererEditorBroken()) {
            cir.setReturnValue(false);
        }
    }
}
