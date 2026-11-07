package io.wifi.srepackcompat;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/** 按实际安装的模组启用补丁，检查时不加载 Minecraft 或第三方目标类。 */
public final class SrePackCompatMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        FabricLoader.getInstance().getModContainer("starrailexpress").ifPresent(core ->
                core.findPath("starrailexpress.mixins.json").ifPresent(path -> {
                    try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        var config = JsonParser.parseReader(reader).getAsJsonObject();
                        if (!config.has("client")) return;
                        for (var entry : config.getAsJsonArray("client")) {
                            String name = entry.getAsString();
                            if (name.startsWith("compat.dashloader.")
                                    || name.equals("client.HttpTextureUploadMixin")
                                    || name.equals("client.BlockStateWarningMixin")) {
                                throw new IllegalStateException("SRE Pack Compat requires the extracted StarRailExpress core. "
                                        + "Replace the old core JAR with the matching new build; duplicate patch: " + name);
                            }
                        }
                    } catch (IOException error) {
                        throw new IllegalStateException("Cannot check StarRailExpress patch registration", error);
                    }
                }));
        io.wifi.srepackcompat.startup.EarlyMixinPruner.install();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        FabricLoader loader = FabricLoader.getInstance();
        if (mixinClassName.contains(".compat.voicechat.")) {
            return loader.isModLoaded("voicechat");
        }
        if (mixinClassName.endsWith(".compat.sre.GeneralModelLookupMixin")) {
            return io.wifi.srepackcompat.startup.SreModelLookupConfig.shouldApply();
        }
        if (mixinClassName.startsWith("io.wifi.srepackcompat.mixin.runtime.")) {
            return io.wifi.srepackcompat.config.RuntimeFeatureSelection.shouldApply(mixinClassName, loader::isModLoaded);
        }
        if (mixinClassName.startsWith("io.wifi.srepackcompat.mixin.settings.")) {
            return loader.isModLoaded("sodium");
        }
        if (mixinClassName.startsWith("io.wifi.srepackcompat.mixin.chloride.")) {
            return loader.isModLoaded("chloride") && loader.isModLoaded("sodium");
        }
        if (mixinClassName.startsWith("io.wifi.srepackcompat.mixin.particle.")) {
            return loader.isModLoaded("particle_core") && loader.isModLoaded("dsurround");
        }
        if (mixinClassName.contains(".compat.resourcefullib.")) {
            return loader.getModContainer("resourcefullib")
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("3.0.12"))
                    .orElse(false);
        }
        boolean dash = mixinClassName.contains(".compat.dashloader.");
        if ((dash || mixinClassName.endsWith(".BlockStateWarningMixin")) && !loader.isModLoaded("dashloader")) {
            return false;
        }
        if (mixinClassName.endsWith(".DashLoadingOverlayMixin") && !loader.isModLoaded("starrailexpress")) {
            return false;
        }
        if (mixinClassName.endsWith(".DashMoonlightNestedModelMixin") && !loader.isModLoaded("moonlight")) {
            return false;
        }
        if (mixinClassName.endsWith(".DashParticleAtlasMixin")) {
            try {
                return (boolean) Class.forName("dev.notalpha.dashloader.config.ConfigHandler")
                        .getMethod("shouldApplyMixin", String.class)
                        .invoke(null, "dev.notalpha.dashloader.mixin.option.cache.SpriteAtlasTextureMixin");
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot check DashLoader atlas cache option", error);
            }
        }
        return true;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
