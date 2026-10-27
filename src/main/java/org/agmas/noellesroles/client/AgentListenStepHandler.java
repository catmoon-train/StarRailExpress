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

import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.echo.client.EchoBagClient;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THMountainRoles;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class AgentListenStepHandler {
    public static final List<SoundInfo> soundInfos = new ArrayList<>();

    // 缓存
    public static final Stack<SoundInfo> soundInfoPool = new Stack<>();
    /** 追捕者听声区域：半径 4 内最多持续 2 秒，结束后该区域冷却 8 秒。 */
    private static final List<ListenZone> zones = new ArrayList<>();
    private static final double ZONE_RADIUS = 4.0D;
    private static final int ZONE_ACTIVE_TICKS = 40;
    private static final int ZONE_COOLDOWN_TICKS = 160;

    public static final ResourceLocation ECHO_TEX = ResourceLocation.tryBuild(Noellesroles.MOD_ID,
            "textures/gui/sound_gui.png");

    public static boolean listening = false;
    public static boolean inListen = false;
    public static long startListenTime = 0;

    public static final int FRAME_WIDTH = 32;
    public static final int FRAME_HEIGHT = 32;
    public static final int FRAME_COUNT = 5;
    public static final int FRAME_DURATION = 10; // 每帧持续 tick 数

    public static int tick = 0;

    public static void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                listening = false;
                inListen = false;
                return;
            }
            if (!canUseListenPassive(mc.player)) {
                listening = false;
                inListen = false;
                return;
            }
            LocalPlayer player = mc.player;
            long now = mc.level.getGameTime();
            if (isEchoHunter(player)) {
                listening = true;
                inListen = true;
                zones.removeIf(zone -> now >= zone.until);
                return;
            }
            zones.clear();

            if (player.isCrouching()) {
                if (!listening) {
                    startListenTime = mc.level.getGameTime();
                    listening = true;
                }

                if (mc.level.getGameTime() - startListenTime >= 15) {
                    inListen = true;
                }
            } else {
                listening = false;
                inListen = false;
            }
        });
    }

    public static boolean canUseListenPassive(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        if (isEchoHunter(player)) {
            return true;
        }
        if (SREClient.gameComponent == null) {
            return false;
        }
        if (SREClient.gameComponent.isRole(player, ModRoles.AGENT)) {
            return true;
        }
        if (SREClient.gameComponent.isRole(player, THMountainRoles.KASODANI_KYOUKO)) {
            return true;
        }
        return false;
    }
    public static boolean isEchoHunter(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        var echo = EchoBagClient.data();
        return echo != null && echo.faction() == EchoFaction.INTERCEPTOR && echo.life().standing();
    }

    /** 追捕者区域门控。true 表示这次声音可以显示；冷却中的区域直接拒绝。 */
    public static boolean acceptHunterPing(Vec3 pos, long now) {
        zones.removeIf(zone -> now >= zone.until);
        for (ListenZone zone : zones) {
            if (zone.center.distanceToSqr(pos) > ZONE_RADIUS * ZONE_RADIUS) {
                continue;
            }
            return now < zone.coolAt;
        }
        zones.add(new ListenZone(pos, now + ZONE_ACTIVE_TICKS, now + ZONE_ACTIVE_TICKS + ZONE_COOLDOWN_TICKS));
        return true;
    }

    public static Vector3f worldToScreen(double worldX, double worldY, double worldZ) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        // ====== 1. 世界坐标 → 相机局部坐标 ======
        Vec3 cam = camera.getPosition();

        float x = (float) (worldX - cam.x);
        float y = (float) (worldY - cam.y);
        float z = (float) (worldZ - cam.z);

        // ====== 2. 相机旋转（四元数） ======
        Quaternionf rotation = camera.rotation().conjugate(new Quaternionf());

        Vector3f local = new Vector3f(x, y, z);
        rotation.transform(local); // 应用相机旋转

        // ====== 3. 获取 Projection 矩阵 ======
        Matrix4f projection = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get());

        // 推入裁剪空间
        Vector4f clip = new Vector4f(local.x, local.y, local.z, 1.0f);
        clip.mul(projection);

        // 在背后（W < 0） → 不可见
        if (clip.w <= 0.0f) {
            return new Vector3f(Float.NaN, Float.NaN, -1);
        }

        // ====== 4. 归一化坐标 (NDC) ======
        clip.div(clip.w);

        // ====== 5. NDC → 屏幕坐标 ======
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float sx = (clip.x * 0.5f + 0.5f) * sw;
        float sy = (clip.y * -0.5f + 0.5f) * sh;

        return new Vector3f(sx, sy, clip.z);
    }

    public static class SoundInfo {
        public long time;
        public Vec3 pos;
    }
    private static final class ListenZone {
        final Vec3 center;
        final long coolAt;
        final long until;

        ListenZone(Vec3 center, long coolAt, long until) {
            this.center = center;
            this.coolAt = coolAt;
            this.until = until;
        }
    }
}
