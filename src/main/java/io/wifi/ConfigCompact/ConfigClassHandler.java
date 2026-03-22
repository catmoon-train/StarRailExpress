package io.wifi.ConfigCompact;

import com.google.gson.*;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import io.wifi.ConfigCompact.config_gui_provider.GenericEnumGuiProvider;
import io.wifi.ConfigCompact.config_gui_provider.GenericMapGuiProvider;
import io.wifi.ConfigCompact.network.SyncConfigPayload;
import io.wifi.starrailexpress.SRE;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigManager;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

public class ConfigClassHandler<T extends ConfigData> {
    private final Class<T> type; // 保存 T 的 Class
    public static final Gson gson = new Gson();
    public static final HashMap<String, Class<?>> nameToClassMap = new HashMap<>();

    @Environment(EnvType.CLIENT)
    public static class GuiGenerator<T extends ConfigData> {
        private final Class<T> type;

        public GuiGenerator(Class<T> type) {
            this.type = type;
        }

        // ✅ 可以安全引用 Screen，因为整个类已是 CLIENT-only
        public net.minecraft.client.gui.screens.Screen generateScreen(Screen parent) {
            GenericMapGuiProvider.register(AutoConfig.getGuiRegistry(type));
            GenericEnumGuiProvider.register(AutoConfig.getGuiRegistry(type));
            return AutoConfig.getConfigScreen(type, parent).get();
        }
    }

    public ConfigClassHandler(Class<T> type) {
        this.type = type;
        nameToClassMap.put(type.getName(), type);
        this.register();
    }

    @Environment(EnvType.CLIENT)
    public GuiGenerator<T> generateGui() {
        return new GuiGenerator<T>(type);
    }

    public static class SyncInfo {
        public String fieldName;
        public Object fieldContent;

        public SyncInfo(String name, Object content) {
            this.fieldName = name;
            this.fieldContent = content;
        }
    }

    public void syncToClient(MinecraftServer server) {
        // 同步所有被 @ConfigSync 标记的字段
        ArrayList<SyncInfo> syncInfos = new ArrayList<>();
        var instance = instance();
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigSync.class)) {
                ConfigSync annotation = field.getAnnotation(ConfigSync.class);
                if (annotation.shouldSync()) {
                    try {
                        field.setAccessible(true);
                        syncInfos.add(new SyncInfo(field.getName(), field.get(instance)));
                    } catch (Exception e) {
                        SRE.LOGGER.error("Unable to sync config {}", field.getName(), e);
                    }
                }
            }
        }
        var content = encodeToJson(syncInfos);
        var payload = new SyncConfigPayload(type.getName(), content);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public void syncToClient(ServerPlayer sp) {
        // 同步所有被 @ConfigSync 标记的字段
        ArrayList<SyncInfo> syncInfos = new ArrayList<>();
        var instance = instance();
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigSync.class)) {
                ConfigSync annotation = field.getAnnotation(ConfigSync.class);
                if (annotation.shouldSync()) {
                    try {
                        field.setAccessible(true);
                        syncInfos.add(new SyncInfo(field.getName(), field.get(instance)));
                    } catch (Exception e) {
                        SRE.LOGGER.error("Unable to sync config {}", field.getName(), e);
                    }
                }
            }
        }
        var content = encodeToJson(syncInfos);
        var payload = new SyncConfigPayload(type.getName(), content);
        ServerPlayNetworking.send(sp, payload);
    }

    @Environment(EnvType.CLIENT)
    public static void recieveConfigPackFromServer(String id, String content) {
        Class<?> type = nameToClassMap.getOrDefault(id, null);
        if (type == null) {
            SRE.LOGGER.error("Sync config failed: Unable to get config of {}", id);
            return;
        }
        JsonElement parser;
        try {
            parser = JsonParser.parseString(content);
        } catch (Exception e) {
            SRE.LOGGER.error("Sync config failed: Unable to decode config pack of {}", id, e);
            return;
        }
        Object target = null;
        try {
            @SuppressWarnings("unchecked")
            var tt = (Class<ConfigData>) type;
            target = instance(tt);
        } catch (Exception e) {
            SRE.LOGGER.error("Sync config failed. Config Type from server: {}", id, e);
            return;
        }
        if (!parser.isJsonArray()) {
            SRE.LOGGER.error("Sync config failed: Not a json array. Config Type from server: {}", type.getSimpleName());
            return;
        }
        JsonArray pack = parser.getAsJsonArray();
        for (JsonElement info : pack.asList()) {
            try {
                if (!info.isJsonObject()) {
                    SRE.LOGGER.error("Sync config failed: Not a json array with object. Config Type from server: {}",
                            type.getSimpleName());
                    return;
                }
                JsonObject _obj = info.getAsJsonObject();
                if (!_obj.has("fieldName"))
                    continue;
                // 获取目标类中声明的字段（包括私有字段）
                Field field = type.getDeclaredField(_obj.get("fieldName").getAsString());
                // 如果是私有字段，允许访问
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
                // 将字段值设置到目标对象
                var ctx = convertValue(_obj.get("fieldContent"), fieldType);
                field.set(target, ctx);
            } catch (Exception e) {
                SRE.LOGGER.error("Sync config failed: {}.{}", id, info.toString(), e);
            }
        }
        SRE.LOGGER.info("Successed recieved config from server: {}", type.getSimpleName());
    }

    private static Object convertValue(JsonElement value, Class<?> targetType) throws JsonSyntaxException {
        if (value == null)
            return null;

        return gson.fromJson(value, targetType);
    }

    private static String encodeToJson(ArrayList<SyncInfo> pack) {
        return gson.toJson(pack);
    }

    public void load() {
        try {
            var config = ((ConfigManager<T>) AutoConfig
                    .getConfigHolder(type));
            config.load();
        } catch (ClassCastException e) {
            // 理论上不会发生，除非 cloth-config 换了实现
            throw new RuntimeException("Failed to reload config", e);
        }
    }

    public void reset() {
        try {
            var config = ((ConfigManager<T>) AutoConfig
                    .getConfigHolder(type));
            config.resetToDefault();
        } catch (ClassCastException e) {
            // 理论上不会发生，除非 cloth-config 换了实现
            throw new RuntimeException("Failed to reload config", e);
        }
    }

    public void save() {
        try {
            var config = ((ConfigManager<T>) AutoConfig
                    .getConfigHolder(type));
            config.save();
        } catch (ClassCastException e) {
            // 理论上不会发生，除非 cloth-config 换了实现
            throw new RuntimeException("Failed to reload config", e);
        }
    }

    public static <T extends ConfigData> T instance(Class<T> clazz) {
        T config = AutoConfig.getConfigHolder(clazz).getConfig();
        return config;
    }

    public T instance() {
        T config = AutoConfig.getConfigHolder(type).getConfig();
        return config;
    }

    public void register() {
        AutoConfig.register(type, GsonConfigSerializer::new);
    }
}
