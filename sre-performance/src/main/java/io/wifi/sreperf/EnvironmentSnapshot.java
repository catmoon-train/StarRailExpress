package io.wifi.sreperf;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.wifi.sreperf.core.SampleAccumulator.row;

/** 只输出诊断所需的允许字段，不保存用户名、服务器地址、聊天或完整启动参数。 */
final class EnvironmentSnapshot {
    static Map<String, Object> collect(Minecraft client) {
        var loader = FabricLoader.getInstance();
        var result = new LinkedHashMap<String, Object>();
        result.put("os", row("name", System.getProperty("os.name"), "version", System.getProperty("os.version"), "arch", System.getProperty("os.arch")));
        result.put("java", row("version", System.getProperty("java.version"), "vm", System.getProperty("java.vm.name"), "processors", Runtime.getRuntime().availableProcessors()));
        String library = System.getProperty("org.lwjgl.opengl.libname", "default").replace('\\', '/');
        result.put("graphics", row("vendor", gl(GL11.GL_VENDOR), "renderer", gl(GL11.GL_RENDERER),
                "version", gl(GL11.GL_VERSION), "library", library.substring(library.lastIndexOf('/') + 1),
                "framebufferWidth", client.getWindow().getWidth(), "framebufferHeight", client.getWindow().getHeight(),
                "gpuExecutionTimer", "not_measured", "shaderPackInUse", shaders()));
        result.put("options", row("renderDistance", client.options.renderDistance().get(),
                "simulationDistance", client.options.simulationDistance().get(), "maxFps", client.options.framerateLimit().get(),
                "vsync", client.options.enableVsync().get(), "graphics", client.options.graphicsMode().get().name(),
                "particles", client.options.particles().get().name(), "fullscreen", client.getWindow().isFullscreen()));
        result.put("resourcePacks", client.getResourcePackRepository().getSelectedIds().stream().sorted().toList());
        result.put("mods", loader.getAllMods().stream().sorted(Comparator.comparing(m -> m.getMetadata().getId()))
                .map(m -> row("id", m.getMetadata().getId(), "name", m.getMetadata().getName(), "version", m.getMetadata().getVersion().getFriendlyString())).toList());
        result.put("minecraft", loader.getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        result.put("fabricLoader", loader.getModContainer("fabricloader").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        result.put("modVersion", loader.getModContainer("sre_performance").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        return result;
    }

    private static String gl(int key) {
        try { return GL11.glGetString(key); }
        catch (RuntimeException | LinkageError e) { return "unavailable"; }
    }

    private static Object shaders() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return false;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return api.getMethod("isShaderPackInUse").invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return null; }
    }
}
