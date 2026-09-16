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

package io.wifi.starrailexpress.mixin.client.texture;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.starrailexpress.client.render.item.CustomItemRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 食用 / 破碎自定义列车物品时的粒子贴图。
 *
 * <p>
 * 原版 {@code ParticleTypes.ITEM} 粒子的贴图取自「物品模型自带的 particle 贴图」，而
 * {@code custom_item} 的模型是 {@code builtin/entity}、没有任何贴图 ⇒ 原样会画出
 * 「材质丢失」的紫黑图标。这里换成配置外观对应的 sprite
 * （见 {@link CustomItemRenderer#resolveParticleSprite}；贴图由
 * {@code CustomItemSpriteSource} 注册进方块图集）。
 */
@Mixin(BreakingItemParticle.class)
public class BreakingItemParticleMixin {

    @ModifyExpressionValue(
            method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDLnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/BakedModel;getParticleIcon()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite sre$customItemSprite(TextureAtlasSprite original, ClientLevel level, double x, double y,
            double z, ItemStack stack) {
        TextureAtlasSprite sprite = CustomItemRenderer.resolveParticleSprite(stack);
        return sprite == null ? original : sprite;
    }
}
