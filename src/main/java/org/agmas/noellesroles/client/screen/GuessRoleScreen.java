package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.widget.ConspiratorRoleWidget;
import org.agmas.noellesroles.client.widget.GuessPlayerWidget;
import org.agmas.noellesroles.utils.RoleUtils;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 身份猜测记录屏幕
 * 
 * 允许玩家记录对其他玩家身份的猜测
 */
public class GuessRoleScreen extends Screen {

    public Screen parent = null;
    // 静态存储猜测记录，游戏结束时清除
    public static Map<UUID, String> guessedRoles = new HashMap<>();
    public static ArrayList<PlayerInfo> allPlayers = new ArrayList<net.minecraft.client.multiplayer.PlayerInfo>();

    // 清除数据的方法
    public static void clearData() {
        guessedRoles.clear();
        allPlayers.clear();
        allPlayers.addAll(Minecraft.getInstance().getConnection().getListedOnlinePlayers());
    }

    // 当前阶段：0 = 选择玩家，1 = 选择角色
    private int phase = 0;

    // 玩家列表分页
    private static final int PLAYERS_PER_PAGE = 10;
    private int currentPlayerPage = 0;
    private int totalPlayerPages = 0;

    // 角色列表分页
    private static final int ROLES_PER_PAGE = 12;
    private int currentRolePage = 0;
    private int totalRolePages = 0;

    // 选中的玩家
    private UUID selectedPlayer = null;
    private String selectedPlayerName = "";

    // 搜索框
    EditBox searchWidget = null;
    String searchContent = null;

    // 玩家列表
    private List<PlayerInfo> players = new ArrayList<>();

    // 角色列表
    private List<SRERole> roles = new ArrayList<>();

    // Widget 列表
    private List<GuessPlayerWidget> playerWidgets = new ArrayList<>();
    private List<ConspiratorRoleWidget> roleWidgets = new ArrayList<>();

    // 翻页按钮
    private Button prevPageButton;
    private Button nextPageButton;

    public GuessRoleScreen() {
        super(Component.translatable("screen.noellesroles.guess_role.title"));
    }

    public GuessRoleScreen(Screen parent) {
        this();
        this.parent = parent;
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

    @Override
    public void onClose(){
        this.minecraft.setScreen(parent);
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
        totalRolePages = 0;
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
        var now_players = new ArrayList<>(minecraft.getConnection().getListedOnlinePlayers());
        now_players.removeIf((a) -> {
            return allPlayers.contains(a);
        });
        allPlayers.addAll(now_players);
        players = new ArrayList<>(allPlayers);

        List<PlayerInfo> filteredPlayers = new ArrayList<>();

        players.removeIf(p -> {
            if (p.getProfile().getId().equals(minecraft.player.getUUID()))
                return true;
            return false;
        });

        if (players.isEmpty()) {
            // 如果没有其他玩家，显示提示或保持空
        }

        if (searchText != null && !searchText.trim().isEmpty()) {
            String lowerCaseSearch = searchText.toLowerCase();
            for (var player : players) {
                String playerName = player.getProfile().getName().toLowerCase();
                if (playerName.contains(lowerCaseSearch)) {
                    filteredPlayers.add(player);
                } else {
                    String role = guessedRoles.get(player.getProfile().getId());
                    if (role != null && !role.trim().isEmpty())
                        if (role.toLowerCase().contains(lowerCaseSearch)) {
                            filteredPlayers.add(player);
                        }
                }
            }
        } else {
            filteredPlayers.addAll(players);
        }
        // 如果有搜索文本，则过滤玩家列表

        // 清除现有的widgets
        for (GuessPlayerWidget widget : playerWidgets) {
            this.removeWidget(widget);
        }
        this.removeWidget(prevPageButton);
        this.removeWidget(nextPageButton);
        playerWidgets.clear();

        boolean isSearchEmpty = false;
        if (filteredPlayers.isEmpty() && !players.isEmpty() && searchText != null) {
            isSearchEmpty = true;
        }

        // 计算分页
        totalPlayerPages = (int) Math.ceil(filteredPlayers.size() / (double) PLAYERS_PER_PAGE);
        if (currentPlayerPage >= totalPlayerPages && totalPlayerPages > 0) {
            currentPlayerPage = totalPlayerPages - 1;
        }
        if (currentPlayerPage < 0)
            currentPlayerPage = 0;

        // 计算当前页的玩家范围
        int startIndex = currentPlayerPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, filteredPlayers.size());

        // 计算布局 - 3x3 网格
        int columns = 5;
        int rows = 2;
        int widgetSize = 36; // 头像大小
        int spacing = 16;
        int totalWidth = columns * (widgetSize + spacing) - spacing;
        int totalHeight = rows * (widgetSize + 12 + spacing) - spacing; // +20 for text
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 10;

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
            searchWidget.setTextColor(Color.RED.getRGB());
        } else {
            searchWidget.setTextColor(Color.WHITE.getRGB());
        }

        // 创建当前页的玩家widgets
        for (int i = startIndex; i < endIndex; i++) {
            int indexOnPage = i - startIndex;
            int col = indexOnPage % columns;
            int row = indexOnPage / columns;
            int x = startX + col * (widgetSize + spacing);
            int y = startY + row * (widgetSize + 12 + spacing);

            var player = filteredPlayers.get(i);
            String role = guessedRoles.get(player.getProfile().getId());

            GuessPlayerWidget widget = new GuessPlayerWidget(
                    this, x, y, widgetSize, player, role);
            playerWidgets.add(widget);
            addRenderableWidget(widget);
        }

        // 添加翻页按钮
        int buttonWidth = 60;
        int buttonHeight = 20;
        int buttonY = startY + totalHeight + 10;

        prevPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.prev_page"),
                button -> {
                    if (currentPlayerPage > 0) {
                        currentPlayerPage--;
                        refreshPlayerSelection(searchWidget != null ? searchWidget.getValue() : null);
                    }
                }).bounds(width / 2 - buttonWidth - 10, buttonY, buttonWidth, buttonHeight).build();
        prevPageButton.active = currentPlayerPage > 0;
        addRenderableWidget(prevPageButton);

        nextPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.next_page"),
                button -> {
                    if (currentPlayerPage < totalPlayerPages - 1) {
                        currentPlayerPage++;
                        refreshPlayerSelection(searchWidget != null ? searchWidget.getValue() : null);
                    }
                }).bounds(width / 2 + 10, buttonY, buttonWidth, buttonHeight).build();
        nextPageButton.active = currentPlayerPage < totalPlayerPages - 1;
        addRenderableWidget(nextPageButton);
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
        roles = Noellesroles.getAllRolesSorted(true);
        roles.add(0, null);
        if (roles.isEmpty()) {
            // 如果没有角色，返回上一级
            phase = 0;
            init();
            return;
        }

        // 计算分页
        int filteredCount = 0;
        for (SRERole role : roles) {
            String roleName = "";
            if (role != null)
                roleName = RoleUtils.getRoleName(role).getString();
            if (searchContent == null || roleName.contains(searchContent)) {
                filteredCount++;
            }
        }

        totalRolePages = (int) Math.ceil(filteredCount / (double) ROLES_PER_PAGE);

        if (currentRolePage >= totalRolePages && totalRolePages > 0) {
            currentRolePage = totalRolePages - 1;
        }
        if (currentRolePage < 0) {
            currentRolePage = 0;
        }

        // 计算当前页的角色范围
        // 注意：这里需要重新遍历以处理搜索过滤和分页

        // 计算布局 - 每页最多12个角色，4列3行
        int columns = 4;
        int rows = 3;
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
        int startIndex = currentRolePage * ROLES_PER_PAGE;
        int endIndex = startIndex + ROLES_PER_PAGE;

        for (int i = 0; i < roles.size(); i++) {
            var role = roles.get(i);
            if (role == null && (searchContent == null || searchContent == "")) {
                if (count >= startIndex && count < endIndex) {
                    int indexOnPage = count - startIndex;
                    int col = indexOnPage % 4;
                    int row = indexOnPage / 4;
                    int x = startX + col * (widgetWidth + spacingX);
                    int y = startY + row * (widgetHeight + spacingY);

                    // 复用 ConspiratorRoleWidget，因为它已经实现了角色显示和点击逻辑
                    // 我们需要重写 onRoleSelected 方法来适配我们的逻辑
                    ConspiratorRoleWidget widget = new ConspiratorRoleWidget(
                            null, x, y, widgetWidth, widgetHeight, null, i) {
                        @Override
                        public void onPress() {
                            onRoleSelected(null);
                        }
                    };
                    roleWidgets.add(widget);
                    addRenderableWidget(widget);
                }
                count++;
            } else {
                if (role == null)
                    continue;
                String roleName = RoleUtils.getRoleName(role).getString();
                if (searchContent == null || roleName.contains(searchContent)) {
                    if (count >= startIndex && count < endIndex) {
                        int indexOnPage = count - startIndex;
                        int col = indexOnPage % 4;
                        int row = indexOnPage / 4;
                        int x = startX + col * (widgetWidth + spacingX);
                        int y = startY + row * (widgetHeight + spacingY);

                        // 复用 ConspiratorRoleWidget，因为它已经实现了角色显示和点击逻辑
                        // 我们需要重写 onRoleSelected 方法来适配我们的逻辑
                        ConspiratorRoleWidget widget = new ConspiratorRoleWidget(
                                null, x, y, widgetWidth, widgetHeight, roles.get(i), i) {
                            @Override
                            public void onPress() {
                                onRoleSelected(role);
                            }
                        };
                        roleWidgets.add(widget);
                        addRenderableWidget(widget);
                    }
                    count++;
                }

            }
        }

        // 添加翻页按钮
        int buttonWidth = 60;
        int buttonHeight = 20;
        int buttonY = startY + totalHeight + 20;

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

        nextPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.next_page"),
                button -> {
                    if (currentRolePage < totalRolePages - 1) {
                        currentRolePage++;
                        refreshRoleSelection();
                    }
                }).bounds(width / 2 + 30, buttonY, buttonWidth, buttonHeight).build();
        nextPageButton.active = currentRolePage < totalRolePages - 1;
        addRenderableWidget(nextPageButton);

        // 搜索框
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
        this.searchContent = null;
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

        // 更新猜测记录
        if (role != null) {

            String roleName = RoleUtils.getRoleName(role).getString();
            guessedRoles.put(selectedPlayer, roleName);
        } else {
            guessedRoles.remove(selectedPlayer);
        }

        // 返回玩家选择阶段
        this.currentPlayerPage = 0;
        this.phase = 0;
        this.selectedPlayer = null;
        this.selectedPlayerName = "";
        this.searchWidget = null;
        this.searchContent = null;
        this.currentRolePage = 0; // 重置页码

        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 渲染背景
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // 渲染标题
        Component title;
        if (phase == 0) {
            title = Component.translatable("screen.noellesroles.guess_role.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else {
            title = Component.translatable("screen.noellesroles.conspirator.select_role", selectedPlayerName)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }

        context.drawCenteredString(font, title, width / 2, 30, 0xFFFFFF);

        // 渲染页码信息
        if (phase == 0 && totalPlayerPages > 1) {
            Component pageInfo = Component.translatable("screen.noellesroles.conspirator.page_info",
                    currentPlayerPage + 1, totalPlayerPages)
                    .withStyle(ChatFormatting.YELLOW);
            context.drawCenteredString(font, pageInfo, width / 2, 45, 0xFFFFFF);
        } else if (phase == 1 && totalRolePages > 1) {
            Component pageInfo = Component.translatable("screen.noellesroles.conspirator.page_info",
                    currentRolePage + 1, totalRolePages)
                    .withStyle(ChatFormatting.YELLOW);
            context.drawCenteredString(font, pageInfo, width / 2, 45, 0xFFFFFF);
        }
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