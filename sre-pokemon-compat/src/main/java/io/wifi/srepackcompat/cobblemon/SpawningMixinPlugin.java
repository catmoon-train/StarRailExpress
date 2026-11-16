package io.wifi.srepackcompat.cobblemon;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** 生成预算的独立入口，不运行客户端 Mixin 插件的启动优化和核心检查。 */
public final class SpawningMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) return false;
        // 精简壳没有刷新/生成类，跳过而不是让 Mixin 准备期失败。
        return !io.wifi.srepokemoncompat.CompatTargets.isRegistryShell("cobblemon");
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
