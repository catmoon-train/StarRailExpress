package io.wifi.srepackcompat.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** 从已解析的内置 TOML 建立字段快照；仅初始化时解析反射，运行时不扫描类结构。 */
public final class ChlorideConfigSnapshot {
    private final List<LockedField> fields;

    public ChlorideConfigSnapshot(Class<?> config, Map<String, Object> entries, Function<String, ?> idParser)
            throws ReflectiveOperationException {
        List<LockedField> result = new ArrayList<>();
        for (var entry : entries.entrySet()) {
            String[] path = entry.getKey().split("\\.");
            Object owner = null;
            Class<?> type = config;
            for (int i = 0; i < path.length - 1; i++) {
                String name = i == 0 && path[i].equals("interface") ? "ui" : path[i];
                Field group = type.getField(name);
                owner = group.get(owner);
                if (owner == null) throw new IllegalArgumentException("空配置分组: " + entry.getKey());
                type = owner.getClass();
            }
            Field field = type.getField(path[path.length - 1]);
            if (Modifier.isFinal(field.getModifiers())) throw new IllegalArgumentException("无法锁定 final 字段: " + field);
            Object value = decode(field.getType(), entry.getValue(), idParser);
            result.add(new LockedField(field, owner, value));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("内置 Chloride 配置为空");
        fields = List.copyOf(result);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object decode(Class<?> type, Object raw, Function<String, ?> idParser) {
        if (List.class.isAssignableFrom(type) && raw instanceof String[] array) {
            List<Object> ids = new ArrayList<>();
            for (String value : array) ids.add(Objects.requireNonNull(idParser.apply(value)));
            return List.copyOf(ids);
        }
        if (!(raw instanceof String value)) throw new IllegalArgumentException("配置类型不匹配: " + type);
        if (type == boolean.class) {
            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("非法布尔值: " + value);
            return Boolean.valueOf(value);
        }
        if (type == int.class) return Integer.parseInt(value.replace("_", ""));
        if (type == double.class) {
            double number = Double.parseDouble(value.replace("_", ""));
            if (!Double.isFinite(number)) throw new IllegalArgumentException("非法浮点数: " + value);
            return number;
        }
        if (type.isEnum()) return Enum.valueOf((Class) type, value);
        if (type == String.class) return value;
        throw new IllegalArgumentException("不支持的 Chloride 配置类型: " + type);
    }

    /** 保留原分组、可变列表引用；仅恢复实际被改写的值。 */
    public int restore() {
        int changed = 0;
        try {
            for (LockedField locked : fields) {
                Object current = locked.field.get(locked.owner);
                if (Objects.equals(current, locked.value)) continue;
                if (current instanceof List<?> list && locked.value instanceof List<?> fixed) {
                    restoreList(locked, list, fixed);
                } else {
                    locked.field.set(locked.owner, locked.value instanceof List<?> list ? new ArrayList<>(list) : locked.value);
                }
                changed++;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法恢复 Chloride 锁定字段", e);
        }
        return changed;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreList(LockedField locked, List current, List<?> fixed) throws IllegalAccessException {
        try {
            current.clear();
            current.addAll(fixed);
        } catch (UnsupportedOperationException e) {
            locked.field.set(locked.owner, new ArrayList<>(fixed));
        }
    }

    public int size() { return fields.size(); }
    private record LockedField(Field field, Object owner, Object value) { }
}
