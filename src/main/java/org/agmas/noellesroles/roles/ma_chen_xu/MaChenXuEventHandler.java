package org.agmas.noellesroles.roles.ma_chen_xu;

import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

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
                if (compc.shieldDuration > 0) {
                    compc.shieldDuration = 0;
                    compc.sync();
                    SRE.REPLAY_MANAGER.breakArmor(victim.getUUID());
                    victim.displayClientMessage(Component.translatable("message.noellesroles.ma_chen_xu.trigger_shield")
                            .withStyle(ChatFormatting.GOLD), true);
                    return false;
                }
                if (compc.spiritWalkDuration > 0) {
                    return false;
                }
            }
            return true;
        });
    }
}