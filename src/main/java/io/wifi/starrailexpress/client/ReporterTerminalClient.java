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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 记者终端的客户端状态：谁戴了摄像头盔、当前看的是第几个人、相机怎么摆。
 *
 * <p>目标列表直接取客户端世界里的玩家实体（装备槽是同步过来的），所以不需要额外网络包；
 * 目标走出同步范围 / 死亡 / 换维度时会在取用阶段被过滤掉。
 */
public final class ReporterTerminalClient {

    private static final List<UUID> targets = new ArrayList<>();
    private static int index = 0;
    private static boolean active = false;

    private ReporterTerminalClient() {
    }

    public static boolean isActive() {
        return active;
    }

    public static int targetCount() {
        return targets.size();
    }

    /** 当前目标是列表里的第几个（从 1 开始），没有目标时返回 0。 */
    public static int targetNumber() {
        return targets.isEmpty() ? 0 : index + 1;
    }

    /**
     * 右键记者终端：扫描戴摄像头盔的玩家。无人穿戴时提示并保持关闭。
     *
     * @return 是否成功打开页面
     */
    public static boolean open(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        List<UUID> found = collectTargets(minecraft);
        if (found.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.reporter_terminal.no_wearer")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        targets.clear();
        targets.addAll(found);
        index = 0;
        active = true;
        // 与监控台一致：切到第三人称，避免目标视角里出现自己的手。
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        return true;
    }

    /** 左键 / 右键在多个戴盔玩家之间切换。 */
    public static void switchTarget(int delta) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        UUID current = currentTargetUuid();
        List<UUID> found = collectTargets(minecraft);
        if (found.isEmpty()) {
            stop();
            return;
        }
        int base = current == null ? 0 : Math.max(0, found.indexOf(current));
        targets.clear();
        targets.addAll(found);
        index = Math.floorMod(base + delta, found.size());
    }

    public static void stop() {
        if (!active) {
            return;
        }
        active = false;
        targets.clear();
        index = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        SecurityMonitorFeedRenderer.reset();
    }

    /**
     * 相机接管：把主相机放到当前目标的眼位，并使用目标玩家自己的视角。
     *
     * @return 是否接管了相机
     */
    public static boolean setupCamera(Camera camera, float partialTick) {
        if (!active) {
            return false;
        }
        Player target = currentTarget();
        if (target == null) {
            stop();
            return false;
        }
        camera.setRotation(target.getViewYRot(partialTick), target.getViewXRot(partialTick));
        camera.setPosition(target.getEyePosition(partialTick));
        return true;
    }

    /** 当前目标的显示名，HUD 顶部信息用；没有目标时返回空串。 */
    public static String currentTargetName() {
        Player target = currentTarget();
        return target == null ? "" : target.getName().getString();
    }

    /** ESC / 潜行退出页面。 */
    public static boolean onKeyPress(int key, int action) {
        if (!active || action != GLFW.GLFW_PRESS) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_LEFT_SHIFT) {
            stop();
            return true;
        }
        return false;
    }

    private static UUID currentTargetUuid() {
        Player target = currentTarget();
        return target == null ? null : target.getUUID();
    }

    /** 取出当前目标；顺带跳过已经离开同步范围或死亡的目标。 */
    private static Player currentTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || targets.isEmpty()) {
            return null;
        }
        for (int offset = 0; offset < targets.size(); offset++) {
            int candidate = Math.floorMod(index + offset, targets.size());
            Player player = minecraft.level.getPlayerByUUID(targets.get(candidate));
            if (player != null && player.isAlive()
                    && player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CAMERA_HELMET)) {
                index = candidate;
                return player;
            }
        }
        return null;
    }

    private static List<UUID> collectTargets(Minecraft minecraft) {
        List<UUID> found = new ArrayList<>();
        if (minecraft.level == null || minecraft.player == null) {
            return found;
        }
        for (Player player : minecraft.level.players()) {
            if (player == minecraft.player || !player.isAlive()) {
                continue;
            }
            if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CAMERA_HELMET)) {
                found.add(player.getUUID());
            }
        }
        return found;
    }
}
