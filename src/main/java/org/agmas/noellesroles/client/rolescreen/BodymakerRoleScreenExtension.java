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

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.widget.BodymakerDeathReasonWidget;
import org.agmas.noellesroles.client.widget.BodymakerPlayerWidget;
import org.agmas.noellesroles.client.widget.MorticianScreenCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_utils.DeathReasonHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 葬仪背包界面扩展：两阶段选择流程（先选目标玩家，再选死亡原因）。
 */
public final class BodymakerRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo>
        implements MorticianScreenCallback {

    private int selectedLevel = 0; // 0=选择玩家, 1=选择死亡原因
    private UUID selectedPlayerUuid = null;
    private LimitedInventoryScreen screen; // 当前屏幕引用（进入阶段2时重建界面用）

    public BodymakerRoleScreenExtension() {
    }

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> h = new RoleScreenHelper<>(
                player,
                ModRoles.MORTICIAN_BODYMAKER,
                this::createBodymakerWidget,
                TEXT_PROVIDER,
                this::drawBodymakerSelectionHint,
                this::getEligiblePlayers);
        h.setNameExtractor(info -> info.getProfile().getName());
        return h;
    }

    private Button createBodymakerWidget(LimitedInventoryScreen screen, int x, int y, PlayerInfo playerInfo,
            int index) {
        BodymakerPlayerWidget widget = new BodymakerPlayerWidget(
                screen, x, y, playerInfo.getProfile().getId(), playerInfo, this);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawBodymakerSelectionHint(GuiGraphics context, java.awt.Point point) {
        // 预留：可在玩家头像上方绘制提示文字
    }

    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }
        List<PlayerInfo> list = new ArrayList<>();
        if (client.getConnection() != null) {
            for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                if (!info.getProfile().getId().equals(client.player.getUUID())) {
                    list.add(info);
                }
            }
        }
        return list;
    }

    @Override
    public void onInventoryScreenInit(LimitedInventoryScreen screen) {
        // 每次打开背包都会创建新的扩展实例，阶段状态（selectedLevel）天然回到阶段1
        this.screen = screen;
        reinit();
    }

    private void reinit() {
        if (selectedLevel == 0) {
            // 阶段1：选择目标玩家（分页翻页 + 搜索框）
            initPlayerList(screen);
        } else {
            // 阶段2：选择死亡原因（无需分页）
            if (!getHelper(screen.player).isRoleActive()) {
                return;
            }

            int apart = 36;
            int y = (screen.height - 32) / 2 + 80;
            ItemStack[] deathReasons = DeathReasonHelper.getAvailableDeathReasons();

            int x = screen.width / 2 - (deathReasons.length * apart) / 2 + 9;
            for (int i = 0; i < deathReasons.length; ++i) {
                String deathReasonId = DeathReasonHelper.getDeathReasonId(deathReasons[i]);
                BodymakerDeathReasonWidget widget = new BodymakerDeathReasonWidget(
                        screen, x + apart * i, y, deathReasons[i], deathReasonId, selectedPlayerUuid);
                screen.addRoleWidget(widget);
            }
        }
    }

    @Override
    public void onInventoryScreenRender(LimitedInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY,
            float delta) {
        if (selectedLevel == 0) {
            super.onInventoryScreenRender(screen, graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public void setSelectedPlayer(@NotNull UUID uuid) {
        this.selectedPlayerUuid = uuid;
        this.selectedLevel = 1;
        // 进入阶段2：清空并重建界面（reinit 会重新触发 INIT/INIT_TAIL 事件）
        if (screen != null) {
            screen.reinit();
        }
    }

    @Override
    public void setSelectedDeathReason(@NotNull String deathReason) {
        // 已不再需要，删除阶段3后此回调可移除
    }
}
