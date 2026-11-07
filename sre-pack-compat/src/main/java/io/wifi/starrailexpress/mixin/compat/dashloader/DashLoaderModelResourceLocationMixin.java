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

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * DashLoader 的 {@code ModelIdentifierMixin}（priority 999）用 {@code id.hashCode()} /
 * {@code id.equals(...)} 覆盖原版 record 实现。原版 {@code Objects.hashCode} 对 null 安全，
 * DashLoader 不是：Fabric extra model 若带着 null {@link ResourceLocation}，
 * {@code ModelBakery} 往 HashMap 塞 key 时会在 95% 资源加载处 NPE。
 */
@Mixin(value = ModelResourceLocation.class, priority = 1500)
public abstract class DashLoaderModelResourceLocationMixin {

    @Shadow
    @Final
    private ResourceLocation id;

    @Shadow
    @Final
    private String variant;

    @Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
    private void sre$dashloaderNullSafeHashCode(CallbackInfoReturnable<Integer> cir) {
        if (this.id == null) {
            cir.setReturnValue(31 * 0 + (this.variant == null ? 0 : this.variant.hashCode()));
        }
    }

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void sre$dashloaderNullSafeEquals(Object o, CallbackInfoReturnable<Boolean> cir) {
        if (this.id != null) {
            return;
        }
        if (this == o) {
            cir.setReturnValue(true);
            return;
        }
        if (!(o instanceof ModelResourceLocation that)) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(that.id() == null && Objects.equals(this.variant, that.variant()));
    }
}
