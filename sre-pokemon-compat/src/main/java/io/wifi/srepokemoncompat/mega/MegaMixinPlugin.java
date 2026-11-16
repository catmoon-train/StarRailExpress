package io.wifi.srepokemoncompat.mega;

import io.wifi.srepokemoncompat.CompatTargets;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Mega Showdown 可选兼容入口；只检查目标字节资源，不提前加载或初始化目标类。 */
public final class MegaMixinPlugin implements IMixinConfigPlugin {
    static final String ITEM_MIXIN = "com.github.yajatkaul.mega_showdown.mixin.minecraft.ItemStackMixin";
    static final String DELEGATE_MIXIN = "com.github.yajatkaul.mega_showdown.fabric.mixin.PokemonClientDelegateMixin";
    static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    static final String CIR = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
    static final Set<String> ITEM_HANDLERS = Set.of(
            "(Lnet/minecraft/class_1937;Lnet/minecraft/class_1657;Lnet/minecraft/class_1268;" + CIR + ")V",
            "(Lnet/minecraft/class_1657;Lnet/minecraft/class_1309;Lnet/minecraft/class_1268;" + CIR + ")V");
    static final String DELEGATE_HANDLER = "(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;" + CI + ")V";

    static boolean applies(boolean installed, boolean client, String mixin) {
        return installed && (client || !mixin.startsWith("io.wifi.srepokemoncompat.mixin.mega.client."));
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        var loader = FabricLoader.getInstance();
        if (!applies(loader.isModLoaded("mega_showdown"), loader.getEnvironmentType() == EnvType.CLIENT, mixin)) return false;
        // 精简壳只有注册身份，没有原玩法类：跳过兼容混入而不是让启动失败。
        if (CompatTargets.isRegistryShell("mega_showdown")) return false;
        if (!CompatTargets.hasTargetClass(target)) {
            CompatTargets.warnMissingOnce("mega_showdown", target);
            return false;
        }
        return true;
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith(".MegaItemStackHandlerMixin")) {
            var descriptors = new java.util.HashSet<String>();
            for (var descriptor : ITEM_HANDLERS) descriptors.add(runtimeDescriptor(descriptor));
            MegaHandlerGuards.apply(node, ITEM_MIXIN, "useOnEntity", descriptors,
                    "io/wifi/starrailexpress/compat/cobblemon/CobblemonMechanics");
        } else if (mixin.endsWith(".MegaClientDelegateHandlerMixin")) {
            MegaHandlerGuards.apply(node, DELEGATE_MIXIN, "tick", Set.of(DELEGATE_HANDLER),
                    "io/wifi/srepackcompat/cobblemon/CobblemonPolicy");
        }
    }

    private static String runtimeDescriptor(String descriptor) {
        var resolver = FabricLoader.getInstance().getMappingResolver();
        for (var name : List.of("class_1937", "class_1657", "class_1268", "class_1309")) {
            descriptor = descriptor.replace("Lnet/minecraft/" + name + ";",
                    "L" + resolver.mapClassName("intermediary", "net.minecraft." + name).replace('.', '/') + ";");
        }
        return descriptor;
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
