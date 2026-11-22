package io.wifi.starrailexpress.mixin.client.ponder;

import com.nododiiiii.ponderer.runtime.core.DynamicPonderPlugin;
import com.nododiiiii.ponderer.scene.DslScene;
import io.wifi.starrailexpress.client.ponder.RolePonderIsolation;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 注册职业场景时改用专属虚拟组件，取消与真实物品的绑定。 */
@Mixin(value = DynamicPonderPlugin.class, remap = false)
public class DynamicPonderPluginRoleMixin {
    private static final String REGISTER_SCENE =
            "registerScene(Lnet/createmod/ponder/api/registration/PonderSceneRegistrationHelper;Lcom/nododiiiii/ponderer/scene/DslScene;)V";

    @Inject(method = REGISTER_SCENE, at = @At("HEAD"), remap = false)
    private void sre$detachRoleItems(PonderSceneRegistrationHelper<ResourceLocation> helper, DslScene scene,
                                     CallbackInfo ci) {
        RolePonderIsolation.detachForRegistration(scene);
    }

    @Inject(method = REGISTER_SCENE, at = @At("RETURN"), remap = false)
    private void sre$restoreRoleItems(PonderSceneRegistrationHelper<ResourceLocation> helper, DslScene scene,
                                      CallbackInfo ci) {
        RolePonderIsolation.restoreAfterRegistration(scene);
    }
}
