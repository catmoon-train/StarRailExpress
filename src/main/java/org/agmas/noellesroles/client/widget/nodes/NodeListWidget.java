package org.agmas.noellesroles.client.widget.nodes;

import net.minecraft.network.chat.Component;

public class NodeListWidget extends AbstractNodeWidget{
    public static class Builder<B extends Builder<B>> extends AbstractNodeWidget.Builder<B> {
        public Builder(int x, int y, int w, int h, Component component) {
            super(x, y, w, h, component);
        }
        public B setMode(ListMode mode) {
            m_emListMode = mode;
            return self();
        }
        public B set_interval(int interval) {
            m_i32Interval = interval;
            return self();
        }
        public B set_original_interval(int interval) {
            m_i32original_interval = interval;
            return self();
        }
        public B set_alignment(int alignment) {
            m_i32Alignment = alignment;
            return self();
        }
        public B unableResize() {
            m_bIsResize = false;
            return self();
        }
        public B enableScroll() {
            m_bCanScroll = true;
            return self();
        }
        @Override
        public NodeListWidget build() {
            NodeListWidget node = (NodeListWidget) super.build();
            node.m_i32original_interval = m_i32original_interval;
            node.m_i32Interval = m_i32Interval;
            node.m_bIsResize = m_bIsResize;
            node.m_bCanScroll = m_bCanScroll;
            if (m_bIsResize) {
                switch (m_emListMode) {
                    case ListMode.HORIZONTAL:
                        node.setSize(2 * m_i32original_interval, 0);
                        break;
                    case ListMode.VERTICAL:
                        node.setSize(0, 2 * m_i32original_interval);
                        break;
                }
            }
            return node;
        }
        @Override
        protected NodeListWidget create() {
            return new NodeListWidget(m_emListMode, m_i32Alignment, x, y, w, h, component);
        }
        protected ListMode m_emListMode = ListMode.VERTICAL;
        protected boolean m_bIsResize = true;
        protected boolean m_bCanScroll = true;
        protected int m_i32original_interval = 0;
        protected int m_i32Interval = 0;
        protected int m_i32Alignment = 1;
    }
    public enum ListMode {
        HORIZONTAL,
        VERTICAL
    }
    public NodeListWidget(ListMode mode, int alignment, int x, int y, int w, int h, Component component) {
        super(x, y, w, h, component);
        m_i32original_interval = 0;
        m_i32Interval = 0;
        m_emListMode = mode;
        m_i32Alignment = alignment;
        m_v2MaxSize = new Vector2i(0, 0);
    }
    public static Builder<?> builder(int x, int y, int w, int h, Component component) {
        return new Builder<>(x, y, w, h, component);
    }
    public void refresh_transform()
    {
        // 列表的起始位置
        int curOffset = offset + m_i32original_interval;
        switch (m_emListMode)
        {
            case ListMode.HORIZONTAL:
                if (m_bIsResize) {
                    setHeight(m_v2MaxSize.y);
                }
                for (AbstractNodeWidget i : m_vecNextNodes) {
                    i.setLocalPosition(new Vector2i(curOffset,
                            get_layout(m_i32Alignment, new Vector2i(i.getWidth(), i.getHeight())).y));
                    curOffset += i.getWidth() + m_i32Interval;
                }
                if (m_bIsResize) {
                    setWidth(m_i32original_interval + curOffset - m_vecNextNodes.size() > 0 ? m_i32Interval : 0);
                }
                break;
            case ListMode.VERTICAL:
                if (m_bIsResize) {
                    setWidth(m_v2MaxSize.x);
                }
                for (AbstractNodeWidget i : m_vecNextNodes) {
                    i.setLocalPosition(new  Vector2i(get_layout(m_i32Alignment, new Vector2i(i.getWidth(), i.getHeight())).x,
                            curOffset));
                    curOffset += i.getHeight() + m_i32Interval;
                }
                if (m_bIsResize) {
                    setHeight(m_i32original_interval + curOffset - m_vecNextNodes.size() > 0 ? m_i32Interval : 0);
                }
                break;
            default:
                break;
        }
        maxLength = curOffset - offset + m_i32original_interval;
    }

//    @Override
//    public void afterUpdateDirty() {
//        refresh_transform();
//    }

    @Override
    public void removeNextNodesTo(AbstractNodeWidget pNewParent) {
        super.removeNextNodesTo(pNewParent);
        refresh_transform();
    }
    @Override
    public void addNode(AbstractNodeWidget pNextNode) {
        super.addNode(pNextNode);
        // 添加新节点会导致自身 size的变化
        if (m_v2MaxSize.x < pNextNode.getWidth())
            m_v2MaxSize.x = pNextNode.getWidth();
        if (m_v2MaxSize.y < pNextNode.getHeight())
            m_v2MaxSize.y = pNextNode.getHeight();
        refresh_transform();
    }
    @Override
    public void eraseNode(AbstractNodeWidget pTarget) {
        super.eraseNode(pTarget);
        // 移除节点后需重新计算最大 size
        for (AbstractNodeWidget w : m_vecNextNodes)
        {
            if (m_v2MaxSize.x < w.getWidth())
                m_v2MaxSize.x = w.getWidth();
            if (m_v2MaxSize.y < w.getHeight())
                m_v2MaxSize.y = w.getHeight();
        }
        refresh_transform();
    }
    @Override
    public void pushBack(AbstractNodeWidget pNextNode) {
        super.pushBack(pNextNode);
        // 添加新节点会导致自身 size的变化
        if (m_v2MaxSize.x < pNextNode.getWidth())
            m_v2MaxSize.x = pNextNode.getWidth();
        if (m_v2MaxSize.y < pNextNode.getHeight())
            m_v2MaxSize.y = pNextNode.getHeight();
        refresh_transform();
    }
    @Override
    public NodeListWidget clone() {
        throw new UnsupportedOperationException("Clone list is invalid.");
    }

    public void set_interval(int interval)
    {
        m_i32Interval = interval;
        refresh_transform();
    }

    public void set_original_interval(int interval)
    {
        m_i32original_interval = interval;
        refresh_transform();
    }
    public void setAlignment(int alignment) {
        m_i32Alignment = alignment;
        refresh_transform();
    }
    public void setListMode(ListMode mode) {
        m_emListMode = mode;
        refresh_transform();
    }
    /** 设置列表偏移量,只允许为负值 */
    public void setOffset(int offset) {
        if (offset > 0)
            offset = 0;
        if (offset < getMaxOffset())
            offset = getMaxOffset();
        this.offset = offset;
        refresh_transform();
    }
    /** 获取列表可滚动的最大偏移量,值为负 */
    public int getMaxOffset() {
        return switch (m_emListMode) {
            case ListMode.HORIZONTAL -> Math.min(width - maxLength, 0);
            case ListMode.VERTICAL -> Math.min(height - maxLength, 0);
        };
    }
    @Override
    protected boolean canScroll(double d, double e, double f, double g) {
        if (m_bCanScroll && isMouseOver(d, e)) {
            return (g > 0 && offset < 0) || (g < 0 && maxLength > height && maxLength + offset > height);
        }
        return false;
    }
    @Override
    protected boolean onScroll(double d, double e, double f, double g) {
        offset += (int) g * 5;
        refresh_transform();
        return true;
    }
    protected ListMode m_emListMode = ListMode.VERTICAL;
    /** 存储最大控件的 size以用于预测开始绘制的行或列的起始位置*/
    Vector2i m_v2MaxSize;
    /** 是否开启自动重绘大小:列表加入节点会自动重绘大小 */
    protected boolean m_bIsResize = true;
    protected boolean m_bCanScroll = false;
    /** 页边距:距离左右边界的距离 */
    protected int m_i32original_interval = 0;
    /** 控件间隔 */
    protected int m_i32Interval = 0;
    /** 对齐方式，同set_layout，可以修改排列时对齐方式，如居中、以左上角为原点等*/
    protected int m_i32Alignment = 1;
    /** 滚动方向的偏移 */
    protected int offset = 0;
    /** 列表方向最大长度 */
    protected int maxLength = 0;
}
