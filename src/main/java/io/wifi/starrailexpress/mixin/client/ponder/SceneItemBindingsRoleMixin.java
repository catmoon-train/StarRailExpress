package io.wifi.starrailexpress.mixin.client.ponder;

import com.nododiiiii.ponderer.runtime.api.SceneItemBindings;
import io.wifi.starrailexpress.client.ponder.RolePonderIsolation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 让 PondererLib 解析载体时接受我们注册的虚拟组件 id。 */
@Mixin(value = SceneItemBindings.class, remap = false)
public class SceneItemBindingsRoleMixin {
    @Inject(method = "resolve(Ljava/util/List;)Ljava/util/List;", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void sre$acceptRoleComponent(List<String> bindings,
                                                CallbackInfoReturnable<List<ResourceLocation>> cir) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        List<ResourceLocation> synthetic = new ArrayList<>();
        for (String binding : bindings) {
            if (!RolePonderIsolation.isSyntheticBinding(binding)) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(binding);
            if (id != null) {
                synthetic.add(id);
            }
        }
        if (!synthetic.isEmpty()) {
            cir.setReturnValue(List.copyOf(synthetic));
        }
    }
}
