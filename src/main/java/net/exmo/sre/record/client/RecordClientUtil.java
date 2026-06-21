package net.exmo.sre.record.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 客户端战绩 GUI 的小工具：组件反序列化与时间格式化。 */
public final class RecordClientUtil {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private RecordClientUtil() {
    }

    /** 把服务端写入的「序列化 Component JSON」还原为带样式的 {@link Component}。失败时回退为纯文本。 */
    public static Component parseComponent(String json) {
        if (json == null || json.isBlank()) {
            return Component.empty();
        }
        try {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            RegistryAccess registryAccess = connection != null ? connection.registryAccess() : RegistryAccess.EMPTY;
            Component component = Component.Serializer.fromJson(json, registryAccess);
            return component == null ? Component.empty() : component;
        } catch (Exception exception) {
            return Component.literal(json);
        }
    }

    public static String formatDate(long epochMillis) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }
}
