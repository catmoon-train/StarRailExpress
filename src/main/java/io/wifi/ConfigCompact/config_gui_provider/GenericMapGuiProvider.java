package io.wifi.ConfigCompact.config_gui_provider;

import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.NestedListListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class GenericMapGuiProvider {

    // GUI 内部使用的可变键值对，与实际 Map 类型解耦
    public static class MapEntry {
        public String key = "";
        public String value = "";

        public MapEntry() {
        }

        public MapEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    // ✅ 注册到任意 GuiRegistry，匹配所有 Map 类型字段
    public static void register(GuiRegistry registry) {
        registry.registerPredicateProvider(
                (i18n, field, config, defaults, access) -> {
                    return provide(i18n, field, config, defaults, access);
                },
                field -> Map.class.isAssignableFrom(field.getType()));
    }

    @SuppressWarnings({ "rawtypes" })
    private static List<AbstractConfigListEntry> provide(
            String i18n, Field field, Object config, Object defaults,
            GuiRegistryAccess access) {

        Map<Object, Object> map = getOrCreate(field, config);
        Map<Object, Object> defMap = getOrCreate(field, defaults);
        Class<?> keyType = getTypeArg(field, 0);
        Class<?> valueType = getTypeArg(field, 1);

        List<MapEntry> entries = toEntryList(map);
        List<MapEntry> defEntries = toEntryList(defMap);

        var listWidget = new NestedListListEntry<MapEntry, MultiElementListEntry<MapEntry>>(
                Component.translatable(i18n),
                entries,
                false,
                Optional::empty,
                updated -> syncBack(field, config, updated, keyType, valueType),
                () -> defEntries,
                Component.translatable("text.cloth-config.reset_value"),
                true,
                true,
                (entry, self) -> buildRow(entry != null ? entry : new MapEntry(), valueType, i18n)); // ✅ 传入 i18n

        return Collections.singletonList(listWidget);
    }

    // --- 每一行：Key 输入 + Value 输入 ---
    private static MultiElementListEntry<MapEntry> buildRow(MapEntry entry, Class<?> valueType, String i18n) {
        var eb = ConfigEntryBuilder.create();

        // ✅ 优先用项目翻译键，没有则 fallback 到 "Key"
        var keyWidget = eb.startStrField(
                Component.translatableWithFallback(i18n + ".key", "Key"),
                entry.key)
                .setDefaultValue("")
                .setSaveConsumer(v -> entry.key = v)
                .build();

        var valueWidget = buildValueWidget(eb, entry, valueType, i18n);

        return new MultiElementListEntry<>(
                Component.literal(entry.key.isBlank() ? "(new)" : entry.key),
                entry,
                List.of(keyWidget, valueWidget),
                true);
    }

    private static AbstractConfigListEntry<?> buildValueWidget(
            ConfigEntryBuilder eb, MapEntry entry, Class<?> type, String i18n) {

        // ✅ 优先用项目翻译键，没有则 fallback 到 "Value"
        Component label = Component.translatableWithFallback(i18n + ".value", "Value");

        if (type == Integer.class || type == int.class) {
            return eb.startIntField(label, tryParseInt(entry.value, 0))
                    .setDefaultValue(0)
                    .setSaveConsumer(v -> entry.value = String.valueOf(v))
                    .build();
        }
        if (type == Long.class || type == long.class) {
            return eb.startLongField(label, tryParseLong(entry.value, 0L))
                    .setDefaultValue(0L)
                    .setSaveConsumer(v -> entry.value = String.valueOf(v))
                    .build();
        }
        if (type == Float.class || type == float.class) {
            return eb.startFloatField(label, tryParseFloat(entry.value, 0f))
                    .setDefaultValue(0f)
                    .setSaveConsumer(v -> entry.value = String.valueOf(v))
                    .build();
        }
        if (type == Double.class || type == double.class) {
            return eb.startDoubleField(label, tryParseDouble(entry.value, 0.0))
                    .setDefaultValue(0.0)
                    .setSaveConsumer(v -> entry.value = String.valueOf(v))
                    .build();
        }
        if (type == Boolean.class || type == boolean.class) {
            return eb.startBooleanToggle(label, Boolean.parseBoolean(entry.value))
                    .setDefaultValue(false)
                    .setSaveConsumer(v -> entry.value = String.valueOf(v))
                    .build();
        }
        return eb.startStrField(label, entry.value)
                .setDefaultValue("")
                .setSaveConsumer(v -> entry.value = v)
                .build();
    }
    // --- 工具方法 ---

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> getOrCreate(Field field, Object obj) {
        try {
            field.setAccessible(true);
            Map<Object, Object> m = (Map<Object, Object>) field.get(obj);
            if (m == null) {
                m = new LinkedHashMap<>();
                field.set(obj, m);
            }
            return m;
        } catch (IllegalAccessException e) {
            return new LinkedHashMap<>();
        }
    }

    private static List<MapEntry> toEntryList(Map<Object, Object> map) {
        return map.entrySet().stream()
                .map(e -> new MapEntry(String.valueOf(e.getKey()), String.valueOf(e.getValue())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @SuppressWarnings("unchecked")
    private static void syncBack(Field field, Object config,
            List<MapEntry> updated, Class<?> keyType, Class<?> valueType) {
        try {
            field.setAccessible(true);
            Map<Object, Object> m = (Map<Object, Object>) field.get(config);
            if (m == null) {
                m = new LinkedHashMap<>();
                field.set(config, m);
            }
            m.clear();
            for (MapEntry e : updated) {
                if (e.key.isBlank())
                    continue; // 跳过空 key
                Object k = parse(e.key, keyType);
                Object v = parse(e.value, valueType);
                if (k != null)
                    m.put(k, v);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    // 解析字符串 -> 目标类型
    private static Object parse(String s, Class<?> type) {
        if (s == null)
            return null;
        try {
            if (type == Integer.class || type == int.class)
                return Integer.parseInt(s.trim());
            if (type == Long.class || type == long.class)
                return Long.parseLong(s.trim());
            if (type == Float.class || type == float.class)
                return Float.parseFloat(s.trim());
            if (type == Double.class || type == double.class)
                return Double.parseDouble(s.trim());
            if (type == Boolean.class || type == boolean.class)
                return Boolean.parseBoolean(s.trim());
        } catch (NumberFormatException e) {
            return null; // 解析失败则丢弃
        }
        return s; // String 或未知类型原样保留
    }

    // 取 Map<K,V> 的第 index 个类型参数
    private static Class<?> getTypeArg(Field field, int index) {
        Type t = field.getGenericType();
        if (t instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > index && args[index] instanceof Class<?> c)
                return c;
        }
        return String.class; // 擦除或原始类型时降级为 String
    }

    private static int tryParseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long tryParseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static float tryParseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double tryParseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}