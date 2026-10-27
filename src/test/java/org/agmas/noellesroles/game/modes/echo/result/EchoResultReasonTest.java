package org.agmas.noellesroles.game.modes.echo.result;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EchoResultReasonTest {
    @Test void everySettlementCodeHasThreeReadableTranslations() throws Exception {
        for(String lang:new String[]{"zh_cn","zh_tw","en_us"}) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/echo_runtime/lang/"+lang+".json"))).getAsJsonObject();
            for(String code:new String[]{"ABORTED","ESCAPE_QUOTA","INSUFFICIENT_CARRIERS","NO_INTERCEPTOR","TIME_EXPIRED","ESCAPE_COMPLETED","NO_CARRIERS_LEFT",
                    "ESCROW_FAILURE","MAP_CHANGED","SESSION_ENDED","RECONNECT_FAILURE","WORLD_UNLOADED","OBJECTIVE_FAILURE",
                    "ADMIN_ABORT","DEBUG_RESULT","EXTERNAL_STOP","MISSING_SESSION","NEW_REASON"}) {
                String key=EchoResultReason.translationKey(code);
                assertTrue(json.has(key),lang+":"+key);
                assertFalse(json.get(key).getAsString().isBlank());
                assertNotEquals(code,json.get(key).getAsString());
            }
        }
        assertEquals("echo.runtime.reason.unknown",EchoResultReason.translationKey(null));
    }
}
