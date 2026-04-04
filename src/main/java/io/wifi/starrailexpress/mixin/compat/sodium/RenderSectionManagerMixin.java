package io.wifi.starrailexpress.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
// import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderSectionManager.class)
public class RenderSectionManagerMixin {

    // @Inject(method = "shouldUseOcclusionCulling",
    //         at = @At("HEAD"),
    //         remap = false,
    //         cancellable = true)
    // private void sre$forceNotUseOcclusionCulling(Camera camera, boolean spectator, CallbackInfoReturnable<Boolean> cir) {
    //     if (SREClient.needsChunkOffset()) {
    //         cir.setReturnValue(false);
    //     }
    // }

//    @ModifyExpressionValue(method = "getSearchDistance",
//            at = @At(value = "FIELD",
//                    target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumGameOptions$PerformanceSettings;useFogOcclusion:Z"),
//            remap = false)
//    private boolean sre$forceNotUseFogOcclusion(boolean original) {
//        if (SREClient.needsChunkOffset()) {
//            return false;
//        }
//        return original;
//    }
}
