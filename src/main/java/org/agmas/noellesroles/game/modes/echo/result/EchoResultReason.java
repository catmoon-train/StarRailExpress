package org.agmas.noellesroles.game.modes.echo.result;

import java.util.Locale;
import java.util.Set;

/** 玩家可读原因与内部诊断码分离；未知原因仍在日志和结算报告保留原码。 */
public final class EchoResultReason {
    private static final Set<String> KNOWN=Set.of("ABORTED","ESCAPE_QUOTA","INSUFFICIENT_CARRIERS","NO_INTERCEPTOR",
            "TIME_EXPIRED","ESCROW_FAILURE","MAP_CHANGED","SESSION_ENDED","RECONNECT_FAILURE","WORLD_UNLOADED",
            "OBJECTIVE_FAILURE","ADMIN_ABORT","DEBUG_RESULT","EXTERNAL_STOP","MISSING_SESSION",
            "ESCAPE_COMPLETED","NO_CARRIERS_LEFT");
    private EchoResultReason() { }
    public static String translationKey(String code) {
        return "echo.runtime.reason."+(KNOWN.contains(code==null?"":code)?code.toLowerCase(Locale.ROOT):"unknown");
    }
}
