package org.agmas.noellesroles.role.bouns.roles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.PurpleMonsterEventC2SPacket;
import org.agmas.noellesroles.packet.PurpleMonsterEventS2CPacket;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

/** Server-side state machine for the target-local Purple Monster event. */
public final class PurpleMonsterRole {
    private static final int EVENT_INTERVAL = 20 * 20;
    private static final int OBSERVE_TICKS = 6 * 20;
    private static final int REVEAL_TICKS = 20;
    private static final int QUESTION_TICKS = 5 * 20;
    private static final int SELECT_TICKS = 15 * 20;
    private static final int TRANSFORM_TICKS = 20;
    private static final int ASSIMILATE_TICKS = 4 * 20;
    private static final int EFFECT_TICKS = 3 * 20;
    private static final Map<UUID, Map<UUID, Integer>> PROXIMITY = new HashMap<>();
    private static final Map<GazeKey, Integer> MUTUAL_GAZE = new HashMap<>();
    /** Progress is recorded only when the controlled player actually becomes a Purple Monster. */
    private static final Map<UUID, Integer> SUCCESSFUL_ASSIMILATIONS = new HashMap<>();
    private static Event active;
    private static boolean registered;

    private PurpleMonsterRole() {}

    public static void registerEvents() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(PurpleMonsterRole::tickServer);
    }

    public static boolean forceStart(ServerPlayer target) {
        if (active != null) finish();
        return startEvent(target, true);
    }

    public static void handleAction(ServerPlayer player, PurpleMonsterEventC2SPacket packet) {
        if (active == null || !active.id.equals(packet.eventId()) || !active.target.equals(player.getUUID())) return;
        switch (packet.action()) {
            case OBSERVED -> {
                if (active.stage == Stage.DISGUISE) beginQuestion(player);
            }
            case ANSWER -> {
                if (active.stage == Stage.QUESTION) beginSelection(player);
            }
            case SELECT -> {
                if (active.stage == Stage.SELECT && packet.selectedPlayer() != null) selectVictim(player,
                        packet.selectedPlayer());
            }
        }
    }

    private static void tickServer(MinecraftServer server) {
        boolean anyRunning = false;
        for (ServerLevel level : server.getAllLevels()) {
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
            if (!game.isRunning()) continue;
            anyRunning = true;
            updateProximity(level);
            if (level.getGameTime() % EVENT_INTERVAL == 0) tryAutomaticEvent(level, game);
            checkPurpleGaze(level, game);
            if (level.getGameTime() % 20 == 0) syncProgress(level, game);
        }
        if (!anyRunning) {
            PROXIMITY.clear();
            MUTUAL_GAZE.clear();
            SUCCESSFUL_ASSIMILATIONS.clear();
            if (active != null) finish();
        }
        if (active != null) tickEvent(server);
    }

    private static void tickEvent(MinecraftServer server) {
        ServerPlayer target = findPlayer(server, active.target);
        if (target == null || !SREGameWorldComponent.KEY.get(target.serverLevel()).isRunning()
                || target.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(target)) {
            finish();
            return;
        }
        long now = target.level().getGameTime();
        if (active.stage == Stage.REVEAL && now >= active.deadline) openQuestion(target);
        else if (active.stage == Stage.QUESTION && now >= active.deadline) beginSelection(target);
        else if (active.stage == Stage.SELECT && now >= active.deadline) selectionTimeout(target);
        else if (active.stage == Stage.ASSIMILATE_TRANSFORM && now >= active.deadline) beginSecondForm(target);
        else if (active.stage == Stage.ASSIMILATE
                && now >= active.deadline - EFFECT_TICKS) beginAssimilationEffect(target);
        else if (active.stage == Stage.ASSIMILATE_EFFECT && now >= active.deadline) assimilate(target);
    }

    private static void tryAutomaticEvent(ServerLevel level, SREGameWorldComponent game) {
        // 本局是否掷中由职业的随机事件统一判定（含 LAB 地图限制与禁用状态，状态维度通用），这里只管触发条件
        if (!BounsRoles.PURPLE_MONSTER.isEventEnabled()
                || active != null || game.getStartingPlayerCount() <= 18) return;
        if (game.getAllWithRole(BounsRoles.PURPLE_MONSTER).stream().anyMatch(id -> {
            ServerPlayer purple = findPlayer(level.getServer(), id);
            return purple != null && GameUtils.isPlayerAliveAndSurvival(purple);
        }))
            return;
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
            SRERole role = game.getRole(player);
            if (role == null || role.getMoodType() == SRERole.MoodType.FAKE
                    || SREPlayerMoodComponent.KEY.get(player).getMood() >= 0.40f) continue;
            if (player.getRandom().nextInt(100) < 5) {
                startEvent(player, false);
                return;
            }
        }
    }

    private static boolean startEvent(ServerPlayer target, boolean forced) {
        if (!forced && !SREGameWorldComponent.KEY.get(target.level()).isRunning())
            return false;
        Vec3 spawn = findSpawn(target);
        UUID close = closestPlayer(target);
        active = new Event(UUID.randomUUID(), target.getUUID(), close, spawn, target.level().getGameTime());
        active.targetPlayer = target;
        send(target, PurpleMonsterEventS2CPacket.Stage.DISGUISE, spawn, close, List.of());
        return true;
    }

    /**
     * Keeps the event entity a short distance in front of the player during
     * reveal and transformation stages.  The vertical look angle is ignored
     * so looking up or down cannot move the entity into the player's camera.
     */
    private static Vec3 monsterPosition(ServerPlayer target) {
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.001) horizontal = new Vec3(0.0, 0.0, 1.0);
        return target.position().add(horizontal.normalize().scale(2.5));
    }

    private static void beginQuestion(ServerPlayer target) {
        if (active == null || active.stage != Stage.DISGUISE) return;
        active.stage = Stage.REVEAL;
        active.deadline = target.level().getGameTime() + REVEAL_TICKS;
        applyControl(target);
        send(target, PurpleMonsterEventS2CPacket.Stage.REVEAL, monsterPosition(target), null, List.of());
    }

    private static void openQuestion(ServerPlayer target) {
        if (active == null || active.stage != Stage.REVEAL) return;
        active.stage = Stage.QUESTION;
        active.deadline = target.level().getGameTime() + QUESTION_TICKS;
        send(target, PurpleMonsterEventS2CPacket.Stage.QUESTION, monsterPosition(target), null, List.of());
    }

    private static void beginSelection(ServerPlayer target) {
        if (active == null || (active.stage != Stage.QUESTION && active.stage != Stage.SELECT)) return;
        active.stage = Stage.SELECT;
        active.deadline = target.level().getGameTime() + SELECT_TICKS;
        List<UUID> candidates = target.level().players().stream()
                .filter(p -> p != target && GameUtils.isPlayerAliveAndSurvival(p))
                .map(p -> p.getUUID()).toList();
        send(target, PurpleMonsterEventS2CPacket.Stage.SELECT, monsterPosition(target), null, candidates);
    }

    private static void selectVictim(ServerPlayer target, UUID selected) {
        ServerPlayer victim = findPlayer(target.getServer(), selected);
        if (victim == null || victim == target || !GameUtils.isPlayerAliveAndSurvival(victim)) return;
        // This is only the stage-two revenge kill. It must never advance assimilation progress.
        GameUtils.forceKillPlayer(victim, true, target,
                io.wifi.starrailexpress.game.GameConstants.DeathReasons.PURPLE_MONSTER_ASSIMILATION);
        active.stage = Stage.ASSIMILATE_TRANSFORM;
        active.deadline = target.level().getGameTime() + TRANSFORM_TICKS;
        send(target, PurpleMonsterEventS2CPacket.Stage.ASSIMILATE_TRANSFORM,
                monsterPosition(target), null, List.of());
    }

    private static void beginSecondForm(ServerPlayer target) {
        if (active == null || active.stage != Stage.ASSIMILATE_TRANSFORM) return;
        active.stage = Stage.ASSIMILATE;
        active.deadline = target.level().getGameTime() + ASSIMILATE_TICKS;
        send(target, PurpleMonsterEventS2CPacket.Stage.ASSIMILATE,
                monsterPosition(target), null, List.of());
    }

    private static void beginAssimilationEffect(ServerPlayer target) {
        if (active == null || active.stage != Stage.ASSIMILATE) return;
        active.stage = Stage.ASSIMILATE_EFFECT;
        send(target, PurpleMonsterEventS2CPacket.Stage.ASSIMILATE_EFFECT,
                monsterPosition(target), null, List.of());
    }

    private static void selectionTimeout(ServerPlayer target) {
        if (active == null || active.stage != Stage.SELECT) return;
        GameUtils.forceKillPlayer(target, true, target,
                io.wifi.starrailexpress.game.GameConstants.DeathReasons.PURPLE_MONSTER_ASSIMILATION);
        finish();
    }

    private static void assimilate(ServerPlayer target) {
        if (active == null || active.stage != Stage.ASSIMILATE_EFFECT) return;
        clearControl(target);
        clearPurpleMonsterHotbar(target);
        RoleUtils.changeRoleAndSendWelcome(target, BounsRoles.PURPLE_MONSTER);
        EntityDisguise.disguise(target, TMMEntities.PURPLE_MONSTER);
        SREArmorPlayerComponent.KEY.get(target).setArmor(3);
        sendProgress(target);
        target.displayClientMessage(Component.translatable("message.noellesroles.purple_monster.welcome"), false);
        finish();
    }

    public static boolean hasReachedGoal(ServerPlayer player) {
        return getAssimilationProgress(player) >= getAssimilationGoal(player);
    }

    private static int getAssimilationProgress(ServerPlayer player) {
        return SUCCESSFUL_ASSIMILATIONS.getOrDefault(player.getUUID(), 0);
    }

    private static void recordSuccessfulAssimilation(ServerPlayer player) {
        // Progress is advanced by a Purple Monster's mutual-gaze assimilation, not by
        // the initial transformation or the stage-two revenge kill.
        SUCCESSFUL_ASSIMILATIONS.merge(player.getUUID(), 1, Integer::sum);
    }

    private static int getAssimilationGoal(ServerPlayer player) {
        return Math.max(1, SREGameWorldComponent.KEY.get(player.serverLevel()).getStartingPlayerCount() / 15);
    }

    private static void syncProgress(ServerLevel level, SREGameWorldComponent game) {
        for (ServerPlayer player : level.players()) {
            if (game.isRole(player, BounsRoles.PURPLE_MONSTER) && GameUtils.isPlayerAliveAndSurvival(player))
                sendProgress(player);
        }
    }

    private static void sendProgress(ServerPlayer player) {
        ServerPlayNetworking.send(player, new org.agmas.noellesroles.packet.PurpleMonsterProgressS2CPacket(
                getAssimilationProgress(player), getAssimilationGoal(player)));
    }

    /** Clears only the hotbar, preserving the standard key and letter items. */
    private static void clearPurpleMonsterHotbar(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.isEmpty() && !stack.is(TMMItems.KEY) && !stack.is(TMMItems.LETTER))
                inventory.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
        }
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(inventory);
    }

    private static void applyControl(ServerPlayer player) {
        int duration = REVEAL_TICKS + QUESTION_TICKS + SELECT_TICKS + TRANSFORM_TICKS
                + ASSIMILATE_TICKS + 40;
        if (active == null) return;
        add(player, ModEffects.INVINCIBLE, duration);
        add(player, ModEffects.MOVE_BANED, duration);
        add(player, ModEffects.TURN_BANED, duration);
        add(player, ModEffects.USED_BANED, duration);
        add(player, ModEffects.INVENTORY_BANED, duration);
        add(player, ModEffects.SKILL_BANED, duration);
        add(player, ModEffects.CHAT_BAN, duration);
        active.controlApplied = true;
    }

    private static void add(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                            int duration) {
        if (active != null && !active.originalControlEffects.containsKey(effect))
            active.originalControlEffects.put(effect, player.getEffect(effect));
        player.addEffect(new MobEffectInstance(effect, duration, 0, false, false, false));
    }

    private static void clearControl(ServerPlayer player) {
        if (active == null || !active.controlApplied) return;
        player.removeEffect(ModEffects.INVINCIBLE);
        player.removeEffect(ModEffects.MOVE_BANED);
        player.removeEffect(ModEffects.TURN_BANED);
        player.removeEffect(ModEffects.USED_BANED);
        player.removeEffect(ModEffects.INVENTORY_BANED);
        player.removeEffect(ModEffects.SKILL_BANED);
        player.removeEffect(ModEffects.CHAT_BAN);
        active.originalControlEffects.forEach((effect, original) -> {
            if (original != null) player.addEffect(new MobEffectInstance(original));
        });
        active.controlApplied = false;
    }

    private static void finish() {
        if (active == null) return;
        if (active.targetPlayer != null) clearControl(active.targetPlayer);
        ServerPlayer target = active.targetPlayer;
        if (target != null) send(target, PurpleMonsterEventS2CPacket.Stage.CLOSE, Vec3.ZERO, null, List.of());
        active = null;
        MUTUAL_GAZE.clear();
    }

    private static void updateProximity(ServerLevel level) {
        List<ServerPlayer> players = level.players().stream()
                .filter(GameUtils::isPlayerAliveAndSurvival).toList();
        for (ServerPlayer target : players) {
            Map<UUID, Integer> times = PROXIMITY.computeIfAbsent(target.getUUID(), ignored -> new HashMap<>());
            for (ServerPlayer other : players) {
                if (target != other && target.distanceToSqr(other) <= 36.0)
                    times.merge(other.getUUID(), 1, Integer::sum);
            }
        }
    }

    private static UUID closestPlayer(ServerPlayer target) {
        Map<UUID, Integer> times = PROXIMITY.getOrDefault(target.getUUID(), Map.of());
        return target.level().players().stream()
                .filter(p -> p != target && GameUtils.isPlayerAliveAndSurvival(p))
                .max(Comparator.comparingInt(p -> times.getOrDefault(p.getUUID(), 0)))
                .map(p -> p.getUUID()).orElse(target.getUUID());
    }

    private static Vec3 findSpawn(ServerPlayer target) {
        Vec3 best = target.position();
        double bestDistance = -1;
        BlockPos origin = target.blockPosition();
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
            double distance = x * x + z * z;
            if (distance > 225 || distance < 4 || distance <= bestDistance) continue;
            BlockPos feet = new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z);
            if (target.level().isEmptyBlock(feet) && target.level().isEmptyBlock(feet.above())
                    && target.level().getBlockState(feet.below()).isFaceSturdy(target.level(), feet.below(), Direction.UP)) {
                best = Vec3.atBottomCenterOf(feet);
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void checkPurpleGaze(ServerLevel level, SREGameWorldComponent game) {
        List<ServerPlayer> purple = level.players().stream()
                .filter(p -> game.isRole(p, BounsRoles.PURPLE_MONSTER) && GameUtils.isPlayerAliveAndSurvival(p)).toList();
        Set<GazeKey> observedThisTick = new HashSet<>();
        for (ServerPlayer monster : purple) {
            for (ServerPlayer observer : level.players()) {
                if (observer == monster || !GameUtils.isPlayerAliveAndSurvival(observer)
                        || monster.distanceToSqr(observer) > 64.0) continue;
                GazeKey key = new GazeKey(monster.getUUID(), observer.getUUID());
                if (looksAt(observer, monster) && looksAt(monster, observer)) {
                    observedThisTick.add(key);
                    int ticks = MUTUAL_GAZE.merge(key, 1, Integer::sum);
                    if (ticks > 5 * 20) {
                        GameUtils.forceKillPlayer(observer, true, monster,
                                io.wifi.starrailexpress.game.GameConstants.DeathReasons.PURPLE_MONSTER_ASSIMILATION);
                        recordSuccessfulAssimilation(monster);
                        sendProgress(monster);
                        MUTUAL_GAZE.remove(key);
                    }
                } else MUTUAL_GAZE.remove(key);
            }
        }
        MUTUAL_GAZE.keySet().removeIf(key -> !observedThisTick.contains(key));
    }

    private static boolean looksAt(ServerPlayer from, ServerPlayer to) {
        Vec3 start = from.getEyePosition();
        Vec3 direction = to.getEyePosition().subtract(start);
        if (direction.lengthSqr() < 0.01) return true;
        if (from.getLookAngle().dot(direction.normalize()) < Math.cos(Math.toRadians(25))) return false;
        HitResult hit = from.level().clip(new ClipContext(start, to.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, from));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static ServerPlayer findPlayer(MinecraftServer server, UUID id) {
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static void send(ServerPlayer target, PurpleMonsterEventS2CPacket.Stage stage, Vec3 pos,
                             UUID skin, List<UUID> candidates) {
        if (target != null) ServerPlayNetworking.send(target,
                new PurpleMonsterEventS2CPacket(active.id, stage, pos.x, pos.y, pos.z, skin, candidates));
    }

    private enum Stage {
        DISGUISE, REVEAL, QUESTION, SELECT, ASSIMILATE_TRANSFORM, ASSIMILATE, ASSIMILATE_EFFECT
    }

    private record GazeKey(UUID monster, UUID observer) {}

    private static final class Event {
        private final UUID id;
        private final UUID target;
        private final UUID closePlayer;
        private final Vec3 spawn;
        private final long startedAt;
        private Stage stage = Stage.DISGUISE;
        private long deadline;
        private ServerPlayer targetPlayer;
        private final Map<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, MobEffectInstance>
                originalControlEffects = new HashMap<>();
        private boolean controlApplied;

        private Event(UUID id, UUID target, UUID closePlayer, Vec3 spawn, long startedAt) {
            this.id = id;
            this.target = target;
            this.closePlayer = closePlayer;
            this.spawn = spawn;
            this.startedAt = startedAt;
        }
    }
}
