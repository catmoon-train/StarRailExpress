package org.agmas.noellesroles.init.events;

import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.LockEntityManager;
import org.agmas.noellesroles.content.item.AlarmTrapItem;
import org.agmas.noellesroles.content.item.ReinforcementItem;
import org.agmas.noellesroles.content.item.SealedArtifactHandler;
import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.agmas.noellesroles.game.command_block.CommandBlockRules;
import org.agmas.noellesroles.game.command_block.Error404Role;
import org.agmas.noellesroles.game.halloween.HalloweenCombat;
import org.agmas.noellesroles.game.halloween.HalloweenLinkRules;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.game.halloween.HalloweenSoulLinks;
import org.agmas.noellesroles.game.halloween.HalloweenTaskRewards;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenHandler;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenTaskShare;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.wifi.starrailexpress.event.GameplayExtensionEvents.*;

/** 自有玩法的职业、修饰符与活动扩展；由统一初始化入口调用一次，不随玩家或对局注册。 */
public final class CoreGameplayRoleEvents {
    private static boolean registered;
    private static final ConcurrentHashMap<UUID, Long> WORKAHOLIC_START_TIME = new ConcurrentHashMap<>();

    private CoreGameplayRoleEvents() {}

    public static void register() {
        if (registered) return;
        registered = true;
        registerDeath();
        registerRound();
        registerTasksAndComponents();
        registerInteractions();
    }

    private static void registerDeath() {
        ALLOW_KILL_ENTRY.register((victim, body, killer, reason, forced) -> {
            if (Error404Role.isHighDimension(victim)) return false;
            return !Error404Role.isHighDimension(killer) || Error404Role.VOID_DEATH.equals(reason)
                    || Noellesroles.id("error404_puppet").equals(reason);
        });
        ALLOW_KILLER_RESOLUTION.register((victim, body, killer, reason, forced) ->
                !PumpkinSpiritHandler.isSuppressionDeath(victim));
        ALLOW_KILL_AFTER_ATTRIBUTION.register((victim, body, killer, reason, forced) -> {
            if (!PumpkinSpiritHandler.preventsKill(victim, killer)) return true;
            if (killer instanceof ServerPlayer actor && victim instanceof ServerPlayer target) {
                PumpkinSpiritHandler.tryPumpkinAttack(actor, target);
            }
            return false;
        });
        WEAK_ARMOR_OVERRIDE.register(victim -> PumpkinSpiritHandler.isSuppressionDeath(victim) ? Integer.valueOf(0) : null);
        ALLOW_BASE_KILL_COINS.register((victim, killer, reason) -> !PumpkinSpiritHandler.deniesKillerCoins(victim, killer));
        ALLOW_EXTRA_KILL_COINS.register((victim, killer, reason) -> !PumpkinSpiritHandler.deniesKillerCoins(victim, killer));
        HANDLE_KILL_REWARD_TIME.register(time -> {
            if (!(time.world instanceof ServerLevel level) || !CommandBlockDirector.isActive(level)) return false;
            if (time.getTime() < time.getResetTime()) time.addKillRewardTime(20 * 20);
            return true;
        });
    }

    private static void registerRound() {
        GAME_END_RESULT.register(CommandBlockDirector::filterEnd);
        ROLE_POOL_RESULT.register(CoreGameplayRoleEvents::replaceCommandBlockExcludedRoles);
        WELCOME_PAYLOAD.register((level, game, players, payload) -> {
            long count = players.stream().filter(p -> game.isRole(p, ModRoles.MAGICIAN)).count();
            return count > 0 ? new AnnounceWelcomePayload(payload.role(), payload.killers() + (int) count,
                    payload.targets()) : payload;
        });
        KILLER_COUNT_OVERRIDE.register(count -> CommandBlockDirector.occupiesKillerSlots() ? Integer.valueOf(0) : null);
        ALLOW_DEFAULT_INTRO.register(player -> !(player.level() instanceof ServerLevel level)
                || !CommandBlockDirector.replacesOpeningIntro(level));
        ALLOW_PLANE_INTRO.register((level, plane) -> !CommandBlockDirector.replacesOpeningIntro(level));
    }

    private static void registerTasksAndComponents() {
        QUEST_REWARD.register((player, quest, streak, parallel, reward) ->
                HalloweenRuntime.isEnabled(player.level()) && PumpkinSpiritHandler.isSpirit(player) ? 60 : reward);
        // 用同一个监听器维持旧 TAIL 顺序，不能拆成可能被其他模块插入的独立注册。
        AFTER_QUEST.register(CoreGameplayRoleEvents::afterQuest);
        ALLOW_ABILITY_TICK.register(player -> !(HalloweenRuntime.isEnabled(player.level())
                && PumpkinSpiritHandler.isSpirit(player) && SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()));
        BEFORE_TASK_TIMER_DECREMENT.register(CoreGameplayRoleEvents::onWorkaholicTaskRefresh);
        MINIGAME_COMPLETED.register((player, pos, reward, id) -> TwinChildrenTaskShare.onTaskCompleted(player));
        PSYCHO_STOPPED.register(player -> {
            var game = SREGameWorldComponent.KEY.get(player.level());
            var magicianRole = TMMRoles.ROLES.get(ModRoles.MAGICIAN_ID);
            if (magicianRole != null && game.isRole(player, magicianRole)) {
                player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ModItems.FAKE_BAT), Integer.MAX_VALUE,
                        player.inventoryMenu.getCraftSlots());
            }
        });
        ARMOR_COUNT_OVERRIDE.register(player -> {
            if (!HalloweenRuntime.isEnabled(player.level())) return null;
            if (HalloweenRuntime.isShieldSuppressed(player)) return 0;
            Player partner = HalloweenSoulLinks.partner(player);
            if (partner == null) return null;
            return HalloweenLinkRules.sharedArmor(HalloweenSoulLinks.rawArmor(player),
                    HalloweenRuntime.isShieldSuppressed(partner) ? 0 : HalloweenSoulLinks.rawArmor(partner));
        });
        ARMOR_CONSUME_OVERRIDE.register((player, amount) -> {
            if (!HalloweenRuntime.isEnabled(player.level())) return null;
            if (HalloweenRuntime.isShieldSuppressed(player)) return false;
            return HalloweenSoulLinks.consumeArmor(player, amount);
        });
        ALLOW_ARMOR_SYNC.register(HalloweenSoulLinks::arePartners);
    }

    private static void registerInteractions() {
        MOUNT_PLAYER.register((state, level, pos, player, hit) -> TwinChildrenHandler.stackMover(player));
        PLATTER_USE.register((state, world, pos, player, hit) -> {
            if (world.isClientSide || player.isCreative()) return null;
            if (world.getBlockEntity(pos) instanceof PlateTrayBlockEntity platter
                    && player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.DELUSION_VIAL)
                    && platter.getPoisoner() == null) {
                platter.setPoisoner(player.getStringUUID());
                platter.isPoisonFake = true;
                player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 1.0F);
                return InteractionResult.SUCCESS;
            }
            return null;
        });
        DOOR_COLLISION.register((state, world, pos, context) -> {
            if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player player) {
                var game = SREGameWorldComponent.KEY.maybeGet(player.level()).orElse(null);
                if (game != null && game.isRole(player, ModRoles.PRIEST)) return Shapes.empty();
            }
            return null;
        });
        AFTER_NOTE_TICK.register(CoreGameplayRoleEvents::tickAttachedNote);
        CROWBAR_USE.register(CoreGameplayRoleEvents::handleReinforcedDoor);
        ALLOW_INFECTION.register((player, infector) -> !SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.GLITCH_ROBOT));
        STUCK_COMMAND.register(CoreGameplayRoleEvents::forfeitCompanionGame);
    }

    private static void afterQuest(Player player, String quest, int streak, boolean parallel) {
        if (player instanceof ServerPlayer serverPlayer) {
            HalloweenCombat.onTaskCompleted(serverPlayer);
            HalloweenTaskRewards.onTaskCompleted(serverPlayer);
            PumpkinSpiritHandler.onTaskCompleted(serverPlayer);
        }
        SealedArtifactHandler.onFinishQuest(player, quest);
        if (WorldModifierComponent.KEY.get(player.level()).isModifier(player, SEModifiers.TASKMASTER)) {
            SREPlayerShopComponent.KEY.get(player).addToBalance(25);
        }
        TwinChildrenTaskShare.onTaskCompleted(player);
    }

    /** 保留静态计时表和首次字段读取后的时机，不修复原自减写回覆盖加速值的既有行为。 */
    private static void onWorkaholicTaskRefresh(SREPlayerTaskComponent component) {
        if (component.getPlayer() instanceof ServerPlayer player && component.nextTaskTimer > 2) {
            var modifiers = WorldModifierComponent.KEY.get(player.level());
            if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.WORKAHOLIC)) {
                long now = player.serverLevel().getGameTime();
                UUID uuid = player.getUUID();
                if (!WORKAHOLIC_START_TIME.containsKey(uuid)) {
                    WORKAHOLIC_START_TIME.put(uuid, now);
                } else if (now - WORKAHOLIC_START_TIME.get(uuid) >= 20) {
                    WORKAHOLIC_START_TIME.remove(uuid);
                    component.nextTaskTimer = (int) (component.nextTaskTimer * 0.5);
                    component.nextTaskTimer = Math.max(component.nextTaskTimer, 1);
                }
            }
        }
    }

    private static List<RoleInstance> replaceCommandBlockExcludedRoles(List<RoleInstance> original) {
        if (!CommandBlockDirector.occupiesKillerSlots() || original == null || original.isEmpty()) return original;
        List<SRERole> enabledRoles = StupidExpress.getEnableRoles();
        enabledRoles.removeIf(role -> role == TMMRoles.CIVILIAN || !role.isInnocent()
                || role.isVigilanteTeam() || role.canUseKiller() || role.isNeutrals()
                || role.isOtherModeRole() || CommandBlockRules.isExcluded(role) || CommandBlockRules.isEventBanned(role));
        RoleAssignmentPool replacementPool = RoleAssignmentPool.create("CommandBlockCivilian", enabledRoles::contains);
        RoleAssignmentPool repeatPool = RoleAssignmentPool.createUnlimited("CommandBlockCivilianFallback", enabledRoles::contains);
        for (RoleInstance instance : original) {
            if (instance != null && enabledRoles.contains(instance.role())) replacementPool.removeRoleCount(instance.role(), 1);
        }
        List<RoleInstance> replaced = new ArrayList<>(original.size());
        boolean changed = false;
        for (RoleInstance instance : original) {
            if (instance != null && (CommandBlockRules.isExcluded(instance.role())
                    || CommandBlockRules.isEventBanned(instance.role()) || instance.role() == TMMRoles.CIVILIAN)) {
                SRERole replacement = replacementPool.selectRole();
                if (replacement == null) replacement = repeatPool.selectRole();
                replaced.add(new RoleInstance(UUID.randomUUID(), replacement != null ? replacement : TMMRoles.CIVILIAN));
                changed = true;
            } else {
                replaced.add(instance);
            }
        }
        return changed ? replaced : original;
    }

    /** 双端便签 TAIL，死亡移除后仍完成当次位置更新。 */
    private static void tickAttachedNote(NoteEntity note) {
        var attached = note.getAttached(ModRoles.ENTITY_NOTE_MAKER);
        if (attached == null) return;
        if (note.tickCount >= 20 * 300) {
            note.remove(Entity.RemovalReason.DISCARDED);
        } else {
            try {
                var first = note.level().players().stream()
                        .filter(player -> player.getUUID().toString().equals(attached)).findFirst();
                if (first.isPresent()) {
                    var player = first.get();
                    if (!GameUtils.isPlayerAliveAndSurvival(player)) note.remove(Entity.RemovalReason.DISCARDED);
                    double yaw = Math.toRadians(player.getYRot());
                    double offsetX = -Math.sin(yaw) * -0.2;
                    double offsetZ = Math.cos(yaw) * -0.2;
                    note.setXRot(player.getXRot());
                    note.moveTo(player.getX() + offsetX, player.getY() + 1.25, player.getZ() + offsetZ,
                            note.getYRot(), note.getXRot());
                } else {
                    note.remove(Entity.RemovalReason.DISCARDED);
                }
            } catch (Exception ignored) {
                note.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

    /** 警报先于加固；未加固时只拆锁，随后继续普通撬门。 */
    private static InteractionResult handleReinforcedDoor(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return null;
        BlockEntity entity = world.getBlockEntity(context.getClickedPos());
        if (!(entity instanceof DoorBlockEntity)) entity = world.getBlockEntity(context.getClickedPos().below());
        if (entity instanceof DoorBlockEntity door && !door.isBlasted()) {
            AlarmTrapItem.triggerAlarmTrap(door, world);
            if (ReinforcementItem.consumeReinforcement(door)) {
                if (!world.isClientSide) {
                    world.playSound(null, context.getClickedPos(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0f, 0.8f);
                    player.displayClientMessage(Component.translatable("message.noellesroles.engineer.reinforcement_broken")
                            .withStyle(ChatFormatting.YELLOW), true);
                }
                if (!player.isCreative()) {
                    int baseCooldown = GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.CROWBAR, 45 * 20);
                    var game = SREGameWorldComponent.KEY.get(world);
                    int cooldown;
                    if (game != null && game.isRole(player, TMMRoles.LOOSE_END)) cooldown = baseCooldown / 4;
                    else if (game != null && game.isRole(player, THRedHouseRoles.FURANDORU)) cooldown = baseCooldown / 6;
                    else cooldown = baseCooldown;
                    player.getCooldowns().addCooldown(context.getItemInHand().getItem(), cooldown);
                }
                return InteractionResult.SUCCESS;
            } else if (!world.isClientSide) {
                BlockPos lock = LockEntityManager.getInstance().getNearByLockPos(door.getBlockPos().above(), world);
                if (lock != null) {
                    LockEntityManager.getInstance().removeLockEntity(lock);
                    LockEntityManager.setDoorLocked(world, door, false);
                }
            }
        }
        return null;
    }

    private static Integer forfeitCompanionGame(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            if (player != null && SRE.isLobby) {
                Class<?> sreGame = Class.forName("net.exmo.sreGame.SreGame");
                var handler = sreGame.getMethod("handleStuck", ServerPlayer.class);
                if (Boolean.TRUE.equals(handler.invoke(null, player))) return 1;
            }
        } catch (ReflectiveOperationException ignored) {
            // 可选模组不存在时保持正常 /stuck；不吞掉其它异常。
        }
        return null;
    }
}
