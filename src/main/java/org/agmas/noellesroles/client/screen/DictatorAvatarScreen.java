package org.agmas.noellesroles.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.DictatorAvatarWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 独裁者通用「玩家头像」选择界面：展示场上所有玩家的头像，点击选中其中一个。
 *
 * <p>裁决之剑用它选择「凶手」（先选死因，见 {@link DictatorReasonScreen}）。
 */
public class DictatorAvatarScreen extends Screen {

    private static final int AVATAR_SIZE = 32;
    private static final int SPACING = 8;
    private static final int PER_ROW = 8;

    private final Component title;
    private final Consumer<AbstractClientPlayer> onPick;
    private final List<DictatorAvatarWidget> widgets = new ArrayList<>();
    private EditBox searchWidget;
    private String search;

    public DictatorAvatarScreen(Component title, Consumer<AbstractClientPlayer> onPick) {
        super(title);
        this.title = title;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        for (DictatorAvatarWidget widget : widgets) {
            removeWidget(widget);
        }
        widgets.clear();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        // 始终展示所有玩家（含已死亡的玩家：猜中已死亡目标时由服务端提示）
        List<AbstractClientPlayer> players = new ArrayList<>(minecraft.level.players());
        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            players.removeIf(p -> !p.getName().getString().toLowerCase().contains(lower));
        }
        if (players.isEmpty()) {
            return;
        }

        int columns = Math.min(players.size(), PER_ROW);
        int rows = (int) Math.ceil(players.size() / (double) PER_ROW);
        int totalWidth = columns * (AVATAR_SIZE + SPACING) - SPACING;
        int totalHeight = rows * (AVATAR_SIZE + SPACING) - SPACING;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 14;

        for (int i = 0; i < players.size(); i++) {
            int col = i % PER_ROW;
            int row = i / PER_ROW;
            int x = startX + col * (AVATAR_SIZE + SPACING);
            int y = startY + row * (AVATAR_SIZE + SPACING);
            DictatorAvatarWidget widget = new DictatorAvatarWidget(x, y, AVATAR_SIZE, players.get(i), onPick);
            widgets.add(widget);
            addRenderableWidget(widget);
        }

        if (searchWidget == null) {
            searchWidget = new EditBox(font, (width - 220) / 2, startY - 32, 220, 20, Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                search = text == null || text.isBlank() ? null : text;
                rebuild();
            });
        }
        addRenderableWidget(searchWidget);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                width / 2, 30, 0xFFFFFF);
        context.drawCenteredString(font,
                Component.translatable("screen.noellesroles.dictator.hint").withStyle(ChatFormatting.GRAY),
                width / 2, height - 26, 0x888888);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
