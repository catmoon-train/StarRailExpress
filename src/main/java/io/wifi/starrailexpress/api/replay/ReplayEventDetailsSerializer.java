package io.wifi.starrailexpress.api.replay;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.EventDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.EventType;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.CustomEventDetails;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;

public class ReplayEventDetailsSerializer implements JsonSerializer<EventDetails>, JsonDeserializer<EventDetails> {

    private static final String EVENT_TYPE_FIELD = "eventType";
    private static final String CUSTOM_EVENT_ID_FIELD = "customEventId";
    private static final String DATA_FIELD = "data";

    @Override
    public JsonElement serialize(EventDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();

        if (src instanceof CustomEventDetails customDetails) {
            // 对于自定义事件，需要特殊处理，因为其 EventType 都是 CUSTOM_EVENT
            // 但实际的事件类型由 customEventId 决定
            ResourceLocation customEventId = ReplayEventRegistry.getCustomEventId(customDetails.getClass());
            if (customEventId == null) {
                throw new JsonParseException("Unregistered custom event details class: " + customDetails.getClass().getName());
            }
            jsonObject.addProperty(EVENT_TYPE_FIELD, EventType.CUSTOM_EVENT.name());
            jsonObject.addProperty(CUSTOM_EVENT_ID_FIELD, customEventId.toString());
            // 使用注册的自定义事件序列化器
            JsonSerializer<EventDetails> serializer = ReplayEventRegistry.getCustomEventSerializer(customEventId);
            if (serializer != null) {
                jsonObject.add(DATA_FIELD, serializer.serialize(customDetails, customDetails.getClass(), context));
            } else {
                // 如果没有注册特定的序列化器，则使用默认的
                jsonObject.add(DATA_FIELD, context.serialize(customDetails));
            }
        } else {
            EventType eventType = ReplayEventRegistry.getEventType(src.getClass());
            if (eventType == null) {
                throw new JsonParseException("Unregistered event details class: " + src.getClass().getName());
            }
            jsonObject.addProperty(EVENT_TYPE_FIELD, eventType.name());
            // 使用注册的事件序列化器
            JsonSerializer<EventDetails> serializer = ReplayEventRegistry.getSerializer(eventType);
            if (serializer != null) {
                jsonObject.add(DATA_FIELD, serializer.serialize(src, src.getClass(), context));
            } else {
                // 如果没有注册特定的序列化器，则使用默认的
                jsonObject.add(DATA_FIELD, context.serialize(src));
            }
        }
        return jsonObject;
    }

    @Override
    public EventDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String eventTypeName = jsonObject.get(EVENT_TYPE_FIELD).getAsString();
        EventType eventType = EventType.valueOf(eventTypeName);

        JsonElement dataElement = jsonObject.get(DATA_FIELD);

        if (eventType == EventType.CUSTOM_EVENT) {
            String customEventIdString = jsonObject.get(CUSTOM_EVENT_ID_FIELD).getAsString();
            ResourceLocation customEventId = ResourceLocation.parse(customEventIdString);
            Class<? extends EventDetails> detailsClass = ReplayEventRegistry.getCustomEventDetailsClass(customEventId);
            if (detailsClass == null) {
                throw new JsonParseException("Unregistered custom event ID: " + customEventId);
            }
            // 使用注册的自定义事件反序列化器
            JsonDeserializer<EventDetails> deserializer = ReplayEventRegistry.getCustomEventDeserializer(customEventId);
            if (deserializer != null) {
                return deserializer.deserialize(dataElement, detailsClass, context);
            } else {
                // 如果没有注册特定的反序列化器，则使用默认的
                return context.deserialize(dataElement, detailsClass);
            }
        } else {
            Class<? extends EventDetails> detailsClass = ReplayEventRegistry.getDetailsClass(eventType);
            if (detailsClass == null) {
                throw new JsonParseException("Unregistered event type: " + eventType);
            }
            // 使用注册的事件反序列化器
            JsonDeserializer<EventDetails> deserializer = ReplayEventRegistry.getDeserializer(eventType);
            if (deserializer != null) {
                return deserializer.deserialize(dataElement, detailsClass, context);
            } else {
                // 如果没有注册特定的反序列化器，则使用默认的
                return context.deserialize(dataElement, detailsClass);
            }
        }
    }
}