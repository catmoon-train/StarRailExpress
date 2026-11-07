package io.wifi.starrailexpress.training;

import carpet.patches.EntityPlayerMPFake;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 训练会话独占的 Carpet 玩家；所有入口仅在服务器线程调用。 */
public final class CarpetTrainingBots implements AutoCloseable {
    private static final int MAX_UNRESOLVED = 256;
    private static final Map<MinecraftServer, Map<String, TrainingBotSlot.Attempt>> RESERVATIONS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Set<CarpetTrainingBots>> ACTIVE = new IdentityHashMap<>();

    static {
        // 常量注册一次。会话关闭后仍追踪异步回调，不依赖已被丢弃的 session.tick()。
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            var sessions = ACTIVE.get(server);
            if (sessions != null) for (var bots : new ArrayList<>(sessions)) {
                if (!bots.session.authorized() || bots.session.owner() == null) bots.close();
            }
            sweep(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var sessions = ACTIVE.get(server);
            if (sessions != null) for (var bots : new ArrayList<>(sessions)) bots.close();
            sweep(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            RESERVATIONS.remove(server);
            ACTIVE.remove(server);
        });
    }

    private final TrainingSession session;
    private final String token = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    private final Map<Integer, Entry> slots = new LinkedHashMap<>();
    private final Set<TrainingBotSlot.Attempt> issued = new HashSet<>();
    private final boolean carpet = FabricLoader.getInstance().isModLoaded("carpet");
    private long generation;
    private int serial;
    private boolean closed;

    public CarpetTrainingBots(TrainingSession session) {
        this.session = session;
        ACTIVE.computeIfAbsent(session.server(), ignored -> new HashSet<>()).add(this);
    }

    /** 数量、职业、行为和装备修改都以 settings 为权威，不把在线状态写入设置。 */
    public void reconcile() {
        if (closed) return;
        session.settings().normalize();
        int count = session.settings().enemyCount;
        for (var iterator = slots.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getKey() >= count) { retire(entry.getValue()); iterator.remove(); session.changed(); }
        }
        for (int i = 0; i < count; i++) {
            Entry entry = slots.get(i);
            if (entry == null) {
                entry = new Entry(i, generation);
                slots.put(i, entry);
                session.changed();
            }
            if (!carpet) { setError(entry, "training.error.carpet_missing"); continue; }
            if (!session.authorized()) { stop(entry); continue; }
            Config desired = Config.of(session.settings().bot(i));
            ServerPlayer bot = online(entry);
            if (bot != null && TrainingRules.isAlive(bot) && !desired.equals(entry.applied)
                    && !desired.equals(entry.attempted)) configure(entry, bot);
        }
        sweep(session.server());
        var reserved = RESERVATIONS.get(session.server());
        issued.removeIf(a -> reserved == null || reserved.get(a.name) != a);
    }

    public void tick() {
        if (closed) return;
        if (!session.authorized() || session.owner() == null) {
            close();
            return;
        }
        reconcile();
        long now = now();
        int creates = 0;
        for (Entry entry : slots.values()) {
            if (!carpet) continue;
            if (entry.state.pending != null) {
                var attempt = entry.state.pending;
                ServerPlayer found = session.server().getPlayerList().getPlayerByName(attempt.name);
                if (found != null) {
                    if (!(found instanceof EntityPlayerMPFake)) fail(entry, "training.error.bot_name_conflict");
                    else if (entry.state.accept(attempt, found.getUUID(), generation)) {
                        entry.reservation = attempt;
                        entry.player = found;
                        moveToSpawn(entry, found);
                        configure(entry, found);
                        session.changed();
                    }
                } else if (entry.state.timedOut(now)) fail(entry, "training.error.bot_spawn_timeout");
            }
            if (entry.state.uuid == null) {
                if (creates < 2 && entry.state.canCreate(now)) { create(entry); creates++; }
                continue;
            }
            ServerPlayer bot = online(entry);
            boolean alive = bot != null && TrainingRules.isAlive(bot) && !bot.isRemoved();
            if (!alive) {
                stop(entry);
                if (entry.deadAt < 0) { entry.deadAt = now; session.changed(); }
                if (session.settings().autoRespawn && !session.settings().paused && entry.reviveFailures < 3
                        && now >= Math.max(entry.deadAt + session.settings().respawnDelay * 20L, entry.reviveAt)) revive(entry);
            } else {
                if (entry.deadAt >= 0) { entry.deadAt = -1; entry.reviveFailures = 0; session.changed(); }
                if (bot.serverLevel() != session.level() || bot.getY() < 55
                        || !TrainingLayout.box(TrainingLayout.get().bounds).inflate(8).contains(bot.position())) {
                    stop(entry); moveToSpawn(entry, bot);
                }
                entry.player = bot;
                if (!entry.state.error.isEmpty()) { stop(entry); continue; }
                try { entry.controller.tick(session, bot, session.settings().bot(entry.state.slot), now); }
                catch (RuntimeException ex) { stop(entry); setError(entry, "training.error.bot_control_failed"); }
            }
        }
    }

    private void create(Entry entry) {
        Map<String, TrainingBotSlot.Attempt> reserved = reservations(session.server());
        if (reserved.values().stream().filter(a -> a.submitted && !a.observed).count() >= MAX_UNRESOLVED
                || serial >= 36 * 36 * 36 * 36 - 8) {
            entry.state.attempts = TrainingBotSlot.MAX_ATTEMPTS;
            setError(entry, "training.error.bot_pending_limit");
            return;
        }
        String name = null;
        for (int tries = 0; tries < 8; tries++) {
            String candidate = TrainingBotSlot.name(token, entry.state.slot, serial++);
            if (!reserved.containsKey(candidate) && session.server().getPlayerList().getPlayerByName(candidate) == null) {
                name = candidate;
                break;
            }
        }
        if (name == null) { entry.state.attempts++; fail(entry, "training.error.bot_name_conflict"); return; }
        var attempt = entry.state.begin(name, now());
        issued.add(attempt);
        reserved.put(name, attempt);
        Vec3 spawn = session.area().botSpawn(entry.state.slot);
        try {
            // 此版本返回 boolean，不返回 future。true 仅表示排队，必须按保留名字轮询。
            attempt.submitted = true;
            boolean accepted = EntityPlayerMPFake.createFake(name, session.server(), spawn, session.area().yaw(), 0,
                    session.level().dimension(), GameType.ADVENTURE, false);
            if (!accepted) { attempt.submitted = false; fail(entry, "training.error.bot_spawn_failed"); }
        } catch (RuntimeException | LinkageError ex) {
            fail(entry, "training.error.bot_spawn_failed");
        }
        session.changed();
    }

    private void configure(Entry entry, ServerPlayer bot) {
        stop(entry);
        try {
            Config config = Config.of(session.settings().bot(entry.state.slot));
            entry.attempted = config;
            session.assign(bot, config.role, true);
            equip(bot, config.equipment);
            entry.applied = config;
            entry.state.error = "";
            entry.controller = new TrainingBotController(entry.state.slot);
            session.changed();
        } catch (RuntimeException ex) { setError(entry, "training.error.bot_config_failed"); }
    }

    /** 只在初始化/重置/重配调用，绝不每 tick 补充耐久或弹药。保留职业道具。 */
    private static void equip(ServerPlayer bot, String equipment) {
        var inventory = bot.getInventory();
        var weapon = "gun".equals(equipment) ? TMMItems.REVOLVER : TMMItems.KNIFE;
        int found = -1;
        for (int i = 0; i < inventory.items.size(); i++) if (inventory.getItem(i).is(weapon)) { found = i; break; }
        if (found > 0) {
            ItemStack old = inventory.getItem(0);
            inventory.setItem(0, inventory.getItem(found));
            inventory.setItem(found, old);
        } else if (found < 0) {
            ItemStack displaced = inventory.getItem(0);
            inventory.setItem(0, weapon.getDefaultInstance());
            if (!displaced.isEmpty() && !inventory.add(displaced)) bot.drop(displaced, false);
        }
        inventory.selected = 0;
        inventory.setChanged();
        bot.containerMenu.broadcastChanges();
    }

    private void revive(Entry entry) {
        ServerPlayer old = online(entry);
        if (old == null) old = entry.player; // Carpet 原版死亡会断开连接，仍交给父会话处理旧实例。
        if (old == null) { retry(entry); return; }
        stop(entry);
        try {
            ServerPlayer revived = session.revive(old, entry.state.slot);
            if (!(revived instanceof EntityPlayerMPFake) || !old.getUUID().equals(revived.getUUID())
                    || !entry.reservation.name.equals(revived.getGameProfile().getName())
                    || revived.isRemoved() || !TrainingRules.isAlive(revived)
                    || session.server().getPlayerList().getPlayer(revived.getUUID()) != revived)
                throw new IllegalStateException("Training revive did not return a connected owned fake");
            entry.player = revived;
            entry.state.uuid = revived.getUUID();
            entry.deadAt = -1;
            entry.reviveFailures = 0;
            moveToSpawn(entry, revived);
            // 父会话 revive 已执行完整职业重置；这里只应用该槽位的武器，避免重复 onInit。
            Config config = Config.of(session.settings().bot(entry.state.slot));
            equip(revived, config.equipment);
            entry.applied = config;
            entry.state.error = "";
            entry.controller = new TrainingBotController(entry.state.slot);
            session.changed();
        } catch (RuntimeException ex) {
            entry.reviveFailures++;
            entry.reviveAt = now() + 40L * entry.reviveFailures;
            setError(entry, "training.error.bot_revive_failed");
        }
    }

    public void reset() {
        if (closed) return;
        generation++;
        for (Entry entry : slots.values()) retire(entry);
        slots.clear();
        reconcile();
        session.changed();
    }

    /** 仅面板显式区域传送/复位使用，不用于 AI 追击或卡住保底。 */
    public void teleport() {
        if (closed) return;
        for (Entry entry : slots.values()) {
            ServerPlayer bot = online(entry);
            if (bot != null && TrainingRules.isAlive(bot)) { stop(entry); moveToSpawn(entry, bot); }
        }
        session.changed();
    }

    public void operate(int slot, String operation, TrainingSettings.BotSettings override) {
        if (closed || slot < 0 || slot >= session.settings().enemyCount || operation == null) return;
        reconcile();
        Entry entry = slots.get(slot);
        switch (operation) {
            case "remove" -> {
                // 删除中间槽位时同时压缩覆盖配置；旧尾部请求全部取消，绝不借用旧名字。
                Map<Integer, TrainingSettings.BotSettings> shifted = new LinkedHashMap<>();
                session.settings().overrides.forEach((index, config) -> {
                    if (index < slot) shifted.put(index, config);
                    else if (index > slot) shifted.put(index - 1, config);
                });
                session.settings().overrides = shifted;
                session.settings().enemyCount--;
                for (var iterator = slots.entrySet().iterator(); iterator.hasNext();) {
                    var tail = iterator.next();
                    if (tail.getKey() >= slot) { retire(tail.getValue()); iterator.remove(); }
                }
                reconcile();
            }
            case "revive", "retry", "reset" -> {
                entry.reviveFailures = 0;
                if (entry.state.uuid == null) retry(entry);
                else revive(entry);
            }
            case "inherit" -> {
                session.settings().overrides.remove(slot);
                entry.applied = null;
                entry.attempted = null;
                entry.state.error = "";
                reconcile();
            }
            case "configure", "apply", "settings", "role", "behavior", "equipment" -> {
                if (override == null) return;
                Config config = Config.of(override);
                session.settings().overrides.put(slot, config.settings());
                entry.state.error = "";
                entry.applied = null;
                entry.attempted = null;
                reconcile();
            }
            default -> { return; }
        }
        session.changed();
    }

    private void retry(Entry entry) {
        cancel(entry.state.retry(now()));
        entry.state.uuid = null;
        entry.state.error = "";
        entry.deadAt = -1;
        entry.applied = null;
        entry.attempted = null;
        session.changed();
    }

    public ListTag snapshot() {
        ListTag list = new ListTag();
        for (Entry entry : slots.values()) {
            var tag = new CompoundTag();
            var config = Config.of(session.settings().bot(entry.state.slot));
            ServerPlayer bot = online(entry);
            boolean alive = bot != null && TrainingRules.isAlive(bot) && !bot.isRemoved();
            String status = !entry.state.error.isEmpty() ? "error" : entry.state.pending != null ? "pending" : alive ? "alive"
                    : entry.state.uuid != null ? "dead" : "pending";
            tag.putInt("slot", entry.state.slot);
            tag.putString("name", entry.reservation != null ? entry.reservation.name
                    : entry.state.pending != null ? entry.state.pending.name : "");
            tag.putString("uuid", entry.state.uuid == null ? "" : entry.state.uuid.toString());
            tag.putString("role", config.role);
            tag.putString("behavior", config.behavior);
            tag.putString("equipment", config.equipment);
            tag.putString("state", status);
            tag.putString("status", status);
            tag.putBoolean("alive", alive);
            tag.putBoolean("pending", entry.state.pending != null);
            tag.putString("error", entry.state.error);
            tag.putInt("attempts", entry.state.attempts);
            list.add(tag);
        }
        return list;
    }

    public int alive() {
        int count = 0;
        for (Entry entry : slots.values()) {
            ServerPlayer bot = online(entry);
            if (bot != null && !bot.isRemoved() && TrainingRules.isAlive(bot)) count++;
        }
        return count;
    }

    public int pending() {
        return (int) slots.values().stream().filter(e -> e.state.pending != null).count();
    }

    public boolean owns(ServerPlayer player) {
        if (!carpet || !(player instanceof EntityPlayerMPFake)) return false;
        return issued.stream().anyMatch(a -> a.matches(player.getGameProfile().getName(), player.getUUID(), true));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        for (Entry entry : slots.values()) retire(entry);
        slots.clear();
        var active = ACTIVE.get(session.server());
        if (active != null) { active.remove(this); if (active.isEmpty()) ACTIVE.remove(session.server()); }
        sweep(session.server());
        session.changed();
    }

    private ServerPlayer online(Entry entry) {
        if (entry.state.uuid == null) return null;
        ServerPlayer bot = session.server().getPlayerList().getPlayer(entry.state.uuid);
        return bot instanceof EntityPlayerMPFake && entry.reservation != null
                && entry.reservation.name.equals(bot.getGameProfile().getName()) ? bot : null;
    }

    private void moveToSpawn(Entry entry, ServerPlayer bot) {
        Vec3 position = session.area().botSpawn(entry.state.slot);
        bot.teleportTo(session.level(), position.x, position.y, position.z, session.area().yaw(), 0);
    }

    private void stop(Entry entry) {
        if (entry.player instanceof EntityPlayerMPFake) entry.controller.stop(entry.player);
    }

    private void setError(Entry entry, String key) {
        boolean changed = !key.equals(entry.state.error);
        entry.state.error = key;
        if (changed) { session.error(key); session.changed(); }
    }

    private void fail(Entry entry, String key) {
        cancel(entry.state.fail(key, now()));
        session.error(key);
        session.changed();
    }

    private void retire(Entry entry) {
        stop(entry);
        cancel(entry.state.cancel());
        cancel(entry.reservation);
    }

    private static void cancel(TrainingBotSlot.Attempt attempt) {
        if (attempt != null) attempt.cancelled = true;
    }

    private long now() { return session.level().getGameTime(); }

    private static Map<String, TrainingBotSlot.Attempt> reservations(MinecraftServer server) {
        return RESERVATIONS.computeIfAbsent(server, ignored -> new HashMap<>());
    }

    /** 无 future 句柄的 Carpet 版本：未观察到回调的取消保留项必须留到停服。 */
    private static void sweep(MinecraftServer server) {
        var reserved = RESERVATIONS.get(server);
        if (reserved == null) return;
        for (var iterator = reserved.values().iterator(); iterator.hasNext();) {
            var attempt = iterator.next();
            if (!attempt.cancelled) continue;
            if (!attempt.submitted) { iterator.remove(); continue; }
            ServerPlayer player = attempt.uuid == null ? server.getPlayerList().getPlayerByName(attempt.name)
                    : server.getPlayerList().getPlayer(attempt.uuid);
            if (player instanceof EntityPlayerMPFake fake && attempt.matches(fake.getGameProfile().getName(), fake.getUUID(), true)) {
                attempt.uuid = fake.getUUID();
                attempt.observed = true;
                TrainingBotController.actions(fake).stopAll();
                var game = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(fake.level());
                var role = game.getRole(fake);
                if (role != null) org.agmas.harpymodloader.events.ModdedRoleRemoved.EVENT.invoker().removeModdedRole(fake, role);
                org.agmas.noellesroles.utils.RoleUtils.removeAllModifiers(fake);
                game.removeRole(fake);
                io.wifi.starrailexpress.game.GameUtils.resetPlayer(fake);
                // Carpet 的 duplicate_login 分支同步移除，不把 kill 排队到下一轮复活之后。
                fake.kill(Component.translatable("multiplayer.disconnect.duplicate_login"));
            } else if (attempt.canForget(player != null)) iterator.remove();
        }
        if (reserved.isEmpty()) RESERVATIONS.remove(server);
    }

    private record Config(String role, String behavior, String equipment) {
        static Config of(TrainingSettings.BotSettings settings) {
            return new Config(settings.role == null ? "starrailexpress:killer" : settings.role,
                    TrainingSettings.validBehavior(settings.behavior), TrainingSettings.validEquipment(settings.equipment));
        }
        TrainingSettings.BotSettings settings() { return new TrainingSettings.BotSettings(role, behavior, equipment); }
    }

    private static final class Entry {
        final TrainingBotSlot state;
        TrainingBotSlot.Attempt reservation;
        TrainingBotController controller;
        ServerPlayer player;
        Config applied;
        Config attempted;
        long deadAt = -1;
        long reviveAt;
        int reviveFailures;

        Entry(int slot, long generation) {
            state = new TrainingBotSlot(slot, generation);
            controller = new TrainingBotController(slot);
        }
    }
}
