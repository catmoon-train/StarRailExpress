package io.wifi.srepokemoncompat.sweep;

import net.fabricmc.loader.api.FabricLoader;
import io.wifi.srepokemoncompat.CompatTargets;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** 只在安装竹叶清时接入服务端公共清理链路。 */
public final class AotakeMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!FabricLoader.getInstance().isModLoaded("aotake_sweep")) return false;
        // 精简壳与不同构建都不含原目标类：跳过并告警，而不是让服务器启动失败。
        if (CompatTargets.isRegistryShell("aotake_sweep")) return false;
        if (!CompatTargets.hasTargetClass(target)) {
            CompatTargets.warnMissingOnce("aotake_sweep", target);
            return false;
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
