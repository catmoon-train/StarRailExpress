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

import com.mojang.blaze3d.platform.InputConstants;
import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.YouluSmokeBallEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.YouluCamPosC2SPacket;
import org.agmas.noellesroles.packet.YouluFreeCamCancelC2SPacket;

/**
 * 幽露自由摄像机客户端处理。
 *
 * <p>服务端通过 {@code YouluFreeCamS2CPacket} 开关本模式：
 * <ul>
 *   <li>相机位置由本类维护，移动键（前后左右/跳跃/潜行）直接轮询物理按键驱动
 *       （本体带 {@code MOVE_BANED}，游戏内按键已被拦截，因此读原始键态）；
 *       朝向复用本体玩家的视角（鼠标仍旋转本体）。</li>
 *   <li>每 tick 经 {@link AdvancedCameraDirector#setFixedOverride} 接管相机（自带平滑插值）。</li>
 *   <li>按 ESC（打开任意界面）即取消：通知服务端退出且不生成球烟。</li>
 *   <li>每 4 tick 向服务端上报当前相机位置，供第二次按 G 生成球烟。</li>
 * </ul>
 *
 * <p>另外维护「本地玩家是否处于某个球烟内」的每 tick 缓存，供雾色染黑 mixin 查询
 * （见 {@code YouluFogColorMixin}）。
 */
@Environment(EnvType.CLIENT)
public final class YouluFreeCamClient {

    /** 相机移动速度（格/tick），按住冲刺键加倍。 */
    private static final double CAM_SPEED = 0.6D;
    /** 相机距本体最大距离（与服务端配置默认值一致；服务端仍会二次校验）。 */
    private static final double MAX_DISTANCE = 28.0D;
    /** 相机位置上报间隔（tick）。 */
    private static final int REPORT_INTERVAL = 4;

    private static boolean active = false;
    private static Vec3 camPos = Vec3.ZERO;
    private static int reportTimer = 0;

    /** 本地玩家当前是否处于球烟内（每 tick 刷新，供雾色 mixin 查询）。 */
    private static boolean insideSmokeBall = false;

    private YouluFreeCamClient() {
    }

    public static boolean isActive() {
        return active;
    }

    /** 供雾色 mixin 查询：本地玩家处于球烟内且带视野迷雾时，雾变为黑色。 */
    public static boolean shouldBlackenFog() {
        Minecraft client = Minecraft.getInstance();
        return insideSmokeBall && client.player != null && client.player.hasEffect(ModEffects.VISION_FOG);
    }

    /** 服务端通知进入自由摄像机。 */
    public static void enter(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;
        active = true;
        camPos = player.getEyePosition();
        reportTimer = 0;

    }

    /** 服务端通知退出（生成球烟 / 取消 / 超时）。 */
    public static void exit() {
        if (!active) return;
        active = false;
        AdvancedCameraDirector.clearFixedOverride();
    }

    /** 客户端每 tick 调用（注册于 NoellesrolesClient 的 client tick）。 */
    public static void tick(Minecraft client) {
        tickSmokeBallCache(client);
        if (!active) return;
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            active = false;
            AdvancedCameraDirector.clearFixedOverride();
            return;
        }


        // ESC（或任何界面打开）= 取消：通知服务端，不生成球烟
        if (client.screen != null) {
            ClientPlayNetworking.send(new YouluFreeCamCancelC2SPacket());
            exit();
            return;
        }
//        SREClient.isInstinctToggleEnabled = false;
        moveCamera(client, player);

        // 相机距本体软限制
        Vec3 origin = player.getEyePosition();
        Vec3 offset = camPos.subtract(origin);
        if (offset.length() > MAX_DISTANCE) {
            camPos = origin.add(offset.normalize().scale(MAX_DISTANCE));
        }

        AdvancedCameraDirector.setFixedOverride(camPos, player.getYRot(),
                Mth.clamp(player.getXRot(), -89.0F, 89.0F), 0.0F);

        if (++reportTimer >= REPORT_INTERVAL) {
            reportTimer = 0;
            ClientPlayNetworking.send(new YouluCamPosC2SPacket(camPos));
        }
    }

    /** 用移动键的物理按键状态驱动相机移动（朝向取本体视角）。 */
    private static void moveCamera(Minecraft client, LocalPlayer player) {
        double speed = isPhysicallyDown(client, client.options.keySprint) ? CAM_SPEED * 2 : CAM_SPEED;

        float yawRad = (float) Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);

        Vec3 delta = Vec3.ZERO;
        if (isPhysicallyDown(client, client.options.keyUp)) delta = delta.add(forward);
        if (isPhysicallyDown(client, client.options.keyDown)) delta = delta.subtract(forward);
        if (isPhysicallyDown(client, client.options.keyRight)) delta = delta.add(right);
        if (isPhysicallyDown(client, client.options.keyLeft)) delta = delta.subtract(right);
        if (isPhysicallyDown(client, client.options.keyJump)) delta = delta.add(0, 1, 0);
        if (isPhysicallyDown(client, client.options.keyShift)) delta = delta.add(0, -1, 0);

        if (delta.lengthSqr() > 1.0e-6) {
            camPos = camPos.add(delta.normalize().scale(speed));
        }
    }

    /** 读取按键映射对应物理键的原始按下状态（绕过 MOVE_BANED 的游戏内按键拦截）。 */
    private static boolean isPhysicallyDown(Minecraft client, KeyMapping mapping) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
        if (key == null || key.getType() != InputConstants.Type.KEYSYM
                || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return mapping.isDown();
        }
        return InputConstants.isKeyDown(client.getWindow().getWindow(), key.getValue());
    }

    /** 刷新「本地玩家是否处于球烟内」缓存。 */
    private static void tickSmokeBallCache(Minecraft client) {
        insideSmokeBall = false;
        if (client.player == null || client.level == null) return;
        Vec3 eye = client.player.getEyePosition();
        double scan = 12.0D;
        var box = client.player.getBoundingBox().inflate(scan);
        for (YouluSmokeBallEntity ball : client.level.getEntitiesOfClass(YouluSmokeBallEntity.class, box)) {
            if (ball.contains(eye)) {
                insideSmokeBall = true;
                return;
            }
        }
    }
}
