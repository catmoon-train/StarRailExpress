package io.wifi.sreperf;

import net.fabricmc.loader.api.FabricLoader;

import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 停止采集后按实际类资源与 Mixin 方法标记定位来源，不把路径关联解释为独占耗时。 */
public final class SourceResolver implements Function<StackTraceElement, String> {
    private record Root(String uri, String id) { }
    private final ArrayList<Root> roots = new ArrayList<>();
    private final Set<String> modIds = new HashSet<>();
    private final Map<String, String> cache = new HashMap<>();
    private final ClassLoader loader;

    public SourceResolver() {
        loader = SourceResolver.class.getClassLoader();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (infrastructureId(id)) continue;
            modIds.add(id);
            if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader")) continue;
            for (var root : mod.getRootPaths()) roots.add(new Root(normalize(root.toUri().toString()), id));
        }
        roots.sort(Comparator.comparingInt((Root r) -> r.uri.length()).reversed());
    }

    @Override
    public String apply(StackTraceElement frame) {
        String name = frame.getClassName();
        if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.blaze3d.")) {
            for (String token : frame.getMethodName().split("\\$")) {
                if (modIds.contains(token) && !token.equals("minecraft")) return "mod:" + token;
            }
        }
        return cache.computeIfAbsent(name, this::resolveClass);
    }

    private String resolveClass(String name) {
        if (name.startsWith("io.wifi.sreperf.")) return "observer";
        if (name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("sun.")) return "java";
        if (name.startsWith("org.lwjgl.")) return "native_gl_or_window";
        if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.blaze3d.")) return "minecraft";
        int lambda = name.indexOf("$$Lambda");
        if (lambda >= 0) name = name.substring(0, lambda);
        try {
            URL resource = loader.getResource(name.replace('.', '/') + ".class");
            if (resource != null) {
                String uri = normalize(resource.toString());
                for (Root root : roots) if (uri.startsWith(root.uri)) return "mod:" + root.id;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // 未解析来源保留 unknown；不主动加载或初始化被观察的类。
        }
        return "unknown";
    }

    static boolean infrastructureId(String id) {
        return id.equals("sre_performance") || id.equals("mixinextras") || id.equals("fabricloader") || id.startsWith("fabric-");
    }

    static String normalize(String uri) {
        return uri.replace('\\', '/').replaceFirst("^jar:", "")
                .replaceFirst("^file:/+", "file:/");
    }
}
