package io.wifi.starrailexpress.util;

import com.google.gson.Gson;
import net.minecraft.nbt.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 通用 NBT 序列化工具 (JDK 21+ 兼容)
 * <p>
 * 支持类型：
 * <ul>
 * <li>基本类型及包装类、String、枚举、原始数组</li>
 * <li>集合、Map（保留泛型嵌套）</li>
 * <li>自定义 POJO、record（无默认构造/有 final 字段）</li>
 * <li>Optional、原子类、UUID、日期时间等</li>
 * <li>实现 {@link NbtSerializable} 接口的类可自定义读写逻辑</li>
 * </ul>
 */
public final class NbtSerializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NbtSerializer.class);
    private static final Gson GSON = new Gson();

    // ========== Unsafe 实例 ==========
    private static final sun.misc.Unsafe UNSAFE;

    static {
        sun.misc.Unsafe unsafe = null;
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            LOGGER.warn("sun.misc.Unsafe is not available", e);
        }
        UNSAFE = unsafe;
    }

    private static Object allocateInstance(Class<?> clazz) {
        if (UNSAFE != null) {
            try {
                return UNSAFE.allocateInstance(clazz);
            } catch (Exception e) {
                throw new RuntimeException("Cannot allocate " + clazz.getName(), e);
            }
        }
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate " + clazz.getName(), e);
        }
    }

    @SuppressWarnings("removal")
    private static void setFieldValue(Object instance, Field field, Object value) throws Exception {
        if (!Modifier.isFinal(field.getModifiers())) {
            field.set(instance, value);
        } else if (UNSAFE != null) {
            long offset = UNSAFE.objectFieldOffset(field);
            Class<?> type = field.getType();
            if (type == boolean.class)
                UNSAFE.putBoolean(instance, offset, (Boolean) value);
            else if (type == byte.class)
                UNSAFE.putByte(instance, offset, (Byte) value);
            else if (type == short.class)
                UNSAFE.putShort(instance, offset, (Short) value);
            else if (type == int.class)
                UNSAFE.putInt(instance, offset, (Integer) value);
            else if (type == long.class)
                UNSAFE.putLong(instance, offset, (Long) value);
            else if (type == float.class)
                UNSAFE.putFloat(instance, offset, (Float) value);
            else if (type == double.class)
                UNSAFE.putDouble(instance, offset, (Double) value);
            else if (type == char.class)
                UNSAFE.putChar(instance, offset, (Character) value);
            else
                UNSAFE.putObject(instance, offset, value);
        } else {
            // 最后的救命稻草：尝试移除 final 修饰符（JDK < 21 有效）
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                field.set(instance, value);
            } catch (NoSuchFieldException ex) {
                throw new RuntimeException("Cannot set final field " + field.getName() + " without Unsafe", ex);
            }
        }
    }

    // ========== 循环引用检测 ==========
    private final boolean detectCycles;
    private final ThreadLocal<Set<Object>> serializingObjects;

    // ========== 配置 ==========
    private final Predicate<Field> fieldFilter;
    private final Map<Class<?>, BiFunction<Object, NbtSerializer, Tag>> serializeAdapters;
    private final Map<Class<?>, Function<Tag, Object>> deserializeAdapters;
    private final Map<Class<?>, Field[]> fieldsCache = new ConcurrentHashMap<>();

    private NbtSerializer(Builder builder) {
        this.fieldFilter = builder.fieldFilter;
        this.serializeAdapters = builder.serializeAdapters;
        this.deserializeAdapters = builder.deserializeAdapters;
        this.detectCycles = builder.detectCycles;
        this.serializingObjects = detectCycles ? ThreadLocal.withInitial(HashSet::new) : null;
    }

    // ========== 公开 API ==========
    public CompoundTag serializeToTag(Object obj) {
        if (obj == null)
            return new CompoundTag();
        Tag tag = serializeObject(obj);
        if (tag instanceof CompoundTag)
            return (CompoundTag) tag;
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("_root", tag);
        return wrapper;
    }

    @SuppressWarnings("unchecked")
    public <T> T deserializeFromTag(CompoundTag tag, Class<T> targetClass) throws Exception {
        if (tag.contains("_root") && isSimpleType(targetClass)) {
            return (T) deserializeObject(tag.get("_root"), targetClass, null);
        }
        T instance = targetClass.getDeclaredConstructor().newInstance();
        deserializeFields(tag, instance);
        return instance;
    }

    // ========== 序列化逻辑 ==========
    @Nullable
    private Tag serializeObject(Object obj) {
        if (obj == null)
            return null;

        if (detectCycles) {
            Set<Object> set = serializingObjects.get();
            if (!set.add(obj)) {
                LOGGER.warn("Cyclic reference detected, skipping: {}", obj);
                return null;
            }
        }

        try {
            Class<?> clazz = obj.getClass();

            // 1. 自定义适配器
            BiFunction<Object, NbtSerializer, Tag> adapter = serializeAdapters.get(clazz);
            if (adapter != null)
                return adapter.apply(obj, this);

            // 2. NbtSerializable 接口
            if (obj instanceof NbtSerializable) {
                CompoundTag compound = new CompoundTag();
                ((NbtSerializable) obj).writeNbt(compound);
                return compound;
            }

            // 3. 基本类型 & 包装类
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

            // 原子类
            if (obj instanceof AtomicInteger)
                return IntTag.valueOf(((AtomicInteger) obj).get());
            if (obj instanceof AtomicLong)
                return LongTag.valueOf(((AtomicLong) obj).get());
            if (obj instanceof AtomicBoolean)
                return ByteTag.valueOf(((AtomicBoolean) obj).get());

            // Optional 系列
            if (obj instanceof Optional) {
                Optional<?> opt = (Optional<?>) obj;
                CompoundTag c = new CompoundTag();
                c.putBoolean("present", opt.isPresent());
                if (opt.isPresent()) {
                    Tag valueTag = serializeObject(opt.get());
                    if (valueTag != null)
                        c.put("value", valueTag);
                }
                return c;
            }
            if (obj instanceof OptionalInt) {
                OptionalInt opt = (OptionalInt) obj;
                CompoundTag c = new CompoundTag();
                c.putBoolean("present", opt.isPresent());
                if (opt.isPresent())
                    c.putInt("value", opt.getAsInt());
                return c;
            }
            if (obj instanceof OptionalLong) {
                OptionalLong opt = (OptionalLong) obj;
                CompoundTag c = new CompoundTag();
                c.putBoolean("present", opt.isPresent());
                if (opt.isPresent())
                    c.putLong("value", opt.getAsLong());
                return c;
            }
            if (obj instanceof OptionalDouble) {
                OptionalDouble opt = (OptionalDouble) obj;
                CompoundTag c = new CompoundTag();
                c.putBoolean("present", opt.isPresent());
                if (opt.isPresent())
                    c.putDouble("value", opt.getAsDouble());
                return c;
            }

            // UUID, 日期时间
            if (obj instanceof UUID)
                return StringTag.valueOf(obj.toString());
            if (obj instanceof Date)
                return LongTag.valueOf(((Date) obj).getTime());
            if (obj instanceof Instant)
                return LongTag.valueOf(((Instant) obj).toEpochMilli());
            if (obj instanceof LocalDate)
                return StringTag.valueOf(((LocalDate) obj).format(DateTimeFormatter.ISO_LOCAL_DATE));
            if (obj instanceof LocalDateTime)
                return StringTag.valueOf(((LocalDateTime) obj).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // 原始数组
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

            // 集合
            if (obj instanceof Collection) {
                ListTag list = new ListTag();
                for (Object item : (Collection<?>) obj) {
                    Tag t = serializeObject(item);
                    if (t != null)
                        list.add(t);
                }
                return list;
            }

            // Map
            if (obj instanceof Map) {
                CompoundTag mapTag = new CompoundTag();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) obj).entrySet()) {
                    String key = e.getKey().toString();
                    Tag value = serializeObject(e.getValue());
                    if (value != null)
                        mapTag.put(key, value);
                }
                return mapTag;
            }

            // 自定义 POJO
            CompoundTag compound = new CompoundTag();
            serializeFields(obj, clazz, compound);
            return compound;
        } finally {
            if (detectCycles) {
                serializingObjects.get().remove(obj);
            }
        }
    }

    private void serializeFields(Object obj, Class<?> clazz, CompoundTag container) {
        for (Field field : getFields(clazz)) {
            if (!fieldFilter.test(field))
                continue;
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                Tag t = serializeObject(value);
                if (t != null)
                    container.put(field.getName(), t);
            } catch (IllegalAccessException e) {
                LOGGER.warn("Failed to access field {}", field.getName(), e);
            }
        }
    }

    // ========== 反序列化逻辑 ==========
    @Nullable
    private Object deserializeObjectByType(Tag tag, Type type, Field field) throws Exception {
        if (type instanceof Class)
            return deserializeObject(tag, (Class<?>) type, field);
        if (type instanceof ParameterizedType) {
            Class<?> rawClass = (Class<?>) ((ParameterizedType) type).getRawType();
            return deserializeObject(tag, rawClass, field);
        }
        if (type instanceof WildcardType) {
            Type[] upper = ((WildcardType) type).getUpperBounds();
            return upper.length > 0 ? deserializeObjectByType(tag, upper[0], field) : null;
        }
        if (type instanceof TypeVariable)
            return deserializeObject(tag, Object.class, field);
        return null;
    }

    @Nullable
    private Object deserializeObject(Tag tag, Class<?> targetType, Field field) throws Exception {
        if (tag == null)
            return null;

        // 1. 自定义适配器
        Function<Tag, Object> adapter = deserializeAdapters.get(targetType);
        if (adapter != null)
            return adapter.apply(tag);

        // 2. Gson 后备
        if (tag instanceof CompoundTag) {
            CompoundTag c = (CompoundTag) tag;
            if (c.contains("_gson_data")) {
                String json = c.getString("_gson_data");
                String className = c.getString("_gson_class");
                try {
                    Class<?> storedClass = Class.forName(className);
                    return targetType.isAssignableFrom(storedClass) ? GSON.fromJson(json, storedClass)
                            : GSON.fromJson(json, targetType);
                } catch (ClassNotFoundException e) {
                    return GSON.fromJson(json, targetType);
                }
            }
        }

        // 3. 基本类型
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

        // 枚举
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

        // 原子类
        if (targetType == AtomicInteger.class)
            return new AtomicInteger(((NumericTag) tag).getAsInt());
        if (targetType == AtomicLong.class)
            return new AtomicLong(((NumericTag) tag).getAsLong());
        if (targetType == AtomicBoolean.class)
            return new AtomicBoolean(((NumericTag) tag).getAsByte() != 0);

        // Optional 系列
        if (targetType == Optional.class && tag instanceof CompoundTag) {
            CompoundTag c = (CompoundTag) tag;
            if (!c.getBoolean("present"))
                return Optional.empty();
            Type valueType = field != null ? resolveTypeArgument(field.getGenericType(), 0) : Object.class;
            return Optional.ofNullable(deserializeObjectByType(c.get("value"), valueType, field));
        }
        if (targetType == OptionalInt.class && tag instanceof CompoundTag) {
            CompoundTag c = (CompoundTag) tag;
            return c.getBoolean("present") ? OptionalInt.of(c.getInt("value")) : OptionalInt.empty();
        }
        if (targetType == OptionalLong.class && tag instanceof CompoundTag) {
            CompoundTag c = (CompoundTag) tag;
            return c.getBoolean("present") ? OptionalLong.of(c.getLong("value")) : OptionalLong.empty();
        }
        if (targetType == OptionalDouble.class && tag instanceof CompoundTag) {
            CompoundTag c = (CompoundTag) tag;
            return c.getBoolean("present") ? OptionalDouble.of(c.getDouble("value")) : OptionalDouble.empty();
        }

        // UUID, 日期时间
        if (targetType == UUID.class)
            return UUID.fromString(tag.getAsString());
        if (targetType == Date.class)
            return new Date(((NumericTag) tag).getAsLong());
        if (targetType == Instant.class)
            return Instant.ofEpochMilli(((NumericTag) tag).getAsLong());
        if (targetType == LocalDate.class)
            return LocalDate.parse(tag.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
        if (targetType == LocalDateTime.class)
            return LocalDateTime.parse(tag.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 原始数组
        if (targetType == int[].class && tag instanceof IntArrayTag)
            return ((IntArrayTag) tag).getAsIntArray();
        if (targetType == byte[].class && tag instanceof ByteArrayTag)
            return ((ByteArrayTag) tag).getAsByteArray();
        if (targetType == long[].class && tag instanceof LongArrayTag)
            return ((LongArrayTag) tag).getAsLongArray();
        if (targetType == short[].class && tag instanceof ListTag) {
            ListTag list = (ListTag) tag;
            short[] arr = new short[list.size()];
            for (int i = 0; i < arr.length; i++)
                arr[i] = ((NumericTag) list.get(i)).getAsShort();
            return arr;
        }
        if (targetType == float[].class && tag instanceof ListTag) {
            ListTag list = (ListTag) tag;
            float[] arr = new float[list.size()];
            for (int i = 0; i < arr.length; i++)
                arr[i] = ((NumericTag) list.get(i)).getAsFloat();
            return arr;
        }
        if (targetType == double[].class && tag instanceof ListTag) {
            ListTag list = (ListTag) tag;
            double[] arr = new double[list.size()];
            for (int i = 0; i < arr.length; i++)
                arr[i] = ((NumericTag) list.get(i)).getAsDouble();
            return arr;
        }

        // 集合 (支持泛型)
        if (List.class.isAssignableFrom(targetType) || Set.class.isAssignableFrom(targetType)) {
            if (!(tag instanceof ListTag))
                return null;
            ListTag listTag = (ListTag) tag;
            Type elementType = field != null ? resolveTypeArgument(field.getGenericType(), 0) : null;
            if (elementType == null) {
                elementType = !listTag.isEmpty() ? guessTypeFromTag(listTag.get(0)) : Object.class;
            }
            Collection<Object> coll = List.class.isAssignableFrom(targetType) ? new ArrayList<>() : new HashSet<>();
            for (Tag itemTag : listTag) {
                try {
                    Object item = deserializeObjectByType(itemTag, elementType, field);
                    if (item != null)
                        coll.add(item);
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid collection element", e);
                }
            }
            return coll;
        }

        // Map (支持泛型)
        if (Map.class.isAssignableFrom(targetType)) {
            if (!(tag instanceof CompoundTag))
                return null;
            CompoundTag ct = (CompoundTag) tag;
            Type valueType = field != null ? resolveTypeArgument(field.getGenericType(), 1) : Object.class;
            Map<Object, Object> map = new HashMap<>();
            for (String key : ct.getAllKeys()) {
                try {
                    Object val = deserializeObjectByType(ct.get(key), valueType, field);
                    if (val != null)
                        map.put(key, val);
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid map value for key {}", key, e);
                }
            }
            return map;
        }

        // 自定义 POJO (包括 record 和 NbtSerializable)
        if (tag instanceof CompoundTag) {
            CompoundTag ct = (CompoundTag) tag;
            if (targetType.isInterface() || Modifier.isAbstract(targetType.getModifiers()))
                return null;

            // 实现了 NbtSerializable 接口
            if (NbtSerializable.class.isAssignableFrom(targetType)) {
                Object instance;
                try {
                    instance = targetType.getDeclaredConstructor().newInstance();
                } catch (NoSuchMethodException e) {
                    instance = allocateInstance(targetType);
                }
                ((NbtSerializable) instance).readNbt(ct);
                return instance;
            }

            // record 类
            if (targetType.isRecord()) {
                return deserializeRecord(ct, targetType);
            }

            // 普通 POJO
            Object instance;
            try {
                instance = targetType.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                instance = allocateInstance(targetType);
            }
            deserializeFields(ct, instance);
            return instance;
        }

        return null;
    }

    private Object deserializeRecord(CompoundTag tag, Class<?> recordClass) throws Exception {
        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String name = component.getName();
            Type genericType = component.getGenericType();
            Tag fieldTag = tag.get(name);
            Object value = null;
            if (fieldTag != null) {
                try {
                    value = deserializeObjectByType(fieldTag, genericType, null);
                } catch (Exception e) {
                    LOGGER.warn("Failed to deserialize record component {}", name, e);
                }
            }
            if (value == null && component.getType().isPrimitive()) {
                Class<?> type = component.getType();
                if (type == int.class)
                    value = 0;
                else if (type == long.class)
                    value = 0L;
                else if (type == short.class)
                    value = (short) 0;
                else if (type == byte.class)
                    value = (byte) 0;
                else if (type == float.class)
                    value = 0.0f;
                else if (type == double.class)
                    value = 0.0d;
                else if (type == char.class)
                    value = '\u0000';
                else if (type == boolean.class)
                    value = false;
            }
            args[i] = value;
        }
        Class<?>[] paramTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        Constructor<?> constructor = recordClass.getDeclaredConstructor(paramTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private void deserializeFields(CompoundTag container, Object instance) {
        Class<?> clazz = instance.getClass();
        for (Field field : getFields(clazz)) {
            if (!fieldFilter.test(field))
                continue;
            String name = field.getName();
            if (!container.contains(name))
                continue;
            field.setAccessible(true);
            try {
                Tag tag = container.get(name);
                Object value = deserializeObjectByType(tag, field.getGenericType(), field);
                if (value != null) {
                    setFieldValue(instance, field, value);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to set field {} on {}, using default value.", name, clazz.getSimpleName(), e);
            }
        }
    }

    // ========== 辅助方法 ==========
    @Nullable
    private static Type resolveTypeArgument(Type genericType, int index) {
        if (genericType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
            if (args.length > index)
                return args[index];
        }
        return null;
    }

    private Field[] getFields(Class<?> clazz) {
        return fieldsCache.computeIfAbsent(clazz, c -> {
            List<Field> list = new ArrayList<>();
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                Collections.addAll(list, cur.getDeclaredFields());
                cur = cur.getSuperclass();
            }
            return list.toArray(new Field[0]);
        });
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

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == Boolean.class || clazz == Byte.class || clazz == Short.class
                || clazz == Integer.class || clazz == Long.class || clazz == Float.class
                || clazz == Double.class || clazz == String.class || clazz.isEnum()
                || clazz == UUID.class || clazz == Date.class || clazz == Instant.class
                || clazz == LocalDate.class || clazz == LocalDateTime.class
                || Optional.class.isAssignableFrom(clazz)
                || AtomicInteger.class == clazz || AtomicLong.class == clazz || AtomicBoolean.class == clazz;
    }

    // ========== Builder ==========
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Predicate<Field> fieldFilter = field -> true;
        private final Map<Class<?>, BiFunction<Object, NbtSerializer, Tag>> serializeAdapters = new HashMap<>();
        private final Map<Class<?>, Function<Tag, Object>> deserializeAdapters = new HashMap<>();
        private boolean detectCycles = false;

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

        public Builder detectCycles(boolean detect) {
            this.detectCycles = detect;
            return this;
        }

        public NbtSerializer build() {
            return new NbtSerializer(this);
        }
    }

    public static final NbtSerializer DEFAULT = builder().build();
}