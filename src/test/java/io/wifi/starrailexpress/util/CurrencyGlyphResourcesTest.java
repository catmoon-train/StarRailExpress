package io.wifi.starrailexpress.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 货币图标字形（Unicode 私有使用区）回归测试。
 * <p>
 * 历史问题：翻译工具会把语言文件里的私有使用区字符改写成别的码位（例如 {@code \uE781} 变成
 * {@code \uE000} 或被吞掉），导致金币/代币图标显示成方块或消失。
 * 现在字形码位由 {@link ShopEntry.Currency#iconText()} 硬编码提供，语言文件只保留 {@code %s}，
 * 本测试确保这条约定不被破坏。
 */
class CurrencyGlyphResourcesTest {

    /** 代码里硬编码的货币字形码位，必须与 font/default.json 的注册一致。 */
    private static final String MONEY_GLYPH = "\uE000";
    private static final String TOKEN_GLYPH = "\uE001";

    /** 价格/金额类翻译键：文案里只允许出现占位符，不允许出现货币字形。 */
    private static final String[][] AMOUNT_KEYS = {
            { "noellesroles", "tip.recaller.not_enough_money" },
            { "noellesroles", "message.note.put_back" },
            { "noellesroles", "message.note.not_enough_money" },
            { "noellesroles", "gui.vendingmachine.money_display" },
            { "noellesroles", "gui.vendingmachine.minigame_token_display" },
            { "starrailexpress", "hud.sre.game_token" },
    };

    private static final String[] LOCALES = { "en_us", "zh_cn", "zh_tw" };

    @Test
    void fontRegistersTheHardcodedCurrencyGlyphs() throws Exception {
        List<String> chars = registeredBitmapChars("assets/minecraft/font/default.json");
        assertTrue(chars.contains(MONEY_GLYPH),
                "font/default.json 必须把金币图标注册到 \\uE000，实际: " + chars);
        assertTrue(chars.contains(TOKEN_GLYPH),
                "font/default.json 必须把代币图标注册到 \\uE001，实际: " + chars);
    }

    @Test
    void amountTranslationKeysCarryNoPrivateUseGlyphs() throws Exception {
        for (String locale : LOCALES) {
            for (String[] entry : AMOUNT_KEYS) {
                String path = "assets/" + entry[0] + "/lang/" + locale + ".json";
                String value = translationValue(path, entry[1]);
                assertFalse(value.isEmpty(), path + " 缺少翻译键 " + entry[1]);
                assertTrue(privateUseGlyphs(value).isEmpty(),
                        path + " 的 " + entry[1] + " 不应包含货币字形（字形由代码追加），实际: "
                                + privateUseGlyphs(value));
            }
        }
    }

    @Test
    void amountTranslationKeysKeepTheirPlaceholders() throws Exception {
        for (String locale : LOCALES) {
            for (String[] entry : AMOUNT_KEYS) {
                String path = "assets/" + entry[0] + "/lang/" + locale + ".json";
                String value = translationValue(path, entry[1]);
                assertTrue(value.contains("%s"),
                        path + " 的 " + entry[1] + " 必须保留 %s 占位符，实际: " + value);
            }
        }
        // put_back 同时带“花费金额”和“目标名字”两个参数。
        for (String locale : LOCALES) {
            String value = translationValue("assets/noellesroles/lang/" + locale + ".json",
                    "message.note.put_back");
            assertEquals(2, countPlaceholders(value),
                    "message.note.put_back 需要两个 %s（金额 + 目标名），实际: " + value);
        }
    }

    private static int countPlaceholders(String value) {
        return value.split("%s", -1).length - 1;
    }

    private static List<String> privateUseGlyphs(String value) {
        List<String> found = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\uE000' && c <= '\uF8FF') {
                found.add(String.format("U+%04X", (int) c));
            }
        }
        return found;
    }

    private static List<String> registeredBitmapChars(String path) throws Exception {
        JsonObject root = JsonParser
                .parseReader(new InputStreamReader(resource(path), StandardCharsets.UTF_8))
                .getAsJsonObject();
        List<String> chars = new ArrayList<>();
        JsonArray providers = root.getAsJsonArray("providers");
        for (int i = 0; i < providers.size(); i++) {
            JsonObject provider = providers.get(i).getAsJsonObject();
            if (!"bitmap".equals(provider.get("type").getAsString())) {
                continue;
            }
            JsonArray providerChars = provider.getAsJsonArray("chars");
            for (int j = 0; j < providerChars.size(); j++) {
                chars.add(providerChars.get(j).getAsString());
            }
        }
        return chars;
    }

    private static String translationValue(String path, String key) throws Exception {
        JsonObject root = JsonParser
                .parseReader(new InputStreamReader(resource(path), StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject lang = root.has("lang") && root.get("lang").isJsonObject()
                ? root.getAsJsonObject("lang")
                : root;
        return lang.has(key) ? lang.get(key).getAsString() : "";
    }

    private static InputStream resource(String path) {
        InputStream stream = CurrencyGlyphResourcesTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "缺少资源: " + path);
        return stream;
    }
}
