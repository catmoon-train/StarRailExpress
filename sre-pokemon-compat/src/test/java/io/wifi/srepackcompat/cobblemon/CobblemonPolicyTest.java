package io.wifi.srepackcompat.cobblemon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CobblemonPolicyTest {
    @AfterEach void disconnect() { CobblemonPolicy.setEnabled(false); }

    @Test void onlyExplicitBooleanTrueEnables() {
        assertTrue(CobblemonPolicy.readServerSetting("[{\"fieldName\":\"pokmen\",\"fieldContent\":true}]"));
        for (String value : new String[]{"false", "null", "\"true\"", "1", "{}", "[]"}) {
            assertFalse(CobblemonPolicy.readServerSetting("[{\"fieldName\":\"pokmen\",\"fieldContent\":" + value + "}]"), value);
        }
    }

    @Test void missingOldOrInvalidServerSettingDisables() {
        for (String packet : new String[]{"[]", "{}", "null", "not json", "[{\"fieldName\":\"other\",\"fieldContent\":true}]",
                "[{\"fieldName\":\"pokmen\"}]", "[null,1,{}]"}) {
            assertFalse(CobblemonPolicy.readServerSetting(packet), packet);
        }
    }

    @Test void connectionStateCanEnableDisableAndResetWithoutRestart() {
        assertFalse(CobblemonPolicy.enabled());
        CobblemonPolicy.setEnabled(true);
        assertTrue(CobblemonPolicy.enabled());
        CobblemonPolicy.setEnabled(false);
        assertFalse(CobblemonPolicy.enabled());
        CobblemonPolicy.setEnabled(CobblemonPolicy.readServerSetting("[]"));
        assertFalse(CobblemonPolicy.enabled());
    }

    @Test void onlyGatedModKeysAreAffected() {
        assertTrue(CobblemonPolicy.isKey("key.journeymap.map_toggle_alt"));
        assertTrue(CobblemonPolicy.isKey("key.journeymap.create_waypoint"));
        assertFalse(CobblemonPolicy.isKey("key.journeymap_other"));
        assertTrue(CobblemonPolicy.isKey("key.cobblemon.throwpartypokemon"));
        assertTrue(CobblemonPolicy.isKey("key.mega_showdown.mega_evo"));
        assertTrue(CobblemonPolicy.isKey("key.mega_showdown.ultra_key"));
        assertFalse(CobblemonPolicy.isKey("key.mega_showdown_other"));
        assertFalse(CobblemonPolicy.isKey("key.jei.showRecipe"));
        assertFalse(CobblemonPolicy.isKey("key.inventory"));
        assertFalse(CobblemonPolicy.isKey("key.starrailexpress.ability"));
        assertFalse(CobblemonPolicy.isKey("key.cobblemon_other"));
    }

    @Test void onlyClientGameplayEventsAreBlocked() {
        for (String event : new String[]{"ClientTickEvent$Pre", "ClientTickEvent$Post", "RenderEvent", "ItemTooltipEvent"}) {
            assertTrue(CobblemonPolicy.isClientGameplayEvent("com.cobblemon.mod.common.platform.events." + event));
        }
        for (String event : new String[]{"ClientPlayerEvent$Login", "ClientPlayerEvent$Logout", "ClientEntityEvent$Unload",
                "ServerTickEvent$Post", "ServerPlayerEvent$Login"}) {
            assertFalse(CobblemonPolicy.isClientGameplayEvent("com.cobblemon.mod.common.platform.events." + event));
        }
        assertFalse(CobblemonPolicy.isPresentation(String.class));
    }
}
