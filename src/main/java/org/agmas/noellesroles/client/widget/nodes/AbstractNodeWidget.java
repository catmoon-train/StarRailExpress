package org.agmas.noellesroles.client.widget.nodes;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽象节点类，用于实现树形结构，九宫格布局；虽然说是抽象节点但是依然可以作为空控件被创建用了布局
 * <p>
 * Warn : 由个人c++库中利用ai转译而来，未验证安全性
 * 本来想做成通用的树节点，但是java不能多继承，写起来有点绕+麻烦，所以直接把功能写控件里了
 * </p>
 */
public class AbstractNodeWidget extends AbstractWidget {
    public static class Builder<B extends Builder<B>> {
        public Builder(int x, int y, int w, int h, Component component) {
            this.component = component;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }
        public B setParent(AbstractNodeWidget parent) {
            m_pParentNode = parent;
            return self();
        }
        /** 如果设置了颜色，将会自动开启背景绘制*/
        public B setBgFillColor(Color color) {
            BG_FILL_COLOR = color;
            m_bIsFillBg = true;
            return self();
        }
        /** 如果设置了颜色，将会自动开启线框绘制*/
        public B setBgLineColor(Color color) {
            BG_LINE_COLOR = color;
            m_bIsLineBg = true;
            return self();
        }
        public B setBgColor(Color fillColor, Color lineColor) {
            BG_FILL_COLOR = fillColor;
            m_bIsFillBg = true;
            BG_LINE_COLOR = lineColor;
            m_bIsLineBg = true;
            return self();
        }
        public B setOrder(int i32Order) {
            m_i32Order = i32Order;
            return self();
        }
        public B enableFillBg() {
            m_bIsFillBg = true;
            return self();
        }
        public B enableLineBg() {
            m_bIsLineBg = true;
            return self();
        }
        public B enableBg() {
            m_bIsFillBg = true;
            m_bIsLineBg = true;
            return self();
        }
        public B setIsFillBg(boolean isFillBg) {
            return self();
        }
        public B setIsLineBg(boolean isLineBg) {
            m_bIsLineBg = isLineBg;
            return self();
        }
        public B setIsBg(boolean isFillBg, boolean isLineBg) {
            m_bIsFillBg = isFillBg;
            m_bIsLineBg = isLineBg;
            return self();
        }
        /** 如果有其他变量需要在build时初始化则需要重载build并调用super的build以实现其他变量的初始化, 这样可以显著减少构造函数的长度*/
        public AbstractNodeWidget build() {
            AbstractNodeWidget node = create();
            node.BG_FILL_COLOR = BG_FILL_COLOR;
            node.BG_LINE_COLOR = BG_LINE_COLOR;
            node.m_i32Order = m_i32Order;
            node.m_bIsFillBg = m_bIsFillBg;
            node.m_bIsLineBg = m_bIsLineBg;
            if (m_pParentNode != null) {
                node.setParentNode(m_pParentNode);
            }
            return node;
        }
        /** 对于具体的节点类需要自行重载方法(java支持重写返回类型) */
        protected AbstractNodeWidget create() {
            return new AbstractNodeWidget(x, y, w, h, component);
        }
        protected Color BG_FILL_COLOR = new Color(0x4F555555, true);
        protected Color BG_LINE_COLOR = new Color(0xFF77CCFF, true);
        protected AbstractNodeWidget m_pParentNode = null;
        protected Component component;
        protected boolean m_bIsFillBg = false;
        protected boolean m_bIsLineBg = false;
        protected int m_i32Order = 0;
        protected int x;
        protected int y;
        protected int w;
        protected int h;
    }

    public static class Vector2i {
        public Vector2i(int x, int y) {
            this.x = x;
            this.y = y;
        }
        public Vector2i() {
            x = 0;
            y = 0;
        }
        public Vector2i add(Vector2i vec) {
            this.x += vec.x;
            this.y += vec.y;
            return this;
        }
        public int x;
        public int y;
    }
    /**
     * 构造控件节点
     * Warn : 无法在构造中直接设置绝对x,y, 但是在获取是会自动更新
     * @param x 相对父控件的 x
     * @param y 相对父控件的 y
     * @param w 控件宽度
     * @param h 控件高度
     * @param component 控件标题
     */
    protected AbstractNodeWidget(int x, int y, int w, int h, Component component) {
        super(0, 0, w, h, component);
        this.m_bIsFillBg = false;
        this.m_bIsLineBg = false;
        this.m_bIsTransformDirty = true;
        this.m_i32Order = 0;
        this.m_vecNextNodes = new ArrayList<>();
        this.m_pParentNode = null;
        localX = x;
        localY = y;
    }

    /**
     * Warn : 无法在构造中直接设置绝对x,y, 但是在获取是会自动更新
     * @param bgFillColor 背景填充色
     * @param bgLineColor 线框色
     * @param order 层级，越大越顶层
     * @param x 相对父控件的x
     * @param y 相对父控件的y
     * @param w 宽
     * @param h 高
     * @param component 控件标题
     */
    protected AbstractNodeWidget(Color bgFillColor, Color bgLineColor, int order, int x, int y, int w, int h, Component component) {
        this(x, y, w, h, component);
        BG_FILL_COLOR = bgFillColor;
        BG_LINE_COLOR = bgLineColor;
        setOrder(order);
    }
    public static Builder<?> builder(int localX, int localY, int w, int h, Component component) {
        return new Builder<>(localX, localY, w, h, component);
    }
    public void destroy() {
        // 先将所有子节点的父节点置空：自身所有子节点均无需remove（因为他们的父节点也会被释放）
        for (AbstractNodeWidget node : m_vecNextNodes) {
            if (node != null) {
                node.m_pParentNode = null;
            }
        }
        clearVec(m_vecNextNodes);
        // 将自身从父节点中移除
        removeFromParent();
    }


    // 变换相关
    // TODO : 改变大小时需要进行处理，比如重新设置自身位置、基类是列表也需要更新列表大小

    // 直接修改世界坐标也要同时修改本地坐标；同时，自身位置改变，子节点世界坐标需要更新
    @Override
    public void setX(int x) {
        super.setX(x);
        if (m_pParentNode != null) {
            localX = x - m_pParentNode.getX();
        }
        makeChildrenDirty();
    }
    @Override
    public void setY(int y) {
        super.setY(y);
        if (m_pParentNode != null) {
            localY = y - m_pParentNode.getY();
        }
        makeChildrenDirty();
    }
    /** 设置自身相对父节点的位置 */
    public void setLocalPosition(Vector2i vec) {
        setLocalPosition(vec.x, vec.y);
    }
    public void setLocalPosition(int x, int y) {
        this.setPosition(m_pParentNode != null ? m_pParentNode.getX() + x : x, m_pParentNode != null ? m_pParentNode.getY() + y : y);
        makeDirty();
    }
    public void setLocalX(int x) {
        this.setX(m_pParentNode != null ? m_pParentNode.getX() + x : x);
        makeDirty();
    }
    public void setLocalY(int y) {
        this.setY(m_pParentNode != null ? m_pParentNode.getY() + y : y);
        makeDirty();
    }
    private void updateAsDirty() {
        if (m_bIsTransformDirty) {
            m_bIsTransformDirty = false;
            updatePosition();
            afterUpdateDirty();
        }
    }
    private void updatePosition() {
        if (m_pParentNode == null) {
            this.setX(localX);
            this.setY(localY);
            return;
        }
        this.setPosition(m_pParentNode.getX() + localX, m_pParentNode.getY() + localY);
    }
    protected void afterUpdateDirty() {
    }
    public Vector2i getLocalPosition() {
        return new Vector2i(localX, localY);
    }
    public int getLocalX() {
        return localX;
    }
    public int getLocalY() {
        return localY;
    }

    @Override
    public int getX() {
        updateAsDirty();
        return super.getX();
    }
    @Override
    public int getY() {
        updateAsDirty();
        return super.getY();
    }
    public void makeChildrenDirty() {
        for (AbstractNodeWidget node : m_vecNextNodes) {
            node.makeDirty();
        }
    }
    public void makeDirty() {
        m_bIsTransformDirty = true;
        makeChildrenDirty();
    }


    // 节点树管理相关

    /** 获取所有子节点 */
    public List<AbstractNodeWidget> getNextNodes() {
        return m_vecNextNodes;
    }

    public AbstractNodeWidget getParentNode() {
        return m_pParentNode;
    }

    /** 设置自身层级 */
    public void setOrder(int i32Order) {
        if (m_pParentNode == null) {
            this.m_i32Order = i32Order;
            return;
        }

        int i32PastIndex = -1;
        List<AbstractNodeWidget> vecParentNextNodes = m_pParentNode.m_vecNextNodes;
        for (int i = 0; i < vecParentNextNodes.size(); i++) {
            if (vecParentNextNodes.get(i) == this) {
                i32PastIndex = i;
                break;
            }
        }

        if (i32PastIndex == -1) {
            return; // 不在父节点的子节点列表中
        }

        // 层级减小：左移
        if (this.m_i32Order > i32Order) {
            for (int i = i32PastIndex; i >= 0; i--) {
                if (i == 0 || vecParentNextNodes.get(i - 1).m_i32Order < i32Order) {
                    vecParentNextNodes.set(i, this);
                    break;
                } else {
                    vecParentNextNodes.set(i, vecParentNextNodes.get(i - 1));
                }
            }
        }
        // 层级增大、不变（相当于刷新也需要移动）：右移
        else {
            int i32VecEndIndex = vecParentNextNodes.size() - 1;
            for (int i = i32PastIndex; i <= i32VecEndIndex; i++) {
                if (i == i32VecEndIndex || vecParentNextNodes.get(i + 1).m_i32Order > i32Order) {
                    vecParentNextNodes.set(i, this);
                    break;
                } else {
                    vecParentNextNodes.set(i, vecParentNextNodes.get(i + 1));
                }
            }
        }
        this.m_i32Order = i32Order;
    }

    /** 修改自身父节点，执行之后还需要尝试更新*/
    public void setParentNode(AbstractNodeWidget pNewParent) {
        if (pNewParent == this || pNewParent == m_pParentNode) {
            return;
        }
        AbstractNodeWidget pParent = this.m_pParentNode;
        if (pParent != null) {
            pParent.eraseNode(this);
        }
        if (pNewParent != null) {
            pNewParent.addNode(this);
        }
        makeDirty();
    }

    /** 返回指定索引对象，若索引不存在则返回null */
    public AbstractNodeWidget getNode(int i32Index) {
        if (i32Index < 0 || i32Index >= m_vecNextNodes.size())
            return null;
        return m_vecNextNodes.get(i32Index);
    }
    /** 获取第一个（层级最小且最先添加的）指定名称的节点，若不存在则返回null */
    public AbstractNodeWidget getNode(Component component) {
        for (AbstractNodeWidget i : m_vecNextNodes) {
            if (i.getMessage().equals(component)) {
                return i;
            }
        }
        return null;
    }
    /** 获取所有名称为指定名称的子节点，不破坏层级及先后顺序 */
    public List<AbstractNodeWidget> getNextNodes(Component component) {
        List<AbstractNodeWidget> vecResult = new ArrayList<>();
        for (AbstractNodeWidget i : m_vecNextNodes) {
            if (i.getMessage().equals(component)) {
                vecResult.add(i);
            }
        }
        return vecResult;
    }

    /** 将子节点移入目标节点*/
    public void removeNextNodesTo(AbstractNodeWidget pNewParent) {
        for (AbstractNodeWidget i : m_vecNextNodes) {
            i.m_pParentNode = pNewParent;
            if (pNewParent != null) {
                pNewParent.addNode(i);
            }
        }
        m_vecNextNodes.clear();
    }

    /** 由于移除需要改变pTarget的父节点，使用eraseNode来控制*/
    public void eraseNode(AbstractNodeWidget pTarget) {
        if (pTarget != null) {
            m_vecNextNodes.remove(pTarget);
            pTarget.m_pParentNode = null;
        }
    }

    /** 自动根据层级添加*/
    public void addNode(AbstractNodeWidget pNextNode) {
        if (pNextNode == null)
            return;

        // 检查是否已存在
        for (AbstractNodeWidget node : m_vecNextNodes) {
            if (node == pNextNode) {
                // 不可重复添加
                return;
            }
        }

        boolean bHasAdd = false;
        for (int i = 0; i < m_vecNextNodes.size(); i++) {
            if (pNextNode.m_i32Order < m_vecNextNodes.get(i).m_i32Order) {
                m_vecNextNodes.add(i, pNextNode);
                bHasAdd = true;
                break;
            }
        }

        pNextNode.m_pParentNode = this;
        // 如果子节点为空或者加入节点最大，则尾插
        if (!bHasAdd) {
            m_vecNextNodes.add(pNextNode);
        }
    }

    /** 直接尾插：将会改变目标层级为最高层*/
    public void pushBack(AbstractNodeWidget pNextNode) {
        if (pNextNode == null)
            return;

        // 检查是否已存在
        for (AbstractNodeWidget node : m_vecNextNodes) {
            if (node == pNextNode) {
                // 不可重复添加
                return;
            }
        }

        if (!m_vecNextNodes.isEmpty()) {
            pNextNode.m_i32Order = m_vecNextNodes.getLast().m_i32Order;
        }
        m_vecNextNodes.add(pNextNode);
        pNextNode.m_pParentNode = this;
    }

    /** 将自身从父节点移除*/
    public void removeFromParent() {
        AbstractNodeWidget pParent = this.m_pParentNode;
        if (pParent != null) {
            pParent.eraseNode( this);
            makeDirty();
        }
    }

    /** 辅助方法：清空列表*/
    private void clearVec(List<?> list) {
        list.clear();
    }

    /**
     * 获得当前控件特定布局位置，从左上角到右下角，从左到右，从上到下，分别为0~8
     * <p>
     * 参数2可以自动计算边缘使得目标控件边缘和当前控件边缘重合而不超出当前控件范围（放不下还是会超的）
     * </p>
     * @param layout_idx 布局索引(0~8)
     */
    public Vector2i get_layout(int layout_idx, Vector2i target_size) {
        Vector2i size = new Vector2i(width, height);
        Vector2i ans = new Vector2i();
        ans = switch (layout_idx) {
            case 0 -> new Vector2i(0, 0);
            case 1 -> new Vector2i(size.x / 2 - target_size.x / 2, 0);
            case 2 -> new Vector2i(size.x - target_size.x, 0);
            case 3 -> new Vector2i(0, size.y / 2 - target_size.y / 2);
            case 4 -> new Vector2i(size.x / 2 - target_size.x / 2, size.y / 2 - target_size.y / 2);
            case 5 -> new Vector2i(size.x - target_size.x, size.y / 2 - target_size.y / 2);
            case 6 -> new Vector2i(0, size.y - target_size.y);
            case 7 -> new Vector2i(size.x / 2 - target_size.x / 2, size.y - target_size.y);
            case 8 -> new Vector2i(size.x - target_size.x, size.y - target_size.y);
            default -> ans;
        };
        return ans;
    }

    // 基类鼠标事件处理及渲染相关
    // Q:为什么是倒序遍历
    // A:当鼠标操作（如点击）时，应该点击到最顶层的节点，由于层级大的子节点后渲染，因此能被先点击到，自身最先渲染所以最后处理

    /**
     * 是否是允许鼠标点击按钮
     * <p>
     *     如果该控件可被点击则返回true并处理点击时间，否则将点击传递给子节点
     * </p>
     * @return 是否可被点击
     */
    protected boolean canClick() {
        return false;
    }
//    @Override
//    protected boolean isValidClickButton(int i) {
//        return i == 1;
//    }
    @Override
    public boolean mouseClicked(double d, double e, int i) {
        if (this.active && this.visible) {
            // 倒序遍历子节点，层级最大的节点在最表层因此应该最先处理其点击事件
            for (int j = m_vecNextNodes.size() - 1; j >= 0; --j) {
                boolean bl = m_vecNextNodes.get(j).mouseClicked(d, e, i);
                if (bl)
                    return true;
            }
            if (this.isValidClickButton(i) && canClick()) {
                boolean bl = this.clicked(d, e);
                if (bl) {
                    this.onClick(d, e);
                    return true;
                }
            }
        }
        return false;
    }
    protected boolean canRelease() {
        return false;
    }
    @Override
    public boolean mouseReleased(double d, double e, int i) {
        for (int j = m_vecNextNodes.size() - 1; j >= 0; --j) {
            boolean bl = m_vecNextNodes.get(j).mouseReleased(d, e, i);
            if (bl)
                return true;
        }
        if (this.isValidClickButton(i) && canRelease()) {
            this.onRelease(d, e);
            return true;
        }
        return false;
    }
    protected boolean canDrag(double d, double e, int i, double f, double g) {
        return false;
    }
    @Override
    public boolean mouseDragged(double d, double e, int i, double f, double g) {
        for (int j = m_vecNextNodes.size() - 1; j >= 0; --j) {
            boolean bl = m_vecNextNodes.get(j).mouseDragged(d, e, i, f, g);
            if (bl)
                return true;
        }
        if (this.isValidClickButton(i) && canDrag(d, e, i, f, g)) {
            this.onDrag(d, e, f, g);
            return true;
        }
        return false;
    }
    protected boolean canScroll(double d, double e, double f, double g) {
        return false;
    }
    protected boolean onScroll(double d, double e, double f, double g) {
        return false;
    }
    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        for (int j = m_vecNextNodes.size() - 1; j >= 0; --j) {
            boolean bl = m_vecNextNodes.get(j).mouseScrolled(d, e, f, g);
            if (bl)
                return true;
        }
        if (canScroll(d, e, f, g)) {
            return onScroll(d, e, f, g);
        }
        return false;
    }
    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
//        Minecraft minecraft = Minecraft.getInstance();
//        if (minecraft.player != null) {
//            minecraft.player.sendSystemMessage(Component.literal("Cur XY : " + getX() + ", " + getY() +
//                    " curWH : " + width + ", " + height));
//        }
        renderBg(guiGraphics, i, j, f);
        renderSelf(guiGraphics, i, j, f);
        renderChildren(guiGraphics, i, j, f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
    protected void renderBg(GuiGraphics guiGraphics, int i, int j, float f) {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        if (m_bIsFillBg)
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, BG_FILL_COLOR.getRGB());
        if (m_bIsLineBg)
            guiGraphics.renderOutline(getX(), getY(), width, height, BG_LINE_COLOR.getRGB());
    }
    protected void renderSelf(GuiGraphics guiGraphics, int i, int j, float f) {
    }
    protected void renderChildren(GuiGraphics guiGraphics, int i, int j, float f) {
        for (AbstractNodeWidget node : m_vecNextNodes) {
            guiGraphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
            node.render(guiGraphics, i, j, f);
            guiGraphics.disableScissor();
        }
    }
    /** 根据层级从小到大排序，层级越小越先渲染；同层级会根据加入顺序决定；移除当前节点会将子节点都移除*/
    protected List<AbstractNodeWidget> m_vecNextNodes;
    /** 改变层级后会调整父层级中的列表*/
    protected AbstractNodeWidget m_pParentNode;
    protected Color BG_FILL_COLOR = new Color(0x4F555555, true);
    protected Color BG_LINE_COLOR = new Color(0xFF77CCFF, true);
    /** 是否填充背景色*/
    protected boolean m_bIsFillBg;
    /** 是否绘制线框*/
    protected boolean m_bIsLineBg;
    /** 父节点及自身改变会导致世界坐标需要更新，在get时更新（虽然Transform已删除，但保留标志位）*/
    protected boolean m_bIsTransformDirty;
    /** 是否裁剪（防止子节点超出父节点范围）*/
    protected boolean m_bIsScissor;
    /** 当前渲染层级：渲染顺序，先渲染当前节点，然后渲染子节点，子节点排序根据层级由小到大排序，因此先渲染层级小的，默认0级*/
    private int m_i32Order;
    private int localX;
    private int localY;
}
