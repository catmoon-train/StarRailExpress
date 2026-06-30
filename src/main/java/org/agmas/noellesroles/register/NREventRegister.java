package org.agmas.noellesroles.register;

import org.agmas.noellesroles.handler.AAAHandlerFather;
import org.agmas.noellesroles.init.ModEventsRegister;

/**
 * Noellesroles 世界系统与事件处理器注册，
 * 从 {@link org.agmas.noellesroles.Noellesroles#onInitialize()} 中按类别剥离归一化而来。
 */
public class NREventRegister {

    public static void registerWorldSystemsAndEvents() {
        // 注册C4系统
        org.agmas.noellesroles.game.c4.C4Detonation.register();
        org.agmas.noellesroles.game.c4.PliersDefuseManager.register();
        // 注册鹈鹕系统
        org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.register();
        // 注册 Mafia 系统
        org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.register();

        // 注册事件处理器
        ModEventsRegister.registerEvents();
        AAAHandlerFather.register();
        // 
        org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaEventHandler.register();
        org.agmas.noellesroles.game.roles.neutral.amon.AmonEventHandler.register();
        org.agmas.noellesroles.game.modes.repair.RepairCombatEvents.register();
        org.agmas.noellesroles.game.modes.repair.RepairWorldInteractions.register();

        // 注册疫使胜利检测
        org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.registerEvent();
    }
}
