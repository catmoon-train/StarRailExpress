/* GNU General Public License v3 or later */
package io.wifi.starrailexpress.client.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 思索里模拟「背包界面里的选人列表」：神权冷笑、巫毒师、变形者等职业的技能
 * 是在背包界面里挑一个玩家来施放，游戏内对应的是
 * {@code io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper} 挂上去的一排
 * 玩家头像。
 *
 * <p>离屏沙盒里构造不出真实的背包界面（服务端同步数据缺失），因此这里按同一套版式
 * 自绘一份：标题、玩家名搜索框、一排玩家头像。
 *
 * <p>头像用默认玩家皮肤填充：思索是离屏场景，没有真实在线玩家，
 * 用固定的用户名与默认皮肤才能保证每次播放画面一致。
 */
public class PonderScreenOverlayElement implements PonderOverlayElement {

    /** 默认填充用的玩家头像（默认皮肤）。 */
    private static final ResourceLocation DEFAULT_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final ResourceLocation DEFAULT_SKIN_ALT =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/alex.png");

    private static final int HEAD_SIZE = 16;
    private static final int CELL = 22;
    private static final int PANEL_WIDTH = 172;

    private final PonderLocalizedText title;
    private final PonderLocalizedText hint;
    private final List<String> players;
    private final int columns;
    private final int accent;
    private boolean visible = true;

    public PonderScreenOverlayElement(PonderLocalizedText title, List<String> players,
                                      PonderLocalizedText hint, int columns, int accent) {
        this.title = title == null ? PonderLocalizedText.of("") : title;
        this.players = List.copyOf(players == null ? List.of() : players);
        this.hint = hint == null ? PonderLocalizedText.of("") : hint;
        this.columns = Math.max(1, columns);
        this.accent = accent;
    }

    public PonderScreenOverlayElement addTo(SceneBuilder scene) {
        scene.addInstruction(ps -> {
            ps.addElement(this);
            this.visible = true;
        });
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        if (!visible || players.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int rows = (players.size() + columns - 1) / columns;
        int panelWidth = Math.max(PANEL_WIDTH, columns * CELL + 16);
        int panelHeight = 26 + rows * CELL + 12 + (hint.isEmpty() ? 0 : font.lineHeight + 4);
        int x = (screen.width - panelWidth) / 2;
        int y = (screen.height - panelHeight) / 2;

        // 面板底板
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xC0101018);
        graphics.renderOutline(x, y, panelWidth, panelHeight, 0xFF3A3A4A);

        int textX = x + 8;
        int textY = y + 7;
        if (!title.isEmpty()) {
            graphics.drawString(font, title.resolve(), textX, textY, accent);
            textY += font.lineHeight + 4;
        }

        // 玩家名搜索框（与游戏内一致的空框 + 光标）
        int boxY = textY;
        graphics.fill(textX, boxY, textX + panelWidth - 16, boxY + 12, 0xFF000000);
        graphics.renderOutline(textX, boxY, panelWidth - 16, 12, 0xFF55556A);
        graphics.fill(textX + 2, boxY + 2, textX + 3, boxY + 10, 0xFFD0D0D0);
        textY = boxY + 12 + 6;

        // 玩家头像网格
        for (int i = 0; i < players.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int headX = textX + column * CELL;
            int headY = textY + row * CELL;
            graphics.fill(headX - 3, headY - 3, headX + HEAD_SIZE + 3, headY + HEAD_SIZE + 3, 0xFF3A3A4A);
            PlayerFaceRenderer.draw(graphics, i % 2 == 0 ? DEFAULT_SKIN : DEFAULT_SKIN_ALT,
                    headX, headY, HEAD_SIZE);
        }

        if (!hint.isEmpty()) {
            int hintY = textY + rows * CELL + 4;
            Component component = hint.resolve();
            graphics.drawString(font, component, x + (panelWidth - font.width(component)) / 2, hintY, 0xFFDDDDDD);
        }
    }
}
