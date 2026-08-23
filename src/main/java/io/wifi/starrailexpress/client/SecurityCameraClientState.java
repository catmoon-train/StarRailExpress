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

package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.content.block.CameraBlock;
import io.wifi.starrailexpress.network.SecurityCameraExitRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端本地持有的监控模式状态。
 * <p>
 * 必须放在纯客户端类里，并且只能由客户端收到 SecurityCameraModePayload 时修改：
 * 在集成服务器（局域网/开放）场景下，服务端与腐竹客户端共享同一个 JVM，
 * 如果这个状态存在 SecurityMonitorBlock 的 static 字段上，其他玩家点击监视器时
 * 服务端的写入会直接污染腐竹客户端的渲染/输入逻辑。
 */
public final class SecurityCameraClientState {
    private static boolean isInSecurityMode = false;
    private static BlockPos currentCameraPos = null;
    private static float currentYaw = 0.0f;
    public static float lastCameraYaw;
    public static float lastCameraPitch;
    public static float yawIncrease;
    public static float pitchIncrease;
    public static int lastCameraId = -1;

    private static boolean preventShiftTillNextKeyUp = false;

    private SecurityCameraClientState() {
    }

    public static boolean onPlayerRotated(double pitchAdd) {
        if (isInSecurityMode()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null)
                return false;

            // float scale = 0.02f;

            // 累加视角偏移量
            // currentPitch = Mth.clamp(currentPitch + (float) ((pitchAdd - currentPitch) *
            // scale), -90, 90);

            // 不更新玩家实体朝向，只更新相机视角
            // 移除 player.turn() 调用以避免与相机视角冲突
            // player.turn((float) (yawAdd * scale), (float) (pitchAdd * scale));
            // player.yHeadRotO = player.yHeadRot;
            // player.xRotO = player.getXRot();

            return true;
        }
        return false;
    }

    public static void onInputUpdate(Input input) {
        // resets input
        if (isInSecurityMode()) {
            input.down = false;
            input.up = false;
            input.left = false;
            input.right = false;
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
        }
        input.shiftKeyDown = false;
        input.jumping = false;
    }

    public static void modifyInputUpdate(Input instance, LocalPlayer player) {
        if (isInSecurityMode()) {
            onInputUpdate(instance);
            preventShiftTillNextKeyUp = true;
        } else if (preventShiftTillNextKeyUp) {
            if (!instance.shiftKeyDown) {
                preventShiftTillNextKeyUp = false;
            } else {
                instance.shiftKeyDown = false;
            }
        }
    }

    public static boolean onEarlyKeyPress(int key, int scanCode, int action, int modifiers) {
        if (!isInSecurityMode())
            return false;
        if (action != GLFW.GLFW_PRESS)
            return false;
        var options = Minecraft.getInstance().options;
        // ESC 键退出监控模式 - 发送退出请求到服务端
        if (key == 256) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                // 发送退出请求到服务端
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new SecurityCameraExitRequestPayload());
            }
            return true;
        } else if (options.keyInventory.matches(key, scanCode)) {
            return true;
        }
        if (options.keyJump.matches(key, scanCode)) {
            return true;
        }
        if (options.keyShift.matches(key, scanCode)) {
            return false;
        }
        return false;
    }

    public static boolean setupCameraMod(Camera camera, BlockGetter level, Entity entity,
            boolean detached, boolean thirdPersonReverse, float partialTick) {

        if (!isInSecurityMode())
            return false;
        BlockPos cameraPos = getCurrentCameraPos();

        float targetXRot;
        // currentPitch = 0f;

        // 获取监控控制台的位置（用于获取监控方块朝向）
        // BlockPos monitorPos = getCurrentMonitorPos();

        if (level != null) {
            BlockState monitorState = level.getBlockState(cameraPos);
            if (monitorState.getBlock() instanceof CameraBlock) {
                Direction monitorFacing = monitorState.getValue(CameraBlock.FACING);

                // 根据监控控制台方向计算基础旋转角度
                float baseYaw = getBaseYawFromDirection(monitorFacing);
                // 计算目标视角：基础角度 + 玩家调整的偏移量
                targetXRot = baseYaw;
                currentYaw = baseYaw;

            } else {
                // 如果无法获取监控方块，使用默认值
                targetXRot = currentYaw;
            }
        } else {
            // 如果无法获取世界或监控方块位置，则使用默认行为
            targetXRot = currentYaw;
        }

        camera.setRotation(targetXRot, 0);
        // 设置相机位置到摄像头位置
        Vec3 targetCameraPos = cameraPos.getCenter().add(0, -1.2, 0);

        camera.setPosition(targetCameraPos);

        lastCameraYaw = camera.getYRot();
        lastCameraPitch = camera.getXRot();

        yawIncrease = 0;
        pitchIncrease = 0;

        return true;
    }

    /**
     * 根据方向获取基础偏航角
     * 
     * @param direction 摄像头方向
     * @return 对应的基础偏航角
     */
    private static float getBaseYawFromDirection(Direction direction) {
        switch (direction) {
            case SOUTH: // -Z方向
                return 180.0f;
            case NORTH: // +Z方向
                return 0.0f;
            case EAST: // -X方向
                return 90.0f;
            case WEST: // +X方向
                return -90.0f;
            default:
                return 0.0f;
        }
    }

    public static boolean isInSecurityMode() {
        return isInSecurityMode;
    }

    public static BlockPos getCurrentCameraPos() {
        return currentCameraPos;
    }

    public static void setCurrentCameraPos(BlockPos pos) {
        currentCameraPos = pos;
    }

    public static void setSecurityMode(boolean mode) {
        isInSecurityMode = mode;
    }

    public static float getCurrentYaw() {
        return currentYaw;
    }

    public static void setCurrentYaw(float yaw) {
        currentYaw = yaw;
    }
}
