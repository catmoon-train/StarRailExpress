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
import org.agmas.noellesroles.packet.VeteranDashC2SPacket;
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
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                }
            } else {
                client.player.displayClientMessage(
                        Component.translatable("message.noellesroles.nianshou.no_target")
                                .withStyle(ChatFormatting.RED), true);
            }
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
        register(ModRoles.CREEPER, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            ClientPlayNetworking.send(new org.agmas.noellesroles.packet.CreeperAbilityC2SPacket());
            return true;
        });
        register(ModRoles.VETERAN, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            ClientPlayNetworking.send(new VeteranDashC2SPacket());
            return true;
        });
        // 交换者：按技能键与正前方目标瞬移交换位置
        register(ModRoles.SWAPPER, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            ClientPlayNetworking.send(new org.agmas.noellesroles.packet.SwapperFrontSwapC2SPacket());
            return true;
        });
        register(ModRoles.VULTURE, false, (client, gameWorld) -> {
            if (NoellesrolesClient.targetBody == null) {
                return true;
            }
            ClientPlayNetworking.send(new VultureEatC2SPacket(NoellesrolesClient.targetBody.getUUID()));
            return true;
        });
        register(ModRoles.PELICAN, true, (client, gameWorld) -> {
            // 蹲下释放，否则对鼠标准星目标吞噬
            if (client.player.isShiftKeyDown()) {
                ClientPlayNetworking.send(new AbilityC2SPacket());
                return true;
            }
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                    return true;
                }
            }
            client.player.displayClientMessage(Component.translatable("message.noellesroles.pelican.no_target").withStyle(ChatFormatting.RED), true);
            return true;
        });
        register(ModRoles.GODFATHER, true, (client, gameWorld) -> {
            var comp = org.agmas.noellesroles.game.roles.neutral.mafia.GodfatherComponent.KEY.maybeGet(client.player).orElse(null);
            if (comp != null && client.player != null && client.player.level() != null) {
                long now = client.player.level().getGameTime();
                if (comp.recruitCooldownUntil > 0 && now < comp.recruitCooldownUntil) {
                    long remaining = (comp.recruitCooldownUntil - now) / 20 + 1;
                    client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.noellesroles.godfather.cooldown", remaining), true);
                    return true;
                }
            }
            client.execute(() -> client.setScreen(new org.agmas.noellesroles.client.screen.GodfatherRecruitScreen()));
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

        // 疫使：瞄准玩家时按技能键感染目标
        register(ModRoles.INFECTED, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return true;
            }
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                }
            } else {
                client.player.displayClientMessage(Component.translatable("hud.infected.target_miss"), true);
            }
            return true;
        });

        // 咒法师：按技能键标记目标，蹲下按技能键触发咒杀
        register(ModRoles.WARLOCK, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) return true;
            // 蹲下 = 咒杀
            if (client.player.isShiftKeyDown()) {
                ClientPlayNetworking.send(new org.agmas.noellesroles.packet.WarlockKillC2SPacket());
                return true;
            }
            // 普通 = 标记目标
            var hitResult = client.hitResult;
            if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.phys.EntityHitResult e = (net.minecraft.world.phys.EntityHitResult) hitResult;
                if (e.getEntity() instanceof Player targetPlayer) {
                    ClientPlayNetworking.send(new AbilityWithTargetC2SPacket(targetPlayer));
                }
            } else {
                client.player.displayClientMessage(Component.translatable("hud.warlock.target_miss"), true);
            }
            return true;
        });

        // 嬉命人：按技能键发动变装（冷却80秒）
        register(ModRoles.EMBALMER, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) return true;
            ClientPlayNetworking.send(new org.agmas.noellesroles.packet.EmbalmerC2SPacket());
            return true;
        });

        // 窃皮者：按技能键偷皮
        register(ModRoles.SKINCRAWLER, true, (client, gameWorld) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) return true;
            ClientPlayNetworking.send(new org.agmas.noellesroles.packet.SkincrawlerC2SPacket());
            return true;
        });
    }

    @FunctionalInterface
    public interface Handler {
        boolean handle(Minecraft client, SREGameWorldComponent gameWorldComponent);
    }
}
