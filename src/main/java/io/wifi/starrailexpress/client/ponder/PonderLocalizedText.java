/* GNU General Public License v3 or later */
package io.wifi.starrailexpress.client.ponder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 思索覆盖层里的多语言文本。
 *
 * <p>场景 JSON 的 {@code text} 步骤本身支持 {@code en_us / zh_cn / zh_tw} 三语对象，
 * 但覆盖层（技能面板、商店陈列、界面板）早先只接受单语言字符串。
 * 这里补一个「按当前语言解析」的多语言文本：
 *
 * <ul>
 *   <li>JSON 里写字符串：按原文显示（以 {@code ponder.} 开头时按翻译键解析）；</li>
 *   <li>JSON 里写 {@code {"en_us":..,"zh_cn":..,"zh_tw":..}}：按当前语言取值，
 *       取不到时依次回退 {@code en_us} → 首个可用值。</li>
 * </ul>
 */
public final class PonderLocalizedText {

    /** 语言回退顺序：当前语言 → en_us → 首个可用值。 */
    private static final List<String> FALLBACK_ORDER = List.of("en_us", "zh_cn", "zh_tw");

    private static final String KEY_PREFIX = "ponder.";

    private final Map<String, String> byLang;

    private PonderLocalizedText(Map<String, String> byLang) {
        this.byLang = byLang;
    }

    /** 单语言文本。 */
    public static PonderLocalizedText of(String plain) {
        Map<String, String> map = new LinkedHashMap<>();
        if (plain != null && !plain.isEmpty()) {
            map.put("plain", plain);
        }
        return new PonderLocalizedText(map);
    }

    /** 多语言文本。 */
    public static PonderLocalizedText of(Map<String, String> byLang) {
        return new PonderLocalizedText(byLang == null ? Map.of() : new LinkedHashMap<>(byLang));
    }

    /** 从 JSON 读：字符串或三语对象。 */
    public static PonderLocalizedText fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return of("");
        }
        if (element.isJsonPrimitive()) {
            return of(element.getAsString());
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            Map<String, String> map = new LinkedHashMap<>();
            for (String key : object.keySet()) {
                JsonElement value = object.get(key);
                if (value != null && value.isJsonPrimitive()) {
                    map.put(key, value.getAsString());
                }
            }
            return of(map);
        }
        return of("");
    }

    /** 当前语言下的纯文本；用于计算宽度。 */
    public String plain() {
        String selected = "en_us";
        try {
            selected = Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Throwable ignored) {
            // 客户端还没准备好语言时退回 en_us。
        }
        String value = byLang.get(selected);
        if (value == null) {
            for (String key : FALLBACK_ORDER) {
                value = byLang.get(key);
                if (value != null) {
                    break;
                }
            }
        }
        if (value == null) {
            value = byLang.values().stream().findFirst().orElse("");
        }
        return value;
    }

    /** 解析成可绘制的组件。 */
    public Component resolve() {
        String text = plain();
        if (text.isEmpty()) {
            return Component.empty();
        }
        return text.startsWith(KEY_PREFIX) ? Component.translatable(text) : Component.literal(text);
    }

    /** 是否为空。 */
    public boolean isEmpty() {
        return plain().isEmpty();
    }
}
