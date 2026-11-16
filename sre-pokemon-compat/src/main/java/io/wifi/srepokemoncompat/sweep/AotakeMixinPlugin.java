package io.wifi.srepokemoncompat.sweep;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** 只在安装竹叶清时接入服务端公共清理链路。 */
public final class AotakeMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!FabricLoader.getInstance().isModLoaded("aotake_sweep")) return false;
        if (getClass().getClassLoader().getResource(target.replace('.', '/') + ".class") == null) {
            throw new IllegalStateException("Aotake Sweep compatibility target missing: " + target);
        }
        return true;
    }
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
