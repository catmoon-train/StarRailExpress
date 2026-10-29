package org.agmas.noellesroles.game.modes.echo.client;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EchoHunterFogClientTest {
    @Test void onlyCurrentLivingInterceptorGetsTwelveBlockRadius() {
        assertEquals(12F,EchoHunterFogClient.RADIUS);
        for(var life:EchoLifeState.values()) {
            assertEquals(life.standing(),EchoHunterFogClient.applies(true,false,EchoFaction.INTERCEPTOR,life));
        }
        assertFalse(EchoHunterFogClient.applies(true,false,EchoFaction.CARRIER,EchoLifeState.HEALTHY));
        assertFalse(EchoHunterFogClient.applies(true,false,EchoFaction.BROKER,EchoLifeState.HEALTHY));
        assertFalse(EchoHunterFogClient.applies(true,true,EchoFaction.INTERCEPTOR,EchoLifeState.HEALTHY));
        assertFalse(EchoHunterFogClient.applies(false,false,EchoFaction.INTERCEPTOR,EchoLifeState.HEALTHY));
        assertFalse(EchoHunterFogClient.applies(true,false,null,EchoLifeState.HEALTHY));
        assertFalse(EchoHunterFogClient.applies(true,false,EchoFaction.INTERCEPTOR,null));
    }

    @Test void strongerVisionFogWinsAndWeakerFogCannotExtendRadius() {
        assertEquals(2F,EchoHunterFogClient.visionEnd(12F,2F));
        assertEquals(8F,EchoHunterFogClient.visionEnd(12F,8F));
        assertEquals(12F,EchoHunterFogClient.visionEnd(12F,14F));
        assertEquals(12F,EchoHunterFogClient.visionEnd(12F,100F));
    }

    @Test void weaponCooldownTextExistsInIsolatedThreeLanguages() throws Exception {
        for(String language:List.of("zh_cn","zh_tw","en_us")) {
            var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/echo_hunter_balance/lang/"+language+".json"),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(1,json.size());
            assertTrue(json.get("echo.hunter_balance.weapon_cooldown").getAsString().contains("%s"));
        }
    }
}
