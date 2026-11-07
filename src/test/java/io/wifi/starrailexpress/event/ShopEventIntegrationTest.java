package io.wifi.starrailexpress.event;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 核对核心调用端与职业监听的边界、原短路点和购买回放求值顺序。 */
class ShopEventIntegrationTest {
    private static final String CORE = "io/wifi/starrailexpress/";

    @Test
    void genericShopEntryKeepsVetoBeforeStackCheckWithoutRoleHardcoding() throws Exception {
        String source = read(CORE + "util/ShopEntry.java");
        String body = between(source, "public boolean canBuy(", "    /**");
        assertOrder(body, "ShopEvents.ALLOW_BUY.invoker().allow(player, this)", "this.stack().isEmpty()");
        for (String role : List.of("ModRoles", "RoleData", "inNearDeath", "InsaneKiller")) assertFalse(body.contains(role), role);
        String handlers = read("org/agmas/noellesroles/init/events/ShopRoleEventHandlers.java");
        assertTrue(handlers.contains("ShopEvents.ALLOW_BUY.register(ShopRoleEventHandlers::allowBuy)"));
        assertTrue(handlers.contains("ModRoles.INSANE_KILLER"));
        assertTrue(handlers.contains("data != null && data.inNearDeath()"));
    }

    @Test
    void balanceEventsKeepTaxSuppressionHandlingAndLedgerOrder() throws Exception {
        String source = read(CORE + "cca/SREPlayerShopComponent.java");
        String add = between(source, "public void addToBalance(", "public void setBalance(");
        assertOrder(add, "ShopEvents.MODIFY_INCOME", "if (suppressIncome", "ShopEvents.HANDLE_BALANCE",
                "ShopEvents.BALANCE_DELTA", "this.setBalance");
        String set = between(source, "public void setBalance(", "public void tryBuy(");
        assertOrder(set, "ShopEvents.BEFORE_SET_BALANCE", "if (suppressIncome", "ShopEvents.HANDLE_BALANCE", "this.balance = amount;");
        for (String business : List.of("TaxedModifier", "WizardRoleData", "ModRoles.WIZARD", "MoneyFlow", "notifyShopPurchase")) {
            assertFalse(source.contains(business), business);
        }
        assertOrder(source, "var itemId =", "int count = entry.stack().getCount();",
                "ShopEvents.PURCHASE_SUCCESS", "replay.recordStoreBuy");
        String hooks = read("org/agmas/noellesroles/init/events/ShopRoleEventHandlers.java");
        assertTrue(hooks.contains("OnShopPurchase.EVENT.invoker().onPurchase(sp, entry, entry.price())"));
        assertTrue(hooks.contains("catch (Throwable ignored)"));
        assertTrue(hooks.contains("catch (Exception ignored)"));
    }

    @Test
    void extensionsAndInventoryRulesLiveInListenersInsteadOfCoreHelpers() throws Exception {
        String shop = read(CORE + "game/ShopContent.java");
        assertTrue(shop.contains("ShopEvents.MODIFY_ENTRIES.invoker().modify(role, player, result)"));
        assertFalse(shop.contains("HalloweenRuntime"));
        assertFalse(shop.contains("HalloweenItems"));
        String items = read(CORE + "util/SREItemUtils.java");
        assertOrder(items, "ItemTransferEvents.ALLOW_HOTBAR_INSERT.invoker()", "sstack.copy()");
        assertFalse(items.contains("HalloweenSoulLinks"));
        String hooks = read("org/agmas/noellesroles/init/events/ShopRoleEventHandlers.java");
        assertOrder(hooks, "if (registered) return;", "ShopEvents.ALLOW_BUY.register");
        assertTrue(hooks.contains("!HalloweenSoulLinks.isItemBorrower(player)"));
        String registration = read("org/agmas/noellesroles/register/NREventRegister.java");
        assertTrue(registration.contains("ShopRoleEventHandlers.register()"));
        assertTrue(registration.contains("GameUtilityRoleHooks.register()"));
        assertFalse(read(CORE + "game/GameTaskScheduler.java").contains("GameUtilityRoleHooks"));
    }

    @Test
    void genericEventDefinitionsNeverReferenceBusinessModules() throws Exception {
        for (String event : List.of("ShopEvents", "ItemTransferEvents", "GameUtilityEvents")) {
            String source = read(CORE + "event/" + event + ".java");
            assertFalse(source.contains("org.agmas.noellesroles"), event);
            assertFalse(source.contains("pro.fazeclan.river.stupid_express"), event);
            assertFalse(source.contains("net.minecraft.client"), event);
        }
    }

    private static void assertOrder(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker, previous + 1);
            assertTrue(current > previous, "缺失或时点错误: " + marker);
            previous = current;
        }
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        assertTrue(begin >= 0, start);
        int finish = source.indexOf(end, begin + start.length());
        assertTrue(finish > begin, end);
        return source.substring(begin, finish);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/java", path));
    }
}
