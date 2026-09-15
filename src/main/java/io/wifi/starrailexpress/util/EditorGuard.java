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

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 「编辑类方块」的服务端防御：展示方块 / 实体交互方块 / 效果生成器 / 售票处 / 小游戏任务方块…
 *
 * <p>
 * 界面只是体验，**服务端必须自己再校验一遍**：打开界面、保存配置、小游戏完成通知都要过这里。
 * 统一收口的原因是历史上各处自己写判定，出现过多套不一致的规则：
 * 只判创造不判权限、干脆没有校验、没有距离限制（可以跨图远程改别人的数据）等等。
 *
 * <p>
 * 规则：**仅「创造模式 + OP 等级 2」的玩家可以编辑**，且必须在该方块附近；与实体交互方块
 * （{@code EntityInteractionBlockServerNetwork}）原本的判定保持一致。纯玩法交互（买票、
 * 完成小游戏）不加权限要求，只要求在场。
 */
public final class EditorGuard {

    /**
     * 玩家与目标方块的最大距离（格）。
     *
     * <p>
     * 给得比较宽松：允许开着界面时被列车 / 移动平台带走一点；但它足够小到挡住
     * 「构造位置远程改全图方块数据」这种玩法之外的操作。
     */
    public static final double EDIT_REACH = 64.0D;

    private EditorGuard() {
    }

    /** 是否是可以编辑这类方块的玩家（创造模式 + OP 等级 2）。 */
    public static boolean canEdit(Player player) {
        return player != null && player.isCreative()
                && player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2);
    }

    /** 只判距离：给「谁都能用、但必须在场」的玩法交互（买票、完成小游戏）用。 */
    public static boolean isNear(Player player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= EDIT_REACH * EDIT_REACH;
    }

    /**
     * 配置保存类包的服务端统一入口：权限 + 距离 + 目标方块存在。
     *
     * <p>
     * 被拒时只记 debug 日志（方便排查「为什么改不了」），不给玩家任何反馈——避免给作弊客户端
     * 明确的探测回显。
     */
    public static boolean canEditAt(ServerPlayer player, BlockPos pos) {
        if (!canEdit(player) || pos == null || player.level().isClientSide()) {
            return false;
        }
        if (!isNear(player, pos)) {
            SRE.LOGGER.debug("[EditorGuard] Denied edit for {} at {} (too far)", player.getName().getString(), pos);
            return false;
        }
        return true;
    }
}
