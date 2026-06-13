package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.client.gui.screen.gamemode.custom_role.CustomRoleSelectScreen;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;
import org.agmas.noellesroles.packet.AbilityC2SPacket;
import org.agmas.noellesroles.packet.RepairPrimarySkillC2SPacket;
import org.agmas.noellesroles.packet.UnifiedSkillInputC2SPacket;

import java.util.UUID;

public class ClientAbilityHandler {
    private static boolean unifiedSkillHeld;
    private static int heldSlot = -1;

    public static void handler(Minecraft client) {

        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(client.player.level());
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
        if (RoleSkill.hasUnifiedSkills(currentRole)) {
            var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player);
            heldSlot = ability.getSelectedSkill();
            unifiedSkillHeld = true;
            ClientPlayNetworking.send(new UnifiedSkillInputC2SPacket(
                    heldSlot, RoleSkill.Phase.PRESS, findTarget(client)));
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
        var gameWorld = SREGameWorldComponent.KEY.get(client.level);
        var role = gameWorld.getRole(client.player);
        var definitions = RoleSkill.getDefinitions(role);
        if (definitions.size() < 2) {
            return;
        }
        var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player);
        int next = (ability.getSelectedSkill() + 1) % definitions.size();
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
