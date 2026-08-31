/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pro.fazeclan.river.stupid_express.modifier.refugee.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.role_data.neutral.MonokumaRoleData;
import org.agmas.noellesroles.api.time.TimeRewind;
import org.agmas.noellesroles.api.time.TimeRewindAreaResult;
import org.agmas.noellesroles.api.time.TimeRewindAreaSnapshot;
import org.agmas.noellesroles.api.time.TimeRewindResult;
import org.agmas.noellesroles.api.time.TimeRewindSnapshot;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEventsRegister;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class RefugeeComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<RefugeeComponent> KEY = ComponentRegistry.getOrCreate(
            StupidExpress.id("refugee"),
            RefugeeComponent.class);

    public HashMap<UUID, PlayerStatsBeforeRefugee> players_stats = new HashMap<>();
    /** Full snapshots replacing the old partial loose-end rewind. */
    private final HashMap<UUID, TimeRewindSnapshot> playerTimeRewindSnapshots = new HashMap<>();
    private TimeRewindAreaSnapshot areaTimeRewindSnapshot;
    private final Level level;

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    public void clear() {
        this.pendingRevivals.clear();
        this.pendingWho = null;
        this.isPendingRestore = false;
        this.players_stats.clear();
        this.playerTimeRewindSnapshots.clear();
        this.areaTimeRewindSnapshot = null;
    }

    public List<RefugeeData> getPendingRevivals() {
        return pendingRevivals;
    }

    private final List<RefugeeData> pendingRevivals = new ArrayList<>();
    public boolean isAnyRevivals = false;
    public boolean isPendingRestore = false;
    private Player pendingWho = null;

    public RefugeeComponent(Level level) {
        this.level = level;
    }

    @Override
    public void serverTick() {
        if (isPendingRestore) {
            isPendingRestore = false;
            afterLooseEndTryRestore(pendingWho);
        }
        if (pendingRevivals.isEmpty()) {
            return;
        }
        boolean shouldSync = false;

        long currentTime = level.getGameTime();
        boolean timeFrozen = SREGameTimeComponent.KEY.get(level).timeFrozen;
        for (RefugeeData data : new ArrayList<>(pendingRevivals)) {
            final var player = level.getPlayerByUUID(data.uuid);
            if (player == null) {
                data.isDead = true;
                continue;
            }
            if (GameUtils.isPlayerAliveAndSurvival(player) && !data.isRevive) {
                data.isDead = true;
                continue;
            }
            if (DefibrillatorComponent.KEY.get(player).isReviving()) {
                data.isDead = true;
                continue;
            }
            if (timeFrozen) {
                data.revivalTime += 1;
                continue;
            }
            if (!data.isRevive && currentTime >= data.revivalTime) {
                reviveLooseEnd(data);
                data.isRevive = true;
            }
            if (data.isRevive && !data.isDead && currentTime >= data.revivalTime + 3000) {
                data.isDead = true;
                {
                    if (player.getUUID().equals(data.uuid)) {
                        if (GameUtils.isPlayerAliveAndSurvival(player)) {
                            GameUtils.killPlayer(player, true, null, StupidExpress.id("loose_end"), true);
                            break;
                        }
                    }
                }
            }
        }
        AtomicBoolean anyOneRemoved = new AtomicBoolean(false);
        pendingRevivals.removeIf((data) -> {
            if (data.isDead) {
                anyOneRemoved.set(true);
                return true;
            }
            return false;
        });
        shouldSync = anyOneRemoved.get() || anyOneRemoved.get();
        // 每600 tick（30秒）发送一次倒计时提示
        if (currentTime % 600 == 0) {
            sendCountdownMessages();
            shouldSync = true;
        }
        if (shouldSync) {
            sync();
        }
    }

    private void sendCountdownMessages() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        long currentTime = level.getGameTime();
        for (RefugeeData data : pendingRevivals) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(data.uuid);
            if (player == null) {
                continue;
            }

            long ticksRemaining = data.revivalTime - currentTime;
            int secondsRemaining = (int) ((ticksRemaining + 19) / 20);

            // 只在特定时间点发送消息（60秒、30秒、10秒）
            if (secondsRemaining == 60 || secondsRemaining == 30 || secondsRemaining == 10) {
                player.sendSystemMessage(
                        Component.translatable("hud.stupid_express.refugee.countdown", secondsRemaining), true);
            }
        }
    }

    public void sync() {
        KEY.sync(this.level);
    }

    private static int lastTime = -1;

    private void reviveLooseEnd(RefugeeData data) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(data.uuid);
        if (player == null) {
            return; // Player is offline
        }

        var i = GameUtils.roomToPlayer.get(data.uuid);
        if (i == null) {
            i = 1;
        }
        MonokumaRoleData monokumaData = io.wifi.starrailexpress.api.data.RoleData.getNullable(MonokumaRoleData.class,
                player);
        if (monokumaData != null) {
            monokumaData.clear();
        }
        WorldModifierComponent.KEY.get(player.serverLevel()).removeModifier(data.uuid, SEModifiers.REFUGEE);

        final var areasWorldComponent = AreasWorldComponent.KEY.get(serverLevel);
        final var roomPosition = areasWorldComponent.getRoomPosition(i);
        // Teleport to death location
        player.teleportTo(serverLevel, roomPosition.x, roomPosition.y, roomPosition.z, player.getYRot(),
                player.getXRot());
        SREArmorPlayerComponent armorCCA = SREArmorPlayerComponent.KEY.get(player);
        int size = serverLevel.getPlayers(GameUtils::isPlayerAliveAndSurvival).size();
        armorCCA.addArmor((Math.clamp(size / 6, 1, 6)));
        player.setGameMode(GameType.ADVENTURE);

        player.addEffect(ModEffects.of(ModEffects.SAFE_TIME, 10, 1, false, false, true));

        SREWorldBlackoutComponent.KEY.get(player.level()).triggerBlackout();
        // Remove body entity
        var bodies = serverLevel.getAllEntities();

        List<Entity> bodiesToRemove = new ArrayList<>();
        for (var body : bodies) {
            if (body instanceof PlayerBodyEntity bodyEntity) {
                if (bodyEntity.getPlayerUuid().equals(data.uuid)) {
                    bodiesToRemove.add(body);
                    break;
                }
            }
        }
        bodiesToRemove.forEach(Entity::discard);
        player.getInventory().clearContent();

        // Change role to LOOSE_END and remove REFUGEE modifier
        StupidRoleUtils.changeRole(player, TMMRoles.LOOSE_END, false, false);
        SRE.REPLAY_MANAGER.recordPlayerRevival(player.getUUID(), TMMRoles.LOOSE_END);
        StupidRoleUtils.sendWelcomeAnnouncement(player);

        // 亡命徒复活倒计时归零时，释放鹈鹕肚子里的所有玩家
        org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager.onLastStand(serverLevel);
        org.agmas.noellesroles.role_data.neutral.RavenRoleData.onLastStand(serverLevel);

        TrainVoicePlugin.resetPlayer(player.getUUID());
        SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(serverLevel);
        lastTime = gameTimeComponent.getTime();
        gameTimeComponent.setTime(gameTimeComponent.getTime() + 120 * 20);
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverLevel);
        worldModifierComponent.removeModifier(player.getUUID(), SEModifiers.REFUGEE);
        // 给效果前保存状态
        if (!isAnyRevivals) {
            SavePlayersStats();
        }

        // Effects and notifications
        // 变更：亡命徒发光时间由 30s 调整为 5 分钟（300s）
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 5 * 60 * 20, 0, false, false));
        for (final ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            SRENetworkMessageUtils.sendSubtitle(p,
                    Component.translatable("title.stupid_express.refugee.subtlte.active")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
            SRENetworkMessageUtils.sendTitle(p,
                    Component.translatable("title.stupid_express.refugee.active").withStyle(ChatFormatting.DARK_RED));
        }
        serverLevel.players().forEach(p -> {
            ServerPlayNetworking.send(p, new TriggerStatusBarPayload("loose_end"));
            p.playNotifySound(SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
            p.addEffect(new MobEffectInstance(MobEffects.WEAVING, 150 * 20, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
            p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 1.0f);

            p.displayClientMessage(Component.translatable("hud.stupid_express.refugee.revived", player.getName()),
                    true);
        });
        isAnyRevivals = true;
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.level);
        // 给所有鹈鹕玩家施加技能禁用效果，持续时间与亡命徒时刻一致（3000 ticks = 150秒）
        for (var p : serverLevel.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p) && gameWorldComponent.isRole(p, ModRoles.PELICAN)) {
                p.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, 3000, 0, false, false, true));
            }
        }
        gameWorldComponent.disableSkillsAndSync();
        this.sync();
    }

    public void SavePlayersStats() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<ServerPlayer> players = serverLevel.getServer().getPlayerList().getPlayers();
        players_stats.clear();
        playerTimeRewindSnapshots.clear();
        for (var player : players) {
            boolean isAlive = GameUtils.isPlayerAliveAndSurvival(player);
            if (isAlive) {
                player.stopRiding();
                player.stopSleeping();
                var ppc = SREPlayerPsychoComponent.KEY.get(player);
                if (ppc.psychoTicks > 0) {
                    ppc.stopPsychoAndRefreshPsychoCount(true);
                    ppc.sync();
                    SREPlayerShopComponent srePlayerShopComponent = SREPlayerShopComponent.KEY.get(player);
                    srePlayerShopComponent.addToBalance((int) (SREConfig.instance().psychoModePrice * 0.75));
                    srePlayerShopComponent.sync();
                }
                players_stats.put(player.getUUID(), PlayerStatsBeforeRefugee.SaveFromPlayer(player, true));
                try {
                    playerTimeRewindSnapshots.put(player.getUUID(), TimeRewind.capture(player));
                } catch (RuntimeException exception) {
                    StupidExpress.LOGGER.error("Failed to capture full loose-end rewind for {}",
                            player.getScoreboardName(), exception);
                }
            }
        }
        try {
            areaTimeRewindSnapshot = TimeRewind.captureArea(serverLevel,
                    AreasWorldComponent.KEY.get(serverLevel).getPlayArea());
        } catch (RuntimeException exception) {
            areaTimeRewindSnapshot = null;
            StupidExpress.LOGGER.error("Failed to capture loose-end game-area rewind", exception);
        }
        SREGameWorldComponent.KEY.get(level).sync();
    }

    public void LoadPlayersStats() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        TimeRewindAreaSnapshot capturedArea = areaTimeRewindSnapshot;
        areaTimeRewindSnapshot = null;
        List<ServerPlayer> players = serverLevel.getServer().getPlayerList().getPlayers();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        var entities = serverLevel.getAllEntities();
        var bodies = new HashMap<UUID, PlayerBodyEntity>();
        for (var entity : entities) {
            if (entity instanceof PlayerBodyEntity body) {
                bodies.put(body.getPlayerUuid(), body);
            }
        }
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(this.level);
        var pendingSmoothRestores = new java.util.concurrent.atomic.AtomicInteger();
        var schedulingFinished = new AtomicBoolean(false);
        Runnable finishWhenReady = () -> {
            if (schedulingFinished.get() && pendingSmoothRestores.get() == 0) {
                finishLooseEndRestore(serverLevel, capturedArea, bodies);
            }
        };
        // 给予 2 tick 的deathPenalty
        for (var player : players) {
            var dpc = DeathPenaltyComponent.KEY.get(player);
            if (dpc.hasPenalty()) {
                continue;
            }
            dpc.setPenalty(2, true);
        }
        for (var player : players) {
            var ppc = SREPlayerPsychoComponent.KEY.get(player);
            if (ppc.psychoTicks > 0) {
                ppc.stopPsychoAndRefreshPsychoCount(false);
            }
            var r = gameWorldComponent.getRole(player);
            if (r != null) {
                if (r.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())
                        || r.identifier().getPath().equals(ModRoles.MONOKUMA.identifier().getPath())) {
                    continue;
                }
            }
            var data = players_stats.get(player.getUUID());
            var snapshot = playerTimeRewindSnapshots.get(player.getUUID());

            if (data != null || snapshot != null) {
                boolean wasAlive = GameUtils.isPlayerAliveAndSurvival(player);

                if (snapshot != null) {
                    PlayerStatsBeforeRefugee.invokeBeforeLoad(player);
                    pendingSmoothRestores.incrementAndGet();
                    TimeRewindResult result = TimeRewind.restore(player, snapshot);
                    finishLooseEndPlayer(player, r, data, wasAlive, true, result,
                            gameWorldComponent, worldModifierComponent, bodies);
                } else {
                    // Original partial rewind remains the compatibility fallback.
                    PlayerStatsBeforeRefugee.LoadToPlayer(player, data, r, this,
                            worldModifierComponent, true);
                    removeBody(player, bodies);
                }
            }
        }
        SREGameWorldComponent.getInstance(serverLevel).refreshPsychoCount(true);
        schedulingFinished.set(true);
        finishWhenReady.run();
    }

    private void finishLooseEndPlayer(ServerPlayer player, io.wifi.starrailexpress.api.SRERole role,
            PlayerStatsBeforeRefugee legacyData, boolean wasAlive, boolean beforeLoadInvoked,
            TimeRewindResult result, SREGameWorldComponent gameWorldComponent,
            WorldModifierComponent worldModifierComponent, HashMap<UUID, PlayerBodyEntity> bodies) {
        boolean vanillaRestored = result.failures().stream()
                .noneMatch(failure -> failure.scope().equals("player")
                        || failure.scope().equals("vanilla")
                        || failure.scope().equals("playback"));
        if (!result.isSuccess()) {
            StupidExpress.LOGGER.warn(
                    "Loose-end rewind for {} completed with {} recoverable issue(s): {}",
                    player.getScoreboardName(), result.failures().size(), result.failures());
        }
        if (player.hasDisconnected()) {
            removeBody(player, bodies);
            return;
        }
        if (vanillaRestored) {
            if (!wasAlive) {
                SRE.REPLAY_MANAGER.recordPlayerRevival(player.getUUID(), role);
            }
            if (!gameWorldComponent.isRole(player, BounsRoles.BASEBALL_PLAYER)) {
                RoleUtils.clearAllSatisfiedItems(player, TMMItems.BAT);
            }
            player.setCamera(player);
            player.addEffect(ModEffects.of(ModEffects.SAFE_TIME, 20, 0, true, false, true));
            TrainVoicePlugin.resetPlayer(player.getUUID());
        } else if (legacyData != null) {
            PlayerStatsBeforeRefugee.LoadToPlayer(player, legacyData, role, this,
                    worldModifierComponent, !beforeLoadInvoked);
        }
        removeBody(player, bodies);
    }

    private static void removeBody(ServerPlayer player, HashMap<UUID, PlayerBodyEntity> bodies) {
        var body = bodies.remove(player.getUUID());
        if (body != null) {
            body.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    private void finishLooseEndRestore(ServerLevel serverLevel,
            TimeRewindAreaSnapshot capturedArea, HashMap<UUID, PlayerBodyEntity> bodies) {
        if (capturedArea != null) {
            try {
                TimeRewindAreaResult result = TimeRewind.restoreArea(serverLevel, capturedArea);
                if (!result.isSuccess()) {
                    StupidExpress.LOGGER.warn(
                            "Loose-end area rewind completed with {} recoverable issue(s): {}",
                            result.failures().size(), result.failures());
                }
            } catch (RuntimeException exception) {
                StupidExpress.LOGGER.error("Failed to restore loose-end game-area rewind", exception);
            }
        }
        SREGameWorldComponent.KEY.get(level).sync();
        bodies.clear();
        ModEventsRegister.reJudgeSpectatorsPenalty(level);
        this.sync();
    }

    public void onLooseEndDeath(Player who, ResourceLocation deathReason) {
        if (!(who instanceof ServerPlayer sp)) {
            return;
        }
        MCItemsUtils.clearItem(who);
        SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(sp.level());
        gameTimeComponent.setTime(lastTime);
        var gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
        var a = sp.getServer().getPlayerList().getPlayers().stream().anyMatch((p) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(p) || p.getUUID().equals(who.getUUID())) {
                return false;
            }
            var r = gameWorldComponent.getRole(p);
            if (r != null) {
                if (r.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                    return true;
                }
            }
            return false;
        });
        if (a) {
            return;
        }

        if (deathReason.equals(GameConstants.DeathReasons.DISCONNECT)) {
            afterLooseEndTryRestore(who);
            return;
        }
        isPendingRestore = true;
        pendingWho = who;
        sp.setGameMode(GameType.SPECTATOR);
    }

    public void afterLooseEndTryRestore(Player who) {
        if (!(who instanceof ServerPlayer sp)) {
            return;
        }
        var gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
        var a = sp.getServer().getPlayerList().getPlayers().stream().anyMatch((p) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(p) || p.getUUID().equals(who.getUUID())) {
                return false;
            }
            var r = gameWorldComponent.getRole(p);
            if (r != null) {
                if (r.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                    return true;
                }
            }
            return false;
        });
        if (a) {
            return;
        }
        sp.setGameMode(GameType.SPECTATOR);
        isAnyRevivals = false;
        StupidExpress.LOGGER.info("Try to restore player's stat");
        for (var rev : this.pendingRevivals) {
            if (rev.uuid.equals(who.getUUID())) {
                rev.isDead = true;
            }
        }
        isAnyRevivals = false;
        gameWorldComponent.enableSkillsAndSync();
        for (final ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            SRENetworkMessageUtils.sendTitle(p,
                    Component.translatable("title.stupid_express.refugee.died").withStyle(ChatFormatting.GOLD));
            SRENetworkMessageUtils.sendSubtitle(p,
                    Component.translatable("title.stupid_express.refugee.died.subtitle")
                            .withStyle(ChatFormatting.AQUA));
        }

        sp.getServer().getPlayerList().getPlayers().forEach((p) -> {
            ServerPlayNetworking.send(p, new RemoveStatusBarPayload("loose_end"));
            p.playNotifySound(SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
            p.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 40, 0, false, false));
            if (p.hasEffect(MobEffects.WEAVING)) {
                p.removeEffect(MobEffects.WEAVING);
            }
            p.displayClientMessage(Component.translatable("gui.stupid_express.refugee.all_death"), true);
            StopSound(p, StupidExpress.SOUND_REGUGEE.getLocation(), SoundSource.AMBIENT);
        });

        LoadPlayersStats();
        players_stats.clear(); // 清空玩家位置信息，避免浪费资源
        playerTimeRewindSnapshots.clear();
        areaTimeRewindSnapshot = null;
        // Penalty re-evaluation runs after every smooth player and the area have
        // reached their rewind nodes.
        this.sync();
    }

    public static void StopSound(ServerPlayer serverPlayer, ResourceLocation resourceLocation,
            SoundSource soundSource) {
        ClientboundStopSoundPacket clientboundStopSoundPacket = new ClientboundStopSoundPacket(resourceLocation,
                soundSource);
        serverPlayer.connection.send(clientboundStopSoundPacket);
    }

    public void addPendingRevival(UUID uuid, double x, double y, double z) {
        // 2 minutes = 120 seconds = 2400 ticks
        long revivalTime = level.getGameTime() + 2400;
        pendingRevivals.add(new RefugeeData(uuid, revivalTime, false));
        this.sync();
    }

    public long getRevivalTime(UUID uuid) {
        for (RefugeeData data : pendingRevivals) {
            if (data.uuid.equals(uuid)) {
                return data.revivalTime;
            }
        }
        return -1;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        this.writeToSyncNbt(tag, buf.registryAccess());
        buf.writeNbt(tag);
    }

    @CheckEnvironment(EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            this.readFromSyncNbt(tag, buf.registryAccess());
        }
    }

    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        pendingRevivals.clear();
        if (tag.contains("pending_revivals")) {
            ListTag list = tag.getList("pending_revivals", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag item = list.getCompound(i);
                pendingRevivals.add(new RefugeeData(
                        item.getUUID("uuid"),
                        item.getLong("revival_time"),
                        item.getBoolean("is_revive")));
            }
        }
        isAnyRevivals = tag.getBoolean("isAnyRevivals");
    }

    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (RefugeeData data : pendingRevivals) {
            CompoundTag item = new CompoundTag();
            item.putUUID("uuid", data.uuid);
            item.putLong("revival_time", data.revivalTime);
            item.putBoolean("is_revive", data.isRevive);

            list.add(item);
        }
        tag.put("pending_revivals", list);
        tag.putBoolean("isAnyRevivals", isAnyRevivals);
    }

    public static class RefugeeData {
        final UUID uuid;
        long revivalTime;

        public boolean isRevive() {
            return isRevive;
        }

        public RefugeeData setRevive(boolean revive) {
            isRevive = revive;
            return this;
        }

        public long getRevivalTime() {
            return revivalTime;
        }

        public UUID getUuid() {
            return uuid;
        }

        boolean isRevive, isDead = false;

        RefugeeData(UUID uuid, long revivalTime, boolean isRevive) {
            this.uuid = uuid;
            this.revivalTime = revivalTime;
            this.isRevive = isRevive;

        }
    }

    public void reset() {
        this.players_stats.clear();
        this.isAnyRevivals = false;
        this.pendingRevivals.clear();
        this.isPendingRestore = false;
        this.pendingWho = null;
        this.sync();
    }

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            if (RoleUtils.isPlayerTheJob(sender, TMMRoles.LOOSE_END)
                    && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(sender))
                return false;
            return true;
        });
    }

    @Override
    public void clientTick() {
        boolean timeFrozen = SREGameTimeComponent.KEY.get(level).timeFrozen;
        if (timeFrozen) {
            for (RefugeeData data : new ArrayList<>(pendingRevivals)) {
                {
                    data.revivalTime++;
                }
            }
        }
    }
}
