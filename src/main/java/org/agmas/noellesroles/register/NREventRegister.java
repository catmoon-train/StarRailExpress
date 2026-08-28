/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        org.agmas.noellesroles.game.roles.neutral.leader.LeaderEventHandler.register();
        net.exmo.sre.repair.event.RepairCombatEvents.register();
        net.exmo.sre.repair.event.RepairWorldInteractions.register();

        // 注册疫使胜利检测
        org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.registerEvent();
        org.agmas.noellesroles.game.fake_steve.FakeSteveDirector.register();
    }
}
