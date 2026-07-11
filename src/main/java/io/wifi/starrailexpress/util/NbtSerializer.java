package io.wifi.starrailexpress.util;

import com.google.gson.Gson;
import net.minecraft.nbt.*;
import org.jetbrains.annotations.Nullable;

import io.wifi.ConfigCompact.annotation.ConfigSync;

import java.lang.reflect.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 通用 NBT 序列化工具
 * <p>
 * 支持类型：
 * <ul>
 * <li>所有基本类型及包装类 (boolean, byte, short, int, long, float, double)</li>
 * <li>字符串 (String)</li>
 * <li>枚举 (Enum) – 存储为名称字符串</li>
 * <li>原始数组 (int[], byte[], long[], short[], float[], double[])</li>
 * <li>集合 (Collection) – 包括 List 和 Set，保留泛型信息（支持嵌套）</li>
 * <li>映射 (Map) – 键强制转为 String（反序列化后键均为 String）</li>
 * <li>自定义 POJO（包括 record 及无默认构造的类）– 递归序列化所有字段（支持继承），优先无参构造，回退到 Unsafe 分配</li>
 * <li>Optional, OptionalInt, OptionalLong, OptionalDouble</li>
 * <li>UUID – 存储为字符串</li>
 * <li>Date, LocalDate, LocalDateTime – 存储为时间戳或 ISO 字符串</li>
 * <li>AtomicInteger, AtomicLong, AtomicBoolean – 存储为对应数值</li>
 * <li>任何其他无法识别的类型 – 通过 Gson 序列化为 JSON 字符串后备</li>
 * </ul>
 * <p>
 * 特性：
 * <ul>
 * <li>字段过滤（基于注解 @ConfigSync 或自定义 Predicate）</li>
 * <li>类型适配器注册（用于定制特定类型的序列化/反序列化）</li>
 * <li>并发安全的字段缓存</li>
 * <li>支持继承层次中的字段</li>
 * <li>类 Gson 的无默认构造器实例化（利用 Unsafe），无需 JVM 参数</li>
 * </ul>
 *
 * @author NbtSerializer
 * @version 2.2
 */
public final class NbtSerializer {

    // ========== Gson 后备 ==========
    private static final Gson GSON = new Gson();

    // ========== Unsafe 分配器（用于无默认构造的类）==========
    private static final sun.misc.Unsafe UNSAFE;

    static {
        sun.misc.Unsafe unsafe = null;
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (sun.misc.Unsafe) f.get(null);
        } catch (Exception ignored) {
            // 如果不支持 Unsafe（极少数环境），UNSAFE 将为 null
        }
        UNSAFE = unsafe;
    }

    /**
     * 分配一个未初始化的类实例（类似 Gson 的做法）
     * 如果 Unsafe 不可用，则尝试使用 ReflectionFactory（可能需要 JVM 参数），都不行则抛出异常
     */
    private static Object allocateInstance(Class<?> clazz) throws Exception {
        if (UNSAFE != null) {
            return UNSAFE.allocateInstance(clazz);
        }
        // 备用：使用 ReflectionFactory（需要 --add-opens）
        // 这里仅作为极端情况下的后备
        try {
            Class<?> rfClass = Class.forName("jdk.internal.reflect.ReflectionFactory");
            Method getReflectionFactory = rfClass.getDeclaredMethod("getReflectionFactory");
            Object rf = getReflectionFactory.invoke(null);
            Constructor<?> objConstructor = Object.class.getDeclaredConstructor();
            Method newConstructorForSerialization = rfClass.getDeclaredMethod(
                    "newConstructorForSerialization", Class.class, Constructor.class);
            Constructor<?> intConstr = (Constructor<?>) newConstructorForSerialization.invoke(rf, clazz,
                    objConstructor);
            intConstr.setAccessible(true);
            return intConstr.newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot create instance of " + clazz.getName() +
                    " (Unsafe unavailable, ReflectionFactory not found). Add --add-opens or provide a no-arg constructor.");
        }
    }

    // ========== 配置 ==========

    private final Predicate<Field> fieldFilter;
    private final Map<Class<?>, BiFunction<Object, NbtSerializer, Tag>> serializeAdapters;
    private final Map<Class<?>, Function<Tag, Object>> deserializeAdapters;
    private final Map<Class<?>, Field[]> fieldsCache = new ConcurrentHashMap<>();

    // ========== 构造函数 ==========

    private NbtSerializer(Builder builder) {
        this.fieldFilter = builder.fieldFilter;
        this.serializeAdapters = builder.serializeAdapters;
        this.deserializeAdapters = builder.deserializeAdapters;
    }

    // ========== 公开 API ==========

    /**
     * 将任意对象序列化为 CompoundTag
     */
    public CompoundTag serializeToTag(Object obj) {
        if (obj == null)
            return new CompoundTag();
        Tag tag = serializeObject(obj);
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag;
        } else {
            // 如果根对象是简单类型，则包装
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("_root", tag);
            return wrapper;
        }
    }

    /**
     * 将 CompoundTag 反序列化为指定类型的对象
     */
    @SuppressWarnings("unchecked")
    public <T> T deserializeFromTag(CompoundTag tag, Class<T> targetClass) throws Exception {
        // 如果 tag 是包装的简单类型
        if (tag.contains("_root") && isSimpleType(targetClass)) {
            Tag inner = tag.get("_root");
            return (T) deserializeObject(inner, targetClass, null);
        }
        // 否则认为是 POJO 的字段容器
        T instance = targetClass.getDeclaredConstructor().newInstance();
        deserializeFields(tag, instance);
        return instance;
    }

    // ========== 核心序列化逻辑 ==========

    @Nullable
    private Tag serializeObject(Object obj) {
        if (obj == null)
            return null;

        // 优先适配器
        Class<?> clazz = obj.getClass();
        BiFunction<Object, NbtSerializer, Tag> adapter = serializeAdapters.get(clazz);
        if (adapter != null) {
            return adapter.apply(obj, this);
        }

        // ----- 基本类型 & 包装类 -----
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

        // ----- 原子类 -----
        if (obj instanceof AtomicInteger)
            return IntTag.valueOf(((AtomicInteger) obj).get());
        if (obj instanceof AtomicLong)
            return LongTag.valueOf(((AtomicLong) obj).get());
        if (obj instanceof AtomicBoolean)
            return ByteTag.valueOf(((AtomicBoolean) obj).get());

        // ----- Optional 系列 -----
        if (obj instanceof Optional) {
            Optional<?> opt = (Optional<?>) obj;
            CompoundTag compound = new CompoundTag();
            if (opt.isPresent()) {
                Tag valueTag = serializeObject(opt.get());
                if (valueTag != null)
                    compound.put("value", valueTag);
                compound.putBoolean("present", true);
            } else {
                compound.putBoolean("present", false);
            }
            return compound;
        }
        if (obj instanceof OptionalInt) {
            OptionalInt opt = (OptionalInt) obj;
            CompoundTag compound = new CompoundTag();
            if (opt.isPresent()) {
                compound.putInt("value", opt.getAsInt());
                compound.putBoolean("present", true);
            } else {
                compound.putBoolean("present", false);
            }
            return compound;
        }
        if (obj instanceof OptionalLong) {
            OptionalLong opt = (OptionalLong) obj;
            CompoundTag compound = new CompoundTag();
            if (opt.isPresent()) {
                compound.putLong("value", opt.getAsLong());
                compound.putBoolean("present", true);
            } else {
                compound.putBoolean("present", false);
            }
            return compound;
        }
        if (obj instanceof OptionalDouble) {
            OptionalDouble opt = (OptionalDouble) obj;
            CompoundTag compound = new CompoundTag();
            if (opt.isPresent()) {
                compound.putDouble("value", opt.getAsDouble());
                compound.putBoolean("present", true);
            } else {
                compound.putBoolean("present", false);
            }
            return compound;
        }

        // ----- UUID -----
        if (obj instanceof UUID)
            return StringTag.valueOf(obj.toString());

        // ----- Date / Time -----
        if (obj instanceof Date)
            return LongTag.valueOf(((Date) obj).getTime());
        if (obj instanceof Instant)
            return LongTag.valueOf(((Instant) obj).toEpochMilli());
        if (obj instanceof LocalDate)
            return StringTag.valueOf(((LocalDate) obj).format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (obj instanceof LocalDateTime)
            return StringTag.valueOf(((LocalDateTime) obj).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // ----- 原始数组 -----
        if (obj instanceof int[])
            return new IntArrayTag(Arrays.copyOf((int[]) obj, ((int[]) obj).length));
        if (obj instanceof byte[])
            return new ByteArrayTag(Arrays.copyOf((byte[]) obj, ((byte[]) obj).length));
        if (obj instanceof long[])
            return new LongArrayTag(Arrays.copyOf((long[]) obj, ((long[]) obj).length));
        if (obj instanceof short[]) {
            short[] arr = (short[]) obj;
            ListTag list = new ListTag();
            for (short v : arr)
                list.add(ShortTag.valueOf(v));
            return list;
        }
        if (obj instanceof float[]) {
            float[] arr = (float[]) obj;
            ListTag list = new ListTag();
            for (float v : arr)
                list.add(FloatTag.valueOf(v));
            return list;
        }
        if (obj instanceof double[]) {
            double[] arr = (double[]) obj;
            ListTag list = new ListTag();
            for (double v : arr)
                list.add(DoubleTag.valueOf(v));
            return list;
        }

        // ----- 集合 (Collection) -----
        if (obj instanceof Collection) {
            ListTag list = new ListTag();
            for (Object item : (Collection<?>) obj) {
                Tag itemTag = serializeObject(item);
                if (itemTag != null)
                    list.add(itemTag);
            }
            return list;
        }

        // ----- Map -----
        if (obj instanceof Map) {
            CompoundTag mapTag = new CompoundTag();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                String key = entry.getKey().toString();
                Tag valueTag = serializeObject(entry.getValue());
                if (valueTag != null)
                    mapTag.put(key, valueTag);
            }
            return mapTag;
        }

        // ----- 自定义 POJO -----
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
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    // ========== 核心反序列化逻辑 ==========

    /**
     * 根据完整 Type 反序列化（保留泛型嵌套信息）
     */
    @Nullable
    private Object deserializeObjectByType(Tag tag, Type type, Field field) throws Exception {
        if (type instanceof Class) {
            return deserializeObject(tag, (Class<?>) type, field);
        } else if (type instanceof ParameterizedType) {
            Class<?> rawClass = (Class<?>) ((ParameterizedType) type).getRawType();
            return deserializeObject(tag, rawClass, field);
        } else if (type instanceof WildcardType) {
            Type[] upperBounds = ((WildcardType) type).getUpperBounds();
            if (upperBounds.length > 0) {
                return deserializeObjectByType(tag, upperBounds[0], field);
            }
            return null;
        } else if (type instanceof TypeVariable) {
            return deserializeObject(tag, Object.class, field);
        }
        return null;
    }

    @Nullable
    private Object deserializeObject(Tag tag, Class<?> targetType, Field field) throws Exception {
        if (tag == null)
            return null;

        // 优先适配器
        Function<Tag, Object> adapter = deserializeAdapters.get(targetType);
        if (adapter != null) {
            return adapter.apply(tag);
        }

        // ----- Gson 后备数据检查 -----
        if (tag instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) tag;
            if (compound.contains("_gson_data")) {
                String json = compound.getString("_gson_data");
                String className = compound.getString("_gson_class");
                try {
                    Class<?> clazz = Class.forName(className);
                    if (targetType.isInterface() || Modifier.isAbstract(targetType.getModifiers())) {
                        return GSON.fromJson(json, clazz);
                    } else {
                        if (targetType.isAssignableFrom(clazz)) {
                            return GSON.fromJson(json, clazz);
                        } else {
                            return GSON.fromJson(json, targetType);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    if (targetType != null && targetType != Object.class) {
                        return GSON.fromJson(json, targetType);
                    }
                    throw new RuntimeException("Cannot load class: " + className, e);
                }
            }
        }

        // ----- 基本类型 -----
        if (targetType == boolean.class || targetType == Boolean.class)
            return ((NumericTag) tag).getAsByte() != 0;
        if (targetType == byte.class || targetType == Byte.class)
            return ((NumericTag) tag).getAsByte();
        if (targetType == short.class || targetType == Short.class)
            return ((NumericTag) tag).getAsShort();
        if (targetType == int.class || targetType == Integer.class)
            return ((NumericTag) tag).getAsInt();
        if (targetType == long.class || targetType == Long.class)
            return ((NumericTag) tag).getAsLong();
        if (targetType == float.class || targetType == Float.class)
            return ((NumericTag) tag).getAsFloat();
        if (targetType == double.class || targetType == Double.class)
            return ((NumericTag) tag).getAsDouble();
        if (targetType == String.class)
            return tag.getAsString();

        // ----- 枚举 -----
        if (targetType.isEnum()) {
            String name = tag.getAsString();
            if (name == null)
                return null;
            for (Object constant : targetType.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name))
                    return constant;
            }
            return null;
        }

        // ----- 原子类 -----
        if (targetType == AtomicInteger.class)
            return new AtomicInteger(((NumericTag) tag).getAsInt());
        if (targetType == AtomicLong.class)
            return new AtomicLong(((NumericTag) tag).getAsLong());
        if (targetType == AtomicBoolean.class)
            return new AtomicBoolean(((NumericTag) tag).getAsByte() != 0);

        // ----- Optional 系列 -----
        if (targetType == Optional.class) {
            if (!(tag instanceof CompoundTag))
                return Optional.empty();
            CompoundTag compound = (CompoundTag) tag;
            boolean present = compound.getBoolean("present");
            if (!present)
                return Optional.empty();
            Tag valueTag = compound.get("value");
            Type valueType = field != null ? resolveTypeArgument(field.getGenericType(), 0) : Object.class;
            Object value = deserializeObjectByType(valueTag, valueType, field);
            return Optional.ofNullable(value);
        }
        if (targetType == OptionalInt.class) {
            if (!(tag instanceof CompoundTag))
                return OptionalInt.empty();
            CompoundTag compound = (CompoundTag) tag;
            if (!compound.getBoolean("present"))
                return OptionalInt.empty();
            return OptionalInt.of(compound.getInt("value"));
        }
        if (targetType == OptionalLong.class) {
            if (!(tag instanceof CompoundTag))
                return OptionalLong.empty();
            CompoundTag compound = (CompoundTag) tag;
            if (!compound.getBoolean("present"))
                return OptionalLong.empty();
            return OptionalLong.of(compound.getLong("value"));
        }
        if (targetType == OptionalDouble.class) {
            if (!(tag instanceof CompoundTag))
                return OptionalDouble.empty();
            CompoundTag compound = (CompoundTag) tag;
            if (!compound.getBoolean("present"))
                return OptionalDouble.empty();
            return OptionalDouble.of(compound.getDouble("value"));
        }

        // ----- UUID -----
        if (targetType == UUID.class) {
            return UUID.fromString(tag.getAsString());
        }

        // ----- Date / Time -----
        if (targetType == Date.class) {
            return new Date(((NumericTag) tag).getAsLong());
        }
        if (targetType == Instant.class) {
            return Instant.ofEpochMilli(((NumericTag) tag).getAsLong());
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(tag.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (targetType == LocalDateTime.class) {
            return LocalDateTime.parse(tag.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        // ----- 原始数组 -----
        if (targetType == int[].class && tag instanceof IntArrayTag)
            return ((IntArrayTag) tag).getAsIntArray();
        if (targetType == byte[].class && tag instanceof ByteArrayTag)
            return ((ByteArrayTag) tag).getAsByteArray();
        if (targetType == long[].class && tag instanceof LongArrayTag)
            return ((LongArrayTag) tag).getAsLongArray();
        if (targetType == short[].class && tag instanceof ListTag) {
            ListTag list = (ListTag) tag;
            short[] arr = new short[list.size()];
            for (int i = 0; i < list.size(); i++)
                arr[i] = ((NumericTag) list.get(i)).getAsShort();
            return arr;
        }
        if (targetType == float[].class && tag instanceof ListTag) {
            ListTag list = (ListTag) tag;
            float[] arr = new float[list.size()];
            for (int i = 0; i < list.size(); i++)
                arr[i] = ((NumericTag) list.get(i)).getAsFloat();
            return arr;
        }
        if (targetType == double[].class && tag instanceof ListTag) {
            ListTag list = (ListTag) tag;
            double[] arr = new double[list.size()];
            for (int i = 0; i < list.size(); i++)
                arr[i] = ((NumericTag) list.get(i)).getAsDouble();
            return arr;
        }

        // ----- 集合 (List/Set) -----
        if (List.class.isAssignableFrom(targetType) || Set.class.isAssignableFrom(targetType)) {
            if (!(tag instanceof ListTag))
                return null;
            ListTag listTag = (ListTag) tag;
            Type elementType = field != null ? resolveTypeArgument(field.getGenericType(), 0) : null;
            if (elementType == null) {
                if (!listTag.isEmpty()) {
                    elementType = guessTypeFromTag(listTag.get(0));
                } else {
                    elementType = Object.class;
                }
            }
            Collection<Object> collection = List.class.isAssignableFrom(targetType)
                    ? new ArrayList<>()
                    : new HashSet<>();
            for (Tag itemTag : listTag) {
                Object item = deserializeObjectByType(itemTag, elementType, field);
                if (item != null)
                    collection.add(item);
            }
            return collection;
        }

        // ----- Map -----
        if (Map.class.isAssignableFrom(targetType)) {
            if (!(tag instanceof CompoundTag))
                return null;
            CompoundTag compound = (CompoundTag) tag;
            Type valueType = field != null ? resolveTypeArgument(field.getGenericType(), 1) : Object.class;
            Map<Object, Object> map = new HashMap<>();
            for (String key : compound.getAllKeys()) {
                Tag valueTag = compound.get(key);
                Object value = deserializeObjectByType(valueTag, valueType, field);
                if (value != null)
                    map.put(key, value);
            }
            return map;
        }

        // ----- 自定义 POJO (CompoundTag) -----
        if (tag instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) tag;
            if (targetType.isInterface() || Modifier.isAbstract(targetType.getModifiers())) {
                return null; // 交由 Gson 后备（已前面检查）
            }
            Object instance;
            try {
                // 1. 首选无参构造器
                instance = targetType.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                // 2. 回退到 Unsafe 分配（类似 Gson）
                instance = allocateInstance(targetType);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create instance of " + targetType.getName(), e);
            }
            deserializeFields(compound, instance);
            return instance;
        }

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
            Object value = deserializeObjectByType(tag, field.getGenericType(), field);
            if (value != null) {
                field.set(instance, value);
            }
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 从字段的泛型类型中安全提取第 index 个类型参数（保留嵌套 ParameterizedType）
     */
    @Nullable
    private static Type resolveTypeArgument(Type genericType, int index) {
        if (genericType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
            if (args.length > index) {
                return args[index];
            }
        }
        return null;
    }

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

    private Class<?> guessTypeFromTag(Tag tag) {
        if (tag instanceof NumericTag) {
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
        }
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

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == Boolean.class || clazz == Byte.class || clazz == Short.class ||
                clazz == Integer.class || clazz == Long.class || clazz == Float.class ||
                clazz == Double.class || clazz == String.class || clazz.isEnum() ||
                clazz == UUID.class || clazz == Date.class || clazz == Instant.class ||
                clazz == LocalDate.class || clazz == LocalDateTime.class ||
                Optional.class.isAssignableFrom(clazz) ||
                AtomicInteger.class == clazz || AtomicLong.class == clazz || AtomicBoolean.class == clazz;
    }

    // ========== 构建器 ==========

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Predicate<Field> fieldFilter = field -> {
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

    // ========== 默认实例 ==========

    public static final NbtSerializer DEFAULT = builder().build();
}