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

import io.wifi.starrailexpress.client.gui.SecurityMonitorFeedRenderer;
import io.wifi.starrailexpress.content.block.CameraBlock;
import io.wifi.starrailexpress.content.block_entity.CameraBlockEntity;
import io.wifi.starrailexpress.network.SecurityCameraExitRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
    /**
     * 动态模块允许的最大视角偏移（度），水平与俯仰共用。
     * <p>
     * 偏移只存在客户端：视角完全由本机计算，不需要服务端知道，也就不会被服务端同步
     * （例如激光冷却每秒一次）覆盖掉。
     */
    public static final float MAX_VIEW_OFFSET = 90.0F;

    /** 监控摄像头相对方块中心的竖向偏移：镜头下沉到方块底部附近。 */
    private static final double CAMERA_Y_OFFSET = -1.2D;

    /** 监控台自带镜头的高度：相对方块中心上移一格，也就是监控台上方那一格。 */
    private static final double MONITOR_FEED_HEIGHT_OFFSET = 1.0D;

    /** 监控台小窗镜头沿正面方向前移，避免视线被监控台自身的方块挡住。 */
    private static final double MONITOR_FEED_FORWARD_OFFSET = 0.55D;

    /** 向服务端上报镜头方向的最小间隔（tick）：约每 100ms 一次，足够让红外发光跟手。 */
    private static final int LOOK_REPORT_INTERVAL_TICKS = 2;

    private static boolean isInSecurityMode = false;
    private static BlockPos currentCameraPos = null;
    /** 监控台自身的绝对位置（服务端已把绑定时的相对偏移加回去了）。 */
    private static BlockPos currentMonitorPos = null;
    private static float currentYaw = 0.0f;
    public static float lastCameraYaw;
    public static float lastCameraPitch;
    public static float yawIncrease;
    public static float pitchIncrease;
    public static int lastCameraId = -1;
    /** 动态模块累加出来的视角偏移（相对摄像头方块朝向）。 */
    private static float viewYawOffset = 0.0F;
    private static float viewPitchOffset = 0.0F;
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
        // 小窗离屏渲染：这一帧的主相机改用「监控台本身」的正面镜头。
        if (SecurityMonitorFeedRenderer.isCapturing()) {
            return setupMonitorFeedCamera(camera, level);
        }
        BlockPos cameraPos = getCurrentCameraPos();
        if (cameraPos == null)
            return false;

        // 基础角度来自摄像头方块的朝向；动态模块在此基础上叠加玩家自己转出来的偏移。
        float targetYaw = currentYaw;
        float targetPitch = 0.0F;

        if (level != null) {
            BlockState cameraState = level.getBlockState(cameraPos);
            if (cameraState.getBlock() instanceof CameraBlock) {
                Direction cameraFacing = cameraState.getValue(CameraBlock.FACING);
                float baseYaw = getBaseYawFromDirection(cameraFacing);
                currentYaw = baseYaw;
                targetYaw = baseYaw;
                // 只有在装了动态模块（且摄像头没失灵）时，模块偏移才生效。
                if (level.getBlockEntity(cameraPos) instanceof CameraBlockEntity cbe && cbe.canRotateView()) {
                    targetYaw = baseYaw + viewYawOffset;
                    targetPitch = viewPitchOffset;
                } else if (viewYawOffset != 0.0F || viewPitchOffset != 0.0F) {
                    // 模块失效 / 被拆掉：偏移作废，画面回到固定视角
                    viewYawOffset = 0.0F;
                    viewPitchOffset = 0.0F;
                }
            }
        }

        camera.setRotation(targetYaw, targetPitch);
        // 设置相机位置到摄像头位置，竖向偏移与小窗镜头共用同一个常量
        Vec3 targetCameraPos = cameraPos.getCenter().add(0, CAMERA_Y_OFFSET, 0);

        camera.setPosition(targetCameraPos);

        lastCameraYaw = camera.getYRot();
        lastCameraPitch = camera.getXRot();

        yawIncrease = 0;
        pitchIncrease = 0;

        return true;
    }

    /**
     * 监控台自带的「小摄像头」：从方块正面朝前看，正好对着站在台前的玩家。
     *
     * <p>位置直接取服务端同步过来的监控台绝对坐标（绑定时的相对偏移已加好），
     * 并且每帧重新读方块状态，所以监控台被搬动、转向或偏移变化后，小窗画面都会正确跟着变。
     */
    private static boolean setupMonitorFeedCamera(Camera camera, BlockGetter level) {
        BlockPos monitorPos = currentMonitorPos != null ? currentMonitorPos : currentCameraPos;
        if (monitorPos == null || level == null) {
            return false;
        }
        BlockState state = level.getBlockState(monitorPos);
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        // getBaseYawFromDirection 给的是「背对传入方向」的 yaw（原本配合 CameraBlock 的放置朝向）；
        // 监控台的 FACING 指向站在台前的玩家，镜头要顺着正面看出去，因此传它的反方向。
        camera.setRotation(getBaseYawFromDirection(facing.getOpposite()), 0.0F);
        camera.setPosition(monitorFeedCameraPosition(monitorPos, facing));
        lastCameraYaw = camera.getYRot();
        lastCameraPitch = camera.getXRot();
        return true;
    }

    /** 小窗相机位置：监控台上方一格，再沿正面方向前移一点，避免镜头埋进方块里。 */
    public static Vec3 monitorFeedCameraPosition(BlockPos pos, Direction facing) {
        return pos.getCenter()
                .add(0, MONITOR_FEED_HEIGHT_OFFSET, 0)
                .add(facing.getStepX() * MONITOR_FEED_FORWARD_OFFSET, 0,
                        facing.getStepZ() * MONITOR_FEED_FORWARD_OFFSET);
    }

    /**
     * 监控画面里正常拖动鼠标时调用：把鼠标位移累加到「当前受控摄像头」的动态模块偏移上。
     *
     * <p>只在装了动态模块、且摄像头没有失灵时才生效；没装模块时视角固定，
     * 与保安原来的监控画面表现一致。
     *
     * @param yawDelta   水平位移（度，向右为正）
     * @param pitchDelta 垂直位移（度，向下为正）
     * @return 是否真的改变了视角
     */
    public static boolean applyMouseLookToDynamicModule(float yawDelta, float pitchDelta) {
        if (!isInSecurityMode())
            return false;
        BlockPos cameraPos = getCurrentCameraPos();
        if (cameraPos == null)
            return false;
        var clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null)
            return false;
        if (!(clientLevel.getBlockEntity(cameraPos) instanceof CameraBlockEntity cbe))
            return false;
        if (!cbe.canRotateView())
            return false;
        float newYaw = Mth.clamp(viewYawOffset + yawDelta, -MAX_VIEW_OFFSET, MAX_VIEW_OFFSET);
        float newPitch = Mth.clamp(viewPitchOffset + pitchDelta, -MAX_VIEW_OFFSET, MAX_VIEW_OFFSET);
        if (newYaw == viewYawOffset && newPitch == viewPitchOffset)
            return false;
        viewYawOffset = newYaw;
        viewPitchOffset = newPitch;
        maybeReportLook(cameraPos, cbe);
        return true;
    }

    /** 上次向服务端上报视角的游戏刻，用于节流。 */
    private static long lastLookReportTick = Long.MIN_VALUE;
    /** 上次上报的 yaw / pitch，视角没变时不重复发包。 */
    private static float lastReportedYaw = Float.NaN;
    private static float lastReportedPitch = Float.NaN;

    /**
     * 把当前镜头方向同步给服务端（红外线扫描仪靠它决定照亮哪一块区域）。
     * <p>
     * 只有该摄像头装了红外线扫描仪时才发——动态模块单独使用时服务端并不需要知道视角。
     * 每 {@link #LOOK_REPORT_INTERVAL_TICKS} tick 最多一次，且视角没变化就不发。
     */
    private static void maybeReportLook(BlockPos cameraPos, CameraBlockEntity cbe) {
        if (!cbe.hasInfraredScanner())
            return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
            return;
        long now = minecraft.level.getGameTime();
        if (lastLookReportTick != Long.MIN_VALUE && now - lastLookReportTick < LOOK_REPORT_INTERVAL_TICKS)
            return;
        float yaw = viewYawOffset;
        float pitch = viewPitchOffset;
        if (yaw == lastReportedYaw && pitch == lastReportedPitch)
            return;
        lastLookReportTick = now;
        lastReportedYaw = yaw;
        lastReportedPitch = pitch;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new org.agmas.noellesroles.packet.CameraViewLookC2SPacket(cameraPos, yaw, pitch));
    }

    /** 进入 / 退出监控模式时清零动态模块偏移，避免把上一台摄像头的视角带到下一台。 */
    public static void resetViewOffset() {
        viewYawOffset = 0.0F;
        viewPitchOffset = 0.0F;
    }

    /**
     * 根据方向获取基础偏航角
     * 
     * @param direction 摄像头方向
     * @return 对应的基础偏航角
     */
    public static float getBaseYawFromDirection(Direction direction) {
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

    public static BlockPos getCurrentMonitorPos() {
        return currentMonitorPos;
    }

    public static void setCurrentMonitorPos(BlockPos pos) {
        currentMonitorPos = pos;
    }

    public static void setSecurityMode(boolean mode) {
        isInSecurityMode = mode;
        if (!mode) {
            SecurityMonitorFeedRenderer.close();
        }
    }

    public static float getCurrentYaw() {
        return currentYaw;
    }

    public static void setCurrentYaw(float yaw) {
        currentYaw = yaw;
    }
}
