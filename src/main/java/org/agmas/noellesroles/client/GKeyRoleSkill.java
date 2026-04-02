package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.screen.BroadcasterScreen;
import org.agmas.noellesroles.client.screen.TelegrapherScreen;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.AbilityC2SPacket;
import org.agmas.noellesroles.packet.AbilityWithTargetC2SPacket;
import org.agmas.noellesroles.packet.VultureEatC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.HashMap;
import java.util.Map;

public final class GKeyRoleSkill {
    private static final Map<ResourceLocation, GKeyRoleSkill> REGISTERED_SKILLS = new HashMap<>();

    private final boolean beforeRhapsody;
    private final Handler handler;

    static {
        registerDefaults();
    }

    private GKeyRoleSkill(boolean beforeRhapsody, Handler handler) {
        this.beforeRhapsody = beforeRhapsody;
        this.handler = handler;
    }

    public static void register(SRERole role, boolean beforeRhapsody, Handler handler) {
        if (role == null || handler == null) {
            return;
        }
        REGISTERED_SKILLS.put(role.identifier(), new GKeyRoleSkill(beforeRhapsody, handler));
    }

    public static boolean trigger(Minecraft client, SREGameWorldComponent gameWorldComponent, boolean beforeRhapsody) {
        if (client.player == null) {
            return false;
        }
        SRERole role = gameWorldComponent.getRole(client.player);
        if (role == null) {
            return false;
        }
        GKeyRoleSkill skill = REGISTERED_SKILLS.get(role.identifier());
        if (skill == null || skill.beforeRhapsody != beforeRhapsody) {
            return false;
        }
        return skill.handler.handle(client, gameWorldComponent);
    }

    private static void registerDefaults() {
        register(ModRoles.BOMBER, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new AbilityC2SPacket());
            return true;
        });
        register(ModRoles.FORTUNETELLER, true, (client, gameWorld) -> {
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                }
            } else {
                client.player.displayClientMessage(Component.translatable("hud.fortuneteller.target_miss"), true);
            }
            return true;
        });
        register(ModRoles.NIAN_SHOU, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new AbilityC2SPacket());
            return true;
        });
        register(ModRoles.GLITCH_ROBOT, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            if (!client.player.getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES)) {
                client.player.displayClientMessage(
                        Component.translatable("info.glitch_robot.noglasses_on_head").withStyle(ChatFormatting.RED),
                        true);
                return true;
            }
            if (!RoleUtils.isPlayerHasFreeSlot(client.player)) {
                client.player.displayClientMessage(
                        Component.translatable("message.hotbar.full").withStyle(ChatFormatting.RED),
                        true);
                return true;
            }
            ClientPlayNetworking.send(new AbilityC2SPacket());
            return true;
        });
        register(ModRoles.NOISEMAKER, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new AbilityC2SPacket());
            return true;
        });
        register(ModRoles.VULTURE, false, (client, gameWorld) -> {
            if (NoellesrolesClient.targetBody == null) {
                return true;
            }
            ClientPlayNetworking.send(new VultureEatC2SPacket(NoellesrolesClient.targetBody.getUUID()));
            return true;
        });
        register(ModRoles.BROADCASTER, false, (client, gameWorld) -> {
            if (!NoellesrolesClient.isPlayerInAdventureMode(client.player)) {
                return true;
            }
            client.execute(() -> client.setScreen(new BroadcasterScreen()));
            return true;
        });

        register(ModRoles.TELEGRAPHER, false, (client, gameWorld) -> {
            if (!NoellesrolesClient.isPlayerInAdventureMode(client.player)) {
                return true;
            }
            client.execute(() -> client.setScreen(new TelegrapherScreen()));
            return true;
        });
    }

    @FunctionalInterface
    public interface Handler {
        boolean handle(Minecraft client, SREGameWorldComponent gameWorldComponent);
    }
}
