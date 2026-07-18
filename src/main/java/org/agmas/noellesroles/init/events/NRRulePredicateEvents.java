package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.rules.*;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.game.modes.ChairWheelRaceGame;
import org.agmas.noellesroles.game.roles.innocence.salted_fish.SaltedFishPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则谓词注册（ChatHudRules, CollisionRules, DropRules, RoleVisibilityRules 等）
 */
public class NRRulePredicateEvents {

    public static List<Item> canThrowItems = new ArrayList<>();
    public static final int TRACK_DISTANCE = 8;

    public static void register() {
        registerOnPlayerDeath();
        registerChatHudRules();
        registerRoleVisibilityRules();
        registerCollisionRules();
        registerDropRules();
        populateCanThrowItems();
    }

    // --- OnPlayerDeath ---

    private static void registerOnPlayerDeath() {
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            SREItemUtils.clearItem(victim, ModItems.BOMB);
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (victim.getVehicle() instanceof WheelchairEntity we) {
                if (gameWorldComponent.isRole(victim, ModRoles.OLDMAN)) {
                    we.discard();
                }
                victim.stopRiding();
            }
        });
    }

    // --- ChatHudRules ---

    private static void registerChatHudRules() {
        ChatHudRules.canUseChatHud.add((role -> role.getIdentifier()
                .equals(ModRoles.INSANE_KILLER_ID)));
        ChatHudRules.canUseChatHudPlayer.add(player -> {
            return SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                    && SREClient.gameComponent.getGameMode() instanceof ChairWheelRaceGame;
        });
    }

    // --- RoleVisibilityRules ---

    private static void registerRoleVisibilityRules() {
        RoleVisibilityRules.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(io.wifi.starrailexpress.api.TMMRoles.DISCOVERY_CIVILIAN.getIdentifier())));
        RoleVisibilityRules.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.INSANE_KILLER_ID)));
        RoleVisibilityRules.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.MONOKUMA_ID)));
        RoleVisibilityRules.canUseOtherPerson.add((role -> role.getIdentifier()
                .equals(ModRoles.MANIPULATOR_ID)));
    }

    // --- CollisionRules ---

    private static void registerCollisionRules() {
        // 精神病杀手活跃时不可碰撞
        CollisionRules.cantCollide.add(a -> {
            final var gameWorldComponent = SREGameWorldComponent.KEY.get(a.level());
            if (gameWorldComponent.isRole(a, ModRoles.INSANE_KILLER)) {
                if (InsaneKillerPlayerComponent.KEY.get(a).isActive) {
                    return true;
                }
            }
            if (gameWorldComponent.isRole(a, ModRoles.SALTED_FISH)) {
                if (SaltedFishPlayerComponent.KEY.get(a).isActive()) {
                    return true;
                }
            }
            return false;
        });

        // 隐身/安全时间/无碰撞效果
        CollisionRules.cantCollide.add(a -> {
            return a.hasEffect(MobEffects.INVISIBILITY) || a.hasEffect(ModEffects.SAFE_TIME)
                    || a.hasEffect(ModEffects.NO_COLLIDE);
        });

        // 傀儡身体不可被推动
        CollisionRules.cantPushableBy.add(entity -> entity instanceof PuppeteerBodyEntity);

        // 多种条件不可被推动
        CollisionRules.cantPushableBy.add(entity -> {
            if (entity instanceof Player serverPlayer) {
                if (serverPlayer.hasEffect(MobEffects.INVISIBILITY)
                        || serverPlayer.hasEffect(ModEffects.SAFE_TIME)
                        || serverPlayer.hasEffect(ModEffects.NO_COLLIDE)) {
                    return true;
                }
                var modifiers = WorldModifierComponent.KEY.get(serverPlayer.level());
                if (modifiers.isModifier(serverPlayer.getUUID(), SEModifiers.FEATHER)) {
                    return true;
                }
                var gameComp = SREGameWorldComponent.KEY.get(serverPlayer.level());
                if (gameComp != null) {
                    if (gameComp.isRole(serverPlayer, ModRoles.NOSTALGIST)) {
                        return true;
                    }
                    if (gameComp.isRole(serverPlayer, ModRoles.SALTED_FISH)) {
                        if (SaltedFishPlayerComponent.KEY.get(serverPlayer).isActive()){
                            return true;
                        }

                    }
                    if (gameComp.isRole(serverPlayer, ModRoles.INSANE_KILLER)) {
                        InsaneKillerPlayerComponent insaneKiller = InsaneKillerPlayerComponent.KEY.get(serverPlayer);
                        if (insaneKiller.isActive) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });

        // 傀儡身体不可被推动
        CollisionRules.cantPushableBy.add(entity -> (entity instanceof io.wifi.starrailexpress.content.entity.NoteEntity));

        // 房车不可被推动（双保险，SixtySecondsRvEntity.isPushable() 已返回 false）
        CollisionRules.cantPushableBy.add(entity ->
                entity instanceof net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity);
        // 房车可与玩家碰撞（防止穿模进车体内部）
        CollisionRules.canCollideEntity.add(entity ->
                entity instanceof net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity);
    }

    // --- DropRules ---

    private static void registerDropRules() {
        DropRules.canDropItem.addAll(List.of(
                "exposure:stacked_photographs",
                "exposure:album",
                "exposure:photograph",
                "noellesroles:mint_candies",
                "noellesroles:alchemist_buff_potion",
                "noellesroles:stalker_knife",
                "noellesroles:yinyang_sword",
                "noellesroles:stalker_knife_offhand",
                "noellesroles:pill",
                "noellesroles:pocket_watch",
                "noellesroles:throwing_knife",
                "supplementaries:key",
                "minecraft:emerald",
                "minecraft:glass_bottle",
                "noellesroles:shisiye",
                "noellesroles:signed_paper",
                "noellesroles:mercenary_contract",
                "noellesroles:diving_helmet",
                "noellesroles:night_vision_glasses",
                "noellesroles:life_and_death_shape",
                "noellesroles:noell_paperclip",
                "noellesroles:jetpack",
                "minecraft:clock",
                "minecraft:lantern",
                "noellesroles:passbook",
                "minecraft:written_book"));
    }

    private static void populateCanThrowItems() {
        BuiltInRegistries.ITEM.entrySet().stream()
                .filter(entry -> DropRules.canDropItem.contains(entry.getKey().toString()))
                .map(entry -> entry.getValue().getDefaultInstance().getItem())
                .forEach(item -> {
                    canThrowItems.add(item);
                });
    }
}
