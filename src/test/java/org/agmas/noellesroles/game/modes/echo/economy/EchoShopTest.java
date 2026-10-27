package org.agmas.noellesroles.game.modes.echo.economy;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EchoShopTest {
    @Test void packOffersSplitIntoTwoCategoriesWithRisingPrices() {
        var items = EchoPackOffer.of(EchoPackOffer.EchoCategory.ITEM);
        var skills = EchoPackOffer.of(EchoPackOffer.EchoCategory.SKILL);
        assertEquals(3, items.size());
        assertEquals(3, skills.size());
        for (var group : List.of(items, skills)) {
            int previous = 0;
            for (var offer : group) {
                assertTrue(offer.price() > previous, offer.id() + " 价格必须递增");
                previous = offer.price();
            }
        }
        // 技能包整体比同档道具包贵，但差距有界
        assertEquals(items.size(), skills.size());
        for (int n = 0; n < items.size(); n++)
            assertTrue(skills.get(n).price() > items.get(n).price());
    }

    @Test void everyOfferAndGoodHasUniqueIdAndKeys() {
        Set<String> ids = new HashSet<>();
        for (var offer : EchoPackOffer.values()) {
            assertTrue(ids.add(offer.id()), offer.id());
            assertTrue(offer.nameKey().startsWith("echo.shop.pack."));
            assertTrue(offer.descriptionKey().startsWith("echo.shop.pack."));
            assertTrue(offer.price() > 0);
        }
        Set<String> goods = new HashSet<>();
        for (var good : EchoShopService.VendingGood.values()) {
            assertTrue(goods.add(good.id()), good.id());
            assertTrue(good.nameKey().startsWith("echo.shop.good."));
            assertTrue(good.descriptionKey().startsWith("echo.shop.good."));
            assertTrue(good.price() > 0);
        }
    }

    @Test void economySourcesArePositiveAndUnique() {
        Set<String> ids = new HashSet<>();
        for (var source : EchoEconomy.Source.values()) {
            assertTrue(ids.add(source.id()), source.id());
            assertTrue(source.coins() > 0, source.id());
        }
    }

    @Test void shopKeysExistInThreeLanguagesWithSameKeySet() throws Exception {
        Set<String> previous = null;
        for (String language : List.of("zh_cn", "zh_tw", "en_us")) {
            var json = JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/echo_freight/lang/" + language + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            if (previous != null) assertEquals(previous, json.keySet(), language);
            previous = new HashSet<>(json.keySet());
            for (var offer : EchoPackOffer.values()) {
                assertTrue(json.has(offer.nameKey()), language + ":" + offer.nameKey());
                assertTrue(json.has(offer.descriptionKey()), language + ":" + offer.descriptionKey());
            }
            for (var good : EchoShopService.VendingGood.values()) {
                assertTrue(json.has(good.nameKey()), language + ":" + good.nameKey());
                assertTrue(json.has(good.descriptionKey()), language + ":" + good.descriptionKey());
            }
            for (String key : List.of("echo.shop.title", "echo.shop.balance", "echo.shop.price",
                    "echo.shop.sold", "echo.shop.no_coins", "echo.shop.bought", "echo.shop.sold_out",
                    "echo.shop.tab.item", "echo.shop.tab.skill", "echo.shop.tab.packs",
                    "echo.repair.title", "echo.repair.hint", "echo.repair.accident_title",
                    "echo.repair.accident_hint"))
                assertTrue(json.has(key), language + ":" + key);
        }
    }
}
