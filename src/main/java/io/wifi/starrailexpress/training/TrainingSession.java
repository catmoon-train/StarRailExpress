package io.wifi.starrailexpress.training;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 每个会话独占一个训练世界；所有方法都在服务器线程执行。 */
public final class TrainingSession {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final MinecraftServer server;
    private final UUID ownerId;
    private final ServerLevel level;
    private final boolean dedicated;
    private ServerPlayer lastOwner;
    private final Path settingsPath;
    private final TrainingCatalogue catalogue = new TrainingCatalogue();
    private final CarpetTrainingBots bots;
    private final TrainingAssistance assistance = new TrainingAssistance();
    private TrainingSettings settings;
    private boolean dirty;
    private boolean closed;
    private long lastFullSnapshot = -100;
    private long ownerDeathAt = -1;
    private long lastCleanup;
    private String message = "";
    private final TrainingRequestLimiter limiter = new TrainingRequestLimiter();

    public TrainingSession(MinecraftServer server, ServerPlayer owner) {
        this(server, owner, server.overworld(), false);
        start(owner);
    }
    public TrainingSession(MinecraftServer server, ServerPlayer owner, ServerLevel level, boolean dedicated) {
        this.server = server;
        this.ownerId = owner.getUUID();
        this.level = Objects.requireNonNull(level);
        this.dedicated = dedicated;
        this.lastOwner = owner;
        Path root = server.getWorldPath(LevelResource.ROOT);
        settingsPath = dedicated ? root.resolve("training-settings").resolve(ownerId + ".json") : root.resolve("training-settings.json");
        settings = loadSettings();
        settings.myRole = resolveRole(settings.myRole, false).identifier().toString();
        settings.enemyRole = resolveRole(settings.enemyRole, true).identifier().toString();
        normalizeModifierSettings();
        bots = new CarpetTrainingBots(this);
    }
    /** 专服先把会话记入租约，再初始化；失败时仍可通过同一会话完成清理。 */
    void start(ServerPlayer owner) {
        try { initialize(owner); }
        catch (RuntimeException e) {
            try { close(); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    public MinecraftServer server() { return server; }
    public ServerLevel level() { return level; }
    public boolean dedicated() { return dedicated; }
    public boolean authorized() { return dedicated ? TrainingRules.isDedicatedTrainingServer(server) : server.isSingleplayer(); }
    public ServerPlayer owner() {
        ServerPlayer current = server.getPlayerList().getPlayer(ownerId);
        if (current != null && current.connection == lastOwner.connection) lastOwner = current;
        return current != null && current.connection == lastOwner.connection ? current : null;
    }
    public TrainingSettings settings() { return settings; }
    public TrainingLayout.Area area() { return TrainingLayout.get().area(settings.area); }
    public boolean owns(ServerPlayer player) { return bots.owns(player); }

    /**
     * 训练场假人的「出生点房间」就是它的初始点位（按当前训练区域与槽位计算）。
     * 非本会话假人返回 null。
     */
    public @Nullable Vec3 botSpawn(ServerPlayer player) {
        Integer slot = bots.slotOf(player);
        return slot == null ? null : area().botSpawn(slot);
    }

    public void changed() { dirty = true; }
    public void error(String key) { message = key; dirty = true; }

    private TrainingSettings loadSettings() {
        try {
            if (Files.isRegularFile(settingsPath) && Files.size(settingsPath) <= 262_144) {
                var loaded = GSON.fromJson(Files.readString(settingsPath, StandardCharsets.UTF_8), TrainingSettings.class);
                if (loaded != null) { loaded.normalize(); return loaded; }
            }
        } catch (Exception e) { SRE.LOGGER.warn("无法读取训练设置，使用默认值", e); }
        return new TrainingSettings();
    }
    private void save() {
        settings.normalize();
        try {
            Files.createDirectories(settingsPath.getParent());
            Path temp = settingsPath.resolveSibling(settingsPath.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(settings), StandardCharsets.UTF_8);
            try { Files.move(temp, settingsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, settingsPath, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) { SRE.LOGGER.error("无法保存训练设置", e); error("training.sre.error.save"); }
    }
    private void initialize(ServerPlayer owner) {
        if (dedicated) cleanupCampEntities();
        if (!MapManager.loadMap(level(), "training")) throw new IllegalStateException("Training map configuration could not load");
        if (dedicated) {
            AreasWorldComponent.KEY.get(level()).setReadyArea(TrainingLayout.get().area("lobby").box());
            AreasWorldComponent.KEY.get(level()).sync();
            var spawn = TrainingLayout.get().area("lobby").spawn();
            level().setDefaultSpawnPos(net.minecraft.core.BlockPos.containing(spawn), TrainingLayout.get().area("lobby").yaw());
        }
        var game = SREGameWorldComponent.KEY.get(level());
        game.setGameMode(SREGameModes.GAME_MODES.get(TrainingGameMode.ID));
        game.setGameStatus(SREGameWorldComponent.GameStatus.ACTIVE);
        game.setBound(false);
        game.setJumpAvailable(true);
        game.isSkillAvailable = true;
        game.setStartingPlayerCount(1 + settings.enemyCount);
        game.setFade(0);
        game.sync();
        GameUtils.isStartingGame = false;
        GameUtils.isGameStarted = true;
        GameUtils.startTime = System.currentTimeMillis();
        var time = SREGameTimeComponent.KEY.get(level());
        time.reset(); time.setTime(24 * 60 * 60 * 20);
        var auto = AutoStartComponent.KEY.get(level());
        auto.setStartTime(0); auto.setTime(0);
        assign(owner, settings.myRole, false);
        move(owner, TrainingLayout.get().area("lobby").spawn(), TrainingLayout.get().area("lobby").yaw());
        catalogue.rebuild(owner);
        bots.reconcile();
        snapshot(true);
    }

    public SRERole resolveRole(String id, boolean bot) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        SRERole role = key == null ? null : TMMRoles.ROLES.get(key);
        return role == null ? (bot ? TMMRoles.KILLER : TMMRoles.CIVILIAN) : role;
    }

    /** 先执行旧职业移除，再走统一重置和新职业分配，避免旧 RoleData 残留。 */
    public void assign(ServerPlayer player, String id, boolean bot) {
        if (!bot) assistance.clear(level().getGameTime());
        var game = SREGameWorldComponent.KEY.get(player.level());
        var old = game.getRole(player);
        if (old != null) ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, old);
        RoleUtils.removeAllModifiers(player);
        org.agmas.noellesroles.role_data.innocence.DisabledPatientRoleData.clearGrantedEffects(player);
        game.removeRole(player);
        GameUtils.resetPlayer(player);
        player.removeAllEffects();
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().eat(20, 1.0F);
        var role = resolveRole(id, bot);
        RoleUtils.changeRole(player, role, false, false, false, false, true);
        if (!bot) TrainingModifiers.addConfigured(player, settings.myModifiers);
        SREAbilityPlayerComponent.KEY.get(player).resetAllCooldowns();
        SREPlayerMoodComponent.KEY.get(player).setMood(1.0F);
        if (!bot) SREPlayerShopComponent.KEY.get(player).setBalance(1000);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (!bot) updateAssistance();
    }

    /** 复用仍在线的假玩家；与 tmm:game revive 使用同一复活 API，不重新登录。 */
    void reviveBot(ServerPlayer player, int slot) {
        if (closed || !authorized() || slot < 0 || slot >= settings.enemyCount || !bots.owns(player)
                || server.getPlayerList().getPlayer(player.getUUID()) != player || player.isRemoved())
            throw new IllegalStateException("Cannot revive an unowned or disconnected training bot");
        Vec3 pos = area().botSpawn(slot);
        move(player, pos, area().yaw());
        GameUtils.revivePlayer(player, pos.x, pos.y, pos.z);
        assign(player, settings.bot(slot).role, true);
        TrainingModifiers.addConfigured(player, settings.bot(slot).modifiers);
        if (!TrainingRules.isAlive(player)) throw new IllegalStateException("Training bot revival did not complete");
        // 只扫描本营已加载实体；按死者 UUID 删除所有旧尸体，不清理其他人的尸体。
        List<PlayerBodyEntity> bodies = new ArrayList<>();
        for (var entity : level().getAllEntities()) {
            if (entity instanceof PlayerBodyEntity body && player.getUUID().equals(body.getPlayerUuid())) bodies.add(body);
        }
        bodies.forEach(Entity::discard);
    }

    private ServerPlayer reviveOwner(ServerPlayer player) {
        assistance.clear(level().getGameTime());
        if (player.isDeadOrDying()) player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        var lobby = TrainingLayout.get().area("lobby");
        Vec3 pos = lobby.spawn();
        move(player, pos, lobby.yaw());
        GameUtils.revivePlayer(player, pos.x, pos.y, pos.z);
        assign(player, settings.myRole, false);
        ownerDeathAt = -1;
        changed();
        return player;
    }

    private void updateAssistance() {
        var owner = owner();
        assistance.update(!closed && authorized() && owner != null && owner.serverLevel() == level() ? owner : null,
                settings, level().getGameTime());
    }

    private void move(ServerPlayer player, Vec3 pos, float yaw) {
        player.stopRiding();
        player.teleportTo(level(), pos.x, pos.y, pos.z, yaw, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    public void tick() {
        if (closed) return;
        ServerPlayer owner = owner();
        if (owner == null) { assistance.clear(level().getGameTime()); return; }
        if (owner.serverLevel() != level() || owner.getY() < 55 || !TrainingLayout.box(TrainingLayout.get().bounds).inflate(8).contains(owner.position()))
            move(owner, TrainingLayout.get().area("lobby").spawn(), 0);
        if (!TrainingRules.isAlive(owner)) {
            if (ownerDeathAt < 0) { ownerDeathAt = level().getGameTime(); changed(); }
            if (settings.autoRespawn && level().getGameTime() - ownerDeathAt >= settings.respawnDelay * 20L)
                reviveOwner(owner);
        } else ownerDeathAt = -1;
        updateAssistance();
        bots.tick();
        if (level().getGameTime() - lastCleanup >= 600) { cleanup(false); lastCleanup = level().getGameTime(); }
        if (dirty && level().getGameTime() % 5 == 0) { snapshot(false); dirty = false; }
    }

    public boolean handle(ServerPlayer actor, TrainingActionPayload payload) {
        if (closed || !actor.getUUID().equals(ownerId) || actor.serverLevel() != level()) return false;
        long tick = level().getGameTime();
        if (!limiter.allow(tick)) return false;
        CompoundTag data = payload.data();
        message = "training.sre.status.applied";
        switch (payload.action()) {
            case "snapshot" -> {
                boolean full = data.getBoolean("full") && tick - lastFullSnapshot >= 20;
                if (full) catalogue.rebuild(actor);
                snapshot(full); return true;
            }
            case "give" -> {
                ItemStack stack = catalogue.stack(data.getString("key"), data.getInt("count"));
                if (stack.isEmpty()) { error("training.sre.error.item"); break; }
                if (!actor.getInventory().add(stack)) { error("training.sre.error.inventory"); break; }
                actor.containerMenu.broadcastChanges();
            }
            case "kit" -> kit(actor, data.getString("id"));
            case "role" -> {
                String id = data.getString("id");
                var key = ResourceLocation.tryParse(id);
                if (key == null || !TMMRoles.ROLES.containsKey(key)) { error("training.sre.error.role"); break; }
                settings.myRole = id; normalizeModifierSettings(); assign(actor, id, false);
            }
            case "reset_self" -> { actor = reviveOwner(actor); }
            case "heal" -> {
                if (!TrainingRules.isAlive(actor)) actor = reviveOwner(actor);
                actor.setHealth(actor.getMaxHealth()); actor.getFoodData().eat(20, 1);
                SREPlayerPoisonComponent.KEY.get(actor).clear();
                SREPlayerMoodComponent.KEY.get(actor).setMood(1.0F);
            }
            case "money" -> {
                var shop = SREPlayerShopComponent.KEY.get(actor);
                shop.setBalance(addTrainingCoins(shop.balance));
            }
            case "cooldown" -> TrainingAssistance.resetCooldowns(actor);
            case "configure" -> configure(data);
            case "revive_bots" -> bots.reviveAll();
            case "modifier" -> modify(actor, data);
            case "bot" -> {
                int slot = data.getInt("slot");
                if (slot < 0 || slot >= settings.enemyCount) return false;
                var old = settings.bot(slot);
                var override = new TrainingSettings.BotSettings(data.contains("role") ? data.getString("role") : old.role,
                        data.contains("behavior") ? data.getString("behavior") : old.behavior,
                        data.contains("equipment") ? data.getString("equipment") : old.equipment, old.modifiers);
                if (!TMMRoles.ROLES.containsKey(ResourceLocation.tryParse(override.role))) { error("training.sre.error.role"); break; }
                override.normalize();
                override.modifiers = TrainingModifiers.compatible(override.modifiers, resolveRole(override.role, true));
                bots.operate(slot, data.getString("operation"), override);
            }
            case "teleport" -> {
                String id = data.getString("area");
                if (!Set.of("lobby", "range", "arena", "train").contains(id)) return false;
                var zone = TrainingLayout.get().area(id);
                move(actor, zone.spawn(), zone.yaw());
                if (!id.equals("lobby")) { settings.area = id; bots.teleport(); }
            }
            case "cleanup" -> cleanup(true);
            case "reset" -> {
                bots.reset(); cleanup(true); reviveOwner(actor); bots.reconcile();
            }
            default -> { return false; }
        }
        save(); changed(); return true;
    }

    private void normalizeModifierSettings() {
        settings.myModifiers = TrainingModifiers.compatible(settings.myModifiers, resolveRole(settings.myRole, false));
        settings.enemyModifiers = TrainingModifiers.compatible(settings.enemyModifiers, resolveRole(settings.enemyRole, true));
        settings.overrides.values().forEach(config ->
                config.modifiers = TrainingModifiers.compatible(config.modifiers, resolveRole(config.role, true)));
    }

    /** 所有候选配置先完整验证，整组修改不清空职业/行为的独立覆盖。 */
    private void modify(ServerPlayer actor, CompoundTag data) {
        String target = data.getString("target"), operation = data.getString("operation"), id = data.getString("id");
        if (!Set.of("self", "batch", "bot").contains(target) || !Set.of("add", "remove", "clear").contains(operation)
                || !operation.equals("clear") && (id.length() > 128 || TrainingModifiers.resolve(id) == null)) {
            error("training.sre.error.modifier"); return;
        }
        if (!operation.equals("clear")) id = TrainingModifiers.resolve(id).identifier().toString();
        try {
            if (target.equals("self")) {
                var selected = TrainingModifiers.edit(TrainingModifiers.actual(actor), operation, id,
                        SREGameWorldComponent.KEY.get(actor.level()).getRole(actor));
                TrainingModifiers.replace(actor, selected);
                settings.myModifiers = new ArrayList<>(selected);
                updateAssistance();
                return;
            }
            if (target.equals("bot")) {
                int slot = data.contains("slot") ? data.getInt("slot") : -1;
                if (slot < 0 || slot >= settings.enemyCount) { error("training.sre.error.modifier"); return; }
                var old = settings.bot(slot);
                var selected = TrainingModifiers.edit(old.modifiers, operation, id, resolveRole(old.role, true));
                settings.overrides.put(slot, new TrainingSettings.BotSettings(old.role, old.behavior, old.equipment, selected));
                bots.updateModifiers(slot);
                return;
            }
            var defaults = TrainingModifiers.edit(settings.enemyModifiers, operation, id, resolveRole(settings.enemyRole, true));
            Map<Integer, TrainingSettings.BotSettings> replacements = new LinkedHashMap<>();
            for (var entry : settings.overrides.entrySet()) {
                var old = entry.getValue();
                var selected = TrainingModifiers.edit(old.modifiers, operation, id, resolveRole(old.role, true));
                replacements.put(entry.getKey(), new TrainingSettings.BotSettings(old.role, old.behavior, old.equipment, selected));
            }
            settings.enemyModifiers = defaults;
            settings.overrides = replacements;
            for (int slot = 0; slot < settings.enemyCount; slot++) bots.updateModifiers(slot);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && Set.of("training.sre.error.modifier", "training.sre.error.modifier_role", "training.sre.error.modifier_limit").contains(ex.getMessage()))
                error(ex.getMessage());
            else throw ex;
        }
    }

    /** 训练金币只累加，不把较高余额重设为初始值；饱和加法避免整数溢出。 */
    static int addTrainingCoins(int balance) {
        return (int) Math.min(Integer.MAX_VALUE, (long) balance + 1000L);
    }

    private void configure(CompoundTag data) {
        String oldArea = settings.area;
        boolean batch = data.contains("enemyRole") || data.contains("behavior") || data.contains("equipment");
        if (data.contains("enemyCount")) settings.enemyCount = data.getInt("enemyCount");
        if (data.contains("enemyRole")) settings.enemyRole = resolveRole(data.getString("enemyRole"), true).identifier().toString();
        if (data.contains("behavior")) settings.behavior = data.getString("behavior");
        if (data.contains("equipment")) settings.equipment = data.getString("equipment");
        if (data.contains("area")) settings.area = data.getString("area");
        if (data.contains("paused")) settings.paused = data.getBoolean("paused");
        if (data.contains("autoRespawn")) settings.autoRespawn = data.getBoolean("autoRespawn");
        if (data.contains("respawnDelay")) settings.respawnDelay = data.getInt("respawnDelay");
        if (data.contains("botHitRate")) settings.botHitRate = data.getInt("botHitRate");
        if (data.contains("friendlyFirePenalty")) settings.friendlyFirePenalty = data.getBoolean("friendlyFirePenalty");
        if (data.contains("unlimitedStamina")) settings.unlimitedStamina = data.getBoolean("unlimitedStamina");
        if (data.contains("speedBoost")) settings.speedBoost = data.getBoolean("speedBoost");
        if (data.contains("noCooldown")) settings.noCooldown = data.getBoolean("noCooldown");
        updateAssistance();
        settings.normalize();
        if (batch) settings.overrides.clear();
        normalizeModifierSettings();
        bots.reconcile();
        if (!oldArea.equals(settings.area)) { bots.teleport(); var owner = owner(); if (owner != null) move(owner, area().spawn(), area().yaw()); }
        SREGameWorldComponent.KEY.get(level()).setStartingPlayerCount(1 + settings.enemyCount);
    }

    private void kit(ServerPlayer actor, String id) {
        List<ItemStack> stacks = switch (id) {
            case "melee" -> List.of(TMMItems.KNIFE.getDefaultInstance());
            case "ranged" -> List.of(TMMItems.REVOLVER.getDefaultInstance(), org.agmas.noellesroles.init.ModItems.SHERIFF_REVOLVER.getDefaultInstance(), TMMItems.MAGNUM_BULLET.getDefaultInstance().copyWithCount(64));
            case "utility" -> List.of(TMMItems.LOCKPICK.getDefaultInstance(), TMMItems.CROWBAR.getDefaultInstance(), TMMItems.DEFENSE_VIAL.getDefaultInstance(), TMMItems.BODY_BAG.getDefaultInstance());
            default -> List.of();
        };
        stacks.forEach(stack -> { if (!actor.addItem(stack.copy())) error("training.sre.error.inventory"); });
        actor.containerMenu.broadcastChanges();
    }

    public void cleanup(boolean all) {
        var entities = level().getEntitiesOfClass(Entity.class, TrainingLayout.box(TrainingLayout.get().bounds).inflate(32),
                e -> e instanceof ItemEntity || e instanceof io.wifi.starrailexpress.content.entity.PlayerBodyEntity);
        int remaining = all ? 0 : 48;
        entities.sort(Comparator.comparingInt(e -> e.tickCount));
        for (Entity e : entities) if (remaining-- <= 0) e.discard();
    }

    private CompoundTag settingsTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("enemyCount", settings.enemyCount); t.putString("enemyRole", settings.enemyRole);
        t.putString("behavior", settings.behavior); t.putString("equipment", settings.equipment); t.putString("area", settings.area);
        t.putBoolean("paused", settings.paused); t.putBoolean("autoRespawn", settings.autoRespawn);
        t.putInt("respawnDelay", settings.respawnDelay); t.putInt("botHitRate", settings.botHitRate);
        t.putBoolean("friendlyFirePenalty", settings.friendlyFirePenalty);
        t.putBoolean("unlimitedStamina", settings.unlimitedStamina); t.putBoolean("speedBoost", settings.speedBoost);
        t.putBoolean("noCooldown", settings.noCooldown);
        t.put("myModifiers", TrainingModifiers.tag(settings.myModifiers));
        t.put("enemyModifiers", TrainingModifiers.tag(settings.enemyModifiers));
        return t;
    }
    public void snapshot(boolean full) {
        var owner = owner(); if (owner == null || !ServerPlayNetworking.canSend(owner, TrainingStatePayload.ID)) return;
        CompoundTag state = new CompoundTag();
        state.putBoolean("active", !closed && authorized()); state.putBoolean("full", full);
        state.putBoolean("dedicated", dedicated);
        state.putString("myRole", settings.myRole); state.putString("message", message);
        state.putInt("alive", bots.alive()); state.putInt("pending", bots.pending());
        state.put("settings", settingsTag()); state.put("bots", bots.snapshot());
        state.put("myModifiers", TrainingModifiers.tag(TrainingModifiers.actual(owner)));
        if (full) {
            state.put("items", catalogue.items(owner)); state.put("roles", catalogue.roles(owner));
            state.put("modifierCatalogue", catalogue.modifiers()); lastFullSnapshot = level().getGameTime();
        }
        ServerPlayNetworking.send(owner, new TrainingStatePayload(state));
        message = "";
    }
    public void close() {
        if (closed) return;
        save(); bots.close();
        assistance.clear(level().getGameTime());
        if (lastOwner != null) clearRole(lastOwner);
        if (dedicated) cleanupCampEntities(); else cleanup(true);
        SREGameWorldComponent.KEY.get(level()).setGameStatus(SREGameWorldComponent.GameStatus.INACTIVE);
        closed = true;
        try { snapshot(false); } catch (RuntimeException e) { SRE.LOGGER.debug("训练营退出通知未发送", e); }
        if (!dedicated) GameUtils.isGameStarted = false;
    }

    private void clearRole(ServerPlayer player) {
        var game = SREGameWorldComponent.KEY.get(level());
        var role = game.getRole(player);
        if (role != null) ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, role);
        RoleUtils.removeAllModifiers(player);
        org.agmas.noellesroles.role_data.innocence.DisabledPatientRoleData.clearGrantedEffects(player);
        game.removeRole(player);
        GameUtils.resetPlayer(player);
        player.removeAllEffects();
    }

    /** 专属维度只保留地图，回收职业衍生假玩家及临时实体，不影响其他营地。 */
    private void cleanupCampEntities() {
        for (var player : new ArrayList<>(level().players())) {
            if (TrainingRules.isManagedBot(player)) {
                clearRole(player);
                ((carpet.patches.EntityPlayerMPFake) player).kill(Component.translatable("multiplayer.disconnect.duplicate_login"));
            }
        }
        var game = SREGameWorldComponent.KEY.get(level());
        game.clearRoleMap(); game.clear(); game.clearBloodTrail(); game.resetPerPlayerKills(); game.resetDrawnCategories();
        game.playerBannedBlockTime.clear();
        for (var entity : new ArrayList<>(level().getEntitiesOfClass(Entity.class,
                TrainingLayout.box(TrainingLayout.get().bounds).inflate(64), e -> !(e instanceof ServerPlayer)))) entity.discard();
    }
}
