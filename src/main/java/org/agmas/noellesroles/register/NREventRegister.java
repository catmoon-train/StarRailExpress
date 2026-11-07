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
        org.agmas.noellesroles.init.events.GameUtilityRoleHooks.register();
        org.agmas.noellesroles.init.events.ShopRoleEventHandlers.register();
        org.agmas.noellesroles.init.events.CoreGameplayRoleEvents.register();
        // 注册C4系统
        org.agmas.noellesroles.game.c4.C4Detonation.register();
        org.agmas.noellesroles.game.c4.PliersDefuseManager.register();
        // 注册鹈鹕系统
        org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.register();
        // 注册 Mafia 系统
        org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.register();

        // 注册事件处理器
        ModEventsRegister.registerEvents();
        org.agmas.noellesroles.content.item.SealedArtifactHandler.register();
        org.agmas.noellesroles.game.roles.neutral.lender.LenderRoleHandler.register();
        org.agmas.noellesroles.game.roles.innocence.insurance.InsuranceRoleHandler.register();
        AAAHandlerFather.register();
        // 
        org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaEventHandler.register();
        org.agmas.noellesroles.game.roles.neutral.amon.AmonEventHandler.register();
        org.agmas.noellesroles.game.roles.neutral.leader.LeaderEventHandler.register();
        // 雪傀儡：伪装、融化被动、雪球碰撞与开局 / 局末清场
        org.agmas.noellesroles.game.roles.neutral.snow_golem.SnowGolemRole.registerEvents();
        org.agmas.noellesroles.game.roles.killer.clone_doctor.CloneDoctorRole.registerEvents();
        org.agmas.noellesroles.game.scanner.IdentityScannerService.registerEvents();

        // 注册入梦药水效果处理
        org.agmas.noellesroles.content.effects.DreamEffectHandler.register();

        // 注册疫使胜利检测
        org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.registerEvent();
        org.agmas.noellesroles.game.roles.neutral.priest.PriestEventHandler.register();
        org.agmas.noellesroles.game.fake_steve.FakeSteveDirector.register();
        org.agmas.noellesroles.game.command_block.CommandBlockDirector.register();
        org.agmas.noellesroles.game.night.NightEvent.register();
        org.agmas.noellesroles.game.MirrorReunionEndEgg.register();
        org.agmas.noellesroles.content.speaker.SpeakerPlayback.register();
    }
}
