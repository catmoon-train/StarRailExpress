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
        if (!FabricLoader.getInstance().isModLoaded("meccha_chameleon")
                && mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.mecchachameleon")) {
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
