package io.wifi.ConfigCompact.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClientUtilScreen extends Screen {

    Screen parent;

    public ClientUtilScreen(Screen parent) {
        super(Component.translatable("screen.starrailexpress.client_utils"));
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
