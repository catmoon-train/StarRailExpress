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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.widget.EmbalmerVoiceSwapWidget;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.Color;
import java.awt.Point;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 嬉命人背包界面扩展：与交换者一致的两轮选人列表，用于交换两名玩家的语音发声位置。
 */
public final class EmbalmerRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {

    /** 上一次渲染时的可选目标 UUID 列表，用于检测「有人死亡/离场」并触发重建列表。 */
    private List<UUID> lastTargetSignature = List.of();

    public EmbalmerRoleScreenExtension() {
    }

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> h = new RoleScreenHelper<>(
                player,
                ModRoles.EMBALMER,
                this::createVoiceSwapWidget,
                TEXT_PROVIDER,
                this::drawVoiceSwapSelectionHint,
                this::getEligiblePlayers);
        h.setNameExtractor(info -> info.getProfile().getName());
        return h;
    }

    private EmbalmerVoiceSwapWidget createVoiceSwapWidget(LimitedInventoryScreen screen, int x, int y,
            PlayerInfo playerEntity, int index) {
        EmbalmerVoiceSwapWidget widget = new EmbalmerVoiceSwapWidget(screen, x, y, playerEntity);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawVoiceSwapSelectionHint(GuiGraphics context, Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text;
        int color;

        if (EmbalmerVoiceSwapWidget.playerChoiceOne == null) {
            text = Component.translatable("hud.embalmer.first_player_selection");
            color = Color.CYAN.getRGB();
        } else {
            text = Component.translatable("hud.embalmer.second_player_selection");
            color = Color.RED.getRGB();
        }

        int textWidth = client.font.width(text);
        context.drawString(client.font, text, point.x - textWidth / 2, point.y + 40, color);
    }

    /**
     * 可选目标：局内存活（非旁观者、非创造、未死亡）的玩家。
     * <p>
     * 技能无法对已死亡玩家释放，因此这里直接把他们过滤掉；<b>允许对自己释放</b>，
     * 所以不排除客户端玩家自己。
     */
    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getConnection() == null) {
            return List.of();
        }

        return client.getConnection().getListedOnlinePlayers().stream()
                .filter(this::isTargetable)
                .collect(Collectors.toList());
    }

    /** 用客户端实际实体判定存活，避免仅凭 PlayerInfo 的游戏模式误判。 */
    private boolean isTargetable(PlayerInfo info) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return false;
        }
        Player entity = client.level.getPlayerByUUID(info.getProfile().getId());
        return entity != null
                && !entity.isSpectator()
                && !entity.isCreative()
                && entity.isAlive();
    }

    /**
     * 每帧比对可选目标集合；有人死亡/离场时立即重建列表并重新排序，
     * 避免背包界面停留在打开那一刻的快照。
     */
    @Override
    public void onInventoryScreenRender(LimitedInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY,
            float delta) {
        refreshIfTargetSetChanged(screen);
        super.onInventoryScreenRender(screen, graphics, mouseX, mouseY, delta);
    }

    private void refreshIfTargetSetChanged(LimitedInventoryScreen screen) {
        List<UUID> signature = getEligiblePlayers().stream()
                .map(info -> info.getProfile().getId())
                .collect(Collectors.toList());
        if (signature.equals(lastTargetSignature)) {
            return;
        }
        lastTargetSignature = signature;

        RoleScreenHelper<PlayerInfo> helper = getHelper(screen.player);
        if (!helper.isRoleActive()) {
            return;
        }
        // 已选中的人若已死亡，清掉选择，避免残留一个无效的第一选择
        if (EmbalmerVoiceSwapWidget.playerChoiceOne != null && !signature.contains(
                EmbalmerVoiceSwapWidget.playerChoiceOne)) {
            EmbalmerVoiceSwapWidget.playerChoiceOne = null;
        }
        helper.getPaginationHelper().setPlayerEntries(getEligiblePlayers());
        helper.getPaginationHelper().refreshPage(screen);
    }

    @Override
    public void onInventoryScreenInit(LimitedInventoryScreen screen) {
        EmbalmerVoiceSwapWidget.playerChoiceOne = null;
        lastTargetSignature = List.of();
        super.onInventoryScreenInit(screen);
        // super 已经填充过一次列表，同步签名，避免开包第一帧又重建一次
        lastTargetSignature = getEligiblePlayers().stream()
                .map(info -> info.getProfile().getId())
                .collect(Collectors.toList());
    }
}
