/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.mixin.compat.dashloader;

import io.wifi.starrailexpress.SRE;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric {@code addExtraModel} 会把 extra model id 包成 {@code ModelResourceLocation}。
 * id 为 null 时，DashLoader 覆盖的 hashCode 会在 {@code modelsToBake.put} 处 NPE，
 * 资源包加载失败并卡在 95%。有 DashLoader 时直接丢掉这种非法 extra model。
 */
@Mixin(value = ModelBakery.class, priority = 1500)
public abstract class DashLoaderModelBakeryMixin {

    @Inject(method = "addExtraModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void sre$skipNullExtraModel(ResourceLocation id, CallbackInfo ci) {
        if (id == null) {
            SRE.LOGGER.warn("[SRE] Skipped a null extra model while DashLoader is present.");
            ci.cancel();
        }
    }
}
