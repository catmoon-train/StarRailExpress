package org.agmas.noellesroles.game.scanner;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.player.Player;

/** 随身扫描仪与固定终端共用的阵营权限，客户端显示和服务端操作使用同一规则。 */
public final class IdentityScannerAccess {
    private IdentityScannerAccess() { }

    public static boolean canUse(Player player) {
        var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        return role != null && allowsFaction(role.isNeutrals(), role.isNeutralForKiller(), role.isNeutralForInnocent());
    }

    /** 所有中立职业均禁用，不因其与杀手或乘客一同获胜而放行。 */
    public static boolean allowsFaction(boolean neutral, boolean neutralForKiller, boolean neutralForInnocent) {
        return !neutral && !neutralForKiller && !neutralForInnocent;
    }
}
