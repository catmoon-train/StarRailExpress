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

package io.wifi.starrailexpress.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class SREMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String s) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!FabricLoader.getInstance().isModLoaded("sodium-extra")) {
            if (mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.sodium_extra"))
                return false;
        }
        if (!FabricLoader.getInstance().isModLoaded("sodium")
                && (mixinClassName.contains("net.exmo.mixin.client.side")
                        || mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.sodium."))) {
            return false;
        }
        
        if (!FabricLoader.getInstance().isModLoaded("carpet")
                && mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.carpet")) {
            return false;
        }
        if (!FabricLoader.getInstance().isModLoaded("dashloader")
                && mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.dashloader")) {
            return false;
        }
        if (mixinClassName.equals("io.wifi.starrailexpress.mixin.compat.dashloader.DashMoonlightNestedModelMixin")
                && !FabricLoader.getInstance().isModLoaded("moonlight")) {
            return false;
        }
        if (mixinClassName.equals("io.wifi.starrailexpress.mixin.compat.dashloader.DashParticleAtlasMixin")) {
            try {
                // 复用 DashLoader 的有效开关（包含配置、模组声明及 VulkanMod），不能要求已禁用的钩子存在。
                return (boolean) Class.forName("dev.notalpha.dashloader.config.ConfigHandler")
                        .getMethod("shouldApplyMixin", String.class)
                        .invoke(null, "dev.notalpha.dashloader.mixin.option.cache.SpriteAtlasTextureMixin");
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot check DashLoader atlas cache option", error);
            }
        }
        if (!FabricLoader.getInstance().isModLoaded("lightspeed")
                && mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.lightspeed.")) {
            return false;
        }
        if (mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.supplementaries.CarpetPistonPlaceholderMixin")
                && (!FabricLoader.getInstance().isModLoaded("carpet")
                    || !FabricLoader.getInstance().isModLoaded("supplementaries"))) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode classNode, String mixinClassName, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
