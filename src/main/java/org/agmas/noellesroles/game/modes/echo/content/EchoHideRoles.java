package org.agmas.noellesroles.game.modes.echo.content;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.modes.echo.EchoRoles;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;

/**
 * 回声阵营查询的窄封装：服务端和客户端共用同一条判定，避免两套逻辑漂移。
 *
 * <p>职业归属由 {@link SREGameWorldComponent} 持有，通过 {@code getRole(player)} 读取；
 * 这里只做「是不是某个回声身份」的等价比较，不缓存、不写状态。
 */
public final class EchoHideRoles {
    private EchoHideRoles() { }

    /** 该玩家本局是否是截流者；未分配身份或身份未注册时返回 false。 */
    public static boolean isInterceptor(Player player) {
        return hasRole(player, EchoFaction.INTERCEPTOR);
    }

    /** 该玩家本局是否是托运者。 */
    public static boolean isCarrier(Player player) {
        return hasRole(player, EchoFaction.CARRIER);
    }

    /** 该玩家是否属于回声对局的任意阵营（用于避免影响其它模式的隐身逻辑）。 */
    public static boolean isEchoPlayer(Player player) {
        return hasRole(player, EchoFaction.INTERCEPTOR)
                || hasRole(player, EchoFaction.CARRIER)
                || hasRole(player, EchoFaction.BROKER);
    }

    private static boolean hasRole(Player player, EchoFaction faction) {
        if (player == null || player.level() == null) return false;
        try {
            var game = SREGameWorldComponent.KEY.get(player.level());
            if (game == null) return false;
            var actual = game.getRole(player);
            return actual != null && actual == EchoRoles.get(faction);
        } catch (RuntimeException unresolved) {
            // 身份尚未注册或组件不可用时保守地按「不是该阵营」处理。
            return false;
        }
    }
}
