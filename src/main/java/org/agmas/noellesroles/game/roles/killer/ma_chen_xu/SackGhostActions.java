package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import io.wifi.starrailexpress.event.ItemGameplayEvents;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import org.agmas.noellesroles.content.item.GhostArtItem;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** 蒙眼只封攻击与物品，保留职业技能；临时控制使用有效对局时钟并保全外部效果。 */
public final class SackGhostActions {
    private record Lease(MobEffectInstance previous, long capturedAt, long until, int amplifier, long expectedEnd) {}
    private static final class State {
        long veilUntil;
        final Map<Holder<MobEffect>, Lease> effects = new HashMap<>();
    }
    private static final Map<ServerLevel, Map<UUID, State>> STATES = new IdentityHashMap<>();
    private static boolean registered;
    private static java.util.function.BooleanSupplier clientVeilProbe = () -> false;
    private SackGhostActions() {}

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.serverLevel(), ignored -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), ignored -> new State());
    }

    public static void setClientVeilProbe(java.util.function.BooleanSupplier probe) { clientVeilProbe = probe; }

    public static boolean itemsBlocked(Player player) {
        if (player.level().isClientSide()) return clientVeilProbe.getAsBoolean();
        if (!(player instanceof ServerPlayer server)) return false;
        Map<UUID, State> states = STATES.get(server.serverLevel());
        State state = states == null ? null : states.get(server.getUUID());
        return state != null && SackGhostItems.now(server.serverLevel()) < state.veilUntil;
    }

    public static boolean escaping(Player player) {
        var data = SackGhostItems.data(player);
        return data != null && data.escapeTicks > 0;
    }

    public static boolean attacksBlocked(Player player) { return itemsBlocked(player) || escaping(player); }

    /** 独立枪击入口也复用此闸门，先否决再耗弹，允许的主动攻击会解除隐身。 */
    public static boolean allowAttack(Player player) {
        if (attacksBlocked(player)) return false;
        if (player instanceof ServerPlayer) {
            var data = SackGhostItems.data(player);
            if (data != null) data.breakShroud();
        }
        return true;
    }

    private static boolean offensive(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof GhostArtItem || stack.is(TMMItemTags.GUNS) || stack.is(TMMItemTags.BOWS)
                || stack.is(TMMItemTags.PSYCHOSIS_ITEMS) || stack.is(TMMItemTags.HELD_LIKE_BAT_ITEMS)
                || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || item instanceof ProjectileWeaponItem
                || item instanceof TridentItem || item instanceof io.wifi.starrailexpress.content.item.GrenadeItem
                || item instanceof org.agmas.noellesroles.content.item.IncendiaryGrenadeItem
                || item instanceof org.agmas.noellesroles.content.item.FlashGrenadeItem
                || item instanceof org.agmas.noellesroles.content.item.SlimeGrenadeItem
                || item instanceof org.agmas.noellesroles.content.item.WallBreakGrenadeItem;
    }

    private static boolean blocksUse(Player player, ItemStack stack) {
        if (itemsBlocked(player) || escaping(player) && offensive(stack)) return true;
        if (!(stack.getItem() instanceof GhostArtItem) && offensive(stack) && player instanceof ServerPlayer) allowAttack(player);
        return false;
    }

    public static void veil(ServerPlayer target, int ticks) {
        State state = state(target);
        state.veilUntil = Math.max(state.veilUntil, SackGhostItems.now(target.serverLevel()) + ticks);
        target.stopUsingItem();
        effect(target, MobEffects.BLINDNESS, ticks, 0);
        ServerPlayNetworking.send(target, new SackGhostPackets.VeilS2C(
                SackGhostRules.remaining(SackGhostItems.now(target.serverLevel()), state.veilUntil)));
    }

    public static void root(ServerPlayer target, int ticks) { effect(target, ModEffects.MOVE_BANED, ticks, 0); }
    public static void slow(ServerPlayer target, int ticks, int amplifier) {
        effect(target, MobEffects.MOVEMENT_SLOWDOWN, ticks, amplifier);
    }

    private static void effect(ServerPlayer target, Holder<MobEffect> effect, int ticks, int amplifier) {
        State state = state(target);
        Lease old = state.effects.get(effect);
        MobEffectInstance previous = old == null ? target.getEffect(effect) : old.previous();
        long now = SackGhostItems.now(target.serverLevel());
        long captured = old == null ? target.level().getGameTime() : old.capturedAt();
        long until = Math.max(now + ticks, old == null ? 0 : old.until());
        state.effects.put(effect, new Lease(previous == null ? null : new MobEffectInstance(previous), captured,
                until, amplifier, target.level().getGameTime() + ticks));
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false, true));
    }

    private static void restore(ServerPlayer target, ServerLevel level, Holder<MobEffect> effect, Lease lease) {
        MobEffectInstance current = target.getEffect(effect);
        if (current != null && !current.isInfiniteDuration() && current.getAmplifier() == lease.amplifier()
                && current.getDuration() <= Math.max(2, lease.expectedEnd() - level.getGameTime() + 2)) {
            target.removeEffect(effect);
        }
        if (lease.previous() != null) {
            int left = SackGhostRules.remainingEffectTicks(lease.previous().getDuration(), level.getGameTime() - lease.capturedAt());
            if (left != 0) target.addEffect(new MobEffectInstance(effect, left, lease.previous().getAmplifier(),
                    lease.previous().isAmbient(), lease.previous().isVisible(), lease.previous().showIcon()));
        }
    }

    /** 临时控制不随玩家快照重新出现；只写回捕获前仍有效的外部基线。 */
    public static void filterRewindSnapshot(ServerPlayer target, net.minecraft.nbt.CompoundTag snapshot) {
        Map<UUID, State> states = STATES.get(target.serverLevel());
        State state = states == null ? null : states.get(target.getUUID());
        if (state == null) return;
        state.effects.forEach((effect, lease) -> {
            MobEffectInstance current = target.getEffect(effect);
            if (current == null || current.isInfiniteDuration() || current.getAmplifier() != lease.amplifier()
                    || current.getDuration() > Math.max(2, lease.expectedEnd() - target.level().getGameTime() + 2)) return;
            SackGhostRewind.removeSnapshotEffect(snapshot, effect);
            if (lease.previous() != null) {
                int left = SackGhostRules.remainingEffectTicks(lease.previous().getDuration(), target.level().getGameTime() - lease.capturedAt());
                if (left != 0) {
                    var effects = snapshot.getList("active_effects", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    effects.add(new MobEffectInstance(effect, left, lease.previous().getAmplifier(), lease.previous().isAmbient(),
                            lease.previous().isVisible(), lease.previous().showIcon()).save());
                    snapshot.put("active_effects", effects);
                }
            }
        });
    }

    private static void clear(ServerLevel level) {
        Map<UUID, State> states = STATES.remove(level);
        if (states == null) return;
        states.forEach((id, state) -> {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(id);
            if (target == null) return;
            state.effects.forEach((effect, lease) -> restore(target, level, effect, lease));
            ServerPlayNetworking.send(target, new SackGhostPackets.VeilS2C(0));
        });
    }

    private static void tick(ServerLevel level) {
        Map<UUID, State> states = STATES.get(level);
        if (states == null) return;
        long now = SackGhostItems.now(level);
        for (var iterator = states.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            State state = entry.getValue();
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (target == null || target.level() != level || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target)) {
                if (target != null) {
                    state.effects.forEach((effect, lease) -> restore(target, level, effect, lease));
                    ServerPlayNetworking.send(target, new SackGhostPackets.VeilS2C(0));
                }
                iterator.remove();
                continue;
            }
            for (var effects = state.effects.entrySet().iterator(); effects.hasNext();) {
                var effect = effects.next();
                Lease lease = effect.getValue();
                int left = SackGhostRules.remaining(now, lease.until());
                if (left == 0) { restore(target, level, effect.getKey(), lease); effects.remove(); continue; }
                MobEffectInstance current = target.getEffect(effect.getKey());
                int refresh = SackGhostRules.effectRefreshDuration(left, current == null ? 0 : current.getDuration());
                if (refresh > 0) {
                    target.addEffect(new MobEffectInstance(effect.getKey(), refresh, lease.amplifier(), false, false, true));
                    effect.setValue(new Lease(lease.previous(), lease.capturedAt(), lease.until(), lease.amplifier(),
                            level.getGameTime() + refresh));
                }
            }
            if (state.veilUntil != 0 && now >= state.veilUntil) {
                state.veilUntil = 0;
                ServerPlayNetworking.send(target, new SackGhostPackets.VeilS2C(0));
            } else if (state.veilUntil > now && level.getGameTime() % 20 == 0) {
                ServerPlayNetworking.send(target, new SackGhostPackets.VeilS2C(SackGhostRules.remaining(now, state.veilUntil)));
            }
            if (state.veilUntil == 0 && state.effects.isEmpty()) iterator.remove();
        }
        if (states.isEmpty()) STATES.remove(level);
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ItemGameplayEvents.ALLOW_GUN_FIRE.register((player, target) -> allowAttack(player));
        ItemGameplayEvents.ALLOW_KNIFE_ATTACK.register((player, target) -> allowAttack(player));
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
                allowAttack(player) ? InteractionResult.PASS : InteractionResult.FAIL);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                attacksBlocked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> blocksUse(player, player.getItemInHand(hand))
                ? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                blocksUse(player, player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) ->
                blocksUse(player, player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerTickEvents.END_WORLD_TICK.register(SackGhostActions::tick);
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        ServerWorldEvents.UNLOAD.register((server, level) -> clear(level));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerLevel level = handler.player.serverLevel();
            Map<UUID, State> states = STATES.get(level);
            State state = states == null ? null : states.remove(handler.player.getUUID());
            if (state != null) state.effects.forEach((effect, lease) -> restore(handler.player, level, effect, lease));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }
}
