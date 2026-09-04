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

package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.game.GameConstants;
import org.agmas.noellesroles.AbilityHandler;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;

/**
 * RoleSkill registrations for abilities previously dispatched by AbilityHandler if-chains.
 */
public final class AbilitySkillRegister {
    private AbilitySkillRegister() {
    }

    public static void register() {
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();

        RoleSkill.register(THRedHouseRoles.HOAN_MEIRIN, RoleSkill.skill(
                SRE.id("hoan_meirin_levitation"),
                "skill.hoan_meirin.levitation",
                context -> AbilityHandler.hoanMeirin(context.player()))
                .toggleable(true).announceToSelf(false).build());

        RoleSkill.register(ModRoles.EXAMPLER, RoleSkill.skill(
                SRE.id("exampler_quiz"),
                "skill.noellesroles.exampler.quiz",
                context -> {
                    if (context.target() != null) {
                        return AbilityHandler.examplerAssign(context.player(), context.target());
                    }
                    return AbilityHandler.examplerBroadcast(context.player());
                }).cooldownSeconds(180).announceToSelf(false).build());

        RoleSkill.register(ModRoles.GLITCH_ROBOT, RoleSkill.skill(
                SRE.id("glitch_robot_glasses"),
                "skill.noellesroles.glitch_robot.glasses",
                context -> AbilityHandler.glitchRobot(context.player()))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.DIVER, RoleSkill.skill(
                SRE.id("diver_unequip"),
                "skill.noellesroles.diver.unequip",
                context -> AbilityHandler.diver(context.player()))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.LEON, RoleSkill.skill(
                SRE.id("leon_kick"),
                "skill.noellesroles.leon.kick",
                context -> AbilityHandler.leonKick(context.player()))
                .cooldownTicks(GameConstants.getInTicks(0, cfg.leonKickCooldown))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.MORPHLING, RoleSkill.skill(
                SRE.id("morphling_dummy"),
                "skill.noellesroles.morphling.dummy",
                context -> AbilityHandler.morphlingDummy(context.player()))
                .cooldownTicks(GameConstants.getInTicks(0, cfg.morphlingDummyCooldown))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.RECALLER, RoleSkill.skill(
                SRE.id("recaller_mark"),
                "skill.noellesroles.recaller.mark",
                context -> AbilityHandler.recaller(context.player(), context))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.JADE_GENERAL, RoleSkill.skill(
                SRE.id("jade_general_skill"),
                "skill.noellesroles.jade_general.skill",
                context -> AbilityHandler.jadeGeneral(context.player()))
                .cooldownTicks(GameConstants.getInTicks(0, 35))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.GHOST_EYE, RoleSkill.skill(
                SRE.id("ghost_eye_domain"),
                "skill.noellesroles.ghost_eye.domain",
                context -> AbilityHandler.ghostEye(context.player()))
                .cooldownTicks(GameConstants.getInTicks(0, cfg.ghostEyeDomainCooldown))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.WIZARD, RoleSkill.skill(
                SRE.id("wizard_spell"),
                "skill.noellesroles.wizard.spell",
                context -> AbilityHandler.wizard(context.player()))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.RAVEN, RoleSkill.skill(
                SRE.id("raven_hunt"),
                "skill.noellesroles.raven.hunt",
                context -> AbilityHandler.raven(context.player()))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.CAKE_MAKER, RoleSkill.skill(
                SRE.id("cake_maker_smoker"),
                "skill.noellesroles.cake_maker.smoker",
                context -> AbilityHandler.cakeMaker(context.player()))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.ADVENTURER, RoleSkill.skill(
                SRE.id("adventurer_waypoint"),
                "skill.noellesroles.adventurer.waypoint",
                context -> AbilityHandler.adventurer(context.player()))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.OLDMAN, RoleSkill.skill(
                SRE.id("oldman_wheelchair"),
                "skill.noellesroles.oldman.wheelchair",
                context -> AbilityHandler.oldman(context.player()))
                .announceToSelf(false).build());

        RoleSkill.register(ModRoles.NOSTALGIST, RoleSkill.skill(
                SRE.id("nostalgist_collapse"),
                "skill.noellesroles.nostalgist.collapse",
                context -> AbilityHandler.nostalgist(context.player()))
                .announceToSelf(false).build());

        RoleSkill.register(THMiscRoles.DOREMY, RoleSkill.skill(
                SRE.id("doremy_dream"),
                "skill.noellesroles.doremy.dream",
                context -> AbilityHandler.doremyDream(context.player(), context.target()))
                .withTarget().announceToSelf(false).build());
    }
}
