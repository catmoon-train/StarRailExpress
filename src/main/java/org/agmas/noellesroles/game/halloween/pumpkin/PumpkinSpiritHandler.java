package org.agmas.noellesroles.game.halloween.pumpkin;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnTrainAreaHaveReseted;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.halloween.HalloweenItems;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 南瓜精统一事件入口；任务、有效敲门、成功吃糖由活动代码各转发一次。 */
public final class PumpkinSpiritHandler {
    private static final String SETTLED = "noellesroles_pumpkin_settled";
    private static final String CANDY = "noellesroles_pumpkin_candy";
    private static final Map<ServerLevel, Round> ROUNDS = new IdentityHashMap<>();
    private static final Map<UUID, ServerLevel> WINNERS = new HashMap<>();
    private static final Set<UUID> DISCONNECTING = new HashSet<>();
    private static boolean registered;

    private record Consumption(int count, long tick) {}
    private static final class Round {
        final UUID id = UUID.randomUUID();
        final int startingPlayers;
        final long createdAt;
        final Map<UUID, PumpkinSpiritState> players = new HashMap<>();
        final Map<UUID, Vec3> anchors = new HashMap<>();
        final Map<UUID, Integer> pendingPumpkins = new HashMap<>();
        final CandyLedger candy = new CandyLedger();
        final Map<ItemStack, Consumption> consumedCounts = new IdentityHashMap<>();
        boolean suppressionDeath;
        Round(int startingPlayers, long createdAt) {
            this.startingPlayers = Math.max(1, startingPlayers);
            this.createdAt = createdAt;
        }
    }

    private PumpkinSpiritHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        PumpkinSpiritRole.register();
        RoleSkill.register(PumpkinSpiritRole.register(), RoleSkill.skill(
                ResourceLocation.fromNamespaceAndPath("noellesroles", "pumpkin_spirit_bat"),
                "skill.noellesroles.pumpkin_spirit.bat", ctx -> {
                    ServerPlayer p = ctx.player();
                    var data = data(p);
                    return active(p.serverLevel()) && alive(p) && !paused(p.serverLevel())
                            && !desperado(p.serverLevel()) && data != null && data.startBat(p);
                }).cooldownSeconds(30).showOnHud(true).build());
        AttackEntityCallback.EVENT.register((p, l, hand, target, hit) -> {
            if (!isSpirit(p) || !HalloweenRuntime.isEnabled(l) || !(target instanceof Player)) return InteractionResult.PASS;
            if (p instanceof ServerPlayer sp && target instanceof ServerPlayer victim) tryPumpkinAttack(sp, victim);
            // 两端都拦截普通预测伤害。客户端 FAIL 不会发攻击包，故返回 SUCCESS 以发送服务端校验包。
            return l.isClientSide ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
        OnGameStarted.EVENT.register(level -> {
            clear(level);
            WINNERS.values().removeIf(winnerLevel -> winnerLevel == level);
            if (HalloweenRuntime.isMapEnabled(level)) {
                ROUNDS.put(level, new Round(SREGameWorldComponent.KEY.get(level).getPlayerCount(), level.getGameTime()));
            }
        });
        OnPlayerDeathWithKiller.FINAL_EVENT.register(PumpkinSpiritHandler::onDeath);
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        OnTrainAreaHaveReseted.EVENT.register(level -> {
            Round r = ROUNDS.get(level);
            // GameUtils.startGame 同一 tick 在 OnGameStarted 后还会派发一次地图初始化事件。
            if (r == null || r.createdAt != level.getGameTime()) clear(level);
        });
        ServerWorldEvents.UNLOAD.register((server, level) -> clear(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (ServerLevel level : new ArrayList<>(ROUNDS.keySet())) clear(level);
            WINNERS.clear();
            DISCONNECTING.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) -> {
            ServerPlayer p = connection.player;
            DISCONNECTING.add(p.getUUID());
            PumpkinHeads.restore(p);
            var data = data(p);
            if (data != null) data.restore(p);
            // 账本与单目标 CD 留到本局结束，重连不可再次领十南瓜奖励。
        });
        ServerPlayConnectionEvents.JOIN.register((connection, sender, server) -> DISCONNECTING.remove(connection.player.getUUID()));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PumpkinHeads.restore(newPlayer);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p, origin, destination) -> {
            PumpkinHeads.restore(p);
            var data = data(p);
            if (data != null) data.restore(p);
            Round old = ROUNDS.get(origin);
            if (old != null) {
                old.players.remove(p.getUUID());
                old.anchors.remove(p.getUUID());
                old.candy.forget(p.getUUID());
            }
        });
        // 这里只清理/管理非职业玩家的头槽与物品来源；职业计时和压制由 RoleData.serverTick 驱动。
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (!ROUNDS.containsKey(level)) return;
            if (!HalloweenRuntime.isMapEnabled(level)
                    || SREGameWorldComponent.KEY.get(level).getGameStatus() == SREGameWorldComponent.GameStatus.INACTIVE) {
                clear(level);
                return;
            }
            PumpkinHeads.tick(level);
            if (!active(level)) return;
            for (ServerPlayer p : level.players()) {
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) onRewardStack(p, p.getInventory().getItem(i));
            }
        });
    }

    public static boolean isSpirit(Player p) {
        var role = RoleUtils.getPlayerRole(p);
        return role != null && PumpkinSpiritRole.ID.equals(role.identifier());
    }

    static PumpkinSpiritRoleData data(Player p) { return RoleData.getNullable(PumpkinSpiritRoleData.class, p); }
    static boolean active(ServerLevel level) {
        return HalloweenRuntime.isEnabled(level) && SREGameWorldComponent.KEY.get(level).isRunning()
                && ROUNDS.containsKey(level);
    }
    static boolean paused(ServerLevel level) { return SREGameTimeComponent.KEY.get(level).isTimeFrozen(); }
    public static boolean alive(Player p) { return p.isAlive() && GameUtils.isPlayerAliveAndSurvival(p); }
    public static long now(ServerLevel level) { return GameUtils.getTicksFromGameStart(level); }
    private static PumpkinSpiritState state(ServerPlayer p) {
        Round r = ROUNDS.get(p.serverLevel());
        return r == null ? null : r.players.computeIfAbsent(p.getUUID(), id -> new PumpkinSpiritState(r.startingPlayers));
    }

    private static boolean desperado(ServerLevel level) {
        return level.players().stream().anyMatch(p -> !DISCONNECTING.contains(p.getUUID()) && alive(p)
                && RoleUtils.getPlayerRole(p) != null
                && (RoleUtils.getPlayerRole(p) == io.wifi.starrailexpress.api.TMMRoles.LOOSE_END
                    || "desperado".equals(RoleUtils.getPlayerRole(p).identifier().getPath())));
    }

    static void tickRole(ServerPlayer p, PumpkinSpiritRoleData data) {
        if (!isSpirit(p) || !active(p.serverLevel())) { data.restore(p); return; }
        PumpkinSpiritState state = state(p);
        Round r = ROUNDS.get(p.serverLevel());
        Integer pending = r.pendingPumpkins.remove(p.getUUID());
        if (pending != null) grantPumpkins(p, pending);
        boolean present = desperado(p.serverLevel());
        if (state.suppress(present, alive(p))) {
            data.stopBat(p);
            r.suppressionDeath = true;
            try {
                GameUtils.forceKillPlayer(p, false, null,
                        ResourceLocation.fromNamespaceAndPath("noellesroles", "pumpkin_spirit_suppressed"));
            } finally { r.suppressionDeath = false; }
        }
        if (state.needsSuppressionRevive() && p.isSpectator() && !paused(p.serverLevel())) {
            Vec3 anchor = r.anchors.get(p.getUUID());
            if (anchor == null) anchor = p.serverLevel().players().stream().filter(PumpkinSpiritHandler::alive)
                    .map(Player::position).findFirst().orElse(p.position());
            revive(p, anchor, state);
        }
        if (alive(p)) {
            data.applyScale(p);
            if (safePoint(p, p.blockPosition()) != null) r.anchors.put(p.getUUID(), p.position());
        } else data.restore(p);
        data.tickBat(p);
        data.update(state);
        if (checkVictory(p)) RoleUtils.customWinnerWin(p.serverLevel(), PumpkinSpiritRole.ID.getPath(), 0xEF8B24);
    }

    public static boolean checkVictory(ServerPlayer p) {
        if (!isSpirit(p) || !active(p.serverLevel()) || paused(p.serverLevel()) || desperado(p.serverLevel())) return false;
        PumpkinSpiritState state = state(p);
        if (!state.won(alive(p))) return false;
        WINNERS.put(p.getUUID(), p.serverLevel());
        return true;
    }

    public static boolean wasWinner(ServerPlayer p) { return WINNERS.get(p.getUUID()) == p.serverLevel(); }

    /** 只替换合法的三格内攻击；任何无效攻击仍由调用方拦截原伤害。 */
    public static void tryPumpkinAttack(ServerPlayer p, ServerPlayer target) {
        if (!active(p.serverLevel()) || !isSpirit(p) || paused(p.serverLevel()) || !alive(p) || !alive(target)
                || p == target || p.serverLevel() != target.serverLevel() || p.distanceToSqr(target) > 9
                || !p.hasLineOfSight(target) || safe(p) || safe(target) || desperado(p.serverLevel())
                || p.hasEffect(ModEffects.USED_BANED) || p.hasEffect(ModEffects.SKILL_BANED)) return;
        if (!state(p).attack(target.getUUID(), now(p.serverLevel()))) return;
        PumpkinHeads.apply(target, 300);
        grantPumpkins(p, 1);
    }

    private static boolean safe(Player p) {
        return p.hasEffect(ModEffects.SAFE_TIME) || p.getCooldowns().isOnCooldown(Items.CLOCK);
    }

    /** 所有有击杀归因的致死入口（包括 forceDeath）均由 mixin 在结算前拦截。 */
    public static boolean preventsKill(Player victim, Player killer) {
        return killer != null && killer != victim && isSpirit(killer) && victim instanceof ServerPlayer
                && killer.level() == victim.level() && HalloweenRuntime.isEnabled(victim.level());
    }

    public static boolean isSuppressionDeath(Player victim) {
        if (!(victim.level() instanceof ServerLevel level)) return false;
        Round r = ROUNDS.get(level);
        return r != null && r.suppressionDeath && isSpirit(victim);
    }

    public static boolean deniesKillerCoins(Player victim, Player killer) {
        return killer != null && isSpirit(victim) && HalloweenRuntime.isEnabled(victim.level())
                && RoleTeam.KILLER.matches(RoleUtils.getPlayerRole(killer));
    }

    private static void onDeath(Player victim, Player killer, ResourceLocation reason) {
        if (!(victim instanceof ServerPlayer dead) || !active(dead.serverLevel()) || !dead.isSpectator()) return;
        Round r = ROUNDS.get(dead.serverLevel());
        PumpkinHeads.restore(dead);
        if (!r.suppressionDeath) {
            for (ServerPlayer p : new ArrayList<>(dead.serverLevel().players())) {
                if (p != dead && isSpirit(p) && alive(p) && p.distanceToSqr(dead) <= 64) grantPumpkins(p, 3);
            }
        }
        if (!isSpirit(dead)) return;
        state(dead).died();
        var data = data(dead);
        if (data != null) data.restore(dead);
        if (!r.suppressionDeath && killer instanceof ServerPlayer sp
                && RoleTeam.CIVILIAN.matches(RoleUtils.getPlayerRole(sp))) {
            sp.sendSystemMessage(Component.translatable("message.noellesroles.pumpkin_spirit.killed"));
            PumpkinHeads.apply(sp, 400);
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 0));
        }
    }

    public static void onTaskCompleted(ServerPlayer p) {
        if (!active(p.serverLevel()) || paused(p.serverLevel()) || !alive(p)) return;
        for (ServerPlayer spirit : new ArrayList<>(p.serverLevel().players())) {
            if (spirit != p && isSpirit(spirit) && alive(spirit) && spirit.distanceToSqr(p) <= 64)
                grantPumpkins(spirit, 1 + p.getRandom().nextInt(2));
        }
    }

    public static void onDoorKnocked(ServerPlayer knocker) {
        if (!active(knocker.serverLevel()) || paused(knocker.serverLevel()) || !alive(knocker)
                || desperado(knocker.serverLevel())) return;
        for (ServerPlayer p : new ArrayList<>(knocker.serverLevel().players())) {
            if (!isSpirit(p) || !p.isSpectator()) continue;
            PumpkinSpiritState state = state(p);
            if (state.door(true, knocker.getRandom().nextBoolean()) && revive(p, knocker.position(), state))
                PumpkinHeads.apply(knocker, 300);
        }
    }

    private static boolean revive(ServerPlayer p, Vec3 anchor, PumpkinSpiritState state) {
        if (!p.isSpectator() || !isSpirit(p) || !active(p.serverLevel()) || desperado(p.serverLevel())) return false;
        BlockPos origin = BlockPos.containing(anchor);
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                for (int dy = 1; dy >= -2; dy--) {
                    Vec3 safe = safePoint(p, origin.offset(dx, dy, dz));
                    if (safe == null) continue;
                    GameUtils.revivePlayer(p, safe.x, safe.y, safe.z);
                    if (!alive(p)) return false;
                    state.revived();
                    // 消除本人的旧尸体，敲门复活不另外生成尸体。
                    for (var body : p.serverLevel().getEntities(TMMEntities.PLAYER_BODY,
                            b -> p.getUUID().equals(b.getPlayerUuid()))) body.discard();
                    return true;
                }
            }
        }
        return false;
    }

    private static Vec3 safePoint(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
        Vec3 point = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        AABB body = new AABB(point.x - 0.3, point.y, point.z - 0.3, point.x + 0.3, point.y + 1.8, point.z + 0.3);
        if (!AreasWorldComponent.KEY.get(level).getPlayArea().contains(point)
                || !AreasWorldComponent.KEY.get(level).getPlayArea().contains(point.add(0, 1.8, 0))
                || !level.noCollision(p, body) || !level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) return null;
        return point;
    }

    /** 首选统一新奖励入口：给实际南瓜物品并记账，背包满时掉落的栈已结算，不能再刷。 */
    public static void grantPumpkins(ServerPlayer p, int count) {
        if (count <= 0 || !active(p.serverLevel())) return;
        while (count > 0) {
            int amount = Math.min(count, 64);
            ItemStack stack = new ItemStack(HalloweenItems.PUMPKIN, amount);
            onRewardStack(p, stack);
            giveRaw(p, stack);
            count -= amount;
        }
    }

    /** 与 grantPumpkins 同义：此入口负责发物品，调用方不能再额外发一次。 */
    public static void onPumpkinsGranted(ServerPlayer p, int count) { grantPumpkins(p, count); }

    /** 给现有奖励栈使用：在插入/掉落前调用，所有职业都要调用，转移物不重复结算。 */
    public static void onRewardStack(ServerPlayer recipient, ItemStack stack) {
        if (!active(recipient.serverLevel()) || !stack.is(HalloweenItems.PUMPKIN) || stack.isEmpty()) return;
        if (settled(stack)) return;
        markSettled(stack);
        credit(recipient, stack.getCount());
    }

    public static boolean settled(ItemStack stack) {
        // 永久的已入账标记而非 owner：跨玩家共享和跨局旧物品也不能重新刷。
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(SETTLED);
    }
    public static void markSettled(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(SETTLED, true));
    }
    public static void unmarkSettled(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(SETTLED));
    }

    /** 拾取 mixin 只为成功插入的数量记账；剩余地上栈保留未领取状态。 */
    public static void onFreshPickup(ServerPlayer p, int count) { credit(p, count); }
    private static void credit(ServerPlayer p, int count) {
        if (count <= 0 || !active(p.serverLevel()) || !isSpirit(p)) return;
        int bags = state(p).credit(count);
        while (bags > 0) {
            int amount = Math.min(bags, 64);
            giveRaw(p, new ItemStack(HalloweenItems.CANDY_BAG, amount));
            bags -= amount;
        }
        var data = data(p);
        if (data != null) data.update(state(p));
        // 胜利由 RoleData 下一 tick 统一复核压制，避免死亡 FINAL_EVENT 中重入结束游戏。
    }

    public static void markThrownCandy(ServerPlayer owner, ItemStack stack) {
        if (!active(owner.serverLevel()) || !isSpirit(owner) || !stack.is(HalloweenItems.CANDY) || stack.isEmpty()) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(CANDY)) return; // 重投、拆栈不能重新发行预算。
        Round r = ROUNDS.get(owner.serverLevel());
        state(owner); // 即使投糖发生在该玩家首次 RoleData tick 前，也建立可重连账本。
        CompoundTag candy = new CompoundTag();
        candy.putUUID("round", r.id);
        candy.putUUID("owner", owner.getUUID());
        candy.putUUID("batch", r.candy.issue(owner.getUUID(), stack.getCount()));
        tag.put(CANDY, candy);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 正常糖果效果确认成功后、shrink(1) 之前调用；一次只结算一枚。 */
    public static void onCandyConsumed(ServerPlayer eater, ItemStack stack) {
        if (!active(eater.serverLevel()) || !stack.is(HalloweenItems.CANDY) || stack.isEmpty()) return;
        Round r = ROUNDS.get(eater.serverLevel());
        CompoundTag c = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(CANDY);
        if (!c.hasUUID("round") || !r.id.equals(c.getUUID("round")) || !c.hasUUID("owner") || !c.hasUUID("batch")) return;
        // 同一 tick、同一栈数量的重复通知才拒绝；拆栈重新合并后仍可逐颗食用。
        Consumption last = r.consumedCounts.put(stack, new Consumption(stack.getCount(), eater.level().getGameTime()));
        if (last != null && last.count == stack.getCount() && last.tick == eater.level().getGameTime()) return;
        UUID ownerId = c.getUUID("owner");
        boolean reward = r.candy.consume(c.getUUID("batch"), ownerId, eater.getUUID());
        ServerPlayer owner = eater.server.getPlayerList().getPlayer(ownerId);
        if (!reward) return;
        var currentRole = SREGameWorldComponent.KEY.get(eater.level()).getRole(ownerId);
        if (currentRole == null || !PumpkinSpiritRole.ID.equals(currentRole.identifier())) return;
        if (owner != null && owner.serverLevel() == eater.serverLevel() && isSpirit(owner)) grantPumpkins(owner, 2);
        else if (r.players.containsKey(ownerId)) r.pendingPumpkins.merge(ownerId, 2, Math::addExact);
    }

    public static void giveRaw(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false, false);
    }

    static void roleRemoved(ServerPlayer p) {
        PumpkinHeads.restore(p);
        if (DISCONNECTING.contains(p.getUUID())) return;
        for (Round r : ROUNDS.values()) {
            r.players.remove(p.getUUID());
            r.anchors.remove(p.getUUID());
            r.pendingPumpkins.remove(p.getUUID());
            r.candy.forget(p.getUUID());
        }
    }

    private static void clear(ServerLevel level) {
        PumpkinHeads.clear(level);
        for (ServerPlayer p : level.players()) {
            var data = data(p);
            if (data != null) data.restore(p);
        }
        ROUNDS.remove(level);
    }
}
