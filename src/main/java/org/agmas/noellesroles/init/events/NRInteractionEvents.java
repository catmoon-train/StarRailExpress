package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.StandardRevolverItem;
import io.wifi.starrailexpress.event.*;
import org.agmas.noellesroles.CustomWinnerClass;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.rules.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.item.*;
import org.agmas.noellesroles.events.OnShopPurchase;
import org.agmas.noellesroles.events.OnVendingMachinesBuyItems;
import org.agmas.noellesroles.game.roles.innocence.cake_maker.CakeMakerComponent;
import org.agmas.noellesroles.game.roles.killer.conspirator.ConspiratorKilledPlayer;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ninja.NinjaPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.commander.CommanderHandler;
import org.agmas.noellesroles.game.roles.neutral.cupid.CupidPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent;
import org.agmas.noellesroles.TrueKillerFinder;
import org.agmas.noellesroles.game.roles.neutral.mafia.GodfatherComponent;
import org.agmas.noellesroles.ModDataComponentTypes;
import org.agmas.noellesroles.init.*;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.EntityClearUtils;
import org.agmas.noellesroles.utils.MapScanner;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.PlayerStatsBeforeRefugee;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.*;

/**
 * 玩家交互、聊天、商店、角色组件注册事件处理
 */
public class NRInteractionEvents {

    public static void register() {
        registerUseEntityCallbacks();
        registerUseItemCallback();
        registerShopEvents();
        registerChatEvents();
        registerDropRules();
        registerMiscRules();
        registerRoleComponentEvents();
        registerOtherEvents();
    }

    // --- UseEntityCallback ---

    private static void registerUseEntityCallbacks() {
        // 蛋糕师：原料输入 + 蛋糕食用
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND)
                return InteractionResult.PASS;

            // 烟熏炉交互方块 - 添加原料
            if (CakeMakerComponent.isSmokerInteractionEntity(entity)) {
                UUID ownerId = CakeMakerComponent.getSmokerOwner(entity);
                if (ownerId != null && ownerId.equals(player.getUUID())) {
                    if (ModComponents.CAKE_MAKER.get(player).addIngredient(player, entity)) {
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            // 蛋糕交互实体 - 食用
            if (CakeMakerComponent.isCakeInteractionEntity(entity)) {
                if (player.isSpectator())
                    return InteractionResult.PASS;
                UUID ownerId = CakeMakerComponent.getCakeOwner(entity);
                if (ownerId != null) {
                    ServerPlayer cakeOwner = world.getServer().getPlayerList().getPlayer(ownerId);
                    if (cakeOwner != null) {
                        if (ModComponents.CAKE_MAKER.get(cakeOwner).eat(entity, (ServerPlayer) player)) {
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });

        // 手铐交互 - 巡警队可取下他人手铐
        UseEntityCallback.EVENT.register((player, level, interactionHand, entity, entityHitResult) -> {
            if (player.isSpectator())
                return InteractionResult.PASS;
            var gameC = SREGameWorldComponent.KEY.get(level);
            var playerRole = gameC.getRole(player);
            if (playerRole == null || !playerRole.isVigilanteTeam())
                return InteractionResult.PASS;
            if (HandCuffsItem.hasHandCuff(player))
                return InteractionResult.PASS;
            if (entity instanceof Player target) {
                if (HandCuffsItem.hasHandCuff(target)) {
                    if (!player.getMainHandItem().isEmpty())
                        return InteractionResult.PASS;
                    var fkit = HandCuffsItem.putOffHandCuff(target);
                    if (fkit == null)
                        return InteractionResult.FAIL;
                    RoleUtils.insertStackInFreeSlot(player, fkit.copy());
                    player.displayClientMessage(
                            Component.translatable("item.noellesroles.handcuffs.put_off", target.getName())
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                    target.displayClientMessage(Component
                            .translatable("item.noellesroles.handcuffs.reciever_put_off", player.getName())
                            .withStyle(ChatFormatting.GREEN), true);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }

    // --- UseItemCallback ---

    private static void registerUseItemCallback() {
        // 老人复活节彩蛋鱼竿 - 召唤骑乘猪
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!RoleShopHandler.isOldmanEasterEggRod(stack)) {
                return InteractionResultHolder.pass(stack);
            }
            if (RoleShopHandler.hasUsedOldmanEasterEggRod(stack)) {
                return InteractionResultHolder.pass(stack);
            }
            if (world.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }

            var pig = EntityType.PIG.create(world);
            if (pig == null) {
                return InteractionResultHolder.fail(stack);
            }
            pig.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
            if (pig instanceof net.minecraft.world.entity.Saddleable saddleable) {
                saddleable.equipSaddle(ItemStack.EMPTY, null);
            }
            var pigStepHeight = pig.getAttribute(Attributes.STEP_HEIGHT);
            if (pigStepHeight != null) {
                pigStepHeight.setBaseValue(0.5D);
            }
            var pigJumpStrength = pig.getAttribute(Attributes.JUMP_STRENGTH);
            if (pigJumpStrength != null) {
                pigJumpStrength.setBaseValue(0.0D);
            }
            pig.addTag(RoleShopHandler.OLDMAN_EASTER_EGG_PIG_NO_STEP_TAG);
            world.addFreshEntity(pig);
            RoleShopHandler.markOldmanEasterEggRodUsed(stack);
            return InteractionResultHolder.success(stack);
        });
    }

    // --- 商店事件 ---

    private static void registerShopEvents() {
        // 吝啬 - 商店购买返还
        OnShopPurchase.EVENT.register((player, entry, price) -> {
            org.agmas.noellesroles.role.ModifierEffects
                    .onStingyPurchase((ServerPlayer) player, price);
        });

        // 一次性左轮购买限制
        OnVendingMachinesBuyItems.EVENT.register((player, itemStack) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (itemStack.stack().is(ModItems.ONCE_REVOLVER)) {
                var role = gameWorldComponent.getRole(player);
                if (role != null) {
                    if (role.isInnocent() && role.canPickUpRevolver() && !role.isNeutrals()) {
                        return true;
                    } else if (role == SpecialGameModeRoles.DIRT) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            return true;
        });
    }

    // --- 聊天事件 ---

    private static void registerChatEvents() {
        // 分裂人格聊天广播
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            if (!WorldModifierComponent.KEY.get(serverPlayer.level()).isModifier(serverPlayer,
                    SEModifiers.SPLIT_PERSONALITY))
                return true;
            var spc = SplitPersonalityComponent.KEY.get(serverPlayer);
            if (!spc.isDeath()) {
                ServerPlayer mainP = serverPlayer.server.getPlayerList().getPlayer(spc.getMainPersonality());
                ServerPlayer secondP = serverPlayer.server.getPlayerList().getPlayer(spc.getSecondPersonality());
                if (mainP == null || secondP == null)
                    return true;
                var broadcastMessage = Component
                        .translatable("message.split_personality.broadcast_prefix",
                                Component.literal("").append(serverPlayer.getDisplayName())
                                        .withStyle(ChatFormatting.AQUA),
                                Component.literal(message.signedContent()).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GOLD);
                BroadcastCommand.BroadcastMessage(mainP, broadcastMessage);
                BroadcastCommand.BroadcastMessage(secondP, broadcastMessage);
            }
            return true;
        });

        // 聊天禁止药水效果
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            if (serverPlayer.hasEffect(ModEffects.CHAT_BAN)) {
                return false;
            }
            return true;
        });
    }

    // --- 掉落规则 ---

    private static void registerDropRules() {
        DropRules.canDrop.add((player) -> {
            var mainHandItem = player.getMainHandItem();
            if (mainHandItem.is(ModItems.NEWSPAPER)) {
                if (mainHandItem.has(DataComponents.WRITTEN_BOOK_CONTENT))
                    return true;
            }
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.isRole(player, RedHouseRoles.BAKA)) {
                if (mainHandItem.is(FunnyItems.PROBLEM_SET))
                    return true;
            }
            if (gameWorldComponent.isRole(player, ModRoles.CHEF)) {
                if (mainHandItem.get(ModDataComponentTypes.COOKED) != null)
                    return true;
            }
            if (mainHandItem.is(ModItems.RADIO))
                return true;
            if (RoleShopHandler.isOldmanEasterEggRod(mainHandItem))
                return true;
            return false;
        });
    }

    // --- 杂项规则 ---

    private static void registerMiscRules() {
        // 观战者不能发送回放
        ReplayRules.cantSendReplay.add(player -> {
            DeathPenaltyComponent component = ModComponents.DEATH_PENALTY.get(player);
            if (component != null && component.hasPenalty())
                return true;
            return false;
        });

        // 防弹衣可被穿透的死亡原因
        ArmorRules.canStickArmor.add((deathInfo -> {
            String path = deathInfo.deathReason().getPath();
            return path.equals("ignited") || path.equals("hoan_meirin_lonely")
                    || path.equals("voodoo") || path.equals("shot_innocent");
        }));

        // 观战者不能使用聊天HUD
        ChatHudRules.cantUseChatHud.add((p) -> {
            var deathPenalty = ModComponents.DEATH_PENALTY.get(p);
            if (deathPenalty.hasPenalty() && deathPenalty.chatEnabled == false)
                return true;
            return false;
        });

        // 可见毒药
        CanSeePoison.EVENT.register((player) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.isRole((Player) player, ModRoles.BARTENDER))
                return true;
            if (gameWorldComponent.isRole((Player) player, ModRoles.POISONER))
                return true;
            return false;
        });
    }

    // --- 角色组件事件注册 ---

    private static void registerRoleComponentEvents() {
        DoomedSinnerPlayerComponent.registerEvents();
        GodfatherComponent.registerEvents();
        StandardRevolverItem.registerEvents();
        RefugeeComponent.register();
        THEventHandler.registerEvents();
        NinjaPlayerComponent.registerEvents();
        org.agmas.noellesroles.game.roles.killer.nostalgist.NostalgistPlayerComponent.registerEvents();
        org.agmas.noellesroles.game.roles.killer.wraith_assassin.WraithAssassinPlayerComponent.registerEvents();
        StalkerPlayerComponent.registerEvents();
        org.agmas.noellesroles.game.roles.killer.delayer.DelayerPlayerComponent.registerEvents();
        CupidPlayerComponent.registerEvents();
        CommanderHandler.registerChatEvent();
        InsaneKillerPlayerComponent.registerEvent();
        ConspiratorKilledPlayer.registerEvents();
        org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.registerEvent();
        EntityClearUtils.registerResetEvent();
        org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerFrameEvents.register();
        MapScanner.registerMapScanEvent();
        CustomWinnerClass.registerCustomWinners();
        XiaoNaoHandler.registerEvent();
        org.agmas.noellesroles.game.roles.innocence.awesome_binglus.AwesomePlayerComponent.registerEvents();
        TrueKillerFinder.registerEvents();

        // 难民逃生前状态保存
        PlayerStatsBeforeRefugee.beforeLoadFunc = (player) -> {
            ModComponents.DEATH_PENALTY.get(player).init();
        };

        // 精神病杀手职业移除清理
        ModdedRoleRemoved.EVENT.register((player, role) -> {
            if (role != null && role.identifier().equals(ModRoles.INSANE_KILLER.identifier())) {
                InsaneKillerPlayerComponent.KEY.get(player).clear();
            }
        });

        ModRolesInitialEventRegister.register();
    }

    // --- 其他事件 ---

    private static void registerOtherEvents() {
        // 漫游者事件
        org.agmas.noellesroles.game.roles.neutral.wayfarer.WayfarerPlayerComponent.registerEvents();
    }
}
