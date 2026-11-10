package io.wifi.srepackcompat.cobblemon;

import com.google.gson.JsonParser;

/** 只接受本次连接收到的服务端开关，不读取或保存客户端本地配置。 */
public final class CobblemonPolicy {
    public static final String CONFIG_ID = "io.wifi.starrailexpress.SREConfig";
    private static volatile boolean enabled;

    public static boolean enabled() { return enabled; }

    static void setEnabled(boolean value) { enabled = value; }

    public static boolean readServerSetting(String content) {
        try {
            var root = JsonParser.parseString(content);
            if (!root.isJsonArray()) return false;
            for (var entry : root.getAsJsonArray()) {
                if (!entry.isJsonObject()) continue;
                var field = entry.getAsJsonObject();
                if (!field.has("fieldName") || !field.get("fieldName").isJsonPrimitive()
                        || !"pokmen".equals(field.get("fieldName").getAsString())) continue;
                var value = field.get("fieldContent");
                return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                        && value.getAsBoolean();
            }
        } catch (RuntimeException ignored) {
            // 缺失、旧服或无效配置均关闭，不能沿用上一台服务器的开启状态。
        }
        return false;
    }

    public static boolean isKey(String name) { return name.startsWith("key.cobblemon."); }

    public static boolean isPresentation(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().startsWith("com.cobblemon.mod.")) return true;
        }
        return false;
    }

    public static boolean isClientGameplayEvent(String name) {
        return name.startsWith("com.cobblemon.mod.common.platform.events.ClientTickEvent$")
                || name.equals("com.cobblemon.mod.common.platform.events.RenderEvent")
                || name.equals("com.cobblemon.mod.common.platform.events.ItemTooltipEvent");
    }

    private CobblemonPolicy() { }
}
