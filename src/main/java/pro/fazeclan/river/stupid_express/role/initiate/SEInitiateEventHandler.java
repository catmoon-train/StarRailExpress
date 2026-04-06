package pro.fazeclan.river.stupid_express.role.initiate;

import java.util.ArrayList;
import java.util.Collections;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public class SEInitiateEventHandler {

    public static void register() {
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            // 初学杀初学
            if (handleInitiateKillInitiate(victim, killer, deathReason)) {
                return;
            }
            // 初学杀错人
            if (handleInitiateKillWrongPlayers(victim, killer, deathReason)) {
                return;
            }
            // 初学被杀
            if (handleInitiateDeadByOtherRolesPlayers(victim, killer, deathReason)) {
                return;
            }
        });
    }
    

    private static boolean handleInitiateDeadByOtherRolesPlayers(Player victim, Player killer,
            ResourceLocation deathReason) {
        var level = (ServerLevel) victim.level();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        if (!gameWorldComponent.isRole(victim, SERoles.INITIATE))
            return false;
        if (!gameWorldComponent.isSkillAvailable) {
            victim.displayClientMessage(
                    Component.translatable("message.stupid_express.generic.skill_not_available"), true);
            return false;
        }
        SRERole newInitiateRole;

        if (killer == null) {
            newInitiateRole = SERoles.AMNESIAC;
        } else if (gameWorldComponent.isRole(killer, SERoles.INITIATE)) {
            return false;
        } else {
            // 初学者被杀死（包括被炸弹炸死、摔死等非玩家攻击，以及被非初学者玩家杀死）
            SRERole killerRole = gameWorldComponent.getRole(killer);
            if (killerRole == null) {
                newInitiateRole = SERoles.AMNESIAC;
            } else if (gameWorldComponent.isKillerTeamRole(killerRole)) {
                // 狼杀死
                var shuffledKillerRoles = new ArrayList<>(StupidExpress.getEnableRoles());
                shuffledKillerRoles.removeIf(role -> {
                    if (role.identifier().getPath().equals("jojo"))
                        return true;
                    if (gameWorldComponent.isKillerTeamRole(role))
                        return true;
                    if (role.isNeutrals())
                        return true;
                    if (role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "magician")))
                        return true;
                    if (role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "doctor")))
                        return true;
                    if (role.identifier()
                            .equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "best_vigilante")))
                        return true;
                    if (role.identifier()
                            .equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "better_vigilante")))
                        return true;
                    return false;
                });
                if (shuffledKillerRoles.isEmpty())
                    shuffledKillerRoles.add(TMMRoles.CIVILIAN);
                Collections.shuffle(shuffledKillerRoles);

                newInitiateRole = shuffledKillerRoles.getFirst();
            } else if (killerRole.isNeutrals()) {
                // 中立杀死
                var shuffledKillerRoles = new ArrayList<>(StupidExpress.getEnableRoles());
                shuffledKillerRoles.removeIf(role -> {
                    if (role.isNeutralForKiller())
                        return true;
                    if (role.isNeutrals())
                        return false;
                    return true;
                });
                if (shuffledKillerRoles.isEmpty())
                    shuffledKillerRoles.add(SERoles.AMNESIAC);
                Collections.shuffle(shuffledKillerRoles);
                newInitiateRole = shuffledKillerRoles.getFirst();
            } else {
                // 好人杀死
                var shuffledKillerRoles = new ArrayList<>(StupidExpress.getEnableRoles());
                shuffledKillerRoles.removeIf(role -> {
                    if (role.identifier().getPath().equals("dio"))
                        return true;
                    if (role.identifier()
                            .equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "water_ghost")))
                        return true;
                    if (!gameWorldComponent.isKillerTeamRole(role))
                        return true;
                    if (role.isNeutrals())
                        return true;
                    if (role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "magician")))
                        return true;
                    if (role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "poisoner")))
                        return true;
                    if (role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "doctor")))
                        return true;
                    if (role.identifier()
                            .equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "best_vigilante")))
                        return true;
                    if (role.identifier()
                            .equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "better_vigilante")))
                        return true;
                    return false;
                });
                if (shuffledKillerRoles.isEmpty())
                    shuffledKillerRoles.add(TMMRoles.KILLER);
                Collections.shuffle(shuffledKillerRoles);

                newInitiateRole = shuffledKillerRoles.getFirst();

            }
        }
        for (ServerPlayer player : level.getPlayers(p -> gameWorldComponent.isRole(p, SERoles.INITIATE))) {
            // 清除物品栏中的所有刀
            clearAllKnives(player);
            // StupidExpress.LOGGER.info(player.getDisplayName().getString());
            StupidRoleUtils.changeRole(player, newInitiateRole);
            // 播放全场音效
            player.level().playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.CONDUIT_ATTACK_TARGET,
                    net.minecraft.sounds.SoundSource.MASTER, 5.0F, 1.0F);
            StupidRoleUtils.sendWelcomeAnnouncement(player);
        }
        return true;
    }

    private static void clearAllKnives(Player player) {
        SREItemUtils.clearItem(player, TMMItems.KNIFE);
    }

    public static boolean handleInitiateKillInitiate(Player victim, Player killer, ResourceLocation deathReason) {
        var level = (ServerLevel) victim.level();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);

        if (!gameWorldComponent.isRole(victim, SERoles.INITIATE)) {
            return false;
        }
        if (killer == null)
            return false;
        if (gameWorldComponent.isRole(killer, SERoles.INITIATE)) {
            var shuffledKillerRoles = new ArrayList<>(StupidExpress.getEnableRoles());
            shuffledKillerRoles.removeIf(role -> Harpymodloader.VANNILA_ROLES.contains(role) || !role.canUseKiller()
                    || HarpyModLoaderConfig.HANDLER.instance().getDisabled().contains(role.identifier().getPath())
                    || role.identifier()
                            .equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "water_ghost"))
                    || role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "poisoner"))
                    || role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "magician"))
                    || role.identifier().getPath().equals("dio")
                    || role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "doctor"))
                    || role.identifier()
                            .equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "best_vigilante"))
                    || role.identifier()
                            .equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "better_vigilante")));
            if (shuffledKillerRoles.isEmpty())
                shuffledKillerRoles.add(TMMRoles.KILLER);
            Collections.shuffle(shuffledKillerRoles);

            var role = shuffledKillerRoles.getFirst();

            // 清除物品栏中的所有刀
            clearAllKnives(killer);

            StupidRoleUtils.changeRole(killer, role, true);
            StupidRoleUtils.changeRole(victim, SERoles.AMNESIAC, true);
            SREPlayerShopComponent.KEY.get(killer).addToBalance(100);
            StupidRoleUtils.sendWelcomeAnnouncement((ServerPlayer) killer);
            return true;
        }
        return false;
    }
    private static boolean handleInitiateKillWrongPlayers(Player victim, Player killer,
            ResourceLocation deathReason) {

        var level = (ServerLevel) victim.level();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        if (killer == null)
            return false;
        if (!gameWorldComponent.isRole(killer, SERoles.INITIATE))
            return false;
        if (!gameWorldComponent.isRole(victim, SERoles.INITIATE)) {
            SRERole newInitiateRole;
            newInitiateRole = SERoles.AMNESIAC;

            // 技能可用
            for (ServerPlayer player : level.getPlayers(p -> gameWorldComponent.isRole(p, SERoles.INITIATE))) {
                // 清除物品栏中的所有刀
                clearAllKnives(player);
                if (gameWorldComponent.isSkillAvailable) {
                    StupidRoleUtils.changeRole(player, newInitiateRole, true);
                    // 播放全场音效
                    player.level().playSound(null, player.blockPosition(),
                            net.minecraft.sounds.SoundEvents.CONDUIT_ATTACK_TARGET,
                            net.minecraft.sounds.SoundSource.MASTER, 5.0F, 1.0F);
                    StupidRoleUtils.sendWelcomeAnnouncement((ServerPlayer) killer);
                }
            }

            GameUtils.killPlayer(killer, true, null, StupidExpress.id("failed_initiation"));
            return true;
        }
        return false;
    }
}
