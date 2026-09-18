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

package org.agmas.noellesroles.game.modifier.refugee;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.packet.CustomParticleS2CPayload;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.Scheduler;
import net.exmo.sre.planecrash.PlaneCrashIntroPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;

import java.util.List;

/**
 * 难民词条变成亡命徒时的出场演出，以及仅难民转化才发放的专属武器替换。
 */
public final class RefugeeDesperadoFx {
    public static final ResourceLocation SPAWN_ID = Noellesroles.id("refugee_desperado_spawn");
    public static final ResourceLocation SLASH_ID = Noellesroles.id("desperado_sword_wave");
    public static final ResourceLocation BEAM_ID = Noellesroles.id("desperado_gun_beam");

    public static final int SPAWN_FOCUS_TICKS = 60;
    public static final int SPAWN_FOCUS_DELAY_TICKS = 60;
    public static final int SPAWN_NIGHT_VISION_TICKS = 100;

    private RefugeeDesperadoFx() {
    }

    public static void replaceStarterWeapons(ServerPlayer player) {
        SREItemUtils.clearItem(player, TMMItems.KNIFE);
        SREItemUtils.clearItem(player, TMMItems.DERRINGER);
        MCItemsUtils.insertStackInFreeSlot(player, ModItems.DESPERADO_KNIFE.getDefaultInstance());
        MCItemsUtils.insertStackInFreeSlot(player, ModItems.DESPERADO_GUN.getDefaultInstance());
    }

    public static void playSpawn(ServerLevel level, ServerPlayer desperado) {
        int entityId = desperado.getId();
        for (ServerPlayer player : level.players()) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SPAWN_NIGHT_VISION_TICKS, 0, false, false, true));
        }
        Scheduler.schedule(() -> {
            if (level.getServer() == null) {
                return;
            }
            ServerPlayer still = level.getServer().getPlayerList().getPlayer(desperado.getUUID());
            Vec3 origin = still != null ? still.position() : desperado.position();
            int focusId = still != null ? still.getId() : entityId;
            PacketTracker.sendToClients(level.players(),
                    new PlaneCrashIntroPayload(focusId, SPAWN_FOCUS_TICKS));
            sendCustomToAll(level, SPAWN_ID, origin, SPAWN_FOCUS_TICKS);
            level.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.15f, 0.55f);
            level.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.4f, 0.65f);
            level.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.85f, 0.45f);
        }, SPAWN_FOCUS_DELAY_TICKS);
    }

    public static void sendCustomToAll(ServerLevel level, ResourceLocation id, Vec3 origin,
            int durationTicks, float... params) {
        CustomParticleS2CPayload payload = new CustomParticleS2CPayload(List.of(
                CustomParticleS2CPayload.Entry.at(id, origin.x, origin.y, origin.z, durationTicks, params)));
        PacketTracker.sendToClients(level.players(), payload);
    }

    public static void sendCustomNearby(ServerLevel level, ResourceLocation id, Vec3 origin,
            int durationTicks, float... params) {
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level, id, origin, durationTicks, params);
    }

    public static boolean isLivingTarget(ServerPlayer attacker, net.minecraft.world.entity.player.Player target) {
        return target != attacker && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(target);
    }
}
