package io.wifi.starrailexpress.util;

import net.minecraft.nbt.*;
import org.jetbrains.annotations.Nullable;

import io.wifi.ConfigCompact.annotation.ConfigSync;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 通用 NBT 序列化工具
 * 支持基本类型、枚举、集合、Map、数组、自定义对象
 * 支持字段过滤（注解或自定义条件）
 * 支持类型适配器（处理特殊类型）
 */
public final class NbtSerializer {

    // ========== 配置 ==========

    /**
     * 字段过滤器：返回 true 表示序列化/反序列化该字段
     * 默认仅包含没有 @ConfigSync(shouldSync = false) 的字段（若该注解存在）
     */
    private final Predicate<Field> fieldFilter;

    /**
     * 类型适配器：优先于默认处理，用于特殊类型
     */
    private final Map<Class<?>, BiFunction<Object, NbtSerializer, Tag>> serializeAdapters;
    private final Map<Class<?>, Function<Tag, Object>> deserializeAdapters;

    private final Map<Class<?>, Field[]> fieldsCache = new ConcurrentHashMap<>();

    private NbtSerializer(Builder builder) {
        this.fieldFilter = builder.fieldFilter;
        this.serializeAdapters = builder.serializeAdapters;
        this.deserializeAdapters = builder.deserializeAdapters;
    }

    // ========== 公开 API ==========

    public CompoundTag serializeToTag(Object obj) {
        if (obj == null)
            return new CompoundTag();
        CompoundTag root = new CompoundTag();
        serializeFields(obj, obj.getClass(), root);
        return root;
    }

    public <T> T deserializeFromTag(CompoundTag tag, Class<T> targetClass) throws Exception {
        T instance = targetClass.getDeclaredConstructor().newInstance();
        deserializeFields(tag, instance);
        return instance;
    }

    // ========== 核心序列化逻辑 ==========

    @Nullable
    private Tag serializeObject(Object obj) {
        if (obj == null)
            return null;

        // 优先使用适配器
        Class<?> clazz = obj.getClass();
        BiFunction<Object, NbtSerializer, Tag> adapter = serializeAdapters.get(clazz);
        if (adapter != null) {
            return adapter.apply(obj, this);
        }

        // 基本类型 / 包装类
        if (obj instanceof Boolean)
            return ByteTag.valueOf((Boolean) obj);
        if (obj instanceof Byte)
            return ByteTag.valueOf((Byte) obj);
        if (obj instanceof Short)
            return ShortTag.valueOf((Short) obj);
        if (obj instanceof Integer)
            return IntTag.valueOf((Integer) obj);
        if (obj instanceof Long)
            return LongTag.valueOf((Long) obj);
        if (obj instanceof Float)
            return FloatTag.valueOf((Float) obj);
        if (obj instanceof Double)
            return DoubleTag.valueOf((Double) obj);
        if (obj instanceof String)
            return StringTag.valueOf((String) obj);
        if (obj instanceof Enum)
            return StringTag.valueOf(((Enum<?>) obj).name());

        // 数组（原始类型）
        if (obj instanceof int[]) {
            int[] arr = (int[]) obj;
            int[] copy = Arrays.copyOf(arr, arr.length);
            return new IntArrayTag(copy);
        }
        if (obj instanceof byte[]) {
            byte[] arr = (byte[]) obj;
            byte[] copy = Arrays.copyOf(arr, arr.length);
            return new ByteArrayTag(copy);
        }
        if (obj instanceof long[]) {
            long[] arr = (long[]) obj;
            long[] copy = Arrays.copyOf(arr, arr.length);
            return new LongArrayTag(copy);
        }
        // 其他原始数组暂不支持（short, float, double）但可以转为 ListTag 或自定义

        // 集合
        if (obj instanceof Collection) {
            ListTag list = new ListTag();
            for (Object item : (Collection<?>) obj) {
                Tag itemTag = serializeObject(item);
                if (itemTag != null) {
                    list.add(itemTag);
                }
            }
            return list;
        }

        // Map（键转为 String）
        if (obj instanceof Map) {
            CompoundTag mapTag = new CompoundTag();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                String key = entry.getKey().toString();
                Tag valueTag = serializeObject(entry.getValue());
                if (valueTag != null) {
                    mapTag.put(key, valueTag);
                }
            }
            return mapTag;
        }

        // 自定义对象 → 递归序列化字段
        CompoundTag compound = new CompoundTag();
        serializeFields(obj, clazz, compound);
        return compound;
    }

    private void serializeFields(Object obj, Class<?> clazz, CompoundTag container) {
        Field[] fields = getFields(clazz);
        for (Field field : fields) {
            if (!fieldFilter.test(field))
                continue;
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                Tag tag = serializeObject(value);
                if (tag != null) {
                    container.put(field.getName(), tag);
                }
            } catch (IllegalAccessException e) {
                // 忽略或记录日志
            }
        }
    }

    // ========== 核心反序列化逻辑 ==========

    @Nullable
    private Object deserializeObject(Tag tag, Class<?> targetType, Field field) throws Exception {
        if (tag == null)
            return null;

        // 优先适配器
        Function<Tag, Object> adapter = deserializeAdapters.get(targetType);
        if (adapter != null) {
            return adapter.apply(tag);
        }

        // 基本类型
        if (targetType == boolean.class || targetType == Boolean.class) {
            return ((ByteTag) tag).getAsByte() != 0;
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return ((ByteTag) tag).getAsByte();
        }
        if (targetType == short.class || targetType == Short.class) {
            return ((ShortTag) tag).getAsShort();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return ((IntTag) tag).getAsInt();
        }
        if (targetType == long.class || targetType == Long.class) {
            return ((LongTag) tag).getAsLong();
        }
        if (targetType == float.class || targetType == Float.class) {
            return ((FloatTag) tag).getAsFloat();
        }
        if (targetType == double.class || targetType == Double.class) {
            return ((DoubleTag) tag).getAsDouble();
        }
        if (targetType == String.class) {
            return tag.getAsString();
        }
        if (targetType.isEnum()) {
            String name = tag.getAsString();
            for (Object constant : targetType.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name)) {
                    return constant;
                }
            }
            return null;
        }

        // 数组
        if (targetType == int[].class && tag instanceof IntArrayTag) {
            return ((IntArrayTag) tag).getAsIntArray();
        }
        if (targetType == byte[].class && tag instanceof ByteArrayTag) {
            return ((ByteArrayTag) tag).getAsByteArray();
        }
        if (targetType == long[].class && tag instanceof LongArrayTag) {
            return ((LongArrayTag) tag).getAsLongArray();
        }

        // 集合
        if (List.class.isAssignableFrom(targetType) || Set.class.isAssignableFrom(targetType)) {
            if (!(tag instanceof ListTag))
                return null;
            ListTag listTag = (ListTag) tag;
            Class<?> elementType = getGenericType(field, 0);
            if (elementType == null) {
                // 尝试从 tag 内容推断（仅适用于非空列表）
                if (!listTag.isEmpty()) {
                    Tag first = listTag.get(0);
                    elementType = guessTypeFromTag(first);
                } else {
                    elementType = Object.class;
                }
            }
            Collection<Object> collection = List.class.isAssignableFrom(targetType)
                    ? new ArrayList<>()
                    : new HashSet<>();
            for (Tag itemTag : listTag) {
                Object item = deserializeObject(itemTag, elementType, null);
                if (item != null) {
                    collection.add(item);
                }
            }
            return collection;
        }

        // Map
        if (Map.class.isAssignableFrom(targetType)) {
            if (!(tag instanceof CompoundTag))
                return null;
            CompoundTag compound = (CompoundTag) tag;
            Class<?> valueType = getGenericType(field, 1);
            if (valueType == null)
                valueType = Object.class;
            Map<Object, Object> map = new HashMap<>();
            for (String key : compound.getAllKeys()) {
                Tag valueTag = compound.get(key);
                Object value = deserializeObject(valueTag, valueType, null);
                if (value != null) {
                    map.put(key, value);
                }
            }
            return map;
        }

        // 自定义对象（CompoundTag）
        if (tag instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) tag;
            // 如果 targetType 是抽象类或接口，则无法实例化，此时可尝试根据某个特殊字段判断（略）
            // 这里简单处理：若无法实例化则返回 null
            if (Modifier.isAbstract(targetType.getModifiers()) || targetType.isInterface()) {
                return null;
            }
            Object instance = targetType.getDeclaredConstructor().newInstance();
            deserializeFields(compound, instance);
            return instance;
        }

        // 其他情况：返回 null 或抛出异常
        return null;
    }

    private void deserializeFields(CompoundTag container, Object instance) throws Exception {
        Class<?> clazz = instance.getClass();
        Field[] fields = getFields(clazz);
        for (Field field : fields) {
            if (!fieldFilter.test(field))
                continue;
            String name = field.getName();
            if (!container.contains(name))
                continue;
            field.setAccessible(true);
            Tag tag = container.get(name);
            Object value = deserializeObject(tag, field.getType(), field);
            if (value != null) {
                field.set(instance, value);
            }
        }
    }

    // ========== 辅助方法 ==========

    private Field[] getFields(Class<?> clazz) {
        return fieldsCache.computeIfAbsent(clazz, c -> {
            List<Field> list = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                Collections.addAll(list, current.getDeclaredFields());
                current = current.getSuperclass();
            }
            return list.toArray(new Field[0]);
        });
    }

    @Nullable
    private Class<?> getGenericType(Field field, int index) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] args = pt.getActualTypeArguments();
            if (args.length > index) {
                Type arg = args[index];
                if (arg instanceof Class) {
                    return (Class<?>) arg;
                } else if (arg instanceof ParameterizedType) {
                    return (Class<?>) ((ParameterizedType) arg).getRawType();
                }
            }
        }
        return null;
    }

    private Class<?> guessTypeFromTag(Tag tag) {
        if (tag instanceof ByteTag)
            return Byte.class;
        if (tag instanceof ShortTag)
            return Short.class;
        if (tag instanceof IntTag)
            return Integer.class;
        if (tag instanceof LongTag)
            return Long.class;
        if (tag instanceof FloatTag)
            return Float.class;
        if (tag instanceof DoubleTag)
            return Double.class;
        if (tag instanceof StringTag)
            return String.class;
        if (tag instanceof ListTag)
            return List.class;
        if (tag instanceof CompoundTag)
            return CompoundTag.class;
        if (tag instanceof IntArrayTag)
            return int[].class;
        if (tag instanceof ByteArrayTag)
            return byte[].class;
        if (tag instanceof LongArrayTag)
            return long[].class;
        return Object.class;
    }

    // ========== 构建器 ==========

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Predicate<Field> fieldFilter = field -> {
            // 默认检查 @ConfigSync（如果存在且 shouldSync=false 则跳过）
            if (field.isAnnotationPresent(ConfigSync.class)) {
                return field.getAnnotation(ConfigSync.class).shouldSync();
            }
            return true;
        };
        private final Map<Class<?>, BiFunction<Object, NbtSerializer, Tag>> serializeAdapters = new HashMap<>();
        private final Map<Class<?>, Function<Tag, Object>> deserializeAdapters = new HashMap<>();

        public Builder fieldFilter(Predicate<Field> filter) {
            this.fieldFilter = filter;
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> Builder addAdapter(Class<T> clazz,
                BiFunction<T, NbtSerializer, Tag> serializer,
                Function<Tag, T> deserializer) {
            this.serializeAdapters.put(clazz, (obj, ctx) -> serializer.apply((T) obj, ctx));
            this.deserializeAdapters.put(clazz, tag -> deserializer.apply(tag));
            return this;
        }

        public NbtSerializer build() {
            return new NbtSerializer(this);
        }
    }

    // ========== 默认实例（使用 ConfigSync） ==========

    public static final NbtSerializer DEFAULT = builder().build();
}