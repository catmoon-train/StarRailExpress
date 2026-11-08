package org.agmas.noellesroles.game.roles.killer.clone_doctor;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.CloneDoctorStateS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 跨职业的变身会话。这里只保存博士身份必须跨职业存活的数据，
 * 被复制职业仍使用正常的 RoleData/组件生命周期，绝不借用目标玩家的数据。
 */
public final class CloneDoctorSessionManager {
    public static final int CAST_TICKS = CloneDoctorTimers.CAST_TICKS;
    public static final int TRANSFORM_TICKS = CloneDoctorTimers.TRANSFORM_TICKS;
    public static final int INVISIBLE_TICKS = CloneDoctorTimers.INVISIBLE_TICKS;
    public static final int COPY_COOLDOWN = CloneDoctorTimers.COPY_COOLDOWN;
    public static final int SUBSTITUTE_COOLDOWN = CloneDoctorTimers.SUBSTITUTE_COOLDOWN;
    public static final int SLIME_COOLDOWN = CloneDoctorTimers.SLIME_COOLDOWN;
    private static final String TEMP_ITEM = "clone_doctor_temporary";
    private static final String TEMP_SESSION = "clone_doctor_session";
    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State extends CloneDoctorTimers {
        ResourceKey<Level> dimension;
        UUID target;
        UUID copySession;
        SRERole pendingRole;
        SRERole copiedRole;
        final List<ItemStack> pendingItems = new ArrayList<>();
        CloneDoctorRoleSnapshot snapshot;
        boolean transitioning;
    }

    private CloneDoctorSessionManager() { }

    private static long now(Player player) { return GameUtils.getTicksFromGameStart(player.level()); }
    private static SREGameWorldComponent game(Player player) { return SREGameWorldComponent.KEY.get(player.level()); }
    private static State state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> {
            State value = new State();
            value.dimension = player.level().dimension();
            return value;
        });
    }

    public static void ensureState(ServerPlayer player) {
        State state = state(player);
        if (!state.transitioning) sync(player, state);
    }

    /** 职业切换期间禁止重置通用任务奖励计数，也不记入正式转职成长统计。 */
    public static boolean isTransitioning(Player player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.transitioning;
    }

    public static boolean beginCopy(ServerPlayer player, ServerPlayer requestedTarget) {
        if (!game(player).isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)
                || !game(player).isRole(player, ModRoles.CLONE_DOCTOR)) return false;
        State state = state(player);
        if (state.transitioning || state.castAt > 0 || state.copiedRole != null) return false;
        if (now(player) < state.copyReadyAt) return fail(player, "cooldown");
        var hit = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof ServerPlayer other && other != player
                        && GameUtils.isPlayerAliveAndSurvival(other), 26.0D);
        if (!(hit instanceof EntityHitResult result) || !(result.getEntity() instanceof ServerPlayer target)
                || requestedTarget != null && target != requestedTarget) return fail(player, "invalid_target");
        SRERole role = game(player).getRole(target);
        if (!canCopy(role)) return fail(player, "invalid_role");
        state.target = target.getUUID();
        state.pendingRole = role;
        state.beginCast(now(player));
        player.displayClientMessage(Component.translatable("message.noellesroles.clone_doctor.casting",
                target.getDisplayName()), true);
        sync(player, state);
        return true;
    }

    public static boolean canCopy(SRERole role) {
        return role != null && role != ModRoles.CLONE_DOCTOR && !role.isNeutrals()
                && !role.isNeutralTeamBase() && role.canBeRandomed();
    }

    private static boolean fail(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.translatable("message.noellesroles.clone_doctor." + reason), true);
        return false;
    }

    private static void completeCopy(ServerPlayer player, State state) {
        ServerPlayer target = player.server.getPlayerList().getPlayer(state.target);
        SRERole expected = state.pendingRole;
        state.cancelCast();
        state.target = null;
        state.pendingRole = null;
        if (target == null || target.level() != player.level() || !GameUtils.isPlayerAliveAndSurvival(target)
                || game(player).getRole(target) != expected || !canCopy(expected)
                || !player.hasLineOfSight(target) || player.distanceToSqr(target) > 26 * 26
                || player.hasEffect(ModEffects.SKILL_BANED) || player.hasEffect(ModEffects.SAFE_TIME)) {
            fail(player, "interrupted");
            sync(player, state);
            return;
        }
        state.snapshot = new CloneDoctorRoleSnapshot(player);
        state.copiedRole = expected;
        state.copySession = UUID.randomUUID();
        try {
            switchRole(player, state, expected, true);
            // 少数职业的初始化会继续转职；会话跟踪最终实际能力职业，仍按时回到博士。
            state.copiedRole = game(player).getRole(player);
            state.snapshot.captureInitialized(player);
            state.completeCopy(now(player));
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVISIBLE_TICKS, 0, false, false, true));
            player.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, INVISIBLE_TICKS, 0, false, false, true));
            player.displayClientMessage(Component.translatable("message.noellesroles.clone_doctor.copied",
                    Component.translatable("announcement.star.role." + expected.identifier().getPath())), true);
        } catch (RuntimeException exception) {
            Noellesroles.LOGGER.error("克隆博士变身失败，正在恢复本体", exception);
            state.copiedRole = game(player).getRole(player);
            returnToSelf(player);
        }
        sync(player, state);
    }

    /**
     * 正常触发职业移除/初始化，但将初始物品与原背包隔离。
     * 返回时保留当前背包的真实消耗/拾取结果，而非回滚旧快照造成复制或退款。
     */
    private static void switchRole(ServerPlayer player, State state, SRERole role, boolean grantTemporaryItems) {
        player.closeContainer();
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            inventory.add(player.getInventory().getItem(i).copy());
        }
        int selected = player.getInventory().selected;
        int balance = MoneyUtils.getBalance(player);
        List<ItemStack> generated = new ArrayList<>();
        state.transitioning = true;
        player.getInventory().clearContent();
        try {
            RoleUtils.changeRole(player, role, true, false, false, false, true);
            if (grantTemporaryItems) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty()) generated.add(markTemporary(stack.copy(), player.getUUID()));
                }
            }
        } finally {
            player.getInventory().clearContent();
            for (int i = 0; i < inventory.size(); i++) player.getInventory().setItem(i, inventory.get(i));
            player.getInventory().selected = selected;
            MoneyUtils.setBalance(player, balance);
            state.transitioning = false;
            state.pendingItems.addAll(generated);
            deliverPendingItems(player, state);
            player.inventoryMenu.broadcastChanges();
        }
    }

    public static void returnToSelf(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null || state.transitioning || state.copiedRole == null) return;
        // 外部合法转职优先，不把感染/管理员的转职强行覆盖回博士。
        if (game(player).getRole(player) != state.copiedRole) {
            forget(player);
            return;
        }
        state.pendingItems.clear();
        removeTemporaryItems(player);
        switchRole(player, state, ModRoles.CLONE_DOCTOR, false);
        if (state.snapshot != null) state.snapshot.restore(player);
        state.snapshot = null;
        state.copiedRole = null;
        state.copySession = null;
        state.stopCopy(now(player));
        state.pendingRole = null;
        SREAbilityPlayerComponent.KEY.get(player).setSkillCooldown(CloneDoctorRole.COPY_SKILL,
                remaining(state.copyReadyAt, now(player)));
        sync(player, state);
        // 只在真正恢复本体之后报幕；死亡、断线清理不向玩家误报主动返回。
        if (!player.hasDisconnected() && game(player).isRunning() && GameUtils.isPlayerAliveAndSurvival(player)) {
            io.wifi.starrailexpress.util.SRENetworkMessageUtils.sendCODSubtitleToPlayerTop(player,
                    Component.translatable("message.noellesroles.clone_doctor.returned")
                            .withColor(0x69C6A1),
                    Component.translatable("message.noellesroles.clone_doctor.returned_detail"), 60);
        }
    }

    /** 由统一转职入口调用，外部转职只清理会话，不触发第二次转职。 */
    public static void beforeRoleChange(Player player) {
        if (!isTransitioning(player) && STATES.containsKey(player.getUUID())) forget(player);
    }

    private static void forget(Player player) {
        removeTemporaryItems(player);
        STATES.remove(player.getUUID());
        WorldModifierComponent.KEY.get(player.level()).removeModifier(player, NRModifiers.CLONED_PERSON);
        if (player instanceof ServerPlayer serverPlayer) sync(serverPlayer, null);
    }

    public static void createSubstitute(ServerPlayer player, UUID targetId) {
        if (!game(player).isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)
                || !CloneDoctorRole.isClone(player) || player.hasEffect(ModEffects.SAFE_TIME)
                || player.hasEffect(ModEffects.SKILL_BANED)) return;
        State state = state(player);
        if (state.transitioning || state.castAt > 0 || now(player) < state.substituteReadyAt) {
            fail(player, "cooldown");
            return;
        }
        if (!MoneyUtils.hasBalance(player, 100)) {
            MoneyUtils.sendNotEnoughtMoneyMessage(player, 100);
            return;
        }
        ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
        if (target == null || !CloneSubstituteManager.create(player, target)) {
            fail(player, "substitute_failed");
            return;
        }
        MoneyUtils.cost(player, 100);
        state.substituteCreated(now(player));
        sync(player, state);
    }

    public static boolean canBuySlime(Player player) {
        State state = STATES.get(player.getUUID());
        boolean hasSlime = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.SLIME_GRENADE)) { hasSlime = true; break; }
        }
        return !hasSlime && !player.containerMenu.getCarried().is(ModItems.SLIME_GRENADE)
                && (state == null || now(player) >= state.slimeReadyAt);
    }

    public static void slimeBought(Player player) {
        state(player).slimeBought(now(player));
    }

    public static boolean blocksDamage(Player player) {
        if (player == null) return false;
        State state = STATES.get(player.getUUID());
        return state != null && now(player) < state.invisibleUntil;
    }

    public static boolean isTemporary(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(TEMP_ITEM);
    }


    public static boolean canReceiveTemporary(Player player, ItemStack stack) {
        State state = STATES.get(player.getUUID());
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (state == null || state.copySession == null || state.copiedRole == null || data == null) return false;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(TEMP_ITEM) && player.getUUID().equals(tag.getUUID(TEMP_ITEM))
                && tag.hasUUID(TEMP_SESSION) && state.copySession.equals(tag.getUUID(TEMP_SESSION));
    }
    private static ItemStack markTemporary(ItemStack stack, UUID owner) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putUUID(TEMP_ITEM, owner);
        State state = STATES.get(owner);
        if (state != null && state.copySession != null) data.putUUID(TEMP_SESSION, state.copySession);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    private static void deliverPendingItems(ServerPlayer player, State state) {
        var iterator = state.pendingItems.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            int capacity = 0;
            boolean alreadyHasSlime = false;
            for (int slot = 0; slot < 9; slot++) {
                ItemStack existing = player.getInventory().getItem(slot);
                if (existing.is(ModItems.SLIME_GRENADE)) alreadyHasSlime = true;
                if (existing.isEmpty()) capacity += stack.getMaxStackSize();
                else if (ItemStack.isSameItemSameComponents(stack, existing)) {
                    capacity += Math.max(0, existing.getMaxStackSize() - existing.getCount());
                }
            }
            if (stack.is(ModItems.SLIME_GRENADE) && alreadyHasSlime) continue;
            // API会复制参数且失败前可能部分合并，必须先确认整份能够容纳。
            if (capacity >= stack.getCount() && RoleUtils.insertStackInFreeSlot(player, stack)) iterator.remove();
        }
    }

    public static void removeTemporaryItems(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isTemporary(player.getInventory().getItem(i))) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (isTemporary(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
    }

    public static int remaining(long deadline, long now) { return CloneDoctorTimers.remaining(deadline, now); }
    private static void sync(ServerPlayer player, State state) {
        if (!ServerPlayNetworking.canSend(player, CloneDoctorStateS2CPacket.ID)) return;
        long now = now(player);
        ServerPlayNetworking.send(player, state == null
                ? new CloneDoctorStateS2CPacket(false, 0, 0, 0, 0)
                : new CloneDoctorStateS2CPacket(true, remaining(state.castAt, now), remaining(state.endsAt, now),
                        remaining(state.copyReadyAt, now), remaining(state.substituteReadyAt, now)));
    }

    public static void registerEvents() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> !(source.getEntity() instanceof Player attacker) || !blocksDamage(attacker));
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
                blocksDamage(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> !blocksDamage(killer));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            returnToSelf(player);
            State state = STATES.get(player.getUUID());
            if (state != null) { state.cancelCast(); state.target = null; state.pendingRole = null; }
            // 保留本局冷却到玩家重连；在线对象/会话不会被静态容器持有。
        });
        OnGameEnd.EVENT.register((level, game) -> {
            for (ServerPlayer player : new ArrayList<>(level.players())) {
                if (STATES.containsKey(player.getUUID())) {
                    removeTemporaryItems(player);
                    sync(player, null);
                }
            }
            STATES.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                State state = STATES.get(player.getUUID());
                if (state == null || state.transitioning) continue;
                if (!CloneDoctorRole.isClone(player)) { forget(player); continue; }
                if (!state.dimension.equals(player.level().dimension())) {
                    returnToSelf(player);
                    forget(player);
                    continue;
                }
                if (!game(player).isRunning()) continue;
                if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                    returnToSelf(player);
                    state.castAt = 0;
                    continue;
                }
                if (state.copiedRole != null && game(player).getRole(player) != state.copiedRole) {
                    forget(player);
                    continue;
                }
                if (state.copiedRole != null) deliverPendingItems(player, state);
                long now = now(player);
                if (state.castAt > 0 && now >= state.castAt) completeCopy(player, state);
                if (state.copiedRole != null && now >= state.endsAt) returnToSelf(player);
                if (player.tickCount % 20 == 0) sync(player, state);
            }
        });
    }
}
