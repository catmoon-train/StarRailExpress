package org.agmas.noellesroles.client.widget.nodes;

import net.minecraft.network.chat.Component;

/**
 * 滚动节点列表控件
 * <p>
 *      该控件是节点列表的代理，包含一个滚动条，无需将自身设置为可滚动
 * </p>
 */
public class ScrollListNodeWidget extends NodeListWidget{
    public static class Builder<B extends Builder<B>> extends NodeListWidget.Builder<B> {
        public Builder(int x, int y, int w, int h, Component component) {
            super(x, y, w, h, component);
            m_i32Alignment = 4;
            m_bIsResize = false;
            m_bCanScroll = true;
        }
        @Override
        public ScrollListNodeWidget build() {
            ScrollListNodeWidget node = (ScrollListNodeWidget) super.build();
            ListMode containMode = m_emListMode == ListMode.HORIZONTAL ? ListMode.VERTICAL : ListMode.HORIZONTAL;
            // 由于间隔的存在，实际可用宽度可能被压缩
            int trueW = w;
            int trueH = h;
            int deltaW = w;
            int deltaH = h;
            switch (containMode) {
                case ListMode.HORIZONTAL:
                    deltaH = 5;
                    trueW -= m_i32original_interval * 2 - m_i32original_interval;
                    break;
                case ListMode.VERTICAL:
                    deltaW = 5;
                    trueH -= m_i32original_interval * 2 - m_i32original_interval;
                    break;
            }
            node.m_pListWidget = NodeListWidget
                    .builder(0, 0,
                            trueW - (containMode == ListMode.HORIZONTAL ? 0 : deltaW),
                            trueH - (containMode == ListMode.VERTICAL ? 0 : deltaH), component)
                    .setMode(containMode)
                    .enableScroll()
                    .unableResize()
                    .build();
            if (containMode == ListMode.HORIZONTAL) {
                node.m_pListWidget.setAlignment(4);
            }
            node.m_pScrollBar = ScrollBarNodeWidget
                    .builder(0, 0, deltaW, deltaH, component)
                    .setListMode(containMode)
                    .setProcessCallBack(process -> {
                        NodeListWidget listWidget = node.m_pListWidget;
                        listWidget.setOffset((int) (listWidget.getMaxOffset() * process));
                    })
                    .build();
            node.addScrollNode(node.m_pListWidget);
            node.addScrollNode(node.m_pScrollBar);
            return node;
        }
        @Override
        protected ScrollListNodeWidget create() {
            return new ScrollListNodeWidget(m_emListMode, m_i32Alignment, x, y, w, h, component);
        }
    }
    protected ScrollListNodeWidget(ListMode mode, int alignment, int x, int y, int w, int h, Component component) {
        super(mode, alignment, x, y, w, h, component);
    }
    public static Builder<?> builder(int x, int y, int w, int h, Component component) {
        return new Builder<>(x, y, w, h, component);
    }

    // 代理list操作
    @Override
    public void removeNextNodesTo(AbstractNodeWidget pNewParent) {
        m_pListWidget.removeNextNodesTo(pNewParent);
        updateScrollBar();
    }
    @Override
    public void eraseNode(AbstractNodeWidget pTarget) {
        m_pListWidget.eraseNode(pTarget);
        updateScrollBar();
    }
    /** 添加到当前节点，仅在初始化时将成员变量添加到自身树中调用 */
    protected void addScrollNode(AbstractNodeWidget pNextNode) {
        super.addNode(pNextNode);
    }
    @Override
    public void addNode(AbstractNodeWidget pNextNode) {
        m_pListWidget.addNode(pNextNode);
        updateScrollBar();
    }
    @Override
    public void pushBack(AbstractNodeWidget pNextNode) {
        m_pListWidget.pushBack(pNextNode);
        updateScrollBar();
    }

    @Override
    public void refresh_transform() {
        ListMode containMode = m_emListMode == ListMode.HORIZONTAL ? ListMode.VERTICAL : ListMode.HORIZONTAL;
        m_pScrollBar.setListMode(containMode);
        m_pListWidget.setListMode(containMode);
        int deltaW = width;
        int deltaH = height;
        switch (containMode) {
            case ListMode.HORIZONTAL:
                deltaH = 5;
                break;
            case ListMode.VERTICAL:
                deltaW = 5;
                break;
        }
        m_pListWidget.setSize(width - (containMode == ListMode.HORIZONTAL ? 0 : deltaW),
                        height - (containMode == ListMode.VERTICAL ? 0 : deltaH));
        m_pScrollBar.setSize(deltaW, deltaH);
        updateScrollBar();
        super.refresh_transform();
    }

    protected void updateScrollBarProcess() {
        float process = switch (m_pListWidget.m_emListMode) {
            case ListMode.HORIZONTAL ->
                    (float) -m_pListWidget.offset / (m_pListWidget.maxLength - m_pListWidget.getWidth());
            case ListMode.VERTICAL ->
                    (float) -m_pListWidget.offset / (m_pListWidget.maxLength - m_pListWidget.getHeight());
        };
        m_pScrollBar.setProcess(process);
    }

    public void set_interval(int interval)
    {
        m_pListWidget.set_interval(interval);
        refresh_transform();
    }
    public void set_original_interval(int interval)
    {
        m_pListWidget.set_original_interval(interval);
        refresh_transform();
    }

    protected void updateScrollBar() {
        int barWidth = switch (m_pListWidget.m_emListMode) {
            case ListMode.HORIZONTAL ->
                    (int) ((float) m_pListWidget.getWidth() / m_pListWidget.maxLength * m_pScrollBar.getWidth());
            case ListMode.VERTICAL ->
                    (int) ((float) m_pListWidget.getHeight() / m_pListWidget.maxLength * m_pScrollBar.getHeight());
        };
        m_pScrollBar.setBarLength(barWidth);
        updateScrollBarProcess();
    }
    @Override
    protected boolean canScroll(double d, double e, double f, double g) {
        return m_pListWidget.canScroll(d, e, f, g);
    }
    @Override
    protected boolean onScroll(double d, double e, double f, double g) {
        // 上移的偏移量（正为上移）
        m_pListWidget.onScroll(d, e, f, g);
        updateScrollBarProcess();
        return true;
    }
    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        // 拦截成员的滚动
        if (canScroll(d, e, f, g)) {
            return onScroll(d, e, f, g);
        }
        for (int j = m_vecNextNodes.size() - 1; j >= 0; --j) {
            boolean bl = m_vecNextNodes.get(j).mouseScrolled(d, e, f, g);
            if (bl)
                return true;
        }
        return false;
    }
    protected NodeListWidget m_pListWidget = null;
    protected ScrollBarNodeWidget m_pScrollBar = null;
}
