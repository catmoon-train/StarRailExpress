package org.agmas.noellesroles.roles.ma_chen_xu;

import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;

/**
 * 马晨絮事件处理器
 */
public class MaChenXuEventHandler {

    /**
     * 注册事件监听器
     */
    public static void register() {
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (SREGameWorldComponent.KEY.get(victim.level()).isRole(victim, ModRoles.MA_CHEN_XU)) {
                if (MaChenXuPlayerComponent.KEY.get(victim).spiritWalkActive) {
                    return false;
                }
            }
            return true;
        });
    }
}