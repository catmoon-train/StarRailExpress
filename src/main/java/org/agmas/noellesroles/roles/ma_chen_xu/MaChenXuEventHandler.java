package org.agmas.noellesroles.roles.ma_chen_xu;

import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 布袋鬼（诡舍·缚灵）事件处理器
 */
public class MaChenXuEventHandler {

    /**
     * 注册事件监听器
     */
    public static void register() {
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (SREGameWorldComponent.KEY.get(victim.level()).isRole(victim, ModRoles.MA_CHEN_XU)) {
                var compc = MaChenXuPlayerComponent.KEY.get(victim);
                // 永久护盾（阶段4获得，一次性抵挡致命伤害）
                if (compc.permanentShield) {
                    compc.permanentShield = false;
                    compc.sync();
                    SRE.REPLAY_MANAGER.breakArmor(victim.getUUID());
                    victim.displayClientMessage(Component.translatable("message.noellesroles.ma_chen_xu.trigger_shield")
                            .withStyle(ChatFormatting.GOLD), true);
                    return false;
                }
                // 里世界中布袋鬼无敌
                if (compc.otherworldActive) {
                    return false;
                }
            }
            return true;
        });
    }
}