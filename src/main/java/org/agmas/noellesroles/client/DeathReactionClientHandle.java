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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.content.effects.FearEffects;
import org.agmas.noellesroles.init.ModEffects;

import java.util.UUID;

/**
 * 害怕抖动、暴怒红屏与锁视角、第三人称锁定。
 */
@Environment(EnvType.CLIENT)
public final class DeathReactionClientHandle {
    private static UUID rageLockId;

    private DeathReactionClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DeathReactionClientHandle::tick);
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null || !player.hasEffect(ModEffects.RAGE_SURGE)) {
                return;
            }
            float pulse = 0.42f + 0.22f * Mth.sin(player.tickCount * 0.35f);
            int alpha = Mth.clamp((int) (pulse * 255.0f), 40, 200);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xC01010);
        });
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            if (localPlayer != null && localPlayer.hasEffect(ModEffects.THIRD_PERSON)) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
        GlitchBlockRenderer.register();
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.isPaused() || !player.isAlive()) {
            rageLockId = null;
            return;
        }
        if (FearEffects.isTrembling(player)) {
            float t = player.tickCount;
            float yawDrift = (Mth.sin(t * 0.37f) + 0.7f * Mth.sin(t * 0.61f + 1.3f)) * 1.15f;
            float pitchDrift = (Mth.cos(t * 0.41f + 0.4f) + 0.7f * Mth.sin(t * 0.53f)) * 0.85f;
            player.turn(yawDrift / 0.15f, pitchDrift / 0.15f);
        }
        if (!player.hasEffect(ModEffects.RAGE_SURGE)) {
            rageLockId = null;
            return;
        }
        Player target = resolveRageTarget(client, player);
        if (target == null) {
            return;
        }
        rageLockId = target.getUUID();
        lookAt(player, target.getEyePosition());
    }

    private static Player resolveRageTarget(Minecraft client, LocalPlayer observer) {
        if (rageLockId != null) {
            Player locked = client.level.getPlayerByUUID(rageLockId);
            if (locked != null && GameUtils.isPlayerAliveAndSurvival(locked)
                    && !locked.getUUID().equals(observer.getUUID())) {
                return locked;
            }
        }
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player other : client.level.players()) {
            if (other.getUUID().equals(observer.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            double distance = observer.distanceToSqr(other);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 delta = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
    }
}
