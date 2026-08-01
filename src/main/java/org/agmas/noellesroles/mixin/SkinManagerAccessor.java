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

package org.agmas.noellesroles.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.google.common.cache.LoadingCache;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;

@Mixin(SkinManager.class)
public interface SkinManagerAccessor {
    @Accessor("skinCache")
    LoadingCache<SkinManager.CacheKey, CompletableFuture<PlayerSkin>> getSkinCache();

    @Accessor("skinTextures")
    SkinManager.TextureCache getSkinTextures();

    @Accessor("capeTextures")
    SkinManager.TextureCache getCapeTextures();

    @Accessor("elytraTextures")
    SkinManager.TextureCache getElytraTextures();
}