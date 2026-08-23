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

package org.agmas.noellesroles.client;

import net.exmo.sre.repair.network.*;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.client.gui.screen.gamemode.custom_role.CustomRoleSelectScreen;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role_data.killer.ImitatorRoleData;
import net.exmo.sre.repair.role.RepairRoleDefinition;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.role_data.killer.ManipulatorRoleData;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

public class ClientAbilityHandler {
    private static boolean unifiedSkillHeld;
    private static int heldSlot = -1;

    public static void handler(Minecraft client) {

        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(client.player.level());

        // 操纵师附身：技能键改为以被操控目标的身份释放目标自身技能（冷却记在目标身上）
        ManipulatorRoleData manipulatorComp = RoleData.getNullable(ManipulatorRoleData.class, client.player);
        if (manipulatorComp != null && manipulatorComp.isControlling && manipulatorComp.target != null) {
            ClientPlayNetworking.send(new ManipulatorAbilityC2SPacket());
            return;
        }
        // 游戏模式：自选职业
        if (gameWorldComponent.isRunning() && gameWorldComponent.getGameMode().equals(SREGameModes.CUSTOM_SELECTED_MODE)
                && gameWorldComponent.isRole(client.player, SpecialGameModeRoles.CUSTOM_PENDING)) {
            if (!CustomRoleGameModeTeamsPlayerComponent.KEY.get(client.player).selected()) {
                client.execute(() -> {
                    client.setScreen(new CustomRoleSelectScreen(client.player));
                });
            }
            return;
        }
        // 慕恋者持续按键检测（窥视）

        RicesRoleRhapsodyClient.handleAdmirerContinuousInput(client);
        if (client.player == null)
            return;

        var currentRole = gameWorldComponent.getRole(client.player);

        // 模仿者客户端前置逻辑：复制模式无目标→提示，消息技能→打开界面
        if (currentRole != null
                && gameWorldComponent.isRole(client.player, org.agmas.noellesroles.role.ModRoles.IMITATOR)) {
            var comp = RoleData.getNullable(ImitatorRoleData.class, client.player);
            if (comp != null && comp.isCopyMode) {
                var hitResult = client.hitResult;
                if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                    net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                    if (entityHit.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                        // 有目标，继续走统一技能发包（带target）
                        var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player);
                        heldSlot = ability.getSelectedSkill();
                        unifiedSkillHeld = true;
                        ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                                heldSlot, RoleSkill.Phase.PRESS, entityHit.getEntity().getUUID()));
                        return;
                    }
                }
                // 复制模式无目标 → 提示
                client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.noellesroles.imitator.copy_mode_hint").withStyle(net.minecraft.ChatFormatting.YELLOW),
                        true);
                return;
            }
            // 非复制模式：检查当前能力是否是消息技能
            var currentAbility = comp == null ? null : comp.getCurrentAbilityRoleId();
            if (currentAbility != null && org.agmas.noellesroles.game.roles.killer.imitator.ImitatorSkillRegistry
                    .isMessageSkill(currentAbility)) {
                int cd = comp.getCurrentSkillCooldown();
                if (cd > 0) {
                    client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.noellesroles.imitator.cooldown", (cd + 19) / 20)
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }
                if (currentAbility.equals(org.agmas.noellesroles.role.BounsRoles.TELEGRAPHER_ID)) {
                    client.execute(
                            () -> client.setScreen(new org.agmas.noellesroles.client.screen.TelegrapherScreen()));
                } else if (currentAbility.equals(org.agmas.noellesroles.role.ModRoles.BROADCASTER_ID)) {
                    client.execute(
                            () -> client.setScreen(new org.agmas.noellesroles.client.screen.BroadcasterScreen()));
                }
                return;
            }
        }

        if (RoleSkill.hasUnifiedSkills(currentRole)) {
            var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player);
            heldSlot = ability.getSelectedSkill();
            unifiedSkillHeld = true;
            boolean sneaking = client.player.isShiftKeyDown();
            ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                    heldSlot, RoleSkill.Phase.PRESS, findTarget(client), sneaking));
            return;
        }

        boolean repairGameRunning = gameWorldComponent.isRunning()
                && gameWorldComponent.getGameMode() == SREGameModes.REPAIR_ESCAPE_MODE;
        var repairComponent = ModComponents.REPAIR_ROLES.get(client.player);
        if (repairGameRunning && (RepairRoleDefinition.byId(repairComponent.activeRole).isPresent()
                || repairComponent.carriedBy != null
                || repairComponent.carrying != null)) {
            ClientPlayNetworking.send(new RepairPrimarySkillC2SPacket());
            return;
        }

        if (GKeyRoleSkill.trigger(client, gameWorldComponent, true)) {
            return;
        }
        if (RicesRoleRhapsodyClient.onAbilityKeyPressed(client)) {
            return;
        }
        if (GKeyRoleSkill.trigger(client, gameWorldComponent, false)) {
            return;
        }
        ClientPlayNetworking.send(new AbilityC2SPacket());
    }

    public static void tickContinuousInput(Minecraft client) {
        if (client.player == null || client.level == null) {
            unifiedSkillHeld = false;
            heldSlot = -1;
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.level);
        var role = gameWorld.getRole(client.player);

        if (!RoleSkill.hasUnifiedSkills(role)) {
            unifiedSkillHeld = false;
            heldSlot = -1;
            return;
        }

        if (unifiedSkillHeld && NoellesrolesClient.abilityBind.isDown()) {
            ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                    heldSlot, RoleSkill.Phase.HOLD, findTarget(client)));
        } else if (unifiedSkillHeld) {
            ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                    heldSlot, RoleSkill.Phase.RELEASE, findTarget(client)));
            unifiedSkillHeld = false;
            heldSlot = -1;
        }
    }

    public static void selectNextSkill(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        // ==================== 傀儡师：优先检测操控假人状态 ====================
        // 必须放在所有角色之前，因为傀儡师操控假人时角色会临时变成其他杀手
        // 如果不优先检测，假人角色的按键处理会拦截G键
        PuppeteerPlayerComponent puppeteerComp = PuppeteerPlayerComponent.KEY.get(client.player);
        if (puppeteerComp.isControllingPuppet && client.player.isShiftKeyDown()) {
            // 检查玩家是否存活
            if (GameUtils.isPlayerAliveAndSurvival(client.player)) {
                // 正在操控假人，按G返回本体
                ClientPlayNetworking.send(new PuppeteerC2SPacket(PuppeteerC2SPacket.Action.RETURN_TO_BODY));
            }
            return;
        }
        var gameWorld = SREGameWorldComponent.KEY.get(client.level);
        var role = gameWorld.getRole(client.player);

        // 处理统一技能体系中的模式切换角色（会计、小偷、药剂师、建筑师、葬仪、设陷者、模仿者等）
        if (gameWorld.isRole(client.player, ModRoles.WIZARD)) {
            ClientPlayNetworking.send(new WizardSwitchSpellC2SPacket());
            return;
        }

        // 仅显式标记 modeSwitch 的技能才由切换键触发。其余潜行技能（重启、篡夺、领域……）
        // 只能由 潜行+技能键 释放，否则切换键会误放技能。
        var modeSwitch = RoleSkill.getModeSwitchDefinition(role);
        if (modeSwitch.isPresent()) {
            // 服务端按「潜行技能」子列表取槽位，这里也要用同一子列表的下标
            var definitions = RoleSkill.getDefinitions(role);
            var shiftedDefs = definitions.stream().filter(RoleSkill.Definition::shifted).toList();
            ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                    Math.max(0, shiftedDefs.indexOf(modeSwitch.get())),
                    RoleSkill.Phase.PRESS,
                    findTarget(client),
                    true));
            return;
        }

        // 普通多技能切换：不再依赖客户端注册的技能数量（避免客户端配置未同步、
        // 或某个技能模块为空导致 getEffectiveSkills 只返回 1 个时切换键静默失效）。
        // 客户端直接把「当前选中 + 1」发给服务端，由服务端用权威列表取模
        // （RoleSkill.selectSkill 内的 Math.floorMod(slot, definitions.size())）。
        // 服务端只有 1 个技能时取模后仍为原槽位，行为正确；≥2 个时正常循环切换。
        var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player);
        int next = ability.getSelectedSkill() + 1;
        ClientPlayNetworking.send(new org.agmas.noellesroles.packet.UnifiedSkillSelectC2SPacket(next));
    }

    private static UUID findTarget(Minecraft client) {
        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof net.minecraft.world.entity.player.Player target) {
            return target.getUUID();
        }
        return null;
    }
}
