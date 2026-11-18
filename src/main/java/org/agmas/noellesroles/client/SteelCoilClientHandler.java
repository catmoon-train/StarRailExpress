package org.agmas.noellesroles.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.event.client.ClientGameplayEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.agmas.noellesroles.content.entity.SteelCoilEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.packet.SteelCoilInputC2SPacket;

/**
 * 金属怪钢卷形态的客户端处理：上报移动输入、推进滚动角、强制第三人称。
 *
 * <p>滚动角按玩家本 tick 的真实位移计算（半径 1 格），因此动画与服务端速度始终一致；
 * 伪装临时实体每帧复制玩家朝向，这里再把朝向覆盖成实际移动方向，让钢卷的圆柱轴横过来。
 */
public final class SteelCoilClientHandler {

    private static Vec3 lastPos;
    private static float rollAngle;
    private static float moveYaw;

    private SteelCoilClientHandler() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SteelCoilClientHandler::tick);
        ClientGameplayEvents.DISGUISE_STATE_COPIED.register(SteelCoilClientHandler::applyToDummy);
        AllowOtherCameraType.EVENT.register((original, localPlayer) ->
                isSteelCoil(localPlayer) ? AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK
                        : AllowOtherCameraType.ReturnCameraType.NO_CHANGE);
    }

    /** 本地玩家是否处于钢卷伪装形态（服务端同步下来的状态）。 */
    private static boolean isSteelCoil(Player player) {
        return player != null && EntityDisguise.get(player).type() == ModEntities.STEEL_COIL;
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            lastPos = null;
            rollAngle = 0.0F;
            return;
        }
        Vec3 pos = player.position();
        double dx = 0.0D;
        double dz = 0.0D;
        if (lastPos != null) {
            dx = pos.x - lastPos.x;
            dz = pos.z - lastPos.z;
        }
        lastPos = pos;

        if (!isSteelCoil(player)) {
            rollAngle = 0.0F;
            return;
        }

        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 1.0E-4D) {
            // 两格直径 → 半径 1 格，位移换算成滚动角度
            rollAngle = (rollAngle + (float) (moved * 180.0D / Math.PI)) % 360.0F;
            moveYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }

        int bits = 0;
        if (isRawDown(client, client.options.keyUp)) bits |= SteelCoilInputC2SPacket.BIT_FORWARD;
        if (isRawDown(client, client.options.keyDown)) bits |= SteelCoilInputC2SPacket.BIT_BACK;
        if (isRawDown(client, client.options.keyLeft)) bits |= SteelCoilInputC2SPacket.BIT_LEFT;
        if (isRawDown(client, client.options.keyRight)) bits |= SteelCoilInputC2SPacket.BIT_RIGHT;
        ClientPlayNetworking.send(new SteelCoilInputC2SPacket(bits, player.getYRot()));
    }

    /**
     * 直接读底层按键状态，而不是 {@link KeyMapping#isDown()}。
     *
     * <p>钢卷形态身上一直挂着 {@code MOVE_BANED}，而 {@code MobEffectKeyMixin} 会让被拦截的
     * 移动键 {@code isDown()} 恒为 {@code false}；若用它采集输入，服务端收到的永远是没有按键，
     * 钢卷就完全动不了。这里绕开效果拦截，只看物理按键是否真的按着。
     */
    private static boolean isRawDown(Minecraft client, KeyMapping mapping) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
        if (key == null || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return mapping.isDown();
        }
        long handle = client.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(handle, key.getValue());
        }
        return mapping.isDown();
    }

    private static void applyToDummy(Player player, Entity dummy) {
        if (!(dummy instanceof SteelCoilEntity coil)) {
            return;
        }
        coil.setRoll(rollAngle);
        // 伪装渲染器会把玩家视角朝向复制过来，这里覆盖为实际移动方向
        if (Math.abs(rollAngle) > 1.0E-4F || lastPos != null) {
            coil.setYRot(moveYaw);
        }
    }
}
