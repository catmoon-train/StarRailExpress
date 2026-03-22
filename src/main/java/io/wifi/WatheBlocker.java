package io.wifi;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class WatheBlocker implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatheBlocker.class);

    private static final String TARGET_MOD_ID = "wathe";
    // private static final Set<String> BLOCKED_MIXIN_CONFIGS = Set.of(
    // "wathe.mixins.json");

    private static final HashSet<String> WatheWhiteList = new HashSet<>(
            List.of("dev.doctor4t.wathe.client.model.WatheModelLayers", "dev.doctor4t.wathe.Wathe"));
    private static final HashSet<String> WatheWhiteListPrefix = new HashSet<>(
            List.of("dev.doctor4t.wathe.util"));

    @Override
    public void onPreLaunch() {
        blockAllStaticInit(); // ← 最先执行，类加载前装好变换器
        blockEntrypoints();
        blockAccessWidener();
        blockCustomData();
    }

    private void blockAllStaticInit() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.lang.reflect.Method getDelegateMethod = cl.getClass().getDeclaredMethod("getDelegate");
            getDelegateMethod.setAccessible(true);
            Object delegate = getDelegateMethod.invoke(cl);

            java.lang.reflect.Method getMixinTransformer = delegate.getClass()
                    .getDeclaredMethod("getMixinTransformer");
            getMixinTransformer.setAccessible(true);
            Object mixinTransformer = getMixinTransformer.invoke(delegate);

            // delegate 字段里找存放 transformer 的字段
            Field transformerField = null;
            Class<?> clazz = delegate.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(delegate);
                    if (val == mixinTransformer) {
                        transformerField = f;
                        break;
                    }
                }
                if (transformerField != null)
                    break;
                clazz = clazz.getSuperclass();
            }

            if (transformerField == null) {
                LOGGER.error("[WatheBlocker] could not find MixinTransformer");
                return;
            }

            LOGGER.info("[WatheBlocker] find transformer: {} in {}",
                    transformerField.getName(), transformerField.getDeclaringClass().getSimpleName());
            java.lang.reflect.Method getPreMixin = delegate.getClass()
                    .getDeclaredMethod("getPreMixinClassByteArray", String.class, boolean.class);
            getPreMixin.setAccessible(true);

            Object wrapper = java.lang.reflect.Proxy.newProxyInstance(
                    mixinTransformer.getClass().getClassLoader(),
                    mixinTransformer.getClass().getInterfaces(),
                    (proxy, method, args) -> {
                        Object result = method.invoke(mixinTransformer, args);

                        // 只处理 wathe 自身类的 <clinit>，不再回退其他类
                        if (method.getName().equals("transformClassBytes")
                                && args != null && args.length == 3
                                && args[0] instanceof String name
                                && name.startsWith("dev.doctor4t.wathe.")
                                && result instanceof byte[] bytes) {
                            if (!WatheWhiteList.contains(name)
                                    && !WatheWhiteListPrefix.stream().anyMatch((prefix) -> name.startsWith(prefix)))
                                return stripClinit(bytes, name);
                        }

                        return result;
                    });
            transformerField.set(delegate, wrapper);
            LOGGER.info("[WatheBlocker] Installed <clinit> remover!");

        } catch (Exception e) {
            LOGGER.error("[WatheBlocker] blockAllStaticInit Failed! ", e);
        }
    }

    private byte[] stripClinit(byte[] bytes, String className) {
        try {
            org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
            org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(cr, 0);

            cr.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, cw) {
                @Override
                public org.objectweb.asm.MethodVisitor visitMethod(
                        int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.equals("<clinit>")) {
                        LOGGER.info("[WatheBlocker] Cleared {}.{}", className, "<clinit>");
                        // 写入空的 <clinit>（只有 RETURN），让类正常加载但不执行任何静态初始化
                        org.objectweb.asm.MethodVisitor mv = super.visitMethod(
                                access, name, descriptor, signature, exceptions);
                        mv.visitCode();
                        mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                        return null; // 返回 null 阻止 ClassReader 继续把原方法体写进去
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);

            return cw.toByteArray();
        } catch (Exception e) {
            LOGGER.warn("[WatheBlocker] stripClinit failed: {}", className, e);
            return bytes;
        }
    }

    private void blockEntrypoints() {
        FabricLoader.getInstance().getModContainer(TARGET_MOD_ID).ifPresentOrElse(
                container -> {
                    try {
                        Object meta = container.getMetadata();
                        Field entrypointsField = findField(meta, Map.class,
                                "entrypoints", "entrypointKeys", "entrypointMap");

                        if (entrypointsField == null) {
                            LOGGER.warn("[WatheBlocker] not found entrypoints, all: ");
                            printAllFields(meta);
                            return;
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, ?> entrypoints = (Map<String, ?>) entrypointsField.get(meta);
                        LOGGER.info("[WatheBlocker] Remove entrypoints: {}", entrypoints.keySet());
                        // ✅ 直接替换字段为新的空 Map，绕过 unmodifiableMap 限制
                        entrypointsField.set(meta, new java.util.HashMap<>());
                        LOGGER.info("[WatheBlocker] Clear {} entrypoints!", TARGET_MOD_ID);

                    } catch (Exception e) {
                        LOGGER.error("[WatheBlocker] Clear entrypoints failed!", e);
                    }
                },
                () -> LOGGER.warn("[WatheBlocker] Mod not found: {}", TARGET_MOD_ID));
    }

    private void blockAccessWidener() {
        FabricLoader.getInstance().getModContainer(TARGET_MOD_ID).ifPresentOrElse(
                container -> {
                    try {
                        Object meta = container.getMetadata();
                        Field customField = findField(meta, String.class,
                                "accessWidener");

                        if (customField == null) {
                            LOGGER.warn("[WatheBlocker] accessWidener not found. All: ");
                            printAllFields(meta);
                            return;
                        }

                        String custom = (String) customField.get(meta);
                        LOGGER.info("[WatheBlocker] remove accessWidener data: {}", custom);
                        // ✅ 直接替换字段为新的空 Map
                        customField.set(meta, "");
                        LOGGER.info("[WatheBlocker] Cleared {} accessWidener data", TARGET_MOD_ID);

                    } catch (Exception e) {
                        LOGGER.error("[WatheBlocker] Clear accessWidener failed! ", e);
                    }
                },
                () -> LOGGER.warn("[WatheBlocker] Mod not found: {}", TARGET_MOD_ID));
    }

    private void blockCustomData() {
        FabricLoader.getInstance().getModContainer(TARGET_MOD_ID).ifPresentOrElse(
                container -> {
                    try {
                        Object meta = container.getMetadata();
                        Field customField = findField(meta, Map.class,
                                "custom", "customValues", "customData");

                        if (customField == null) {
                            LOGGER.warn("[WatheBlocker] custom not found. All: ");
                            printAllFields(meta);
                            return;
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, ?> custom = (Map<String, ?>) customField.get(meta);
                        LOGGER.info("[WatheBlocker] Remove custom data: {}", custom.keySet());
                        // ✅ 直接替换字段为新的空 Map
                        Map<String, ?> map = new java.util.HashMap<String, Object>(custom);
                        map.remove("loom:injected_interfaces");
                        map.remove("cardinal-components");
                        customField.set(meta, map);
                        LOGGER.info("[WatheBlocker] cleared {} custom data!", TARGET_MOD_ID);

                    } catch (Exception e) {
                        LOGGER.error("[WatheBlocker] cleared custom failed! ", e);
                    }
                },
                () -> LOGGER.warn("[WatheBlocker] Mod not found: {}", TARGET_MOD_ID));
    }

    /** 遍历类及其父类，查找指定名称且类型匹配的字段 */
    private Field findField(Object obj, Class<?> expectedType, String... names) {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (String name : names) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    if (expectedType.isAssignableFrom(f.getType())) {
                        return f;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private void printAllFields(Object obj) {
        for (Field f : obj.getClass().getDeclaredFields()) {
            LOGGER.warn("  - {} : {}", f.getName(), f.getType().getName());
        }
    }
}