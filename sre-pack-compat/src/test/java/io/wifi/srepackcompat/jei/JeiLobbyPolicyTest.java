package io.wifi.srepackcompat.jei;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class JeiLobbyPolicyTest {
    public static class Core { public static boolean isLobby; }
    public static class Missing { }
    @Test void requiresBothConnectionAndLobby() {
        assertTrue(JeiLobbyPolicy.allows(true, true));
        assertFalse(JeiLobbyPolicy.allows(true, false));
        assertFalse(JeiLobbyPolicy.allows(false, true));
        assertFalse(JeiLobbyPolicy.allows(false, false));
    }
    @Test void observesLiveLobbyChangesWithoutSavingOrCachingValue() throws Exception {
        var lobby = JeiLobbyPolicy.bind(Core.class);
        for (boolean value : new boolean[]{false, true, false, true}) {
            Core.isLobby = value;
            assertEquals(value, lobby.getAsBoolean());
        }
    }
    @Test void missingCoreFieldIsNotImplicitlyAllowed() {
        assertThrows(NoSuchFieldException.class, () -> JeiLobbyPolicy.bind(Missing.class));
        assertFalse(JeiLobbyPolicy.isPresentation(String.class));
    }
    @Test void jeiConfigIsClientOnlyAndStrict() throws Exception {
        try (var in = getClass().getResourceAsStream("/sre-pack-compat-jei.mixins.json")) {
            assertNotNull(in);
            var config = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(config.has("mixins"));
            assertEquals(5, config.getAsJsonArray("client").size());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        }
    }
}
