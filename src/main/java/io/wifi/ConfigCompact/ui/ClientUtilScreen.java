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

package io.wifi.ConfigCompact.ui;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.agmas.noellesroles.mixin.SkinManagerAccessor;

import io.wifi.rhythm.client.screen.RhythmGameListScreen;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.network.chat.Component;

public class ClientUtilScreen extends Screen {

    Screen parent;

    public ClientUtilScreen(Screen parent) {
        super(Component.translatable("screen.starrailexpress.client_utils"));
        this.parent = parent;
    }

    static int WIDE_BUTTON_WIDTH = 204;
    static int SMALL_BUTTON_WIDTH = 204;
    static final int BUTTON_HEIGHT = 20;
    static final int MARGIN = 4;
    static int COLUMN_COUNT = 1;

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    public final Button openScreenButton(Component component, Supplier<Screen> supplier) {
        return Button.builder(component, (button) -> {
            Screen scr = supplier.get();
            if (scr != null || scr != this) {
                this.minecraft.setScreen(scr);
            }
        }).width(SMALL_BUTTON_WIDTH)
                .build();
    }

    public final Button simpleButton(Component component, Consumer<Button> runner) {
        return Button.builder(component, (button) -> {
            runner.accept(button);
        }).width(SMALL_BUTTON_WIDTH)
                .build();
    }

    private boolean isSmallUI() {
        return height <= 300;
    }

    @Override
    protected void init() {
        super.init();

        int top = 20;
        int maxWidth = this.width;
        this.addRenderableWidget(new StringWidget(0, top, maxWidth, 9, this.title, this.font));
        COLUMN_COUNT = isSmallUI() ? 2 : 1;
        SMALL_BUTTON_WIDTH = isSmallUI() ? 98 : 204;
        GridLayout gridLayout = new GridLayout();
        gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
        GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(COLUMN_COUNT);
        // 客户端设置
        // rowHelper.addChild()

        // 角色介绍

        // 列车设置
        {
            Button btn = simpleButton(Component.translatable("screen.starrailexpress.client_utils.reload_resourcepack"),
                    (b) -> {
                        minecraft.reloadResourcePacks();
                    });
            btn.setWidth(WIDE_BUTTON_WIDTH);

            rowHelper.addChild(btn, COLUMN_COUNT, gridLayout.newCellSettings().paddingTop(50));
        }

        {
            Button btn = simpleButton(Component.translatable("screen.starrailexpress.client_utils.clear_chat"),
                    (b) -> {
                        if (minecraft.gui == null)
                            return;
                        if (minecraft.gui.getChat() == null)
                            return;
                        minecraft.gui.getChat().clearMessages(false);
                    });
            rowHelper.addChild(btn);
        }

        {
            Button btn = simpleButton(Component.translatable("screen.starrailexpress.client_utils.test_screen"),
                    (b) -> {
                        this.minecraft.setScreen(new TestScreen(this));
                    });
            rowHelper.addChild(btn);
        }
        {
            Button btn = simpleButton(Component.translatable("screen.starrailexpress.client_utils.clear_skins"),
                    (b) -> {
                        var client = minecraft;
                        if (client.player == null)
                            return;

                        SkinManager skinManager = client.getSkinManager();
                        SkinManagerAccessor accessor = (SkinManagerAccessor) skinManager;
                        var cache = accessor.getSkinCache();

                        // 复制键集，避免在迭代过程中修改集合导致并发异常
                        ArrayList<SkinManager.CacheKey> allKeys = new ArrayList<>(cache.asMap().keySet());

                        for (SkinManager.CacheKey key : allKeys) {
                            // 仅清除其他玩家的缓存，保留自己的
                            cache.invalidate(key);
                        }

                        // 立即执行清理，确保旧数据被丢弃
                        cache.cleanUp();
                        SRE.LOGGER.info("Skin caches cleared by user.");
                    });
            rowHelper.addChild(btn);
        }

        {
            Button btn = simpleButton(Component.translatable("screen.starrailexpress.client_utils.rhythm_game"),
                    (b) -> {
                        this.minecraft.setScreen(new RhythmGameListScreen(this));
                    });
            rowHelper.addChild(btn);
        }
        // 返回
        rowHelper.addChild(Button.builder(Component.translatable("gui.back"), (button) -> {
            this.minecraft.setScreen((Screen) parent);
        }).width(WIDE_BUTTON_WIDTH).build(), COLUMN_COUNT);
        // gridLayout.newCellSettings().paddingTop(50)
        gridLayout.arrangeElements();
        FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5F, 0.25F);
        gridLayout.visitWidgets(this::addRenderableWidget);
    }

}
