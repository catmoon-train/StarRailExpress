package org.agmas.noellesroles.client.widget.nodes;

import net.minecraft.network.chat.Component;

import java.awt.*;

/**
 * 节点控件工厂
 * <p>
 *     可以创建模板控件
 * </p>
 */
public class NodeWidgetFactory {
    protected NodeWidgetFactory() {
    }

    public static ButtonNodeWidget createDefaultBtn(int x, int y, int w, int h, Component component) {
        return (ButtonNodeWidget) ButtonNodeWidget.builder(x, y, w, h, component)
                .enableBg()
                .build();
    }

    /**
     * 生成只读Label的按钮
     * <p>
     * 由label挂载在button下组合而成，无法通过修改btn的component来修改label的属性
     * </p>
     * <p>
     * 当然，label有着最小的order,可以通过 getNextNodes.getFirt()来访问到
     * </p>
     */
    public static ButtonNodeWidget createDefaultLabelBtn(int x, int y, int w, int h, Component component) {
        ButtonNodeWidget btn = ButtonNodeWidget.builder(x, y, w, h, component)
                .enableBg()
                .build();
        LabelNodeWidget.builder(0, 0, w, h, component)
                .setIsBg(false, false)
                .setOrder(-2147483647)
                .setParent(btn)
                .build();
        return btn;
    }

    public static ButtonNodeWidget createDefaultLabelBtn(int x, int y, int w, int h, Component component, AbstractNodeWidget parent) {
        ButtonNodeWidget btn = createDefaultLabelBtn(x, y, w, h, component);
        if (parent != null)
            btn.setParentNode(parent);
        return btn;
    }

    public static ButtonNodeWidget createDefaultLabelBtn(int x, int y, int w, int h, Component component, AbstractNodeWidget parent, ButtonNodeWidget.OnClicked onClicked) {
        ButtonNodeWidget btn = createDefaultLabelBtn(x, y, w, h, component, parent);
        if (onClicked != null)
            btn.setCallBack(onClicked);
        return btn;
    }

    public static ScrollListNodeWidget createDefaultScrollList(int x, int y, int w, int h, Component component) {
        int contentGap = 5;
        ScrollListNodeWidget scrollBarNodeWidget = ScrollListNodeWidget
                .builder(x, y, w, h, component)
                .setMode(NodeListWidget.ListMode.HORIZONTAL)
                .unableResize()
                .setOrder(-10)
                .build();
        scrollBarNodeWidget.set_interval(contentGap);
        return scrollBarNodeWidget;
    }

    public static ScrollListNodeWidget createDefaultScrollList(int x, int y, int w, int h, Component component, AbstractNodeWidget parent) {
        ScrollListNodeWidget list = createDefaultScrollList(x, y, w, h, component);
        if (parent != null)
            list.setParentNode(parent);
        return list;
    }

    /**
     * 创建一个默认的菜单模板
     *
     * @param x         root X
     * @param y         root Y
     * @param width     root width
     * @param height    root height
     * @param component root component
     * @return 返回一个基类根节点，默认包含一个层级为0的列表，该列表中包含1个列表，上方选项卡层级-100，可以使用get访问
     */
    public static AbstractNodeWidget createDefaultMenuTemplate(int x, int y, int width, int height, Component component) {
        float typeCardsHeightPercent = 0.1f;
        AbstractNodeWidget root = AbstractNodeWidget
                .builder(x, y, width, height, component)
                .build();
        int typeCardsHeight = (int) (height * typeCardsHeightPercent);
        int typeCardGap = 10;
        NodeListWidget rootList = NodeListWidget
                .builder(0, 0, width, height, Component.literal("root_list"))
                .unableResize()
                .setOrder(0)
                .setParent(root)
                .build();
        NodeListWidget
                .builder(0, 0, width, typeCardsHeight, Component.literal("type_cards_list"))
                .set_original_interval(typeCardGap)
                .set_interval(typeCardGap)
                .set_alignment(4)
                .setMode(NodeListWidget.ListMode.HORIZONTAL)
                .enableScroll()
                .unableResize()
                .enableFillBg()
                .setOrder(-100)
                .setParent(rootList)
                .build();
        AbstractNodeWidget
                .builder(3, 0, width - 6, 2, Component.literal("gap_line"))
                .setBgFillColor(new Color(0xFF88AAAA, true))
                .setOrder(-10)
                .setParent(rootList)
                .build();
        return root;
    }

    /**
     * 创建一个带滚动条的选项
     */
    public static AbstractNodeWidget createDefaultScrollBarOptionWidget(int x, int y, int width, int height, Component component) {
        float barWidthPercent = 0.6f;
        AbstractNodeWidget root = AbstractNodeWidget
                .builder(x, y, width, height, component)
                .enableFillBg()
                .build();
        LabelNodeWidget
                .builder(0, 0, (int) (width * barWidthPercent), height, component)
                .setIsLineBg(false)
                .setParent(root)
                .build();
        ScrollBarNodeWidget bar = ScrollBarNodeWidget.builder(0, 0, width - (int) (width * barWidthPercent), (int) (0.3f * height), Component.literal("scroll_bar"))
                .setListMode(NodeListWidget.ListMode.HORIZONTAL)
                .setParent(root)
                .build();
        AbstractNodeWidget.Vector2i barPos = root.get_layout(5, new AbstractNodeWidget.Vector2i(bar.getWidth(), bar.getHeight()));
        bar.setLocalPosition(barPos.x, barPos.y);
        return root;
    }

    public static AbstractNodeWidget createDefaultScrollBarOptionWidget(int x, int y, int width, int height, Component component, AbstractNodeWidget parent) {
        AbstractNodeWidget root = createDefaultScrollBarOptionWidget(x, y, width, height, component);
        if (parent != null)
            root.setParentNode(parent);
        return root;
    }
}