package io.wifi.srepokemoncompat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** 只判断可选模组是否存在，不在 Mixin 准备期加载玩法或客户端类。 */
public final class PokemonMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        var loader = FabricLoader.getInstance();
        if (mixin.startsWith("io.wifi.srepokemoncompat.mixin.journeymap.")) {
            return loader.isModLoaded("journeymap");
        }
        boolean shared = mixin.endsWith(".ServerConfigMixin") || mixin.endsWith(".KeyMappingAccess")
                || mixin.endsWith(".ScreenAccess") || mixin.endsWith(".KeyMappingsMixin")
                || mixin.endsWith(".KeyBindsListMixin") || mixin.endsWith(".ScreensMixin");
        return (loader.isModLoaded("cobblemon") || (shared && (loader.isModLoaded("journeymap")
                || loader.isModLoaded("mega_showdown"))))
                && (!mixin.endsWith(".ServerConfigMixin") || loader.isModLoaded("starrailexpress"));
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
