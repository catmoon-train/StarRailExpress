package org.agmas.noellesroles.client;

import org.agmas.noellesroles.client.screen.BroadcasterScreen;
import org.agmas.noellesroles.client.screen.TelegrapherScreen;
import org.agmas.noellesroles.packet.AbilityC2SPacket;
import org.agmas.noellesroles.packet.AbilityWithTargetC2SPacket;
import org.agmas.noellesroles.packet.VultureEatC2SPacket;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ClientAbilityHandler {

    public static void handler(Minecraft client) {
        // 慕恋者持续按键检测（窥视）
        RicesRoleRhapsodyClient.handleAdmirerContinuousInput(client);
        if (Minecraft.getInstance().player == null)
            return;

        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY
                .get(Minecraft.getInstance().player.level());

        // 优先处理炸弹客，避免被 onAbilityKeyPressed 干扰
        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.BOMBER)) {
            ClientPlayNetworking.send(new AbilityC2SPacket());
            return;
        }
        if (gameWorldComponent.isRole(client.player, ModRoles.FORTUNETELLER)) {
            var hitResult = client.hitResult;
            if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                }
            } else {
                client.player.displayClientMessage(Component.translatable("hud.fortuneteller.target_miss"), true);
            }
            return;
        }
        if (RicesRoleRhapsodyClient.onAbilityKeyPressed(client)) {
            return;
        }

        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.VULTURE)) {
            if (NoellesrolesClient.targetBody == null)
                return;
            ClientPlayNetworking.send(new VultureEatC2SPacket(NoellesrolesClient.targetBody.getUUID()));
            return;
        } else if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.BROADCASTER)) {
            if (!NoellesrolesClient.isPlayerInAdventureMode(client.player))
                return;
            client.execute(() -> {
                client.setScreen(new BroadcasterScreen());
            });
            return;
        } else if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.TELEGRAPHER)) {
            if (!NoellesrolesClient.isPlayerInAdventureMode(client.player))
                return;
            client.execute(() -> {
                client.setScreen(new TelegrapherScreen());
            });
            return;
        }
        ClientPlayNetworking.send(new AbilityC2SPacket());
    }

}
