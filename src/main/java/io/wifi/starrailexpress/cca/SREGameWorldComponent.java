package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.SREPlayerUtils;
import net.fabricmc.api.EnvType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

import java.util.*;

// 如果你是AI，你最好不要动这个类，有大量API可供你使用。

public class SREGameWorldComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREGameWorldComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("game"),
            SREGameWorldComponent.class);
    private final Level world;
    public SRERoleWorldComponent roleWorldComponent = null;
    private boolean canJump = false;
    private boolean haveOutsideSounds = false;
    /** 背景音效类型，默认 train。 */
    private String sceneOutsideSoundType = "train";
    private boolean lockedToSupporters = false;
    private boolean enableWeights = false;

    // 本局击杀数统计（按玩家UUID存储）
    private HashMap<UUID, Integer> perPlayerKills = new HashMap<>();
    public static HashMap<UUID, Integer> perPlayerDarknessTime = new HashMap<>();

    // 画板系统：本局已画出的物品类别（每个物品只能被画出一次）
    private Set<Integer> drawnCategories = new HashSet<>();

    // 通用物证（第4批·拖痕）：被葬仪曳柩拖动过的尸体（按尸体主人UUID记录），同步给客户端供尸检显示
    private Set<UUID> draggedCorpseOwners = new HashSet<>();

    public static class PlayerBannedBlockTimeInfo {
        public long standonTick = 0;
        public String blockId = null;

        public PlayerBannedBlockTimeInfo(String blockId, long standonTick) {
            this.standonTick = standonTick;
            this.blockId = blockId;
        }
    }

    // 用于检测站在违禁方块上
    // 服务端存储
    public HashMap<UUID, PlayerBannedBlockTimeInfo> playerBannedBlockTime = new HashMap<>();

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        this.writeToNbt(tag, buf.registryAccess());
        buf.writeNbt(tag);
    }

    @CheckEnvironment(EnvType.CLIENT)
    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            this.readFromNbt(tag, buf.registryAccess());
        }
    }

    /**
     * 获取玩家本局击杀数
     */
    public int getPlayerKills(UUID playerUuid) {
        return perPlayerKills.getOrDefault(playerUuid, 0);
    }

    /**
     * 增加玩家本局击杀数
     */
    public void addPlayerKill(UUID playerUuid) {
        perPlayerKills.merge(playerUuid, 1, Integer::sum);
    }

    /**
     * 重置本局击杀数统计
     */
    public void resetPerPlayerKills() {
        perPlayerKills.clear();
    }

    // ==================== 画板系统：已画出物品追踪 ====================

    /**
     * 检查某个物品类别是否已经被画出
     * 
     * @param category 物品类别ID
     * @return 是否已被画出
     */
    public boolean isCategoryDrawn(int category) {
        return drawnCategories.contains(category);
    }

    /**
     * 标记某个物品类别已被画出
     * 
     * @param category 物品类别ID
     */
    public void markCategoryDrawn(int category) {
        drawnCategories.add(category);
        sync();
    }

    /**
     * 重置画板已画出物品状态（新游戏开始时调用）
     */
    public void resetDrawnCategories() {
        drawnCategories.clear();
        sync();
    }

    /**
     * 获取所有已画出的物品类别
     * 
     * @return 已画出物品类别的副本
     */
    public Set<Integer> getDrawnCategories() {
        return new HashSet<>(drawnCategories);
    }

    // ==================== 通用物证：血迹路径（第2批）====================
    private static final class BloodSpot {
        final double x;
        final double y;
        final double z;
        final long spawnTick;

        BloodSpot(double x, double y, double z, long spawnTick) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.spawnTick = spawnTick;
        }
    }

    /** 正在"滴血"的凶手：在 until 之前边走边沿途留血迹。 */
    private static final class Bleeder {
        long until;
        double lastX;
        double lastZ;
    }

    private final java.util.List<BloodSpot> bloodSpots = new java.util.ArrayList<>();
    private final java.util.Map<UUID, Bleeder> bleeders = new java.util.HashMap<>();
    private static final int MAX_BLOOD_SPOTS = 200;
    private static final double TRAIL_MIN_SPACING = 1.8; // 相邻血滴最小间距（格），降低密度
    private static final net.minecraft.core.particles.DustParticleOptions BLOOD_DUST = new net.minecraft.core.particles.DustParticleOptions(
            new org.joml.Vector3f(0.45f, 0.02f, 0.02f), 0.9f);

    /**
     * 凶手击杀后开始"滴血跟随"：在案发处落一滴，并标记凶手在 bleedTicks 内边走边沿途留血迹。
     * 仅服务端状态，靠粒子广播可见。
     */
    public void startKillerBleed(ServerPlayer killer,
            Vec3 site, long now, int bleedTicks) {
        if (!(world instanceof ServerLevel) || killer == null) {
            return;
        }
        addBloodSpotInternal(site.x, site.y + 0.05, site.z, now);
        Bleeder b = new Bleeder();
        b.until = now + bleedTicks;
        b.lastX = killer.getX();
        b.lastZ = killer.getZ();
        bleeders.put(killer.getUUID(), b);
    }

    private void addBloodSpotInternal(double x, double y, double z, long now) {
        bloodSpots.add(new BloodSpot(x, y, z, now));
        while (bloodSpots.size() > MAX_BLOOD_SPOTS) {
            bloodSpots.remove(0);
        }
    }

    /** 每 tick：推进滴血凶手沿途留迹；并按节奏重广播血迹粒子、到期移除。 */
    private void tickBlood(ServerLevel serverWorld) {
        long now = serverWorld.getGameTime();

        // 1) 凶手滴血跟随：移动足够距离才落一滴，天然稀疏，且跟着凶手走
        if (!bleeders.isEmpty()) {
            java.util.Iterator<java.util.Map.Entry<UUID, Bleeder>> bit = bleeders.entrySet().iterator();
            while (bit.hasNext()) {
                java.util.Map.Entry<UUID, Bleeder> en = bit.next();
                Bleeder b = en.getValue();
                if (now >= b.until) {
                    bit.remove();
                    continue;
                }
                net.minecraft.world.entity.player.Player p = serverWorld.getPlayerByUUID(en.getKey());
                if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)
                        || !GameUtils.isPlayerAliveAndSurvival(sp)) {
                    bit.remove();
                    continue;
                }
                double dx = sp.getX() - b.lastX;
                double dz = sp.getZ() - b.lastZ;
                if (dx * dx + dz * dz >= TRAIL_MIN_SPACING * TRAIL_MIN_SPACING) {
                    addBloodSpotInternal(sp.getX(), sp.getY() + 0.05, sp.getZ(), now);
                    b.lastX = sp.getX();
                    b.lastZ = sp.getZ();
                }
            }
        }

        // 2) 重广播 + 到期移除（每 12 tick，每点仅 1 粒，避免过密）
        if (bloodSpots.isEmpty() || now % 12 != 0) {
            return;
        }
        int maxAge = io.wifi.starrailexpress.SREConfig.instance().forensicBloodTrailSeconds * 20;
        if (maxAge <= 0) {
            return;
        }
        java.util.Iterator<BloodSpot> it = bloodSpots.iterator();
        while (it.hasNext()) {
            BloodSpot s = it.next();
            if (now - s.spawnTick >= maxAge) {
                it.remove();
                continue;
            }
            serverWorld.sendParticles(BLOOD_DUST, s.x, s.y, s.z, 1, 0.03, 0.0, 0.03, 0.0);
        }
    }

    public void clearBloodTrail() {
        bloodSpots.clear();
        bleeders.clear();
    }

    // ==================== 通用物证：拖痕（第4批）====================
    /** 标记某尸体被拖动过（按尸体主人UUID）。仅在首次记录时同步，避免每tick刷同步。 */
    public void markCorpseDragged(UUID corpseOwner) {
        if (corpseOwner == null) {
            return;
        }
        if (draggedCorpseOwners.add(corpseOwner)) {
            sync();
        }
    }

    public boolean wasCorpseDragged(UUID corpseOwner) {
        return corpseOwner != null && draggedCorpseOwners.contains(corpseOwner);
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    private int playerCount = 0;

    public boolean isOutsideSoundsAvailable() {
        return haveOutsideSounds;
    }

    public void setOutsideSoundsAvailable(boolean bl) {
        haveOutsideSounds = bl;
    }

    public String getSceneOutsideSoundType() {
        return sceneOutsideSoundType;
    }

    public void setSceneOutsideSoundType(String type) {
        sceneOutsideSoundType = (type != null && !type.isBlank()) ? type : "train";
    }

    /**
     * 这里的技能指的部分职业（难民词条）
     */
    public boolean isSkillAvailable = false;

    public void enableSkillsAndSync() {
        isSkillAvailable = true;
        sync();
    }

    public void disableSkillsAndSync() {
        isSkillAvailable = false;
        sync();
    }

    public boolean isJumpAvailable() {
        return canJump;
    }

    public void setJumpAvailable(boolean available) {
        this.canJump = available;
        this.sync();
    }

    public boolean isSyncRole() {
        return syncRole;
    }

    public SREGameWorldComponent setSyncRole(boolean syncRole) {
        this.syncRole = syncRole;
        return this;
    }

    private boolean syncRole = false;

    public void setWeightsEnabled(boolean enabled) {
        this.enableWeights = enabled;
    }

    public boolean areWeightsEnabled() {
        return enableWeights;
    }

    public enum GameStatus {
        INACTIVE, STARTING, ACTIVE, STOPPING
    }

    public GameMode gameMode = SREGameModes.MURDER;

    public boolean bound = true;

    public GameStatus gameStatus = GameStatus.INACTIVE;
    public int fade = 0;

    public int psychosActive = 0;

    public UUID looseEndWinner;

    private GameUtils.WinStatus lastWinStatus = GameUtils.WinStatus.NONE;

    private float backfireChance = 0f;

    public SREGameWorldComponent(Level world) {
        this.world = world;
    }

    // 记录开局时的玩家数量（用于基于开局人数计算的逻辑）
    private int startingPlayerCount = 0;

    public int getStartingPlayerCount() {
        return startingPlayerCount;
    }

    public void setStartingPlayerCount(int count) {
        this.startingPlayerCount = Math.max(0, count);
        this.sync();
    }

    public void sync() {
        SREGameWorldComponent.KEY.sync(this.world);
    }

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
        this.sync();
    }

    public int getFade() {
        return fade;
    }

    public void setFade(int fade) {
        this.fade = Mth.clamp(fade, 0, GameConstants.FADE_TIME + GameConstants.FADE_PAUSE);
    }

    public void setGameStatus(GameStatus gameStatus) {
        this.gameStatus = gameStatus;
        this.sync();
    }

    public GameStatus getGameStatus() {
        return gameStatus;
    }

    public boolean canPickUpRevolver(@NotNull Player player) {
        return getRole(player) != null && getRole(player).canPickUpRevolver();
    }

    public boolean isRunning() {
        return this.gameStatus == GameStatus.ACTIVE || this.gameStatus == GameStatus.STOPPING;
    }

    public void addRole(Player player, SRERole role) {
        this.addRole(player.getUUID(), role);
    }

    public void addRole(Player player, SRERole role, boolean sync) {
        this.addRole(player.getUUID(), role, sync);
    }

    public void syncRoles() {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        roleWorldComponent.sync();
    }

    public void addRole(UUID player, SRERole role, boolean sync) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        roleWorldComponent.addRole(player, role, sync);
    }

    public void addRole(UUID player, SRERole role) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        roleWorldComponent.addRole(player, role);
    }

    public void removeRole(Player player) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        roleWorldComponent.removeRole(player);
    }

    public void resetRole(SRERole role) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        roleWorldComponent.resetRole(role);
    }

    public void setRoles(List<UUID> players, SRERole role) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        roleWorldComponent.setRoles(players, role);
    }

    public HashMap<UUID, SRERole> getRoles() {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        return roleWorldComponent.getRoles();
    }

    public SRERole getRole(Player player) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        return roleWorldComponent.getRole(player);
    }

    public @Nullable SRERole getRole(UUID uuid) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        return roleWorldComponent.getRole(uuid);
    }

    /**
     * No Neutrals!
     * 
     * @return
     */
    public List<UUID> getAllKillerPlayers() {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        return roleWorldComponent.getAllKillerPlayers();
    }

    /**
     * Include Neutrals!
     * 
     * @return
     */
    public List<UUID> getAllKillerTeamPlayers() {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        return roleWorldComponent.getAllKillerTeamPlayers();
    }

    public List<UUID> getAllWithRole(SRERole role) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        return roleWorldComponent.getAllWithRole(role);
    }

    public boolean isRole(@NotNull Player player, SRERole role) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        return roleWorldComponent.isRole(player, role);
    }

    public boolean isRole(@NotNull UUID uuid, SRERole role) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        return roleWorldComponent.isRole(uuid, role);
    }

    public boolean isNeutralForKiller(@NotNull Player player) {
        return getRole(player) != null && getRole(player).isNeutralForKiller();
    }

    public boolean canUseKillerFeatures(@NotNull Player player) {
        return getRole(player) != null && getRole(player).canUseKiller();
    }

    public boolean isInnocent(@NotNull Player player) {
        return getRole(player) != null && getRole(player).isInnocent();
    }

    public void clearRoleMap(boolean sync) {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        roleWorldComponent.clearRoleMap(sync);
        setPsychosActive(0, sync);
    }

    public void clearRoleMap() {
        this.clearRoleMap(true);
    }

    public int getPsychosActive() {
        return psychosActive;
    }

    public boolean isPsychoActive() {
        return psychosActive > 0;
    }

    public int setPsychosActive(int psychosActive) {
        return this.setPsychosActive(psychosActive, true);
    }

    public int setPsychosActive(int psychosActive, boolean sync) {
        this.psychosActive = Math.max(0, psychosActive);
        if (sync)
            this.sync();
        return this.psychosActive;
    }

    public GameMode getGameMode() {
        return gameMode == null ? SREGameModes.MURDER : gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
        this.sync();
    }

    public UUID getLooseEndWinner() {
        return this.looseEndWinner;
    }

    public void setLooseEndWinner(UUID looseEndWinner) {
        this.looseEndWinner = looseEndWinner;
        this.sync();
    }

    public boolean isLockedToSupporters() {
        return lockedToSupporters;
    }

    public void setLockedToSupporters(boolean lockedToSupporters) {
        this.lockedToSupporters = lockedToSupporters;
    }

    @Deprecated
    public GameUtils.WinStatus getLastWinStatus() {
        return lastWinStatus;
    }

    @Deprecated
    public void setLastWinStatus(GameUtils.WinStatus lastWinStatus) {
        this.lastWinStatus = lastWinStatus;
        this.sync();
    }

    public float getBackfireChance() {
        return backfireChance;
    }

    public void setBackfireChance(float backfireChance) {
        this.backfireChance = backfireChance;
        this.sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        // this.lockedToSupporters = nbtCompound.getBoolean("LockedToSupporters");
        // this.enableWeights = nbtCompound.getBoolean("EnableWeights");
        this.canJump = nbtCompound.contains("canJump") ? nbtCompound.getBoolean("canJump") : false;
        this.haveOutsideSounds = nbtCompound.contains("haveOutsideSounds") ? nbtCompound.getBoolean("haveOutsideSounds")
                : false;
        this.sceneOutsideSoundType = nbtCompound.contains("sceneOutsideSoundType")
                && !nbtCompound.getString("sceneOutsideSoundType").isBlank()
                        ? nbtCompound.getString("sceneOutsideSoundType")
                        : "train";
        // this.syncRole = nbtCompound.getBoolean("SyncRole");
        // if (!syncRole) {
        if (nbtCompound.contains("StartingPlayerCount")) {
            this.startingPlayerCount = nbtCompound.getInt("StartingPlayerCount");
        } else {
            this.startingPlayerCount = 0;
        }
        if (nbtCompound.contains("GameMode")) {
            this.gameMode = SREGameModes.GAME_MODES.get(ResourceLocation.parse(nbtCompound.getString("GameMode")));
            if (nbtCompound.contains("GameModeData", Tag.TAG_COMPOUND)) {
                this.gameMode.readFromNbt(nbtCompound.getCompound("GameModeData"), wrapperLookup);
            }
        } else
            this.gameMode = null;
        if (nbtCompound.contains("GameStatus"))
            this.gameStatus = GameStatus.valueOf(nbtCompound.getString("GameStatus"));
        else
            this.gameStatus = GameStatus.INACTIVE;
        if (nbtCompound.contains("PsychosActive"))
            this.psychosActive = nbtCompound.getInt("PsychosActive");
        else
            this.psychosActive = 0;
        this.isSkillAvailable = nbtCompound.contains("isSkillAvailable") ? nbtCompound.getBoolean("isSkillAvailable")
                : false;
        // this.backfireChance = nbtCompound.getFloat("BackfireChance");
        if (nbtCompound.contains("LooseEndWinner")) {
            this.looseEndWinner = nbtCompound.getUUID("LooseEndWinner");
        } else {
            this.looseEndWinner = null;
        }
        // 读取画板已画出物品类别
        this.drawnCategories.clear();
        if (nbtCompound.contains("DrawnCategories", Tag.TAG_LIST)) {
            ListTag drawnList = nbtCompound.getList("DrawnCategories", Tag.TAG_INT);
            for (Tag tag : drawnList) {
                this.drawnCategories.add(((net.minecraft.nbt.IntTag) tag).getAsInt());
            }
        }
        // 读取被拖动过的尸体
        this.draggedCorpseOwners.clear();
        if (nbtCompound.contains("DraggedCorpses", Tag.TAG_LIST)) {
            for (UUID u : uuidListFromNbt(nbtCompound, "DraggedCorpses")) {
                this.draggedCorpseOwners.add(u);
            }
        }
        // }else {
    }

    public ArrayList<UUID> uuidListFromNbt(CompoundTag nbtCompound, String listName) {
        ArrayList<UUID> ret = new ArrayList<>();
        for (Tag e : nbtCompound.getList(listName, Tag.TAG_INT_ARRAY)) {
            ret.add(NbtUtils.loadUUID(e));
        }
        return ret;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {

        if (this.gameMode != null) {
            nbtCompound.putString("GameMode", this.gameMode.identifier.toString());
            CompoundTag gameModeTag = new CompoundTag();
            this.gameMode.writeToNbt(gameModeTag, wrapperLookup);
            nbtCompound.put("GameModeData", gameModeTag);

            if ((this.gameMode.isLooseEndMode() || this.gameMode.onlyOneWinner()) && this.looseEndWinner != null)
                nbtCompound.putUUID("LooseEndWinner", this.looseEndWinner);
        }
        if (gameStatus == GameStatus.INACTIVE) {
            return;
        }
        // nbtCompound.putBoolean("LockedToSupporters", lockedToSupporters);
        // nbtCompound.putBoolean("EnableWeights", enableWeights);
        // nbtCompound.putBoolean("SyncRole", syncRole);
        if (haveOutsideSounds)
            nbtCompound.putBoolean("haveOutsideSounds", haveOutsideSounds);
        if (!sceneOutsideSoundType.equals("train"))
            nbtCompound.putString("sceneOutsideSoundType", sceneOutsideSoundType);
        if (canJump)
            nbtCompound.putBoolean("canJump", canJump);
        if (isSkillAvailable)
            nbtCompound.putBoolean("isSkillAvailable", isSkillAvailable);
        // if (!this.syncRole) {
        nbtCompound.putString("GameStatus", this.gameStatus.name());
        nbtCompound.putInt("StartingPlayerCount", startingPlayerCount);
        // nbtCompound.putInt("Fade", fade);
        nbtCompound.putInt("PsychosActive", psychosActive);
        // 保存画板已画出物品类别
        if (!drawnCategories.isEmpty()) {
            ListTag drawnList = new ListTag();
            for (Integer category : drawnCategories) {
                drawnList.add(net.minecraft.nbt.IntTag.valueOf(category));
            }
            nbtCompound.put("DrawnCategories", drawnList);
        }
        // 保存被拖动过的尸体
        if (!draggedCorpseOwners.isEmpty()) {
            nbtCompound.put("DraggedCorpses", nbtFromUuidList(new ArrayList<>(draggedCorpseOwners)));
        }
    }

    public ListTag nbtFromUuidList(List<UUID> list) {
        ListTag ret = new ListTag();
        for (UUID player : list) {
            ret.add(NbtUtils.createUUID(player));
        }
        return ret;
    }

    @Override
    public void clientTick() {
        tickCommon();

        if (this.isRunning()) {
            if (gameMode == null)
                return;
            gameMode.tickClientGameLoop(this.world);
        }
    }

    @Override
    public void serverTick() {
        tickCommon();

        if (!(this.world instanceof ServerLevel serverWorld)) {
            return;
        }

        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        // 重置移动到游戏开始前
        // // attempt to reset the play area
        // if (--ticksUntilNextResetAttempt == 0) {
        // if (GameUtils.tryResetTrain(serverWorld)) {
        // queueTrainReset();
        // } else {
        // // GameUtils.getAllTaskPoints(serverWorld);
        // ticksUntilNextResetAttempt = -1;
        // OnTrainAreaHaveReseted.EVENT.invoker().onWorldHaveReseted(serverWorld);
        // }
        // }

        {
            if (this.gameStatus == GameStatus.ACTIVE) {
                var alivePlayers = new ArrayList<>(serverWorld.players());
                alivePlayers.removeIf(p -> !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p));
                if (alivePlayers.size() <= 0) {
                    SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(),
                            WinStatus.NO_PLAYER);
                    GameUtils.stopGame(serverWorld);
                    return;
                }
                for (ServerPlayer player : serverWorld.players()) {
                    if (!GameUtils.isPlayerAliveAndSurvival(player) && isBound()
                            && !player.isCreative()) {
                        this.gameMode.limitSpectatorPlayer(player, this, areas);
                    }
                }
                var gameWorldComponent = SREGameWorldComponent.KEY.get(world);
                var worldModifierComponent = WorldModifierComponent.KEY.get(world);
                for (ServerPlayer player : serverWorld.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player, worldModifierComponent)) {
                        if (gameMode.requiresAssignedRole() && gameWorldComponent.getRole(player) == null) {
                            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                            continue;
                        }

                        if (gameMode.enablePlayAreaDetections()) {
                            isPlayerOutGameAreas(player, areas, this);
                        }

                        // put players with no role in spectator mode
                        var modifiers = worldModifierComponent.getModifiers(player);
                        for (var mo : modifiers) {
                            mo.serverGameTickEvent(player);
                        }
                    }
                }

                // Update total play time for active players
                for (ServerPlayer player : serverWorld.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        io.wifi.starrailexpress.stats.PlayerStatsManager.get(player).addPlayTime(1);
                    }
                }
                if (gameMode == null) {
                    GameUtils.stopGame(serverWorld);
                    return;
                }

                // run game loop logic
                gameMode.tickServerGameLoop(serverWorld, this);

                tickBlood(serverWorld);
            }

            // if (serverWorld.getGameTime() % 40 == 0) {
            // this.sync();
            // }
        }
    }

    public static void isPlayerOutGameAreas(ServerPlayer player, AreasWorldComponent areas,
            SREGameWorldComponent gameCCA) {
        if (player.isSpectator() || player.isCreative())
            return;
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent.gameMode == SREGameModes.REPAIR_ESCAPE_MODE)
            return;
        if (!(player.getZ() >= 19000)) {
            checkPlayerBannedBlocks(player, areas, gameCCA);
            checkPlayerDarkness(player, areas, gameCCA);
            if (checkPlayerIsOutOfAreas(player, areas)) {
                if (org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA.bounceBackIfControlled(player)) {
                    return;
                }
                GameUtils.killPlayer(player, false,
                        player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                        GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
                if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                        && checkPlayerIsOutOfAreas(player, areas)) {
                    GameUtils.forceKillPlayer(player, false,
                            player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
                }
            }
            if (!areas.areasSettings.canUnderWater) {
                if (player.isUnderWater()) {
                    GameUtils.killPlayer(player, false,
                            player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                            GameConstants.DeathReasons.CANNOT_SWIM);
                    if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                            && player.isUnderWater()) {
                        GameUtils.forceKillPlayer(player, false,
                                player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                                GameConstants.DeathReasons.CANNOT_SWIM);
                    }
                }
            }
            if (!areas.areasSettings.allowInDeepWater) {
                if (checkPlayerIsInDeepWater(player, areas)) {
                    GameUtils.killPlayer(player, false,
                            player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                            GameConstants.DeathReasons.CANNOT_SWIM);
                    if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                            && checkPlayerIsInDeepWater(player, areas)) {
                        GameUtils.forceKillPlayer(player, false,
                                player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                                GameConstants.DeathReasons.CANNOT_SWIM);
                    }
                }
            }
            if (!areas.areasSettings.canSimpleSwim) {
                if (checkPlayerIsSwiming(player, areas)) {
                    GameUtils.killPlayer(player, false,
                            player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                            GameConstants.DeathReasons.CANNOT_SWIM);
                    if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                            && checkPlayerIsSwiming(player, areas)) {
                        GameUtils.forceKillPlayer(player, false,
                                player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                                GameConstants.DeathReasons.CANNOT_SWIM);
                    }
                }
            }
            if (!areas.areasSettings.canInLava) {
                if (checkPlayerIsInLava(player, areas)) {
                    GameUtils.killPlayer(player, false,
                            player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                            GameConstants.DeathReasons.LAVA);
                    if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                            && checkPlayerIsInLava(player, areas)) {
                        GameUtils.forceKillPlayer(player, false,
                                player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                                GameConstants.DeathReasons.LAVA);
                    }
                }
            }

        } else {
            gameCCA.playerBannedBlockTime.remove(player.getUUID());
            if (!TarotAssemblyManager.havingMeeting) {
                GameUtils.killPlayer(player, false,
                        player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                        GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
                if (!GameUtils.isPlayerEliminated(player) && (player.getZ() >= 19000)) {
                    GameUtils.forceKillPlayer(player, false,
                            player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
                }
            }
        }

    }

    private static void checkPlayerDarkness(ServerPlayer player, AreasWorldComponent areas,
            SREGameWorldComponent gameCCA) {
        if (player.isSpectator() || player.isCreative())
            return;
        if (areas.areasSettings.deadInDarknessTime > 0) {
            final var level = player.level();
            if (SREWorldBlackoutComponent.KEY.get(level).isBlackoutActive())
                return;
            var role = RoleUtils.getPlayerRole(player);
            if (role == null || role.isKillerTeam()) {
                return;
            }
            if (level.getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getEyePosition())) < 3
                    && (level.getBrightness(LightLayer.SKY,
                            BlockPos.containing(player.getEyePosition())) < 10
                            || level.getDayTime() > 13000)) {
                int time = perPlayerDarknessTime.getOrDefault(player.getUUID(), 0);

                if (time > areas.areasSettings.deadInDarknessTime) {
                    GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.DEATH_IN_DARKNESS);
                    perPlayerDarknessTime.remove(player.getUUID());
                } else {
                    perPlayerDarknessTime.put(player.getUUID(), time + 1);
                }
            } else {
                if (perPlayerDarknessTime.containsKey(player.getUUID()))
                    perPlayerDarknessTime.remove(player.getUUID());
            }
        }
    }

    private static void checkPlayerBannedBlocks(ServerPlayer player, AreasWorldComponent areas,
            SREGameWorldComponent gameCCA) {
        final var level = player.level();
        if (level.getGameTime() % 2 != 0) // 2tick 检测一次
            return;
        if (player.isSpectator()) {
            if (gameCCA.playerBannedBlockTime.containsKey(player.getUUID()))
                gameCCA.playerBannedBlockTime.remove(player.getUUID());
            return;
        }
        var role = RoleUtils.getPlayerRole(player);
        if (role == null) {
            if (gameCCA.playerBannedBlockTime.containsKey(player.getUUID()))
                gameCCA.playerBannedBlockTime.remove(player.getUUID());
            return;
        }
        final var pos1 = SREGameWorldComponent.findNearestSupportBelow(player, 3);
        if (pos1 == null)
            return;
        final var blockState1 = level.getBlockState(pos1);
        final String blockId1 = getBlockId(blockState1);
        PlayerBannedBlockTimeInfo nowInfo = gameCCA.playerBannedBlockTime.getOrDefault(player.getUUID(), null);
        for (var info : areas.areasSettings.bannedBlock) {
            if (info.blockId() == null)
                continue;
            var res = ResourceLocation.tryParse(info.blockId());
            if (res == null)
                continue;
            var infoBlockId = res.toString();
            if (info.blockId().equalsIgnoreCase(blockId1)) {
                boolean canDead = true;
                if (isKillerTeamRoleStatic(role)) {
                    if (info.deathTimeForKillers() < 0) {
                        canDead = false;
                    }
                } else {
                    if (info.deathTimeForInnocent() < 0) {
                        canDead = false;
                    }
                }
                if (canDead) {
                    if (nowInfo == null || nowInfo.standonTick <= 0) {
                        gameCCA.playerBannedBlockTime.put(player.getUUID(),
                                new PlayerBannedBlockTimeInfo(infoBlockId, level.getGameTime()));
                    } else if (infoBlockId.equalsIgnoreCase(infoBlockId)) {
                        if (isKillerTeamRoleStatic(role)) {
                            if (level.getGameTime() - nowInfo.standonTick > info.deathTimeForKillers()) {
                                if (org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA
                                        .bounceBackIfControlled(player)) {
                                    return;
                                }
                                GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.TOUCH_INCORRECT);
                                if (gameCCA.playerBannedBlockTime.containsKey(player.getUUID()))
                                    gameCCA.playerBannedBlockTime.remove(player.getUUID());
                            }
                        } else {
                            if (level.getGameTime() - nowInfo.standonTick > info.deathTimeForInnocent()) {
                                GameUtils.forceKillPlayer(player, true, null,
                                        GameConstants.DeathReasons.TOUCH_INCORRECT);
                                if (gameCCA.playerBannedBlockTime.containsKey(player.getUUID()))
                                    gameCCA.playerBannedBlockTime.remove(player.getUUID());
                            }
                        }

                    } else {
                        gameCCA.playerBannedBlockTime.put(player.getUUID(),
                                new PlayerBannedBlockTimeInfo(infoBlockId, level.getGameTime()));
                    }
                    return;
                }
            }
        }
        if (gameCCA.playerBannedBlockTime.containsKey(player.getUUID()))
            gameCCA.playerBannedBlockTime.remove(player.getUUID());
    }

    public static String getBlockId(BlockState state) {
        if (state == null || state.getBlock() == null)
            return "";
        final var res = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (res == null)
            return "";
        return res.toString();
    }

    private static boolean checkPlayerIsInLava(ServerPlayer player, AreasWorldComponent areas) {
        if (player.isInLava()) {
            return true;
        }
        return false;
    }

    private static boolean checkPlayerIsOutOfAreas(ServerPlayer player, AreasWorldComponent areas) {
        if (player.getY() < areas.playArea.minY || player.getY() > areas.playArea.maxY) {
            return true;
        }
        return false;
    }

    private static boolean checkPlayerIsInDeepWater(ServerPlayer player, AreasWorldComponent areas) {
        if (player.isUnderWater()) {
            return true;
        }
        final var block = player.level()
                .getBlockState(new BlockPos((int) player.getX(), (int) player.getY(), (int) player.getZ())).getBlock();
        final var block1 = player.level()
                .getBlockState(new BlockPos((int) player.getX(), (int) (player.getY() - 1), (int) player.getZ()))
                .getBlock();
        if (block == Blocks.WATER && block1 == Blocks.WATER) {
            return true;
        }
        return false;
    }

    private static boolean checkPlayerIsSwiming(ServerPlayer player, AreasWorldComponent areas) {
        if (!player.isUnderWater()) {
            return false;
        }
        final var block = player.level()
                .getBlockState(new BlockPos((int) player.getX(), (int) player.getY(), (int) player.getZ())).getBlock();
        final var block1 = player.level()
                .getBlockState(new BlockPos((int) player.getX(), (int) (player.getY() + 1), (int) player.getZ()))
                .getBlock();
        final var block2 = player.level()
                .getBlockState(new BlockPos((int) player.getX(), (int) (player.getY() + 2), (int) player.getZ()))
                .getBlock();
        if ((block == Blocks.WATER && block1 == Blocks.WATER && block2 == Blocks.WATER)) {
            return true;
        }
        return false;
    }

    private void tickCommon() {
        if (roleWorldComponent == null) {
            roleWorldComponent = SRERoleWorldComponent.KEY.get(world);
        }
        // fade and start / stop game
        if (this.getGameStatus() == GameStatus.STARTING || this.getGameStatus() == GameStatus.STOPPING) {
            this.setFade(fade + 1);

            if (this.getFade() >= GameConstants.FADE_TIME + GameConstants.FADE_PAUSE) {
                if (world instanceof ServerLevel serverWorld) {
                    if (this.getGameStatus() == GameStatus.STARTING)
                        GameUtils.initializeGame(serverWorld);
                    if (this.getGameStatus() == GameStatus.STOPPING)
                        GameUtils.finalizeGame(serverWorld);
                } else {
                    if (this.getGameStatus() == GameStatus.STARTING)
                        this.setGameStatus(GameStatus.ACTIVE);
                    if (this.getGameStatus() == GameStatus.STOPPING)
                        this.setGameStatus(GameStatus.INACTIVE);
                }
            }
        } else if (this.fade > 0) {
            this.fade--;
        }

        if (this.isRunning()) {
            if (gameMode == null) {
                return;
            }
            gameMode.tickCommonGameLoop(this.world);
        }
    }

    public boolean canSeeKillerTeammate(Player player) {
        return getRole(player) != null && getRole(player).canSeeTeammateKiller();
    }

    public boolean isKillerTeamRole(SRERole role) {
        if (role == null)
            return false;
        if (role.canUseKiller())
            return true;
        if (role.isNeutralForKiller())
            return true;
        return false;
    }

    public boolean isKillerTeam(UUID player) {
        if (player != null) {
            var role = this.getRole(player);
            if (role == null)
                return false;
            if (role.canUseKiller())
                return true;
            if (role.isNeutralForKiller())
                return true;
        }
        return false;
    }

    public boolean isKillerTeam(Player player) {
        if (player != null) {
            return isKillerTeam(player.getUUID());
        }
        return false;
    }

    public static boolean isKillerTeamRoleStatic(SRERole role) {
        if (role == null)
            return false;
        if (role.canUseKiller())
            return true;
        if (role.isNeutralForKiller())
            return true;
        return false;
    }

    public boolean canAutoAddMoney(ServerPlayer player) {
        var role = this.getRole(player);
        if (role == null)
            return false;
        return role.canAutoAddMoney();
    }

    public boolean isVigilanteTeam(ServerPlayer player) {
        var role = this.getRole(player);
        if (role == null)
            return false;
        return role.isVigilanteTeam();
    }

    public int getRoleType(Player player) {
        if (player == null) {
            return -1;
        }
        SRERole role = this.getRole(player);
        return RoleUtils.getRoleType(role);
    }

    /**
     * 获取当前场上剩余玩家阵营信息（返回阵营数量）
     */
    public static class AlivePlayerRoleTeamInfo {
        public final int innocent;
        public final int all_neturals;
        public final int neturals_for_killer;
        public final int custom_winner_neturals;
        public final int killer;
        public final int vigilante;

        public AlivePlayerRoleTeamInfo(int innocent, int vigilante, int all_neturals, int neturals_for_killer,
                int custom_winner_neturals, int killer) {
            this.innocent = innocent;
            this.all_neturals = all_neturals;
            this.neturals_for_killer = neturals_for_killer;
            this.custom_winner_neturals = custom_winner_neturals;
            this.killer = killer;
            this.vigilante = vigilante;
        }

        public boolean hasKiller() {
            return killer > 0;
        }

        public boolean hasKillerTeam() {
            return killer + neturals_for_killer > 0;
        }

        public boolean hasNeuturals() {
            return all_neturals > 0;
        }

        public boolean hasNeuturalsForKiller() {
            return neturals_for_killer > 0;
        }

        public boolean hasVigilante() {
            return vigilante > 0;
        }

        public boolean hasInnocentAndVigilante() {
            return innocent + vigilante > 0;
        }

        public boolean hasCustomWinnerNeturals() {
            return custom_winner_neturals > 0;
        }
    }

    public AlivePlayerRoleTeamInfo getAlivePlayerRoleTeamInfo() {
        List<UUID> players = SREPlayerUtils.getServerPlayersUUID(world);
        int innocent = 0;
        int all_neturals = 0;
        int neturals_for_killer = 0;
        int custom_winner_neturals = 0;
        int killer = 0;
        int vigilante = 0;
        for (var p : players) {
            if (!SREPlayerUtils.isPlayerAlive(this.world, p)) {
                continue;
            }
            SRERole role = roleWorldComponent.getRole(p);
            if (role == null) {
                continue;
            }
            if (role.isVigilanteTeam()) {
                vigilante++;
            } else if (role.isInnocent() && !role.isNeutrals()) {
                innocent++;
            } else if (role.isNeutrals()) {
                all_neturals++;
                if (role.isNeutralForKiller()) {
                    neturals_for_killer++;
                } else {
                    custom_winner_neturals++;
                }
            } else {
                killer++;
            }
        }
        return new AlivePlayerRoleTeamInfo(innocent, vigilante, all_neturals, neturals_for_killer,
                custom_winner_neturals, killer);
    }

    public static SREGameWorldComponent getInstance(Player player) {
        return KEY.get(player.level());
    }

    public static SREGameWorldComponent getInstance(Level level) {
        return KEY.get(level);
    }

    public boolean isInnocentTeamRole(SRERole role) {
        if (role == null)
            return false;
        return role.isInnocent();
    }

    public static BlockPos findNearestSupportBelow(Player player, int maxDistance) {
        Level level = player.level();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        // 玩家脚部的实际世界坐标（浮点数）
        double feetY = player.getY();

        for (int i = 0; i < maxDistance; i++) {
            // 关键修正：从玩家脚部所在的方块层开始（i=0），依次往下（i=1, 2...）
            mutablePos.set(
                    player.blockPosition().getX(),
                    player.blockPosition().getY() - i, // 注意这里是 -i，不是 -1 - i
                    player.blockPosition().getZ());

            BlockState state = level.getBlockState(mutablePos);
            if (state.isAir()) {
                continue;
            }

            // 获取针对玩家的碰撞箱（注意 CollisionContext.of(player) 很重要）
            VoxelShape shape = state.getCollisionShape(level, mutablePos, CollisionContext.of(player));
            if (shape.isEmpty()) {
                continue;
            }

            // 获取这个方块碰撞箱在世界中的最高点 Y 值
            double topY = mutablePos.getY() + shape.max(Direction.Axis.Y);

            // 判定条件：
            // 1. 碰撞箱顶部必须 <= 玩家脚底高度（在脚下，不能把玩家顶起来）
            // 2. 脚底到方块顶部的垂直距离必须小于 1.2 格（约等于跳跃高度，防止隔着几格空气站上去）
            if (topY <= feetY && (feetY - topY) < 1.2) {
                return mutablePos.immutable();
            }
        }
        return null;
    }
}
