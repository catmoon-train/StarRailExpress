package io.wifi.starrailexpress.cca;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
// 导入Mth类
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class SREGameWorldComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREGameWorldComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("game"),
            SREGameWorldComponent.class);
    private final Level world;
    private SRERoleWorldComponent roleWorldComponent = null;
    private boolean canJump = false;
    private boolean haveOutsideSounds = false;
    private boolean lockedToSupporters = false;
    private boolean enableWeights = false;

    public boolean isOutsideSoundsAvailable(){
        return haveOutsideSounds;
    }
    public void setOutsideSoundsAvailable(boolean bl){
        haveOutsideSounds = bl;
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

    private GameMode gameMode = SREGameModes.MURDER;

    private boolean bound = true;

    private GameStatus gameStatus = GameStatus.INACTIVE;
    public int fade = 0;

    public int ticksUntilNextResetAttempt = -1;

    private int psychosActive = 0;

    private UUID looseEndWinner;

    private GameUtils.WinStatus lastWinStatus = GameUtils.WinStatus.NONE;

    private float backfireChance = 0f;

    public SREGameWorldComponent(Level world) {
        this.world = world;
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

    public void queueTrainReset() {
        if (SRE.isLobby) {
            ticksUntilNextResetAttempt = -1;
            return;
        }
        ticksUntilNextResetAttempt = 10;
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
        // this.syncRole = nbtCompound.getBoolean("SyncRole");
        // if (!syncRole) {
        if (nbtCompound.contains("GameMode"))
            this.gameMode = SREGameModes.GAME_MODES.get(ResourceLocation.parse(nbtCompound.getString("GameMode")));
        else
            this.gameMode = null;
        this.gameStatus = GameStatus.valueOf(nbtCompound.getString("GameStatus"));

        this.fade = nbtCompound.getInt("Fade");
        this.psychosActive = nbtCompound.getInt("PsychosActive");
        this.isSkillAvailable = nbtCompound.contains("isSkillAvailable") ? nbtCompound.getBoolean("isSkillAvailable")
                : false;
        // this.backfireChance = nbtCompound.getFloat("BackfireChance");
        if (nbtCompound.contains("LooseEndWinner")) {
            this.looseEndWinner = nbtCompound.getUUID("LooseEndWinner");
        } else {
            this.looseEndWinner = null;
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
        // nbtCompound.putBoolean("LockedToSupporters", lockedToSupporters);
        // nbtCompound.putBoolean("EnableWeights", enableWeights);
        // nbtCompound.putBoolean("SyncRole", syncRole);
        if (canJump)
            nbtCompound.putBoolean("canJump", canJump);
        if (isSkillAvailable)
            nbtCompound.putBoolean("isSkillAvailable", isSkillAvailable);
        // if (!this.syncRole) {
        if (this.gameMode != null)
            nbtCompound.putString("GameMode", this.gameMode.identifier.toString());
        nbtCompound.putString("GameStatus", this.gameStatus.toString());

        nbtCompound.putInt("Fade", fade);
        nbtCompound.putInt("PsychosActive", psychosActive);
        if (this.looseEndWinner != null)
            nbtCompound.putUUID("LooseEndWinner", this.looseEndWinner);

        // nbtCompound.putString("LastWinStatus", this.lastWinStatus.toString());
        // nbtCompound.putFloat("BackfireChance", backfireChance);
        // }
        // else {

        // }

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
            gameMode.tickClientGameLoop();
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

        // if not running and spectators or not in lobby reset them
        if (serverWorld.getServer().getTickCount() % 20 == 0) {
            for (ServerPlayer player : serverWorld.players()) {
                if (!isRunning() && (player.isSpectator()
                        && serverWorld.getServer().getProfilePermissions(player.getGameProfile()) < 2
                        || (GameUtils.isPlayerAliveAndSurvival(player)
                                && areas.playArea.contains(player.position())))) {
                    GameUtils.resetPlayerAfterGame(player);
                }
            }
        }

        if (serverWorld.getServer().overworld().equals(serverWorld)) {
            SRETrainWorldComponent trainComponent = SRETrainWorldComponent.KEY.get(serverWorld);

            // spectator limits
            if (trainComponent.getSpeed() > 0) {
                for (ServerPlayer player : serverWorld.players()) {
                    if (!GameUtils.isPlayerAliveAndSurvival(player) && isBound()
                            && !GameUtils.isPlayerCreative(player)) {
                        GameUtils.limitPlayerToBox(player, areas.playArea);
                    }
                }
            }

            if (this.isRunning()) {
                var gameWorldComponent = SREGameWorldComponent.KEY.get(world);
                var worldModifierComponent = WorldModifierComponent.KEY.get(world);
                for (ServerPlayer player : serverWorld.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player, worldModifierComponent)) {
                        // kill players who fell off the train
                        if (gameWorldComponent.getRole(player) == null) {
                            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                        }
                        if (GameUtils.isPlayerAliveAndSurvival(player)) {
                            isPlayerOutGameAreas(player, areas);
                        }

                        // put players with no role in spectator mode

                        // 调用角色的服务器端tick方法
                        io.wifi.starrailexpress.api.RoleMethodDispatcher.callServerTick(player, gameWorldComponent);
                        var modifiers = worldModifierComponent.getModifiers(player);
                        for (var mo : modifiers) {
                            mo.serverGameTickEvent(player);
                        }
                    }
                }

                // Update total play time for active players
                for (ServerPlayer player : serverWorld.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        SREPlayerStatsComponent.KEY.get(player).addPlayTime(1);
                    }
                }
                if (gameMode == null) {
                    gameStatus = GameStatus.STOPPING;
                    return;
                }

                // run game loop logic
                gameMode.tickServerGameLoop(serverWorld, this);

            }

            // if (serverWorld.getGameTime() % 40 == 0) {
            // this.sync();
            // }
        }
    }

    public static void isPlayerOutGameAreas(ServerPlayer player, AreasWorldComponent areas) {
        final var block = player.level()
                .getBlockState(new BlockPos((int) player.getX(), (int) player.getY(), (int) player.getZ())).getBlock();
        final var block1 = player.level()
                .getBlockState(new BlockPos((int) player.getX(), (int) (player.getY() - 1), (int) player.getZ()))
                .getBlock();
        final var block2 = player.level()
                .getBlockState(new BlockPos((int) player.getX(), (int) (player.getY() - 2), (int) player.getZ()))
                .getBlock();
        if (player.getY() < areas.playArea.minY
                || !areas.canSwim && (block == Blocks.WATER && block1 == Blocks.WATER && block2 == Blocks.WATER)) {
            GameUtils.killPlayer(player, false,
                    player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null,
                    GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
        }
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
        } else if (this.getGameStatus() == GameStatus.ACTIVE || this.getGameStatus() == GameStatus.INACTIVE) {
            this.setFade(fade - 1);
        } else if (this.fade != 0) {
            this.fade = 0;
        }

        if (this.isRunning()) {
            if (gameMode == null) {

                return;
            }
            gameMode.tickCommonGameLoop();
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
}