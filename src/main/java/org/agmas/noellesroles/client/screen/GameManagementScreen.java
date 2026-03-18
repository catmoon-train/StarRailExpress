package org.agmas.noellesroles.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.nodes.*;

import java.util.Map;

public class GameManagementScreen extends AbstractPixelScreen {
    Screen parent;

    public GameManagementScreen() {
        super(Component.empty());
    }

    public GameManagementScreen(Screen parent) {
        this();
        this.parent = parent;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        super.init();
        this.rootNode = NodeWidgetFactory.createDefaultMenuTemplate(0, 0, width, height,
                Component.literal("settings_root"));
        addRenderableWidget(rootNode);
        NodeListWidget rootList = (NodeListWidget) rootNode.getNode(0);
        NodeListWidget typeCardsList = (NodeListWidget) rootList.getNode(0);
        float typeCardHeightPercent = 0.08f;
        float typeCardWidthPercent = 0.18f;

        ScrollListNodeWidget videoSettingsList = NodeWidgetFactory.createDefaultScrollList(0, 0, rootList.getWidth(),
                rootList.getHeight() - typeCardsList.getHeight() - 2,
                Component.literal("video_content_list"));
        NodeWidgetFactory.createDefaultScrollBarOptionWidget(0, 0, width, (int) (height * typeCardHeightPercent),
                Component.literal("test video setting"), videoSettingsList);

        // TODO : 右边不显示进度条的bug
        ScrollListNodeWidget audioSettingsList = NodeWidgetFactory.createDefaultScrollList(0, 0, rootList.getWidth(),
                rootList.getHeight() - typeCardsList.getHeight() - 2,
                Component.literal("audio_content_list"));
        for (int i = 0; i < 20; ++i) {
            NodeWidgetFactory.createDefaultScrollBarOptionWidget(0, 0, width - 50,
                    (int) (height * typeCardHeightPercent),
                    Component.literal("test audio setting"), audioSettingsList);
        }

        NodeWidgetFactory.createDefaultLabelBtn(0, 0,
                (int) (typeCardWidthPercent * width), (int) (typeCardHeightPercent * height),
                Component.literal("VIDEO"),
                typeCardsList, (btn) -> {
                    // warn : 具体列表索引为2，不要随意更改list的内容
                    AbstractNodeWidget lastNode = rootList.getNode(2);
                    if (lastNode != null) {
                        if (!lastNode.equals(videoSettingsList)) {
                            lastNode.removeFromParent();
                            rootList.pushBack(videoSettingsList);
                        }
                    } else
                        rootList.pushBack(videoSettingsList);
                });
        NodeWidgetFactory.createDefaultLabelBtn(0, 0,
                (int) (typeCardWidthPercent * width), (int) (typeCardHeightPercent * height),
                Component.literal("AUDIO"),
                typeCardsList, (btn) -> {
                    AbstractNodeWidget lastNode = rootList.getNode(2);
                    if (lastNode != null) {
                        if (!lastNode.equals(audioSettingsList)) {
                            lastNode.removeFromParent();
                            rootList.pushBack(audioSettingsList);
                        }
                    } else
                        rootList.pushBack(audioSettingsList);
                });
        NodeWidgetFactory.createDefaultLabelBtn(0, 0, 100, 20,
                Component.literal("点击切换文本"), videoSettingsList, btn -> {
                    btn.getNextNodes().getFirst().setMessage(Component.literal("文本已切换"));
                });
    }

    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        rootNode.mouseScrolled(d, e, f, g);
        return true;
    }

    @Override
    public boolean mouseDragged(double d, double e, int i, double f, double g) {
        rootNode.mouseDragged(d, e, i, f, g);
        return true;
    }

    protected AbstractNodeWidget rootNode;
    protected Map<Component, AbstractNodeWidget> contentLists;
}
