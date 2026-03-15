package org.agmas.noellesroles.roles.ma_chen_xu;

import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;

/**
 * 布袋鬼事件处理器
 */
public class MaChenXuEventHandler {

    /**
     * 注册事件监听器
     */
    public static void register() {
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (SREGameWorldComponent.KEY.get(victim.level()).isRole(victim, ModRoles.MA_CHEN_XU)) {
                var compc = MaChenXuPlayerComponent.KEY.get(victim);
                if(compc.shieldActive){
                    compc.shieldActive = false;
                    compc.sync();
                    return false;
                }
                if (compc.otherworldActive) {
                    return false;
                }
            }
            return true;
        });
    }
}