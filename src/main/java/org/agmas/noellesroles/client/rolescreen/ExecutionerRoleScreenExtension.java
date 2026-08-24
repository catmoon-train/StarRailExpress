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

package org.agmas.noellesroles.client.rolescreen;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleInventoryScreenExtension;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.agmas.noellesroles.client.widget.ExecutionerPlayerWidget;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role_data.killer.ExecutionerRoleData;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;

/**
 * 处刑人背包界面扩展：背包界面末尾（init TAIL）列出可选目标的平民玩家。
 * 不使用分页/搜索，保持原版一排按钮的样式。
 */
public final class ExecutionerRoleScreenExtension implements RoleInventoryScreenExtension {

    public ExecutionerRoleScreenExtension() {
    }

    /** 背包界面 {@code init()} 末尾调用。 */
    @Override
    public void onInventoryScreenInitTail(LimitedInventoryScreen screen) {
        // 检查是否启用了手动选择目标功能
        if (!NoellesRolesConfig.HANDLER.instance().executionerCanSelectTarget) {
            return; // 如果未启用，则不显示选择界面
        }

        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(screen.player.level());

        // 检查是否是Executioner角色
        if (gameWorldComponent.isRole(screen.player, ModRoles.EXECUTIONER)) {
            ExecutionerRoleData executionerComponent = RoleData.getNullable(ExecutionerRoleData.class, screen.player);

            // 只有在未选择目标时才显示选择界面
            if (executionerComponent != null && !executionerComponent.targetSelected) {
                List<AbstractClientPlayer> entries = Minecraft.getInstance().level.players();

                // 筛选出平民阵营且存活的玩家
                entries.removeIf((e) -> {
                    if (e.getUUID().equals(screen.player.getUUID()))
                        return true;
                    if (!GameUtils.isPlayerAliveAndSurvival(e))
                        return true;
                    return ExecutionerRoleData.judgeRole(screen.player.level(), gameWorldComponent.getRole(e));
                });

                int apart = 36;
                int x = screen.width / 2 - entries.size() * apart / 2 + 9;
                int shouldBeY = (screen.height - 32) / 2;
                int y = shouldBeY + 80;

                for (int i = 0; i < entries.size(); ++i) {
                    ExecutionerPlayerWidget child = new ExecutionerPlayerWidget(x + apart * i, y, entries.get(i), i);
                    screen.addRoleWidget(child);
                }
            }
        }
    }
}
