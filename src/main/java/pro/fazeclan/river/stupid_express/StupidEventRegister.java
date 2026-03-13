package pro.fazeclan.river.stupid_express;

import java.util.ArrayList;
import java.util.Collections;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.TMMItemUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.cursed.cca.CursedComponent;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public class StupidEventRegister {
    private static void clearAllKnives(Player player) {
        TMMItemUtils.clearItem(player, TMMItems.KNIFE);
    }

    public static void register() {
        // 死灵
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var component = StarGameWorldComponent.KEY.get(victim.level());
            if (component.canUseKillerFeatures(victim)) {
                var nc = NecromancerComponent.KEY.get(victim.level());
                nc.increaseAvailableRevives();
                nc.sync();
            }
        });
        // 初学被初学杀死
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {

            var level = (ServerLevel) victim.level();
            var gameWorldComponent = StarGameWorldComponent.KEY.get(level);

            if (!gameWorldComponent.isRole(victim, SERoles.INITIATE)) {
                return;
            }
            if (killer == null)
                return;
            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                victim.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available"), true);
                return;
            }
            if (gameWorldComponent.isRole(killer, SERoles.INITIATE)) {
                var shuffledKillerRoles = new ArrayList<>(StupidExpress.getEnableRoles());
                shuffledKillerRoles.removeIf(role -> Harpymodloader.VANNILA_ROLES.contains(role) || !role.canUseKiller()
                        || HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().getPath())
                        || role.identifier().equals(ResourceLocation.fromNamespaceAndPath("noellesroles", "poisoner"))
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
                StupidRoleUtils.sendWelcomeAnnouncement((ServerPlayer) killer);
            }
        });
        // 初学杀错人
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var level = (ServerLevel) victim.level();
            var gameWorldComponent = StarGameWorldComponent.KEY.get(level);
            if (killer == null)
                return;
            if (!gameWorldComponent.isRole(killer, SERoles.INITIATE))
                return;
            if (!gameWorldComponent.isRole(victim, SERoles.INITIATE)) {
                Role newInitiateRole;
                newInitiateRole = SERoles.AMNESIAC;

                // 技能可用
                for (ServerPlayer player : level.getPlayers(p -> gameWorldComponent.isRole(p, SERoles.INITIATE))) {
                    // 清除物品栏中的所有刀
                    clearAllKnives(player);
                    if (gameWorldComponent.isSkillAvailable) {
                        StupidRoleUtils.changeRole(player, newInitiateRole, true);
                        StupidRoleUtils.sendWelcomeAnnouncement((ServerPlayer) killer);
                    }
                }

                GameFunctions.killPlayer(killer, true, null, StupidExpress.id("failed_initiation"));
                return;
            }
        });
        // 初学被杀
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            // StupidExpress.LOGGER.info(victim.getDisplayName().getString()+" Dead, by
            // "+killer.getDispla);
            var level = (ServerLevel) victim.level();
            var gameWorldComponent = StarGameWorldComponent.KEY.get(level);
            if (!gameWorldComponent.isRole(victim, SERoles.INITIATE))
                return;
            if (!gameWorldComponent.isSkillAvailable) {
                victim.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available"), true);
                return;
            }
            Role newInitiateRole;

            if (killer == null) {
                newInitiateRole = SERoles.AMNESIAC;
            } else if (gameWorldComponent.isRole(killer, SERoles.INITIATE)) {
                return;
            } else {
                // 初学者被杀死（包括被炸弹炸死、摔死等非玩家攻击，以及被非初学者玩家杀死）
                Role killerRole = gameWorldComponent.getRole(killer);
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
                        if (!gameWorldComponent.isKillerTeamRole(role))
                            return true;
                        if (role.isNeutrals())
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
                StupidRoleUtils.sendWelcomeAnnouncement(player);
            }
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            LoversComponent component = LoversComponent.KEY.get(victim);

            if (!component.isLover()) {
                return;
            }

            var level = victim.level();
            var lover = level.getPlayerByUUID(component.getLover());
            if (lover != null) {
                if (GameFunctions.isPlayerAliveAndSurvival(lover)) {
                    GameFunctions.killPlayer(
                            lover,
                            true,
                            killer,
                            StupidExpress.id("broken_heart"));
                } else {
                    var wmc = WorldModifierComponent.KEY.get(lover.level());
                    if (wmc.isModifier(lover, SEModifiers.SPLIT_PERSONALITY)) {
                        var splc = SplitPersonalityComponent.KEY.get(lover);
                        if (!splc.isDeath()) {
                            GameFunctions.killPlayer(
                                    lover,
                                    true,
                                    killer,
                                    StupidExpress.id("broken_heart"));
                        }
                    }

                }

            }
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            CursedComponent cursedComponent = CursedComponent.KEY.get(victim);

            if (cursedComponent.isCursed() && killer != null) {
                // Transfer curse
                cursedComponent.reset();
                WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(victim.level());
                worldModifierComponent.removeModifier(victim.getUUID(), SEModifiers.CURSED);

                CursedComponent killerCursedComponent = CursedComponent.KEY.get(killer);
                killerCursedComponent.setCursed(killer.getUUID());
                killerCursedComponent.sync();
                worldModifierComponent.addModifier(killer.getUUID(), SEModifiers.CURSED);
            }
        });
    }
}
