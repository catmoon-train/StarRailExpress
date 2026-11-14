package org.agmas.noellesroles.role_data.killer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.agmas.noellesroles.game.command_block.StandSpots;
import org.agmas.noellesroles.game.command_block.StringerRole;
import org.agmas.noellesroles.init.ModEffects;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 丝线人的缠丝集合、双人牵线与短暂禁锢，仅服务端保存，无逐 tick 同步。 */
public class StringerRoleData extends SimpleRoleData {
    private record Target(ServerPlayer player, SRERole role, RoleData data, long until) {}
    private record Hold(Target target, Vec3 anchor) {}
    private record Lease(MobEffectInstance previous, int capturedAt, int amplifier, int until) {}

    private final Map<UUID, Target> marks = new HashMap<>();
    private final Map<UUID, Hold> holds = new HashMap<>();
    private final Map<ServerPlayer, Map<Holder<MobEffect>, Lease>> effects = new HashMap<>();
    private ResourceKey<Level> dimension;
    private Target linkFirst;
    private Target linkSecond;
    private Vec3 firstSafe;
    private Vec3 secondSafe;
    private long selectionUntil;
    private UUID[] pendingChoice;

    public StringerRoleData(RoleDataContext context) {
        super(context);
        dimension = player.level().dimension();
    }

    private ServerPlayer owner() {
        return (ServerPlayer) player;
    }

    private long now() {
        return player.level().getGameTime();
    }

    private Target capture(ServerPlayer target, int ticks) {
        return new Target(target, SREGameWorldComponent.KEY.get(target.level()).getRole(target),
                RoleData.getNullable(target), now() + ticks);
    }

    private boolean valid(Target target) {
        if (target == null || now() >= target.until()) return false;
        ServerPlayer p = target.player();
        return p.level() == player.level() && p.server.getPlayerList().getPlayer(p.getUUID()) == p
                && GameUtils.isPlayerAliveAndSurvival(p)
                && SREGameWorldComponent.KEY.get(p.level()).getRole(p) == target.role()
                && RoleData.getNullable(p) == target.data();
    }

    /** 跨维度立即作废旧会话，避免往返维度后旧标记重新生效。 */
    private boolean ready() {
        if (!dimension.equals(player.level().dimension())) {
            clear();
            dimension = player.level().dimension();
        }
        if (!StringerRole.canAct(owner()) || !RoleData.isAttached(this)) {
            clear();
            return false;
        }
        return true;
    }

    public void mark(ServerPlayer target, int ticks) {
        if (!ready()) return;
        pruneMarks();
        Target old = marks.get(target.getUUID());
        int duration = valid(old) ? (int) Math.max(ticks, old.until() - now()) : ticks;
        marks.put(target.getUUID(), capture(target, duration));
        apply(target, MobEffects.GLOWING, duration, 0);
        apply(target, MobEffects.MOVEMENT_SLOWDOWN, duration, 1);
    }

    /** 仅缠丝集合参与线轴最近目标选择；牵线本身不允许被线轴拉取。 */
    public ServerPlayer current(ServerPlayer self) {
        if (!ready()) return null;
        pruneMarks();
        return marks.values().stream().map(Target::player).filter(target -> target != self)
                .min(Comparator.comparingDouble(self::distanceToSqr)).orElse(null);
    }

    public boolean canCut(ServerPlayer target) {
        if (!ready()) return false;
        pruneMarks();
        pruneLink();
        return marks.containsKey(target.getUUID())
                || linkFirst != null && (linkFirst.player() == target || linkSecond.player() == target);
    }

    public void hold(ServerPlayer target, int ticks) {
        if (!ready()) return;
        Hold old = holds.get(target.getUUID());
        int duration = old != null && valid(old.target())
                ? (int) Math.max(ticks, old.target().until() - now()) : ticks;
        holds.put(target.getUUID(), new Hold(capture(target, duration), target.position()));
        target.setDeltaMovement(Vec3.ZERO);
        apply(target, ModEffects.MOVE_BANED, duration, 0);
    }

    public void beginSelection() {
        if (ready()) selectionUntil = now() + 30 * 20;
    }

    public boolean selectionOpen() {
        return ready() && selectionUntil > now();
    }

    public void endSelection() {
        selectionUntil = 0;
    }

    public UUID[] pendingChoice() {
        return pendingChoice;
    }

    public void setPendingChoice(UUID first, UUID second) {
        pendingChoice = new UUID[] { first, second };
    }

    public void clearPendingChoice() {
        pendingChoice = null;
    }

    public void link(ServerPlayer first, ServerPlayer second, int ticks) {
        linkFirst = capture(first, ticks);
        linkSecond = capture(second, ticks);
        // 初始相距过远也要立刻收紧，不让技能生效后留下不受约束的一帧。
        if (first.distanceToSqr(second) > StringerRole.LINK_DISTANCE * StringerRole.LINK_DISTANCE) {
            Vec3 spot = first.position().add(second.position().subtract(first.position()).normalize()
                    .scale(StringerRole.LINK_DISTANCE - 0.05D));
            placeLinked(second, safe(second, spot) ? spot : first.position());
        }
        firstSafe = first.position();
        secondSafe = second.position();
    }

    private boolean safe(ServerPlayer target, Vec3 spot) {
        var box = target.getBoundingBox().move(spot.subtract(target.position()));
        return spot.y >= target.level().getMinBuildHeight() && box.maxY < target.level().getMaxBuildHeight()
                && target.level().getWorldBorder().isWithinBounds(box)
                && target.level().noCollision(target, box);
    }

    private void pruneMarks() {
        marks.entrySet().removeIf(entry -> {
            if (valid(entry.getValue())) return false;
            release(entry.getValue().player(), MobEffects.GLOWING);
            release(entry.getValue().player(), MobEffects.MOVEMENT_SLOWDOWN);
            return true;
        });
    }

    private void pruneLink() {
        if (linkFirst != null && (!valid(linkFirst) || !valid(linkSecond))) {
            linkFirst = null;
            linkSecond = null;
            firstSafe = null;
            secondSafe = null;
        }
    }

    /** 双方越界时回退到上一组合法位置，服务器传送纠正，不能靠客户端选人包修改距离。 */
    private void constrainLink() {
        pruneLink();
        if (linkFirst == null) return;
        ServerPlayer first = linkFirst.player();
        ServerPlayer second = linkSecond.player();
        if (first.distanceToSqr(second) > StringerRole.LINK_DISTANCE * StringerRole.LINK_DISTANCE) {
            if (safe(first, firstSafe) && safe(second, secondSafe)) {
                placeLinked(first, firstSafe);
                placeLinked(second, secondSafe);
            } else {
                // 旧站位被方块占据时，回到另一人的当前位置，避免塞进旧方块。
                placeLinked(second, first.position());
            }
        }
        firstSafe = first.position();
        secondSafe = second.position();
    }

    /** 牵线收紧时同步更新本职业的禁锢落点，避免两种约束来回传送。 */
    private void placeLinked(ServerPlayer target, Vec3 spot) {
        StandSpots.place(target, spot);
        Hold hold = holds.get(target.getUUID());
        if (hold != null) holds.put(target.getUUID(), new Hold(hold.target(), spot));
    }

    private void apply(ServerPlayer target, Holder<MobEffect> effect, int ticks, int amplifier) {
        var leases = effects.computeIfAbsent(target, ignored -> new HashMap<>());
        Lease old = leases.get(effect);
        MobEffectInstance previous = old == null ? target.getEffect(effect) : old.previous();
        int captured = old == null ? target.tickCount : old.capturedAt();
        leases.put(effect, new Lease(previous == null ? null : new MobEffectInstance(previous), captured,
                amplifier, Math.max(target.tickCount + ticks, old == null ? 0 : old.until())));
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false, true));
    }

    /** 只撤销与本次施加相匹配的效果，并恢复仍未到期的原有效果。 */
    private void release(ServerPlayer target, Holder<MobEffect> effect) {
        var leases = effects.get(target);
        Lease lease = leases == null ? null : leases.remove(effect);
        if (lease == null) return;
        MobEffectInstance current = target.getEffect(effect);
        if (current != null && !current.isInfiniteDuration() && current.getAmplifier() == lease.amplifier()
                && current.getDuration() <= Math.max(2, lease.until() - target.tickCount + 2)) {
            target.removeEffect(effect);
        }
        MobEffectInstance previous = lease.previous();
        if (previous != null) {
            int left = previous.isInfiniteDuration() ? -1 : previous.getDuration() - (target.tickCount - lease.capturedAt());
            if (previous.isInfiniteDuration() || left > 0) target.addEffect(new MobEffectInstance(effect, left,
                    previous.getAmplifier(), previous.isAmbient(), previous.isVisible(), previous.showIcon()));
        }
        if (leases.isEmpty()) effects.remove(target);
    }

    @Override
    public void serverTick() {
        if (!ready()) return;
        pruneMarks();
        holds.entrySet().removeIf(entry -> {
            Hold hold = entry.getValue();
            ServerPlayer target = hold.target().player();
            if (!valid(hold.target())) {
                release(target, ModEffects.MOVE_BANED);
                return true;
            }
            if (target.position().distanceToSqr(hold.anchor()) > 1.0E-6) StandSpots.place(target, hold.anchor());
            target.setDeltaMovement(Vec3.ZERO);
            return false;
        });
        constrainLink();
        if (selectionUntil <= now()) selectionUntil = 0;
    }

    @Override
    public void clear() {
        for (ServerPlayer target : new ArrayList<>(effects.keySet())) {
            for (var effect : new ArrayList<>(effects.get(target).keySet())) {
                release(target, effect);
            }
        }
        marks.clear();
        holds.clear();
        linkFirst = null;
        linkSecond = null;
        firstSafe = null;
        secondSafe = null;
        selectionUntil = 0;
        pendingChoice = null;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 选人仅发送 UUID，缠丝与牵线集合不需要发给客户端。
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
