package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;
import java.util.HashMap;
import java.util.Map;

/** 回声机器复用既有任务点编号、同步包与筛选器，不公开任何私人资源状态。 */
public final class EchoMachineTasks {
    public static final int TYPE = 26;
    private EchoMachineTasks() { }
    public static boolean anchorPart(int part) { return part == 0; }
    public static int color(EchoMachines.Kind kind) {
        if(kind==null) return 0xD4AF37;
        return switch(kind) {
            case BEACON, CRYSTAL -> 0x5EB7D8;
            case CORE_BEACON, ALTAR, BROKER_EXIT -> 0xD4AF37;
            case BLACK_NODE, BLACK_DROPOFF, BLACK_MARKET -> 0xBA80E8;
            case FREE_EXIT -> 0x72C17B;
            case EVACUATION_CONSOLE -> 0xF0AA55;
            case SUPPLY, CORE_CONSOLE -> 0xC8B898;
        };
    }
    /** 回声局使用当前配置主点，避免旧扫描缓存漏掉新机器；其它模式完全沿用原数据。 */
    public static HashMap<BlockPos,Integer> snapshot(ServerLevel level,Map<BlockPos,Integer> fallback) {
        var match=EchoSessions.get(level);
        return match!=null && match.result()==null ? match.objectives().taskPoints() : new HashMap<>(fallback);
    }
}
