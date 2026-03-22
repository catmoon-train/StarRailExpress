package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.widget.ConspiratorPlayerWidget;
import org.agmas.noellesroles.client.widget.ConspiratorRoleWidget;
import org.agmas.noellesroles.packet.ConspiratorC2SPacket;
import org.agmas.noellesroles.utils.RoleUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 阴谋家选择屏幕
 * 
 * 两阶段选择：
 * 1. 选择目标玩家（显示所有玩家头像）
 * 2. 选择角色（显示所有可用角色）
 */
public class ConspiratorScreen extends Screen {

    // 当前阶段：0 = 选择玩家，1 = 选择角色
    private int phase = 0;
    private int totalPages = 0;
    // 选中的玩家
    private UUID selectedPlayer = null;
    private String selectedPlayerName = "";
    // 搜索框
    EditBox searchWidget = null;
    String searchContent = null;
    // 玩家列表
    private List<AbstractClientPlayer> players = new ArrayList<>();

    // 角色列表
    private List<SRERole> roles = new ArrayList<>();

    // Widget 列表
    private List<ConspiratorPlayerWidget> playerWidgets = new ArrayList<>();
    private List<ConspiratorRoleWidget> roleWidgets = new ArrayList<>();

    // 翻页相关
    private static final int ROLES_PER_PAGE = 12; // 每页最多12个角色
    private int currentRolePage = 0; // 当前角色页码
    private Button prevPageButton;
    private Button nextPageButton;

    public ConspiratorScreen() {
        super(Component.translatable("screen.noellesroles.conspirator.title"));
    }

    @Override
    protected void init() {
        super.init();

        // 清空旧的 widget
        playerWidgets.clear();
        roleWidgets.clear();
        searchWidget = null;
        if (phase == 0) {
            initPlayerSelection();
        } else {
            initRoleSelection();
        }
    }

    /**
     * 选择阶段：搜索职业
     */
    private void onRoleSearch(String text) {
        if (text == "") {
            searchContent = null;
        } else {
            searchContent = text;
        }
        currentRolePage = 0;
        totalPages = 0;
        refreshRoleSelection();
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

        // 获取所有其他玩家
        players = new ArrayList<>(minecraft.level.players());
        players.removeIf(p -> p.getUUID().equals(minecraft.player.getUUID()));

        if (players.isEmpty()) {
            onClose();
            return;
        }

        // 如果有搜索文本，则过滤玩家列表
        List<AbstractClientPlayer> filteredPlayers = new ArrayList<>();
        if (searchText != null && !searchText.trim().isEmpty()) {
            String lowerCaseSearch = searchText.toLowerCase();
            for (AbstractClientPlayer player : players) {
                if (player.isCreative() || player.isSpectator())
                    continue;
                String playerName = player.getName().getString();
                if (playerName.toLowerCase().contains(lowerCaseSearch)) {
                    filteredPlayers.add(player);
                }
            }
        } else {
            filteredPlayers.addAll(players);
        }

        // 清除现有的widgets
        for (ConspiratorPlayerWidget widget : playerWidgets) {
            this.removeWidget(widget);
        }
        playerWidgets.clear();
        boolean isSearchEmpty = false;
        if (filteredPlayers.isEmpty()) {
            isSearchEmpty = true;
            // 如果没有匹配的玩家，但仍有原始玩家列表，则显示全部
            filteredPlayers.addAll(players);
        }
        // 计算布局
        int columns = Math.min(filteredPlayers.size(), 8);
        int rows = (int) Math.ceil(filteredPlayers.size() / 8.0);
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
            addRenderableWidget(searchWidget);

        }
        if (isSearchEmpty) {
            // 如果没有匹配的玩家，则文本变红
            searchWidget.setTextColor(Color.RED.getRGB());
        } else {
            // 变回白色
            searchWidget.setTextColor(Color.WHITE.getRGB());
        }
        // 创建过滤后的玩家widgets
        for (int i = 0; i < filteredPlayers.size(); i++) {
            int col = i % 8;
            int row = i / 8;
            int x = startX + col * (widgetSize + spacing);
            int y = startY + row * (widgetSize + spacing);

            ConspiratorPlayerWidget widget = new ConspiratorPlayerWidget(
                    this, x, y, widgetSize, filteredPlayers.get(i), i);
            playerWidgets.add(widget);
            addRenderableWidget(widget);
        }
    }

    /**
     * 初始化玩家选择阶段
     */
    private void initPlayerSelection() {
        clearWidgets();
        this.searchWidget = null;
        refreshPlayerSelection(null);
    }

    /**
     * 初始化角色选择阶段
     */
    private void initRoleSelection() {
        // 获取所有注册的角色
        roles = Noellesroles.getAllRolesSorted(false);

        if (roles.isEmpty()) {
            onClose();
            return;
        }

        if (totalPages > 0) {
            // 确保当前页码有效
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

        // 添加当前页的角色
        int count = 0;

        for (int i = 0; i < roles.size(); i++) {
            var role = roles.get(i);
            String roleid = "";
            if (role != null) {
                roleid = role.identifier().toString();
            }
            String roleName = RoleUtils.getRoleName(role).getString();
            if (searchContent == null || roleName.contains(searchContent) || roleName.contains(roleid)) {
                if (count >= startIndex && count < endIndex) {
                    int indexOnPage = count - startIndex;
                    int col = indexOnPage % 4;
                    int row = indexOnPage / 4;
                    int x = startX + col * (widgetWidth + spacingX);
                    int y = startY + row * (widgetHeight + spacingY);

                    ConspiratorRoleWidget widget = new ConspiratorRoleWidget(
                            this, x, y, widgetWidth, widgetHeight, roles.get(i), i);
                    roleWidgets.add(widget);
                    addRenderableWidget(widget);

                }
                count++;
            }
        }

        totalPages = (int) Math.ceil(count / (double) ROLES_PER_PAGE);
        // 添加翻页按钮
        int buttonWidth = 60;
        int buttonHeight = 20;
        int buttonY = startY + totalHeight + 20;
        // 上一页按钮
        if (prevPageButton != null) {

        }
        prevPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.prev_page"),
                button -> {
                    if (currentRolePage > 0) {
                        currentRolePage--;
                        refreshRoleSelection();
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
                        refreshRoleSelection();
                    }
                }).bounds(width / 2 + 30, buttonY, buttonWidth, buttonHeight).build();
        nextPageButton.active = currentRolePage < totalPages - 1;
        addRenderableWidget(nextPageButton);
        // 避免重复创建组件
        if (searchWidget == null) {
            searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20, Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setEditable(true);
            searchWidget.setResponder((text) -> {
                onRoleSearch(text);
            });
            addRenderableWidget(searchWidget);
        }
        if (count <= 0) {
            // 没有
            searchWidget.setTextColor(Color.RED.getRGB());
        } else {
            searchWidget.setTextColor(Color.WHITE.getRGB());
        }

    }

    /**
     * 刷新角色选择界面
     */
    private void refreshRoleSelection() {
        for (int i = 0; i < roleWidgets.size(); i++) {
            this.removeWidget(roleWidgets.get(i));
        }
        this.removeWidget(prevPageButton);
        this.removeWidget(nextPageButton);
        roleWidgets.clear();
        initRoleSelection();
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
    @SuppressWarnings("unused")
    public void onRoleSelected(SRERole role) {
        if (selectedPlayer == null)
            return;
        if (minecraft == null || minecraft.player == null)
            return;

        // 发送网络包到服务端
        ClientPlayNetworking.send(new ConspiratorC2SPacket(
                selectedPlayer,
                role.identifier().toString()));

        // 消耗书页物品
        ItemStack mainHand = minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = minecraft.player.getItemInHand(InteractionHand.OFF_HAND);

        // 物品消耗由服务端处理

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
            title = Component.translatable("screen.noellesroles.conspirator.select_player")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else {
            title = Component.translatable("screen.noellesroles.conspirator.select_role", selectedPlayerName)
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
        Component hint = Component.translatable("screen.noellesroles.conspirator.hint")
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