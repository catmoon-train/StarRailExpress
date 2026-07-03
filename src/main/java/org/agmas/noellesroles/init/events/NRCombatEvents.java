package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.*;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ShootingFrenzyPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.item.*;
import org.agmas.noellesroles.game.roles.innocence.hoan_meirin.HoanMeirinFistPunchHandler;
import org.agmas.noellesroles.game.roles.innocence.veteran.VeteranKnifeHandler;
import org.agmas.noellesroles.game.roles.innocence.voodoo.VoodooDeathHandler;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuEventHandler;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggHandler;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerHandler;
import org.agmas.noellesroles.game.roles.vigilante.guard.GuardPlayerHandler;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.utils.MCItemsUtils;

import java.util.ArrayList;

/**
 * 武器、战斗、技能相关事件处理
 */
public class NRCombatEvents {

    public static void register() {
        registerAllowPlayerPunching();
        registerAllowShootRevolverDrop();
        registerOnRevolverUsed();
        registerOnPlayerUsedSkill();
        registerWeaponHandlers();
        registerCombatRoleEvents();
    }

    // --- AllowPlayerPunching ---

    private static void registerAllowPlayerPunching() {
        // 渡鸦狩猎期间允许空手攻击
        AllowPlayerPunching.EVENT.register(player -> {
            RavenPlayerComponent raven = ModComponents.RAVEN.get(player);
            return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.RAVEN) && raven.isHunting();
        });
    }

    // --- AllowShootRevolverDrop ---

    private static void registerAllowShootRevolverDrop() {
        // 观者冷静姿态不掉枪
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            ItemStack mainHandStack = player.getMainHandItem();
            if (!mainHandStack.is(TMMItems.DERRINGER) && gameWorldComponent.isRole(target, ModRoles.WATCHER)) {
                if (WatcherPlayerComponent.KEY.get(target).isInCalmStance())
                    return TrueFalseResult.TRUE;
            }
            return TrueFalseResult.PASS;
        });
    }

    // --- OnRevolverUsed (合并为单一注册) ---

    private static void registerOnRevolverUsed() {
        OnRevolverUsed.EVENT.register((player, target) -> {
            handleUniversalGunCooldown(player);
            handleJojoDoubleCooldown(player);
            handleButterFingersCooldown(player);
        });
    }

    /** 所有枪械公用冷却 */
    private static void handleUniversalGunCooldown(Player player) {
        if (player.isCreative() || !(player instanceof ServerPlayer sp))
            return;
        var cooldowns = sp.getCooldowns();
        ItemStack mainHandStack = sp.getMainHandItem();
        var items = new ArrayList<>(MCItemsUtils.getItemsByTag(sp.serverLevel(), TMMItemTags.GUNS));
        int REVOLVER_COOLDOWN = GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0);
        items.remove(ModItems.FAKE_REVOLVER);
        if (mainHandStack.is(ModItems.ONCE_REVOLVER)) {
            items.remove(ModItems.ONCE_REVOLVER);
        }
        items.remove(ModItems.PATROLLER_REVOLVER);
        if (mainHandStack.is(ModItems.PATROLLER_REVOLVER)) {
            cooldowns.addCooldown(ModItems.PATROLLER_REVOLVER, REVOLVER_COOLDOWN / 3);
        } else {
            cooldowns.addCooldown(ModItems.PATROLLER_REVOLVER, REVOLVER_COOLDOWN / 15);
        }
        items.forEach((item) -> {
            cooldowns.addCooldown(item,
                    (Integer) GameConstants.ITEM_COOLDOWNS.getOrDefault(item, REVOLVER_COOLDOWN));
        });
    }

    /** JOJO 两倍冷却 */
    private static void handleJojoDoubleCooldown(Player player) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        ItemStack mainHandStack = player.getMainHandItem();
        if (mainHandStack.is(TMMItemTags.GUNS)) {
            if (gameWorldComponent.isRole(player, ModRoles.JOJO)) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(),
                        (Integer) GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 0) * 2);
            }
        }
    }

    /** 黄油手 - 手枪冷却随机变化 */
    private static void handleButterFingersCooldown(Player player) {
        if (!(player instanceof ServerPlayer))
            return;
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        ItemStack mainHandStack = player.getMainHandItem();
        if (mainHandStack.is(TMMItemTags.GUNS)
                && modifiers.isModifier(player.getUUID(), TraitorAndModifiers.BUTTER_FINGERS)) {
            int roll = player.getRandom().nextInt(100);
            int baseCooldown = (Integer) GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHandStack.getItem(), 400);
            int newCooldown = baseCooldown;
            if (roll < 33) {
                newCooldown = baseCooldown + 60;
                player.displayClientMessage(
                        Component.translatable("modifier.noellesroles.butter_fingers.cooldown_up"), true);
            } else if (roll < 66) {
                newCooldown = Math.max(0, baseCooldown - 60);
                player.displayClientMessage(
                        Component.translatable("modifier.noellesroles.butter_fingers.cooldown_down"), true);
            } else if (roll < 99) {
                // 无事发生
            } else {
                newCooldown = 0;
                player.displayClientMessage(Component.translatable("modifier.noellesroles.butter_fingers.reset"), true);
            }
            if (newCooldown != baseCooldown) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(), newCooldown);
            }
        }
    }

    // --- OnPlayerUsedSkill ---

    private static void registerOnPlayerUsedSkill() {
        OnPlayerUsedSkill.EVENT.register((player) -> {
            NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
            if (!config.skillEchoEventEnabled)
                return false;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorld.isRunning())
                return false;
            var role = gameWorld.getRole(player);
            if (role == null)
                return false;
            if (Math.random() <= 0.6)
                return false;

            int delayTicks = (int) ((Math.random() * 4 + 3) * 20);
            if (player.level() instanceof ServerLevel serverLevel) {
                GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(delayTicks, () -> {
                    ConfigWorldComponent.KEY.get(serverLevel).announceSkillEchoForRole(role);
                }));
            }
            return false;
        });
    }

    // --- 武器处理器注册 ---

    private static void registerWeaponHandlers() {
        MaChenXuEventHandler.register();
        VeteranKnifeHandler.register();
        GamblerHandler.register();
        HoanMeirinFistPunchHandler.register();
        BatonHandler.register();
        BoneStaffHandler.register();
        RiotShieldHandler.register();
        BenevolenceSwordHandler.register();
        CuckooEggHandler.register();
        GuardPlayerHandler.register();
        GroselleJourneyManager.register();
    }

    // --- 战斗相关的角色事件 ---

    private static void registerCombatRoleEvents() {
        ShootingFrenzyPlayerComponent.registerGunNoDropEvent();
        ExecutionerPlayerComponent.registerBackfireEvent();
        ShootingFrenzyPlayerComponent.registerFrenzyCooldownEvent();
        org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent.registerEvents();
        VoodooDeathHandler.registerEvents();
    }
}
