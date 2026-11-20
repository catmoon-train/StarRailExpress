package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.init.RoleInitialItems;
import org.agmas.noellesroles.utils.RoleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过反射打开 Ponderer 思索场景，并把当前职业的名称、商店和初始物品注入场景文本。
 * 这里不直接引用 Ponderer 的类，确保未安装 Ponderer 时职业介绍页仍然可以正常打开。
 */
public final class RolePonderTutorial {
    private static final Logger LOGGER = LoggerFactory.getLogger(RolePonderTutorial.class);

    private static final String SCENE_NAMESPACE = "starrailexpress";
    private static final String SCENE_PREFIX = SCENE_NAMESPACE + ":role_tutorial.";

    private static final String RUNTIME_ACCESS = "com.nododiiiii.ponderer.application.api.RuntimeAccess";
    private static final String SCENE_VARIANTS = "com.nododiiiii.ponderer.runtime.api.SceneVariants";
    private static final String DLS_SCENE = "com.nododiiiii.ponderer.scene.DslScene";
    private static final String LOCALIZED_TEXT = "com.nododiiiii.ponderer.scene.LocalizedText";
    private static final String SCENE_SNAPSHOT = "com.nododiiiii.ponderer.scene.SceneSnapshot";
    private static final String COMPILE_REQUEST = "com.nododiiiii.ponderer.runtime.api.CompileRequest";
    private static final String RUNTIME_ENGINE = "com.nododiiiii.ponderer.runtime.api.RuntimeEngine";
    private static final String COMPILATION_RESULT = "com.nododiiiii.ponderer.runtime.api.CompilationResult";
    private static final String PONDER_SCREEN_NAVIGATION = "com.nododiiiii.ponderer.application.ui.PonderScreenNavigation";
    private static final String DRAFT_UI = "com.nododiiiii.ponderer.application.ui.PonderScreenNavigation$DraftPonderUI";
    private static final String PONDER_UI = "net.createmod.ponder.foundation.ui.PonderUI";

    private RolePonderTutorial() {
    }

    /** 打开职业对应的动态教程；找不到场景或 Ponderer 不可用时返回 false。 */
    public static boolean open(SRERole role) {
        if (role == null) {
            return false;
        }
        try {
            String wantedId = SCENE_PREFIX + role.identifier().getPath();
            Object scene = findScene(wantedId);
            if (scene == null) {
                return false;
            }
            applyRoleContext(scene, role);
            return openCompiledScene(scene);
        } catch (Throwable throwable) {
            LOGGER.warn("无法打开职业 {} 的思索场景：{}", role.identifier(), describe(throwable));
            return false;
        }
    }

    private static Object findScene(String wantedId) throws Exception {
        Object result = invokeStaticOrNull(RUNTIME_ACCESS, "scenes");
        if (result == null) {
            result = invokeStaticOrNull(SCENE_VARIANTS, "activeScenes");
        }
        if (!(result instanceof List<?> scenes)) {
            return null;
        }
        Class<?> dslClass = Class.forName(DLS_SCENE);
        Field idField = findField(dslClass, "id");
        for (Object scene : scenes) {
            if (dslClass.isInstance(scene) && wantedId.equals(idField.get(scene))) {
                return scene;
            }
        }
        return null;
    }

    private static void applyRoleContext(Object scene, SRERole role) throws Exception {
        Map<String, String> variables = buildVariables(role);
        Class<?> localizedClass = Class.forName(LOCALIZED_TEXT);

        replaceInText(getField(scene, "title"), variables, localizedClass);
        if (getField(scene, "scenes") instanceof List<?> segments) {
            for (Object segment : segments) {
                replaceInText(getField(segment, "title"), variables, localizedClass);
                if (getField(segment, "steps") instanceof List<?> steps) {
                    for (Object step : steps) {
                        replaceInStep(step, variables, localizedClass);
                    }
                }
            }
        }
    }

    private static void replaceInStep(Object step, Map<String, String> variables, Class<?> localizedClass)
            throws Exception {
        replaceInText(getField(step, "text"), variables, localizedClass);
        replaceInText(getField(step, "hint"), variables, localizedClass);
        if (getField(step, "nbt") instanceof String nbt) {
            setField(step, "nbt", replace(nbt, variables));
        }
        if (getField(step, "steps") instanceof List<?> nested) {
            for (Object child : nested) {
                replaceInStep(child, variables, localizedClass);
            }
        }
    }

    private static void replaceInText(Object text, Map<String, String> variables, Class<?> localizedClass)
            throws Exception {
        if (text == null || !localizedClass.isInstance(text)) {
            return;
        }
        Field plain = findField(localizedClass, "plain");
        Field localized = findField(localizedClass, "localized");
        Field reference = findField(localizedClass, "reference");
        Field key = findField(localizedClass, "key");

        if (plain.get(text) instanceof String value) {
            plain.set(text, replace(value, variables));
            return;
        }

        Map<String, String> source;
        if (reference.get(text) != null) {
            Method allTranslations = localizedClass.getMethod("getAllTranslations");
            Object translated = allTranslations.invoke(text);
            source = translated instanceof Map<?, ?> map ? castToStringMap(map) : Map.of();
        } else if (localized.get(text) instanceof Map<?, ?> map) {
            source = castToStringMap(map);
        } else {
            return;
        }

        Map<String, String> replaced = new LinkedHashMap<>();
        source.forEach((lang, value) -> replaced.put(lang, replace(value, variables)));
        localized.set(text, replaced);
        plain.set(text, null);
        reference.set(text, null);
        key.set(text, null);
    }

    private static Map<String, String> castToStringMap(Map<?, ?> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((k, v) -> result.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        return result;
    }

    private static String replace(String source, Map<String, String> variables) {
        String result = source;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Map<String, String> buildVariables(SRERole role) {
        List<ShopEntry> entries = LimitedInventoryScreen.getRoleShopEntries(role, null);
        if (entries == null) {
            entries = List.of();
        }
        String separator = ", ";
        StringBuilder shop = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            ShopEntry entry = entries.get(i);
            if (i > 0) {
                shop.append(separator);
            }
            String currency = Component.translatable(entry.currency().displayNameKey()).getString();
            shop.append(entry.stack().getHoverName().getString()).append(" (")
                    .append(entry.price()).append(" ").append(currency).append(")");
        }
        if (shop.isEmpty()) {
            shop.append(Component.translatable("screen.roleintroduce.ponder.no_shop").getString());
        }

        List<String> initialItems = new ArrayList<>();
        for (var stack : RoleInitialItems.getInitialItemsForRole(role)) {
            if (stack != null && !stack.isEmpty()) {
                initialItems.add(stack.getHoverName().getString());
            }
        }

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("{role_name}", RoleUtils.getRoleName(role).getString());
        variables.put("{role_id}", role.identifier().toString());
        variables.put("{shop_summary}", shop.toString());
        variables.put("{shop_count}", String.valueOf(entries.size()));
        variables.put("{initial_items}", initialItems.isEmpty()
                ? Component.translatable("screen.roleintroduce.ponder.no_initial").getString()
                : String.join(separator, initialItems));
        if (!entries.isEmpty()) {
            variables.put("{shop_first_item}", entries.getFirst().stack().getHoverName().getString());
            variables.put("{shop_first_price}", String.valueOf(entries.getFirst().price()));
        } else {
            variables.put("{shop_first_item}", Component.translatable("screen.roleintroduce.ponder.no_shop").getString());
            variables.put("{shop_first_price}", "0");
        }
        return variables;
    }

    private static boolean openCompiledScene(Object scene) throws Exception {
        try {
            Method openDraft = Class.forName(PONDER_SCREEN_NAVIGATION).getDeclaredMethod(
                    "openDraftScene", Class.forName(DLS_SCENE), int.class);
            openDraft.setAccessible(true);
            if (Boolean.TRUE.equals(openDraft.invoke(null, scene, 0))) {
                return true;
            }
        } catch (Throwable throwable) {
            LOGGER.debug("Ponderer openDraftScene 不可用，回退到手动编译：{}", describe(throwable));
        }

        Object engine = resolveEngine(scene);
        if (engine == null) {
            return false;
        }
        Class<?> dslClass = Class.forName(DLS_SCENE);
        Class<?> snapshotClass = Class.forName(SCENE_SNAPSHOT);
        Object snapshot = snapshotClass.getMethod("of", dslClass).invoke(null, scene);

        Class<?> requestClass = Class.forName(COMPILE_REQUEST);
        Object request = requestClass.getConstructor(snapshotClass, int.class).newInstance(snapshot, 0);
        Method compile = Class.forName(RUNTIME_ENGINE).getMethod("compile", requestClass);
        Object compiled = compile.invoke(engine, request);
        if (compiled == null) {
            return false;
        }
        return showCompiledScreen(compiled);
    }

    private static Object resolveEngine(Object scene) throws Exception {
        Class<?> dslClass = Class.forName(DLS_SCENE);
        Object engine = invokeStaticOrNull(RUNTIME_ACCESS, "engineFor", new Class<?>[]{dslClass}, scene);
        if (engine == null) {
            engine = invokeStaticOrNull(SCENE_VARIANTS, "owner", new Class<?>[]{dslClass}, scene);
        }
        return engine;
    }

    private static boolean showCompiledScreen(Object compiled) {
        try {
            Class<?> draftClass = Class.forName(DRAFT_UI);
            Constructor<?> constructor = draftClass.getDeclaredConstructor(compiled.getClass());
            constructor.setAccessible(true);
            Object screen = constructor.newInstance(compiled);
            Minecraft.getInstance().setScreen((Screen) screen);
            return true;
        } catch (Throwable draftError) {
            LOGGER.debug("Ponderer 草稿界面不可用，尝试直接打开 PonderUI：{}", describe(draftError));
        }

        try {
            Object ponderScene = Class.forName(COMPILATION_RESULT).getMethod("scene").invoke(compiled);
            Class<?> uiClass = Class.forName(PONDER_UI);
            Constructor<?> constructor = uiClass.getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            Object ui = constructor.newInstance(List.of(ponderScene));
            ponderScene.getClass().getMethod("begin").invoke(ponderScene);
            Minecraft.getInstance().setScreen((Screen) ui);
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("无法创建思索界面：{}", describe(throwable));
            return false;
        }
    }

    private static Object invokeStaticOrNull(String className, String methodName, Class<?>[] types, Object... args) {
        try {
            Class<?> owner = Class.forName(className);
            Method method = owner.getMethod(methodName, types);
            return method.invoke(null, args);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException throwable) {
            LOGGER.debug("调用 {}.{} 失败：{}", className, methodName, describe(throwable));
            return null;
        }
    }

    private static Object invokeStaticOrNull(String className, String methodName) {
        return invokeStaticOrNull(className, methodName, new Class<?>[0]);
    }

    private static Object getField(Object target, String name) throws Exception {
        if (target == null) {
            return null;
        }
        return findField(target.getClass(), name).get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 继续向上查找
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static String describe(Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
        return cause.getClass().getName() + ": " + cause.getMessage();
    }
}
