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

package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.init.ModEffects;
import pro.fazeclan.river.stupid_express.constants.SEEffects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 纵火犯职业数据：被浇油的玩家与点燃归属都记在纵火犯身上，而不是每个受害者一份 CCA。
 */
public class ArsonistRoleData extends SimpleRoleData {

    public int dousedCount = 0;
    public final Set<UUID> dousedPlayers = new LinkedHashSet<>();
    public final Map<UUID, UUID> burningKillers = new HashMap<>();
    /** 仅服务端使用，记录真正被本纵火犯烧死的玩家及死亡位置。 */
    public final Map<UUID, BurnedPlayer> burnedPlayers = new HashMap<>();
    public UUID activeOilCan;

    public record BurnedPlayer(ServerLevel level, Vec3 position, float yaw, float pitch) {
        public static BurnedPlayer at(ServerPlayer target) {
            return new BurnedPlayer(target.serverLevel(), target.position(), target.getYRot(), target.getXRot());
        }

        public void release(ServerPlayer target, boolean revive) {
            clearBurnRestrictions(target);
            if (revive && target.isSpectator()) {
                target.teleportTo(level, position.x, position.y, position.z, yaw, pitch);
                GameUtils.revivePlayer(target, position.x, position.y, position.z);
                target.setCamera(target);
                target.displayClientMessage(Component.translatable("message.stupid_express.arsonist.revived"), false);
            }
        }
    }

    public void captureBurnedPlayer(ServerPlayer target, BurnedPlayer origin) {
        if (!(player instanceof ServerPlayer owner) || !target.isSpectator()) return;
        if (!GameUtils.isPlayerAliveAndSurvival(owner)) {
            origin.release(target, true);
            return;
        }
        burnedPlayers.put(target.getUUID(), origin);
        bindBurnedPlayer(owner, target);
        target.displayClientMessage(Component.translatable("message.stupid_express.arsonist.bound"), false);
    }

    private static void bindBurnedPlayer(ServerPlayer owner, ServerPlayer target) {
        DeathPenaltyComponent penalty = DeathPenaltyComponent.KEY.get(target);
        if (!owner.getUUID().equals(penalty.limitCameraUUID) || !penalty.hasPenalty()) {
            penalty.setPenaltyWithCameraLimit(-1, owner, true);
        }
        DefibrillatorComponent.KEY.get(target).init();
        if (!target.hasEffect(ModEffects.CHAT_BAN))
            target.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, -1, 0, false, false, false));
        if (!target.hasEffect(ModEffects.VOICE_SILENCE))
            target.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, -1, 0, false, false, false));
        if (!target.hasEffect(ModEffects.SKILL_BANED))
            target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, -1, 0, false, false, false));
        if (!target.hasEffect(ModEffects.USED_BANED))
            target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, -1, 0, false, false, false));
    }

    private static void clearBurnRestrictions(ServerPlayer target) {
        DeathPenaltyComponent.KEY.get(target).init();
        target.removeEffect(ModEffects.CHAT_BAN);
        target.removeEffect(ModEffects.VOICE_SILENCE);
        target.removeEffect(ModEffects.SKILL_BANED);
        target.removeEffect(ModEffects.USED_BANED);
        target.removeEffect(ModEffects.MOVE_BANED);
        target.setCamera(target);
    }

    /** 死亡、离线或换职业时释放；局末只解除限制，不把人复活进结算场景。 */
    public void releaseBurnedPlayers(boolean revive) {
        if (!(player instanceof ServerPlayer owner)) return;
        for (var entry : burnedPlayers.entrySet()) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(entry.getKey());
            if (target != null) entry.getValue().release(target, revive);
            else ArsonistEvents.releaseOnJoin(entry.getKey(), entry.getValue(), revive);
        }
        burnedPlayers.clear();
        for (UUID id : burningKillers.keySet()) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
            if (target != null) {
                target.removeEffect(SEEffects.BURNING);
                target.clearFire();
            }
        }
        burningKillers.clear();
        if (activeOilCan != null && owner.serverLevel().getEntity(activeOilCan) instanceof LargeOilCanEntity can) {
            can.finish();
        }
        activeOilCan = null;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer owner)) return;
        boolean running = SREGameWorldComponent.KEY.get(owner.level()).isRunning();
        if (!running || !GameUtils.isPlayerAliveAndSurvival(owner)) {
            if (!burnedPlayers.isEmpty() || !burningKillers.isEmpty() || activeOilCan != null)
                releaseBurnedPlayers(running);
            return;
        }
        if (activeOilCan != null && !(owner.serverLevel().getEntity(activeOilCan) instanceof LargeOilCanEntity)) {
            activeOilCan = null;
            owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCameraPacket(owner));
        }
        if (owner.tickCount % 20 != 0) return;
        var iterator = burnedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer target = owner.server.getPlayerList().getPlayer(entry.getKey());
            if (target == null) continue;
            if (GameUtils.isPlayerAliveAndSurvival(target)) {
                clearBurnRestrictions(target);
                iterator.remove();
            } else if (target.isSpectator()) {
                bindBurnedPlayer(owner, target);
            }
        }
    }

    @Override
    public void clear() {
        releaseBurnedPlayers(SREGameWorldComponent.KEY.get(player.level()).isRunning());
    }

    public ArsonistRoleData(RoleDataContext context) {
        super(context);
    }

    @Nullable
    public static ArsonistRoleData of(Player player) {
        return RoleData.getNullable(ArsonistRoleData.class, player);
    }

    @Nullable
    public static ArsonistRoleData find(Level level) {
        if (level == null) {
            return null;
        }
        for (Player p : level.players()) {
            ArsonistRoleData data = of(p);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    public boolean isDoused(UUID playerId) {
        return playerId != null && dousedPlayers.contains(playerId);
    }

    public void douse(Player victim) {
        if (victim == null) {
            return;
        }
        if (dousedPlayers.add(victim.getUUID())) {
            dousedCount++;
            sync();
        }
    }

    public void undouse(UUID playerId) {
        if (playerId != null && dousedPlayers.remove(playerId)) {
            sync();
        }
    }

    public void setBurningKiller(UUID victimId, @Nullable UUID killerId) {
        if (victimId == null) {
            return;
        }
        if (killerId == null) {
            burningKillers.remove(victimId);
        } else {
            burningKillers.put(victimId, killerId);
        }
        sync();
    }

    @Nullable
    public UUID getBurningKiller(UUID victimId) {
        return victimId == null ? null : burningKillers.get(victimId);
    }

    public void resetDouses() {
        dousedPlayers.clear();
        dousedCount = 0;
        sync();
    }

    public void reset() {
        releaseBurnedPlayers(SREGameWorldComponent.KEY.get(player.level()).isRunning());
        dousedPlayers.clear();
        dousedCount = 0;
        burningKillers.clear();
        sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("dousedCount", dousedCount);
        ListTag doused = new ListTag();
        for (UUID id : dousedPlayers) {
            doused.add(NbtUtils.createUUID(id));
        }
        tag.put("dousedPlayers", doused);
        ListTag burning = new ListTag();
        for (Map.Entry<UUID, UUID> entry : burningKillers.entrySet()) {
            CompoundTag pair = new CompoundTag();
            pair.putUUID("v", entry.getKey());
            pair.putUUID("k", entry.getValue());
            burning.add(pair);
        }
        tag.put("burningKillers", burning);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        dousedCount = tag.contains("dousedCount") ? tag.getInt("dousedCount") : 0;
        dousedPlayers.clear();
        ListTag doused = tag.getList("dousedPlayers", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < doused.size(); i++) {
            dousedPlayers.add(NbtUtils.loadUUID(doused.get(i)));
        }
        burningKillers.clear();
        ListTag burning = tag.getList("burningKillers", Tag.TAG_COMPOUND);
        for (int i = 0; i < burning.size(); i++) {
            CompoundTag pair = burning.getCompound(i);
            if (pair.hasUUID("v") && pair.hasUUID("k")) {
                burningKillers.put(pair.getUUID("v"), pair.getUUID("k"));
            }
        }
    }
}
