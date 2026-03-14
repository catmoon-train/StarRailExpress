package org.agmas.noellesroles.client.screen;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.widget.RecorderPlayerWidget;
import org.agmas.noellesroles.client.widget.RecorderRoleWidget;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.RecorderPlayerComponent;
import org.agmas.noellesroles.packet.RecorderC2SPacket;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 记录员选择屏幕
 *
 * 两阶段选择：
 * 1. 选择目标玩家（显示所有玩家头像）
 * 2. 选择角色（显示当前局有的身份）
 */
public class RecorderScreen extends Screen {

    // 当前阶段：0 = 选择玩家，1 = 选择角色
    private int phase = 0;

    // 选中的玩家
    private UUID selectedPlayer = null;
    private String selectedPlayerName = "";

    // 搜索框
    EditBox searchWidget = null;
    String searchContent = null;
    private RecorderPlayerComponent recorderPlayerComponent = null;

    // 角色列表
    private List<SRERole> roles = new ArrayList<>();

    // Widget 列表
    private List<RecorderPlayerWidget> playerWidgets = new ArrayList<>();
    private List<RecorderRoleWidget> roleWidgets = new ArrayList<>();

    // 翻页相关
    private static final int ROLES_PER_PAGE = 12; // 每页最多12个角色
    private int currentRolePage = 0; // 当前角色页码
    private Button prevPageButton;
    private Button nextPageButton;
    private int totalPages = 0;

    public RecorderScreen(Player player) {
        super(Component.translatable("screen.noellesroles.recorder.title"));
        if (player != null) {
            var recorderC = RecorderPlayerComponent.KEY.get(player);
            if (recorderC != null) {
                recorderPlayerComponent = recorderC;
            } else {
                // onClose();
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        if (recorderPlayerComponent == null) {
            onClose();
            return;
        }
        // 清空旧的 widget
        playerWidgets.clear();
        roleWidgets.clear();

        if (phase == 0) {
            initPlayerSelection();
        } else {
            initRoleSelection();
        }
    }

    /**
     * 初始化玩家选择阶段
     */
    private void initPlayerSelection() {
        refreshPlayerSelection(null);
    }

    /**
     * 玩家选择：搜索玩家
     */
    private void onPlayerSearch(String text) {
        if (text == null || minecraft == null || minecraft.level == null || minecraft.player == null)
            return;

        // 重新初始化玩家选择以应用搜索过滤
        refreshPlayerSelection(text);
    }

    /**
     * 刷新玩家选择界面，可带搜索过滤
     */
    private void refreshPlayerSelection(String searchText) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null)
            return;

        // 尝试从组件获取开局玩家列表
        RecorderPlayerComponent recorder = ModComponents.RECORDER.get(minecraft.player);
        Map<UUID, String> startPlayers = recorder.getStartPlayers();

        List<UUID> playerUuids = new ArrayList<>();
        Map<UUID, String> playerNames = new HashMap<>();

        if (!startPlayers.isEmpty()) {
            for (Map.Entry<UUID, String> entry : startPlayers.entrySet()) {
                playerUuids.add(entry.getKey());
                playerNames.put(entry.getKey(), entry.getValue());
            }
        } else {
            // 回退到当前在线玩家
            for (AbstractClientPlayer p : minecraft.level.players()) {
                if (!p.getUUID().equals(minecraft.player.getUUID())) {
                    playerUuids.add(p.getUUID());
                    playerNames.put(p.getUUID(), p.getName().getString());
                }
            }
        }

        if (playerUuids.isEmpty()) {
            onClose();
            return;
        }

        // 如果有搜索文本，则过滤玩家列表
        List<UUID> filteredPlayerUuids = new ArrayList<>();
        if (searchText != null && !searchText.trim().isEmpty()) {
            String lowerCaseSearch = searchText.toLowerCase();
            for (UUID uuid : playerUuids) {
                String playerName = playerNames.get(uuid);
                if (playerName.toLowerCase().contains(lowerCaseSearch)) {
                    filteredPlayerUuids.add(uuid);
                }
            }
        } else {
            filteredPlayerUuids.addAll(playerUuids);
        }

        // 清除现有的widgets
        for (RecorderPlayerWidget widget : playerWidgets) {
            this.removeWidget(widget);
        }
        playerWidgets.clear();
        boolean isSearchEmpty = false;
        if (filteredPlayerUuids.isEmpty()) {
            isSearchEmpty = true;
            // 如果没有匹配的玩家，但仍有原始玩家列表，则显示全部
            filteredPlayerUuids.addAll(playerUuids);
        }
        // 计算布局
        int columns = Math.min(filteredPlayerUuids.size(), 8);
        int rows = (int) Math.ceil(filteredPlayerUuids.size() / 8.0);
        int widgetSize = 32;
        int spacing = 8;
        int totalWidth = columns * (widgetSize + spacing) - spacing;
        int totalHeight = rows * (widgetSize + spacing) - spacing;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 20;

        // 创建搜索框
        if (searchWidget == null) {
            searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20,
                    Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setEditable(true);
            searchWidget.setResponder((text) -> {
                onPlayerSearch(text);
            });
        }
        if (isSearchEmpty) {
            // 如果没有匹配的玩家，则变红
            searchWidget.setTextColor(Color.RED.getRGB());
        } else {
            // 变回白色
            searchWidget.setTextColor(Color.WHITE.getRGB());
        }
        addRenderableWidget(searchWidget);

        // 创建过滤后的玩家widgets
        for (int i = 0; i < filteredPlayerUuids.size(); i++) {
            int col = i % 8;
            int row = i / 8;
            int x = startX + col * (widgetSize + spacing);
            int y = startY + row * (widgetSize + spacing);

            UUID uuid = filteredPlayerUuids.get(i);
            String name = playerNames.get(uuid);

            // 获取皮肤
            ResourceLocation skin = DefaultPlayerSkin.get(uuid).texture();
            if (minecraft.getConnection() != null) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
                if (info != null) {
                    skin = info.getSkin().texture();
                }
            }
            boolean hasGuessed = false;
            hasGuessed = recorderPlayerComponent.hasGuessed(uuid);
            RecorderPlayerWidget widget = new RecorderPlayerWidget(
                    this, x, y, widgetSize, uuid, name, skin, i, hasGuessed);
            playerWidgets.add(widget);
            addRenderableWidget(widget);
        }
    }

    /**
     * 初始化角色选择阶段
     */
    private void initRoleSelection() {
        searchWidget = null;
        clearWidgets();
        nextPageButton = null;
        prevPageButton = null;
        roleWidgets.clear();
        currentRolePage = 0;
        refreshRoleSelection(null);
    }

    private void onRoleSearch(String text) {
        if (text == null || minecraft == null || minecraft.level == null || minecraft.player == null)
            return;
        currentRolePage = 0;
        refreshRoleSelection(text);
    }

    /**
     * 刷新角色选择界面
     */
    private void refreshRoleSelection(String searchText) {

        for (int i = 0; i < roleWidgets.size(); i++) {
            this.removeWidget(roleWidgets.get(i));
        }
        roleWidgets.clear();

        if (nextPageButton != null)
            this.removeWidget(nextPageButton);
        if (prevPageButton != null)
            this.removeWidget(prevPageButton);

        if (minecraft == null || minecraft.player == null)
            return;

        // 从组件获取当前局有的身份
        // RecorderPlayerComponent recorder =
        // ModComponents.RECORDER.get(minecraft.player);
        var availableRoleIds = Noellesroles.getAllRolesSorted(false);

        roles.clear();
        roles.addAll(availableRoleIds);

        // 如果列表为空（可能是单人测试或者数据未同步），回退到显示所有角色
        if (roles.isEmpty()) {
            roles = Noellesroles.getAllRolesSorted(false);
        }

        if (roles.isEmpty()) {
            onClose();
            return;
        }

        // 确保当前页码有效
        if (totalPages != 0) {
            if (currentRolePage >= totalPages) {
                currentRolePage = totalPages - 1;
            }
            if (currentRolePage < 0) {
                currentRolePage = 0;
            }
        }

        // 计算当前页的角色范围
        int startIndex = currentRolePage * ROLES_PER_PAGE;
        int endIndex = Math.min(startIndex + ROLES_PER_PAGE, roles.size());
        int rolesOnThisPage = endIndex - startIndex;

        // 计算布局 - 每页最多12个角色，4列3行
        int columns = Math.min(rolesOnThisPage, 4);
        int rows = (int) Math.ceil(rolesOnThisPage / 4.0);
        int widgetWidth = 90;
        int widgetHeight = 24;
        int spacingX = 10;
        int spacingY = 6;
        int totalWidth = columns * (widgetWidth + spacingX) - spacingX;
        int totalHeight = rows * (widgetHeight + spacingY) - spacingY;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 10;

        int totalRoles = 0;
        // 添加当前页的角色
        int k = 0;
        String lowerCasedSearchText = searchText;
        if (searchText != null) {
            lowerCasedSearchText = searchText.toLowerCase();
        }
        for (SRERole role : roles) {
            String roleName = RoleUtils.getRoleName(role).getString();
            String roleId = "";
            if (role != null) {
                roleId = role.identifier().toString();
            }
            if (roleName == null)
                continue;
            roleName = roleName.toLowerCase();
            if (searchText == null || searchText == "" || roleName.contains(lowerCasedSearchText)
                    || roleId.contains(lowerCasedSearchText)) {
                if (totalRoles >= startIndex && totalRoles < endIndex) {
                    int col = k % 4;
                    int row = k / 4;
                    int x = startX + col * (widgetWidth + spacingX);
                    int y = startY + row * (widgetHeight + spacingY);

                    RecorderRoleWidget widget = new RecorderRoleWidget(
                            this, x, y, widgetWidth, widgetHeight, role, k);
                    roleWidgets.add(widget);
                    addRenderableWidget(widget);
                    k++;
                }
                totalRoles++;
            }
        }

        // 添加翻页按钮
        int buttonWidth = 60;
        int buttonHeight = 20;
        int buttonY = startY + totalHeight + 20;
        totalPages = (int) Math.ceil(totalRoles / (double) ROLES_PER_PAGE);
        // 上一页按钮
        prevPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.prev_page"),
                button -> {
                    if (currentRolePage > 0) {
                        currentRolePage--;
                        refreshRoleSelection(searchText);
                    }
                }).bounds(width / 2 - buttonWidth - 30, buttonY, buttonWidth, buttonHeight).build();
        prevPageButton.active = currentRolePage > 0;
        addRenderableWidget(prevPageButton);

        // 下一页按钮
        nextPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.next_page"),
                button -> {
                    if (currentRolePage < totalPages - 1) {
                        currentRolePage++;
                        refreshRoleSelection(searchText);
                    }
                }).bounds(width / 2 + 30, buttonY, buttonWidth, buttonHeight).build();
        nextPageButton.active = currentRolePage < totalPages - 1;
        addRenderableWidget(nextPageButton);
        if (searchWidget == null) {
            searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20,
                    Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setEditable(true);
            searchWidget.setResponder((text) -> {
                onRoleSearch(text);
            });
            addRenderableWidget(searchWidget);
        }
        if (totalRoles <= 0) {
            searchWidget.setTextColor(Color.RED.getRGB());
        } else {
            searchWidget.setTextColor(Color.WHITE.getRGB());
        }
    }

    /**
     * 玩家被选中时调用
     */
    public void onPlayerSelected(UUID playerUuid, String playerName) {
        this.selectedPlayer = playerUuid;
        this.selectedPlayerName = playerName;
        this.phase = 1;
        this.currentRolePage = 0; // 重置页码
        this.searchWidget = null;
        // 重新初始化，显示角色选择
        clearWidgets();
        init();
    }

    /**
     * 角色被选中时调用
     */
    public void onRoleSelected(SRERole role) {
        if (selectedPlayer == null)
            return;
        if (minecraft == null || minecraft.player == null)
            return;

        // 发送网络包到服务端
        ClientPlayNetworking.send(new RecorderC2SPacket(
                selectedPlayer,
                role.identifier().toString()));

        // 关闭屏幕
        onClose();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 渲染背景
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // 渲染标题
        Component title;
        if (phase == 0) {
            title = Component.translatable("screen.noellesroles.recorder.select_player")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else {
            title = Component.translatable("screen.noellesroles.recorder.select_role", selectedPlayerName)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }

        context.drawCenteredString(font, title, width / 2, 30, 0xFFFFFF);

        // 渲染页码信息（仅在角色选择阶段）
        if (phase == 1 && roles.size() > ROLES_PER_PAGE) {
            Component pageInfo = Component.translatable("screen.noellesroles.conspirator.page_info",
                    currentRolePage + 1, totalPages)
                    .withStyle(ChatFormatting.YELLOW);
            context.drawCenteredString(font, pageInfo, width / 2, 45, 0xFFFFFF);
        }

        // 渲染提示
        Component hint = Component.translatable("screen.noellesroles.recorder.hint")
                .withStyle(ChatFormatting.GRAY);
        context.drawCenteredString(font, hint, width / 2, height - 30, 0x888888);

    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 键返回上一阶段或关闭
        if (keyCode == 256) { // ESC
            if (phase == 1) {
                // 返回玩家选择阶段
                phase = 0;
                selectedPlayer = null;
                selectedPlayerName = "";
                currentRolePage = 0; // 重置页码
                clearWidgets();
                init();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}