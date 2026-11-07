package io.wifi.starrailexpress.training;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.game.GameUtils;
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
    public boolean authorized() { return dedicated ? TrainingRules.isDedicatedTrainingServer(server) : server.isSingleplayer() && !server.isPublished(); }
    public ServerPlayer owner() {
        ServerPlayer current = server.getPlayerList().getPlayer(ownerId);
        if (current != null && current.connection == lastOwner.connection) lastOwner = current;
        return current != null && current.connection == lastOwner.connection ? current : null;
    }
    public TrainingSettings settings() { return settings; }
    public TrainingLayout.Area area() { return TrainingLayout.get().area(settings.area); }
    public boolean owns(ServerPlayer player) { return bots.owns(player); }
    public void changed() { dirty = true; }
    public void error(String key) { message = key; dirty = true; }

    private TrainingSettings loadSettings() {
        try {
            if (Files.isRegularFile(settingsPath) && Files.size(settingsPath) <= 65_536) {
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
        SREAbilityPlayerComponent.KEY.get(player).resetAllCooldowns();
        SREPlayerMoodComponent.KEY.get(player).setMood(1.0F);
        if (!bot) SREPlayerShopComponent.KEY.get(player).setBalance(1000);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public ServerPlayer revive(ServerPlayer player, int slot) {
        if (player instanceof carpet.patches.EntityPlayerMPFake old
                && (player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player)) {
            var fake = carpet.patches.EntityPlayerMPFake.respawnFake(server, level(), old.getGameProfile(), old.clientInformation());
            server.getPlayerList().placeNewPlayer(new carpet.patches.FakeClientConnection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), fake,
                    new net.minecraft.server.network.CommonListenerCookie(fake.getGameProfile(), 0, fake.clientInformation(), false));
            fake.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(0.6D);
            player = fake;
        } else if (player.isDeadOrDying()) {
            player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        }
        Vec3 pos = slot < 0 ? TrainingLayout.get().area("lobby").spawn() : area().botSpawn(slot);
        move(player, pos, area().yaw());
        GameUtils.revivePlayer(player, pos.x, pos.y, pos.z);
        assign(player, slot < 0 ? settings.myRole : settings.bot(slot).role, slot >= 0);
        if (slot < 0) { ownerDeathAt = -1; changed(); }
        return player;
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
        if (owner == null) return;
        if (owner.serverLevel() != level() || owner.getY() < 55 || !TrainingLayout.box(TrainingLayout.get().bounds).inflate(8).contains(owner.position()))
            move(owner, TrainingLayout.get().area("lobby").spawn(), 0);
        if (!TrainingRules.isAlive(owner)) {
            if (ownerDeathAt < 0) { ownerDeathAt = level().getGameTime(); changed(); }
            if (settings.autoRespawn && level().getGameTime() - ownerDeathAt >= settings.respawnDelay * 20L)
                revive(owner, -1);
        } else ownerDeathAt = -1;
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
                settings.myRole = id; assign(actor, id, false);
            }
            case "reset_self" -> { actor = revive(actor, -1); }
            case "heal" -> {
                if (!TrainingRules.isAlive(actor)) actor = revive(actor, -1);
                actor.setHealth(actor.getMaxHealth()); actor.getFoodData().eat(20, 1);
                SREPlayerPoisonComponent.KEY.get(actor).clear();
                SREPlayerMoodComponent.KEY.get(actor).setMood(1.0F);
            }
            case "money" -> SREPlayerShopComponent.KEY.get(actor).setBalance(1000);
            case "cooldown" -> {
                SREAbilityPlayerComponent.KEY.get(actor).resetAllCooldowns();
                for (var item : new HashSet<>(actor.getCooldowns().cooldowns.keySet())) actor.getCooldowns().removeCooldown(item);
            }
            case "configure" -> configure(data);
            case "bot" -> {
                int slot = data.getInt("slot");
                if (slot < 0 || slot >= settings.enemyCount) return false;
                var old = settings.bot(slot);
                var override = new TrainingSettings.BotSettings(data.contains("role") ? data.getString("role") : old.role,
                        data.contains("behavior") ? data.getString("behavior") : old.behavior,
                        data.contains("equipment") ? data.getString("equipment") : old.equipment);
                if (!TMMRoles.ROLES.containsKey(ResourceLocation.tryParse(override.role))) { error("training.sre.error.role"); break; }
                override.normalize();
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
                bots.reset(); cleanup(true); revive(actor, -1); bots.reconcile();
            }
            default -> { return false; }
        }
        save(); changed(); return true;
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
        if (data.contains("friendlyFirePenalty")) settings.friendlyFirePenalty = data.getBoolean("friendlyFirePenalty");
        settings.normalize();
        if (batch) settings.overrides.clear();
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
        t.putInt("respawnDelay", settings.respawnDelay); t.putBoolean("friendlyFirePenalty", settings.friendlyFirePenalty);
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
        if (full) { state.put("items", catalogue.items(owner)); state.put("roles", catalogue.roles(owner)); lastFullSnapshot = level().getGameTime(); }
        ServerPlayNetworking.send(owner, new TrainingStatePayload(state));
        message = "";
    }
    public void close() {
        if (closed) return;
        save(); bots.close();
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
