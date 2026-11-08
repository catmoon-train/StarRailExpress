package io.wifi.starrailexpress.settlement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes;
import io.wifi.starrailexpress.api.replay.ReplayPlayerProfile;
import io.wifi.starrailexpress.api.replay.ReplayTimelineEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 事件文本只替换占位符，购买数量与价格严格取自原始记录。 */
class SettlementEventTextTest {
    private static final String PREFIX = "settlement.starrailexpress.event.";
    private static final ReplayPlayerProfile ACTOR = new ReplayPlayerProfile(
            UUID.randomUUID(), "Alice", "sre:test", Component.literal("Test"), true);
    private static final ReplayPlayerProfile TARGET = new ReplayPlayerProfile(
            UUID.randomUUID(), "Bob", "sre:test", Component.literal("Test"), true);

    @Test void detectsOnlyMissingBlankAndMatchingEnumPlaceholders() {
        assertTrue(SettlementEventText.needsFallback(null, "STORE_BUY", null));
        assertTrue(SettlementEventText.needsFallback(Component.empty(), "STORE_BUY", null));
        assertTrue(SettlementEventText.needsFallback(Component.literal(" \n\t"), "STORE_BUY", null));
        assertTrue(SettlementEventText.needsFallback(Component.literal(" STORE_BUY ")
                .withStyle(ChatFormatting.DARK_GRAY), "STORE_BUY", null));
        assertTrue(SettlementEventText.needsFallback(Component.literal("SKILL_USED"), "ITEM_USED", "SKILL_USED"));
        assertFalse(SettlementEventText.needsFallback(Component.literal("Alice: STORE_BUY completed"), "STORE_BUY", null));
        assertFalse(SettlementEventText.needsFallback(Component.literal("DOOR_OPEN"), "STORE_BUY", null));
    }

    @Test void preservesReplayAndCustomComponentsByIdentity() {
        var translated = Component.translatable("sre.replay.event.store_buy", Component.literal("Alice"),
                Component.translatable("item.minecraft.diamond"), 20).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("!").withStyle(ChatFormatting.BOLD));
        var custom = Component.literal("Custom purchase: two gifts").withStyle(ChatFormatting.AQUA);
        for (Component original : new Component[]{translated, custom}) {
            var event = event(ReplayEventTypes.EventType.STORE_BUY, original, Map.of());
            assertSame(original, SettlementEventText.resolve(event, purchase("minecraft:diamond:2", "20"),
                    id -> fail("已有文本不应重新解析或翻译物品")));
        }
        var future = event(null, custom, Map.of("legacyType", "FUTURE_EVENT"));
        assertSame(custom, SettlementEventText.resolve(future, null, id -> null));
    }

    @Test void parsesNamespacedPurchaseQuantityAndRecordedPriceSeparately() {
        var parsed = SettlementEventText.parsePurchase("noellesroles:shop/medical_pack:4", "27");
        assertEquals("noellesroles:shop/medical_pack", parsed.itemId().toString());
        assertEquals(Integer.valueOf(4), parsed.quantity());
        assertEquals(Integer.valueOf(27), parsed.cost());
    }

    @Test void neverSplitsAnOrdinaryNamespaceOrNumericItemPath() {
        for (String id : new String[]{"minecraft:diamond", "example:123", "example:folder/item"}) {
            var parsed = SettlementEventText.parsePurchase(id, null);
            assertEquals(id, parsed.itemId().toString());
            assertNull(parsed.quantity());
            assertNull(parsed.cost());
        }
        assertEquals("minecraft:diamond", SettlementEventText.parsePurchase("diamond", null).itemId().toString());
    }

    @Test void omitsUnconfirmedNumbersAndAcceptsRecordedFreePurchases() {
        for (String value : new String[]{"", "-1", "garbage", "2147483648", "3:20", " 20"}) {
            var parsed = SettlementEventText.parsePurchase("minecraft:diamond:" + value, value);
            assertNull(parsed.quantity());
            assertNull(parsed.cost());
        }
        var free = SettlementEventText.parsePurchase("minecraft:diamond:0", "0");
        assertNull(free.quantity());
        assertEquals(Integer.valueOf(0), free.cost());
        for (String item : new String[]{null, "", "unknown", "minecraft:air:2", "bad item:diamond:2"})
            assertNull(SettlementEventText.parsePurchase(item, null).itemId());
    }

    @Test void purchaseFallbackKeepsPlayerTranslatedItemAndActualNumbers() {
        var raw = purchase("minecraft:diamond:3", "40");
        var event = event(ReplayEventTypes.EventType.STORE_BUY, Component.literal("STORE_BUY"), Map.of());
        Component text = SettlementEventText.resolve(event, raw, id -> {
            assertEquals("minecraft:diamond", id.toString());
            return Component.translatable("item.minecraft.diamond");
        });
        var content = translation(text, PREFIX + "store_buy");
        assertEquals("Alice", ((Component) content.getArgs()[0]).getString());
        translation((Component) content.getArgs()[1], "item.minecraft.diamond");
        assertEquals(2, text.getSiblings().size());
        assertEquals(3, translation(text.getSiblings().get(0), PREFIX + "purchase_quantity").getArgs()[0]);
        assertEquals(40, translation(text.getSiblings().get(1), PREFIX + "purchase_cost").getArgs()[0]);
        assertEquals("minecraft:diamond", SettlementEventText.item(event, raw));
    }

    @Test void timelineRecordedDataCanSupplyPurchaseWhenRawIndexDoesNotMatch() {
        var event = event(ReplayEventTypes.EventType.STORE_BUY, null,
                Map.of("legacyType", "STORE_BUY", "itemUsed", "example:bandage:2", "message", "15"));
        Component text = SettlementEventText.resolve(event, null, id -> Component.literal(id.toString()));
        assertEquals("example:bandage", SettlementEventText.item(event, null));
        assertEquals(2, translation(text.getSiblings().get(0), PREFIX + "purchase_quantity").getArgs()[0]);
        assertEquals(15, translation(text.getSiblings().get(1), PREFIX + "purchase_cost").getArgs()[0]);
        assertEquals("minecraft:diamond", SettlementEventText.item(event, purchase("minecraft:diamond:7", "30")));
    }

    @Test void missingPurchaseDataDoesNotInventQuantityCostOrItemTranslationKeys() {
        var event = event(ReplayEventTypes.EventType.STORE_BUY, Component.empty(), Map.of());
        Component text = SettlementEventText.resolve(event, null, id -> fail("缺少物品 ID"));
        assertTrue(text.getSiblings().isEmpty());
        var content = translation(text, PREFIX + "store_buy");
        translation((Component) content.getArgs()[1], PREFIX + "unknown_item");
        assertNull(SettlementEventText.item(event, null));
        Component missingItem = SettlementEventText.resolve(event, purchase("example:missing:1", null), id -> null);
        translation((Component) translation(missingItem, PREFIX + "store_buy").getArgs()[1], PREFIX + "unknown_item");
    }

    @Test void nonPurchaseItemCaptureIsUnchanged() {
        var event = event(ReplayEventTypes.EventType.ITEM_USED, Component.literal("item used"), Map.of());
        var raw = new GameReplayData.ReplayEvent(GameReplayData.EventType.ITEM_USED, ACTOR.uuid(), null,
                "example:tool", null, false, Map.of());
        assertEquals("example:tool", SettlementEventText.item(event, raw));
        assertNull(SettlementEventText.item(event, null));
    }

    @Test void trapFallbackDoesNotReverseTrapperAndVictim() {
        var event = event(ReplayEventTypes.EventType.TRAP_TRIGGERED, Component.literal("TRAP_TRIGGERED"), Map.of());
        var content = translation(SettlementEventText.resolve(event, null, id -> null), PREFIX + "trap_triggered");
        assertEquals("Bob", ((Component) content.getArgs()[0]).getString());
        assertEquals("Alice", ((Component) content.getArgs()[1]).getString());
    }

    @Test void unknownTypeUsesLocalizedGenericRecordInvolvingPlayer() {
        var event = event(null, null, Map.of("legacyType", "FUTURE_EVENT"));
        var content = translation(SettlementEventText.resolve(event, null, id -> null), PREFIX + "generic");
        assertEquals("Alice", ((Component) content.getArgs()[0]).getString());
        assertEquals(PREFIX + "generic", SettlementEventText.fallbackKey("FUTURE_EVENT"));
    }

    @Test void everyKnownTypeAndComicLabelHasBothTranslations() throws IOException {
        for (String locale : new String[]{"zh_cn", "en_us"}) {
            JsonObject language;
            try (var reader = Files.newBufferedReader(Path.of("src/main/resources/assets/starrailexpress/lang/"
                    + locale + ".json"), StandardCharsets.UTF_8)) {
                language = JsonParser.parseReader(reader).getAsJsonObject();
            }
            for (var type : ReplayEventTypes.EventType.values()) {
                String key = SettlementEventText.fallbackKey(type.name());
                assertNotEquals(PREFIX + "generic", key, type.name());
                assertTranslation(language, key);
                Component text = SettlementEventText.resolve(event(type, Component.literal(type.name()), Map.of()),
                        null, id -> null);
                translation(text, key);
            }
            for (var type : GameReplayData.EventType.values())
                assertTranslation(language, SettlementEventText.fallbackKey(type.name()));
            for (String suffix : new String[]{"generic", "unknown_player", "unknown_item", "purchase_quantity", "purchase_cost"})
                assertTranslation(language, PREFIX + suffix);
            for (String suffix : new String[]{"category.purchase", "category.interaction", "category.conflict",
                    "category.change", "category.story", "roster", "issue", "next"})
                assertTranslation(language, "settlement.starrailexpress.comic." + suffix);
        }
    }

    private static void assertTranslation(JsonObject language, String key) {
        assertTrue(language.has(key), key);
        assertFalse(language.get(key).getAsString().isBlank(), key);
        assertNotEquals(key, language.get(key).getAsString());
    }

    private static TranslatableContents translation(Component text, String key) {
        var content = assertInstanceOf(TranslatableContents.class, text.getContents());
        assertEquals(key, content.getKey());
        return content;
    }

    private static ReplayTimelineEvent event(ReplayEventTypes.EventType type, Component text, Map<String, String> data) {
        return new ReplayTimelineEvent(UUID.randomUUID(), type, 100, 50, ACTOR, TARGET, text, false, data);
    }

    private static GameReplayData.ReplayEvent purchase(String item, String cost) {
        return new GameReplayData.ReplayEvent(GameReplayData.EventType.STORE_BUY, ACTOR.uuid(), null,
                item, cost, false, Map.of());
    }
}
