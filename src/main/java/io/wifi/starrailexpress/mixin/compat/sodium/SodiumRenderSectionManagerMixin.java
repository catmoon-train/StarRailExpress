package io.wifi.starrailexpress.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.starrailexpress.client.SREClient;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 配套的 RenderSectionManager 补丁。
 *
 * <p>
 * 列车移动时，景色区块经过 UBO 偏移后会出现在它们物理位置之外的地方，
 * 因此需要禁用 Sodium 的遮挡剔除和雾遮挡，确保这些区块始终进入渲染列表。
 * </p>
 *
 * <p>
 * 此 Mixin 替代了原 SodiumSceneryWorldRendererMixin 中的渲染注入逻辑。
 * 旧方案通过额外的 chunkRenderer.render() 调用导致 GL 摄像机 uniform 污染，
 * 新方案完全依赖 SodiumChunkRendererMixin 的 UBO 偏移，不再需要任何额外渲染通道。
 * </p>
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public class SodiumRenderSectionManagerMixin {

    /**
     * 列车移动时禁用遮挡剔除。
     * 遮挡剔除基于区块的物理位置，而景色区块在视觉上已被偏移，
     * 物理位置的遮挡关系与视觉结果不符，必须关闭。
     */
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), remap = false, cancellable = true)
    private void sre$disableOcclusionCulling(Camera camera, boolean spectator,
            CallbackInfoReturnable<Boolean> cir) {
        if (SREClient.isTrainMoving()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 列车移动时禁用雾遮挡。
     * 雾遮挡同样基于物理距离，偏移后的景色区块可能被错误地雾遮挡掉。
     */
    @ModifyExpressionValue(method = "getSearchDistance", at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumGameOptions$PerformanceSettings;useFogOcclusion:Z"), remap = false)
    private boolean sre$disableFogOcclusion(boolean original) {
        return SREClient.isTrainMoving() ? false : original;
    }
}