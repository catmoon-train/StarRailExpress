package io.wifi.starrailexpress.api.replay;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.EventDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.EventType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class ReplayEventRegistry {
    private static final Map<EventType, Class<? extends EventDetails>> EVENT_TYPE_TO_DETAILS_CLASS = new HashMap<>();
    private static final Map<Class<? extends EventDetails>, EventType> DETAILS_CLASS_TO_EVENT_TYPE = new HashMap<>();
    private static final Map<EventType, JsonSerializer<? extends EventDetails>> SERIALIZERS = new HashMap<>();
    private static final Map<EventType, JsonDeserializer<? extends EventDetails>> DESERIALIZERS = new HashMap<>();

    // 新增：用于存储自定义事件的 ResourceLocation 到其 EventDetails 类的映射
    private static final Map<ResourceLocation, Class<? extends EventDetails>> CUSTOM_EVENT_ID_TO_DETAILS_CLASS = new HashMap<>();
    private static final Map<Class<? extends EventDetails>, ResourceLocation> CUSTOM_EVENT_DETAILS_CLASS_TO_ID = new HashMap<>();
    private static final Map<ResourceLocation, JsonSerializer<? extends EventDetails>> CUSTOM_EVENT_SERIALIZERS = new HashMap<>();
    private static final Map<ResourceLocation, JsonDeserializer<? extends EventDetails>> CUSTOM_EVENT_DESERIALIZERS = new HashMap<>();


    public static <T extends EventDetails> void register(EventType eventType, Class<T> detailsClass, JsonSerializer<T> serializer, JsonDeserializer<T> deserializer) {
        if (EVENT_TYPE_TO_DETAILS_CLASS.containsKey(eventType)) {
            throw new IllegalArgumentException("Event type " + eventType + " already registered.");
        }
        // 允许同一个详情类被多个事件类型注册，但只存储第一个映射到 DETAILS_CLASS_TO_EVENT_TYPE
        if (!DETAILS_CLASS_TO_EVENT_TYPE.containsKey(detailsClass)) {
            DETAILS_CLASS_TO_EVENT_TYPE.put(detailsClass, eventType);
        }
        EVENT_TYPE_TO_DETAILS_CLASS.put(eventType, detailsClass);
        SERIALIZERS.put(eventType, serializer);
        DESERIALIZERS.put(eventType, deserializer);
    }

    public static Class<? extends EventDetails> getDetailsClass(EventType eventType) {
        return EVENT_TYPE_TO_DETAILS_CLASS.get(eventType);
    }

    public static EventType getEventType(Class<? extends EventDetails> detailsClass) {
        return DETAILS_CLASS_TO_EVENT_TYPE.get(detailsClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends EventDetails> JsonSerializer<T> getSerializer(EventType eventType) {
        return (JsonSerializer<T>) SERIALIZERS.get(eventType);
    }

    @SuppressWarnings("unchecked")
    public static <T extends EventDetails> JsonDeserializer<T> getDeserializer(EventType eventType) {
        return (JsonDeserializer<T>) DESERIALIZERS.get(eventType);
    }

    /**
     * 用于注册自定义事件的辅助方法。
     * 自定义事件通过 ResourceLocation id 唯一标识。
     *
     * @param id 自定义事件的唯一标识符。
     * @param detailsClass 自定义事件详情的类。
     * @param serializer 自定义事件详情的序列化器。
     * @param deserializer 自定义事件详情的反序列化器。
     * @param <T> EventDetails 的子类型。
     */
    public static <T extends EventDetails> void registerCustomEvent(ResourceLocation id, Class<T> detailsClass, JsonSerializer<T> serializer, JsonDeserializer<T> deserializer) {
        if (CUSTOM_EVENT_ID_TO_DETAILS_CLASS.containsKey(id)) {
            throw new IllegalArgumentException("Custom event ID " + id + " already registered.");
        }
        if (CUSTOM_EVENT_DETAILS_CLASS_TO_ID.containsKey(detailsClass)) {
            throw new IllegalArgumentException("Custom event details class " + detailsClass.getName() + " already registered.");
        }
        CUSTOM_EVENT_ID_TO_DETAILS_CLASS.put(id, detailsClass);
        CUSTOM_EVENT_DETAILS_CLASS_TO_ID.put(detailsClass, id);
        CUSTOM_EVENT_SERIALIZERS.put(id, serializer);
        CUSTOM_EVENT_DESERIALIZERS.put(id, deserializer);
    }

    public static Class<? extends EventDetails> getCustomEventDetailsClass(ResourceLocation id) {
        return CUSTOM_EVENT_ID_TO_DETAILS_CLASS.get(id);
    }

    public static ResourceLocation getCustomEventId(Class<? extends EventDetails> detailsClass) {
        return CUSTOM_EVENT_DETAILS_CLASS_TO_ID.get(detailsClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends EventDetails> JsonSerializer<T> getCustomEventSerializer(ResourceLocation id) {
        return (JsonSerializer<T>) CUSTOM_EVENT_SERIALIZERS.get(id);
    }

    @SuppressWarnings("unchecked")
    public static <T extends EventDetails> JsonDeserializer<T> getCustomEventDeserializer(ResourceLocation id) {
        return (JsonDeserializer<T>) CUSTOM_EVENT_DESERIALIZERS.get(id);
    }
}