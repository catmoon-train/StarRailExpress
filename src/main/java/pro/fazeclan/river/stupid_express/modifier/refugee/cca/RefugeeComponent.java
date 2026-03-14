package pro.fazeclan.river.stupid_express.modifier.refugee.cca;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.wifi.starrailexpress.cca.*;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public class RefugeeComponent implements AutoSyncedComponent, ServerTickingComponent {

    public static final ComponentKey<RefugeeComponent> KEY = ComponentRegistry.getOrCreate(
            StupidExpress.id("refugee"),
            RefugeeComponent.class);

    public HashMap<UUID, PlayerStatsBeforeRefugee> players_stats = new HashMap<>();
    private final Level level;

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    public List<RefugeeData> getPendingRevivals() {
        return pendingRevivals;
    }

    private final List<RefugeeData> pendingRevivals = new ArrayList<>();
    public boolean isAnyRevivals = false;

    public RefugeeComponent(Level level) {
        this.level = level;
    }

    @Override
    public void serverTick() {
        if (pendingRevivals.isEmpty()) {
            return;
        }

        long currentTime = level.getGameTime();
        pendingRevivals.removeIf((data) -> {
            if (data.isDead)
                return true;
            return false;
        });
        for (RefugeeData data : pendingRevivals) {
            if (!data.isRevive && currentTime >= data.revivalTime) {
                revivePlayer(data);
                data.isRevive = true;
            }
            if (data.isRevive && !data.isDead && currentTime >= data.revivalTime + 3000) {
                data.isDead = true;
                for (var player : level.players()) {
                    if (player.getUUID().equals(data.uuid)) {
                        if (GameUtils.isPlayerAliveAndSurvival(player)) {
                            GameUtils.killPlayer(player, true, null);
                            break;
                        }
                    }
                }
                this.sync();
            }
        }
        pendingRevivals.removeIf((data) -> {
            if (data.isDead)
                return true;
            return false;
        });
        // 每200 tick（10秒）发送一次倒计时提示
        if (currentTime % 200 == 0) {
            sendCountdownMessages();
            this.sync();
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

    private void revivePlayer(RefugeeData data) {
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
        final var areasWorldComponent = AreasWorldComponent.KEY.get(serverLevel);
        final var roomPosition = areasWorldComponent.getRoomPosition(i);
        // Teleport to death location
        player.teleportTo(serverLevel, roomPosition.x, roomPosition.y, roomPosition.z, player.getYRot(),
                player.getXRot());
        BartenderPlayerComponent bartenderPlayerComponent = BartenderPlayerComponent.KEY.get(player);
        int size = serverLevel.getPlayers(GameUtils::isPlayerAliveAndSurvival).size();
        bartenderPlayerComponent.removeArmor(-1 * (Math.clamp(size / 6, 1, 3)));
        player.setGameMode(GameType.ADVENTURE);
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
        StupidRoleUtils.changeRole(player, TMMRoles.LOOSE_END, false);
        SRE.REPLAY_MANAGER.recordPlayerRevival(player.getUUID(), TMMRoles.LOOSE_END);
        StupidRoleUtils.sendWelcomeAnnouncement(player);

        TrainVoicePlugin.resetPlayer(player.getUUID());
        SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(serverLevel);
        lastTime = gameTimeComponent.getTime();
        gameTimeComponent.setTime(gameTimeComponent.getTime() + 120 * 20);
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverLevel);
        worldModifierComponent.removeModifier(player.getUUID(), SEModifiers.REFUGEE);

        // Effects and notifications
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30 * 20, 0, false, false));
        serverLevel.getServer().getCommands().performPrefixedCommand(serverLevel.getServer().createCommandSourceStack(),
                "title @a title {\"translate\":\"title.stupid_express.refugee.active\",\"color\":\"dark_red\"}");

        serverLevel.players().forEach(p -> {
            ServerPlayNetworking.send(p, new TriggerStatusBarPayload("loose_end"));
            p.playNotifySound(SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
            p.addEffect(new MobEffectInstance(MobEffects.WEAVING, 150 * 20, 0, false, false));
            p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 1.0f);

            p.sendSystemMessage(Component.translatable("hud.stupid_express.refugee.revived", player.getDisplayName()),
                    true);
        });
        if (!isAnyRevivals) {
            SavePlayersStats();
        }
        isAnyRevivals = true;
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.level);
        gameWorldComponent.disableSkillsAndSync();
        this.sync();
    }

    public void SavePlayersStats() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<ServerPlayer> players = serverLevel.getServer().getPlayerList().getPlayers();
        players_stats.clear();
        for (var player : players) {
            var ppc = SREPlayerPsychoComponent.KEY.get(player);
            if (ppc.psychoTicks > 0) {
                ppc.stopPsycho();
            }
            boolean isAlive = GameUtils.isPlayerAliveAndSurvival(player);
            if (isAlive) {
                players_stats.put(player.getUUID(), PlayerStatsBeforeRefugee.SaveFromPlayer(player, true));
            }
        }
    }

    public void LoadPlayersStats() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
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
        for (var player : players) {
            var ppc = SREPlayerPsychoComponent.KEY.get(player);
            if (ppc.psychoTicks > 0) {
                ppc.stopPsycho();
            }
            var r = gameWorldComponent.getRole(player);
            if (r != null) {
                if (r.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                    continue;
                }
            }
            var data = players_stats.get(player.getUUID());

            if (data != null) {
                PlayerStatsBeforeRefugee.LoadToPlayer(player, data, r, this, worldModifierComponent);
                // 删除玩家尸体
                // List<PlayerBodyEntity> bodies
                var body = bodies.get(player.getUUID());
                if (body != null)
                    body.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        bodies.clear();
    }

    public void onLooseEndDeath(Player who) {
        if (!(who instanceof ServerPlayer sp)) {
            return;
        }
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
        StupidExpress.LOGGER.info("Try to restore player's stat");
        for (var rev : this.pendingRevivals) {
            if (rev.uuid.equals(who.getUUID())) {
                rev.isDead = true;
            }
        }

        isAnyRevivals = false;
        gameWorldComponent.enableSkillsAndSync();
        sp.getServer().getCommands().performPrefixedCommand(sp.getServer().createCommandSourceStack(),
                "title @a title {\"translate\":\"title.stupid_express.refugee.died\",\"color\":\"gold\"}");

        sp.getServer().getPlayerList().getPlayers().forEach((p) -> {
            ServerPlayNetworking.send(p, new RemoveStatusBarPayload("loose_end"));
            p.playNotifySound(SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
            p.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 40, 0, false, false));
            if (p.hasEffect(MobEffects.WEAVING)) {
                p.removeEffect(MobEffects.WEAVING);
            }
            p.displayClientMessage(Component.translatable("gui.stupid_express.refugee.all_death"), true);
            StopSound(p, StupidExpress.SOUND_REGUGEE.getLocation(), SoundSource.AMBIENT);
        });
        sp.setGameMode(GameType.SPECTATOR);

        LoadPlayersStats();
        players_stats.clear(); // 清空玩家位置信息，避免浪费资源

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
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
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

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
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
        final long revivalTime;

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

        this.sync();
    }
}