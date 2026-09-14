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

package io.wifi.starrailexpress.client.gui.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 可环绕的预览视口：左键拖动=旋转视角、滚轮=缩放、Shift+左键=平移。
 *
 * <p>内容由调用方通过 {@link Content} 注入，控件本身不认识画的是文本还是方块。
 * 约定：内容拿到的 pose 已经保证"1 个方块 = 1 个单位、y 轴向上"，所以两个界面都能直接
 * 复用自己的世界渲染逻辑；相机信息以 yaw/pitch 传给内容，billboard 才能跟着视角走。
 *
 * <p>轴线与地面网格是自己把 3D 端点投影到屏幕坐标后画 2D 细四边形，
 * 刻意不用 {@code RenderType.lines()}：那个渲染类型带 {@code ITEM_ENTITY_TARGET} 输出状态，
 * 在 GUI pass 里会画到别的帧缓冲上去。深度排序靠 GUI 的正交投影（z 越大越靠前）：
 * 网格/轴线 z=0、内容在 150、轴标签放到 200，所以不受绘制顺序影响。
 */
public class OrbitPreviewWidget extends AbstractWidget {

    /** 内容渲染器；{@code cameraYaw/cameraPitch} 是"等价相机"朝向，用于 billboard。 */
    @FunctionalInterface
    public interface Content {
        void render(GuiGraphics graphics, PoseStack poseStack, float cameraYaw, float cameraPitch);
    }

    private static final float DEFAULT_YAW = -35.0F;
    private static final float DEFAULT_PITCH = 20.0F;
    private static final float MIN_PITCH = -89.0F;
    private static final float MAX_PITCH = 89.0F;
    private static final float MIN_ZOOM = 0.3F;
    private static final float MAX_ZOOM = 6.0F;
    /** 按住 Shift 时旋转吸附的步长（度）。 */
    private static final float SNAP_STEP = 15.0F;
    /** 视口里露出的方块数（越小内容越大）。 */
    private static final float BLOCKS_ACROSS = 2.8F;
    /** 轴线长度（方块）；比一个方块长一点，方便看出方向。 */
    private static final float AXIS_LENGTH = 1.7F;
    /** 地面网格半宽（方块）。 */
    private static final float GRID_HALF = 1.5F;
    /** 地面所在高度：方块占据 [-0.5, 0.5]³，所以底面在 y=-0.5。 */
    private static final float GROUND_Y = -0.5F;
    private static final float GRID_STEP = 0.5F;

    private static final int COLOR_BACKDROP = 0x66000000;
    private static final int COLOR_BORDER = 0xFF5A4530;
    private static final int COLOR_GROUND = 0x40FFFFFF;
    private static final int COLOR_GROUND_EDGE = 0x80FFFFFF;
    private static final int COLOR_AXIS_X = 0xFFFF5555;
    private static final int COLOR_AXIS_Y = 0xFF55FF55;
    private static final int COLOR_AXIS_Z = 0xFF5555FF;
    /** GUI 里画 3D 内容的基准 z（原版物品模型就在 150）。 */
    private static final float CONTENT_Z = 150.0F;
    /** 轴标签要比内容更靠前，才不会被内容盖住。 */
    private static final float LABEL_Z = 200.0F;

    private final Content content;

    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = 1.0F;
    private float panX;
    private float panY;
    private boolean dragging;

    private final Vector4f scratchPoint = new Vector4f();
    private final Vector3f projectedA = new Vector3f();
    private final Vector3f projectedB = new Vector3f();
    private final Quaternionf scratchPitch = new Quaternionf();
    private final Quaternionf scratchYaw = new Quaternionf();

    public OrbitPreviewWidget(int x, int y, int width, int height, Content content) {
        super(x, y, width, height, Component.empty());
        this.content = content;
    }

    /** 回到默认视角（界面每次打开都是默认值，这里给个手动复位入口）。 */
    public void resetView() {
        this.yaw = DEFAULT_YAW;
        this.pitch = DEFAULT_PITCH;
        this.zoom = 1.0F;
        this.panX = 0.0F;
        this.panY = 0.0F;
    }

    // ───────────────────────── 渲染 ─────────────────────────

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.getX();
        int top = this.getY();
        int right = left + this.getWidth();
        int bottom = top + this.getHeight();

        graphics.fill(left, top, right, bottom, COLOR_BACKDROP);

        // 投影用的环境矩阵：2D 图元（网格/轴线/标签）都用它，绕开旋转过的 3D pose
        Matrix4f ambient = new Matrix4f(graphics.pose().last().pose());

        graphics.enableScissor(left, top, right, bottom);

        float pixelsPerBlock = Math.min(this.getWidth(), this.getHeight()) / BLOCKS_ACROSS * this.zoom;
        float centerX = left + this.getWidth() / 2.0F + this.panX;
        float centerY = top + this.getHeight() / 2.0F + this.panY;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        // y 取负：内容在自己的空间里是 y 轴向上，GUI 是 y 轴向下
        graphics.pose().scale(pixelsPerBlock, -pixelsPerBlock, 1.0F);
        graphics.pose().mulPose(this.scratchPitch.rotationX((float) Math.toRadians(-this.pitch)));
        graphics.pose().mulPose(this.scratchYaw.rotationY((float) Math.toRadians(this.yaw)));
        Matrix4f modelView = new Matrix4f(graphics.pose().last().pose());

        // 先画地面，再画内容：从上方看时方块会正常遮住地面
        renderGround(graphics, ambient, modelView);
        renderAxes(graphics, ambient, modelView);

        this.content.render(graphics, graphics.pose(), 180.0F - this.yaw, this.pitch);

        graphics.pose().popPose();

        renderAxisLabels(graphics, modelView);

        graphics.disableScissor();
        graphics.renderOutline(left, top, this.getWidth(), this.getHeight(), COLOR_BORDER);
    }

    /** 地面网格：每 0.5 格一条线，外加方块底面那一个格子的亮边。 */
    private void renderGround(GuiGraphics graphics, Matrix4f ambient, Matrix4f modelView) {
        for (float offset = -GRID_HALF; offset <= GRID_HALF + 1.0E-4F; offset += GRID_STEP) {
            boolean edge = Math.abs(offset) <= 1.0E-4F || Math.abs(Math.abs(offset) - GRID_HALF) <= 1.0E-4F;
            int color = edge ? COLOR_GROUND_EDGE
                    : (Math.abs(Math.abs(offset) - 0.5F) <= 1.0E-4F ? COLOR_GROUND_EDGE : COLOR_GROUND);
            line(graphics, ambient, modelView, offset, GROUND_Y, -GRID_HALF, offset, GROUND_Y, GRID_HALF, 1.0F, color);
            line(graphics, ambient, modelView, -GRID_HALF, GROUND_Y, offset, GRID_HALF, GROUND_Y, offset, 1.0F, color);
        }
        // 方块本身占的那一格描一圈
        for (float[] segment : UNIT_BOX_BOTTOM) {
            line(graphics, ambient, modelView, segment[0], GROUND_Y, segment[1], segment[2], GROUND_Y, segment[3],
                    1.0F, COLOR_GROUND_EDGE);
        }
    }

    private static final float[][] UNIT_BOX_BOTTOM = {
            { -0.5F, -0.5F, 0.5F, -0.5F },
            { 0.5F, -0.5F, 0.5F, 0.5F },
            { 0.5F, 0.5F, -0.5F, 0.5F },
            { -0.5F, 0.5F, -0.5F, -0.5F },
    };

    /** 三条坐标轴，沿用 Minecraft 调试渲染的 X=红 / Y=绿 / Z=蓝 约定。 */
    private void renderAxes(GuiGraphics graphics, Matrix4f ambient, Matrix4f modelView) {
        line(graphics, ambient, modelView, 0.0F, 0.0F, 0.0F, AXIS_LENGTH, 0.0F, 0.0F, 1.5F, COLOR_AXIS_X);
        line(graphics, ambient, modelView, 0.0F, 0.0F, 0.0F, 0.0F, AXIS_LENGTH, 0.0F, 1.5F, COLOR_AXIS_Y);
        line(graphics, ambient, modelView, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, AXIS_LENGTH, 1.5F, COLOR_AXIS_Z);
    }

    /** 轴末端的 X/Y/Z 字母：画在最前面，任何角度都读得到。 */
    private void renderAxisLabels(GuiGraphics graphics, Matrix4f modelView) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, LABEL_Z);
        label(graphics, modelView, AXIS_LENGTH + 0.18F, 0.0F, 0.0F, "X", COLOR_AXIS_X);
        label(graphics, modelView, 0.0F, AXIS_LENGTH + 0.18F, 0.0F, "Y", COLOR_AXIS_Y);
        label(graphics, modelView, 0.0F, 0.0F, AXIS_LENGTH + 0.18F, "Z", COLOR_AXIS_Z);
        graphics.pose().popPose();
    }

    private void label(GuiGraphics graphics, Matrix4f modelView, float x, float y, float z, String text, int color) {
        project(modelView, x, y, z, this.projectedA);
        graphics.drawString(Minecraft.getInstance().font, text, (int) this.projectedA.x + 1,
                (int) this.projectedA.y - 3, color, true);
    }

    /**
     * 把一条 3D 线段投影到屏幕，再按 2D 细四边形画出来。
     * 颜色固定不走光照，所以不依赖 GUI pass 里的光照设置。
     */
    private void line(GuiGraphics graphics, Matrix4f ambient, Matrix4f modelView,
            float x1, float y1, float z1, float x2, float y2, float z2, float halfWidth, int color) {
        project(modelView, x1, y1, z1, this.projectedA);
        project(modelView, x2, y2, z2, this.projectedB);
        float dx = this.projectedB.x - this.projectedA.x;
        float dy = this.projectedB.y - this.projectedA.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) {
            return;
        }
        float nx = -dy / length * halfWidth;
        float ny = dx / length * halfWidth;
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        // 用环境矩阵画：坐标已经是屏幕像素了，不能再叠 3D 旋转
        consumer.addVertex(ambient, this.projectedA.x + nx, this.projectedA.y + ny, 0.0F).setColor(color);
        consumer.addVertex(ambient, this.projectedA.x - nx, this.projectedA.y - ny, 0.0F).setColor(color);
        consumer.addVertex(ambient, this.projectedB.x - nx, this.projectedB.y - ny, 0.0F).setColor(color);
        consumer.addVertex(ambient, this.projectedB.x + nx, this.projectedB.y + ny, 0.0F).setColor(color);
    }

    /** 用 pose 矩阵把 3D 点变换到屏幕坐标（GUI 是正交投影，所以变换结果直接就是像素）。 */
    private void project(Matrix4f modelView, float x, float y, float z, Vector3f out) {
        this.scratchPoint.set(x, y, z, 1.0F);
        modelView.transform(this.scratchPoint);
        out.set(this.scratchPoint.x, this.scratchPoint.y, this.scratchPoint.z);
    }

    // ───────────────────────── 交互 ─────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || button != 0 || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.dragging || button != 0) {
            return false;
        }
        if (Screen.hasShiftDown()) {
            // Shift = 平移视角（单位跟屏幕走）
            this.panX += (float) dragX;
            this.panY += (float) dragY;
            return true;
        }
        this.yaw = wrapDegrees(this.yaw - (float) dragX * 0.8F);
        this.pitch = clamp(this.pitch - (float) dragY * 0.8F, MIN_PITCH, MAX_PITCH);
        if (Screen.hasControlDown()) {
            // Ctrl = 吸附到 15° 步进，方便摆正
            this.yaw = snap(this.yaw);
            this.pitch = snap(this.pitch);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        boolean wasDragging = this.dragging;
        this.dragging = false;
        return wasDragging;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.zoom = clamp(this.zoom * (float) Math.pow(1.15D, scrollY), MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float snap(float value) {
        return Math.round(value / SNAP_STEP) * SNAP_STEP;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
