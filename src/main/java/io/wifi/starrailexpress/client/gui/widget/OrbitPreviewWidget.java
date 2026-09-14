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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 可环绕的预览视口：左键拖动=旋转视角、滚轮=缩放、Shift+左键=平移、Ctrl+拖动=吸附 15°。
 *
 * <p>内容由调用方通过 {@link Content} 注入，控件本身不认识画的是文本还是方块。
 * 约定：内容拿到的 pose 保证"1 个方块 = 1 个单位、y 轴向上、内容以原点为中心"，
 * 两个界面都能直接复用自己在世界里的渲染逻辑；另外把"等价相机"朝向传过去，billboard 才能跟着视角走。
 *
 * <h2>几个刻意的实现选择</h2>
 * <ul>
 *   <li>轴线、箭头、地面网格都是**真正的 3D 长方体**，用 {@code RenderType.debugQuads()}
 *       （它显式 {@code NO_CULL}，手搓的顶点不用操心绕序；{@code RenderType.gui()} 和
 *       {@code RenderType.lines()} 都不合适：前者有背面剔除、后者带 {@code ITEM_ENTITY_TARGET}
 *       输出状态会画到别的帧缓冲）。</li>
 *   <li>它们统一画在**内容之后**，并且整体抬到环境 z=250：GUI 是正交投影且 z 越大越靠前，
 *       所以辅助线永远在最上层，不会被文字的背景板或方块盖掉（之前用 gui() 画在内容之前，
 *       同深度时"后画的赢"，结果被文字背景板切掉一半）。</li>
 *   <li>轴末端的 X/Y/Z 字母用屏幕空间绘制（先投影 3D 端点、再画 2D 文字），任何角度都读得到。</li>
 * </ul>
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
    private static final float SNAP_STEP = 15.0F;
    /** 视口里露出的方块数（越小内容越大）。 */
    private static final float BLOCKS_ACROSS = 2.9F;
    /** 轴线长度（方块）；比方块长一点，方便看出方向。 */
    private static final float AXIS_LENGTH = 1.55F;
    /** 轴线条的半厚。 */
    private static final float AXIS_RADIUS = 0.028F;
    /** 地面网格半宽与格距（方块）。 */
    private static final float GRID_HALF = 1.5F;
    private static final float GRID_STEP = 0.5F;
    private static final float GRID_RADIUS = 0.012F;
    /** 方块占据 [-0.5, 0.5]³，所以底面在 y=-0.5。 */
    private static final float GROUND_Y = -0.5F;
    /**
     * z 分层（GUI 是正交投影、z 越大越靠前）：
     * 地面网格与坐标轴放在 {@value #GIZMO_Z}，内容放在 {@value #CONTENT_Z}，
     * 于是辅助线会被方块/文字正常遮挡（不再糊在内容上面），而内容又稳稳盖在辅助线之前。
     */
    private static final float GIZMO_Z = 40.0F;
    private static final float CONTENT_Z = 100.0F;
    /** 轴标签与操作提示永远最前，任何角度都读得到。 */
    private static final float LABEL_Z = 300.0F;

    private static final int COLOR_BACKDROP = 0x66000000;
    private static final int COLOR_BORDER = 0xFF5A4530;
    private static final int COLOR_GROUND = 0x33FFFFFF;
    private static final int COLOR_GROUND_EDGE = 0x77FFFFFF;
    private static final int COLOR_AXIS_X = 0xFFFF5555;
    private static final int COLOR_AXIS_Y = 0xFF55FF55;
    private static final int COLOR_AXIS_Z = 0xFF5555FF;
    private static final int COLOR_HINT = 0xFF9E8B6E;

    /** 长方体 6 个面，用角点索引（bit0=x, bit1=y, bit2=z）表示；无剔面所以只要求围成环。 */
    private static final int[][] BOX_FACES = {
            { 0, 2, 6, 4 },
            { 1, 3, 7, 5 },
            { 0, 1, 5, 4 },
            { 2, 3, 7, 6 },
            { 0, 2, 3, 1 },
            { 4, 6, 7, 5 },
    };

    private final Content content;

    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = 1.0F;
    private float panX;
    private float panY;
    private boolean dragging;

    private final Vector4f scratchPoint = new Vector4f();
    private final Vector3f projected = new Vector3f();
    private final Quaternionf scratchPitch = new Quaternionf();
    private final Quaternionf scratchYaw = new Quaternionf();

    public OrbitPreviewWidget(int x, int y, int width, int height, Content content) {
        super(x, y, width, height, Component.empty());
        this.content = content;
    }

    /** 回到默认视角。 */
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

        // 2D 图元（轴标签、操作提示）用的环境矩阵：绕开旋转过的 3D pose
        Matrix4f ambient = new Matrix4f(graphics.pose().last().pose());

        graphics.enableScissor(left, top, right, bottom);

        float pixelsPerBlock = Math.min(this.getWidth(), this.getHeight()) / BLOCKS_ACROSS * this.zoom;
        float centerX = left + this.getWidth() / 2.0F + this.panX;
        float centerY = top + this.getHeight() / 2.0F + this.panY;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, CONTENT_Z);
        // y 取负：内容在自己的空间里 y 轴向上，GUI 是 y 轴向下
        graphics.pose().scale(pixelsPerBlock, -pixelsPerBlock, 1.0F);
        graphics.pose().mulPose(this.scratchPitch.rotationX((float) Math.toRadians(-this.pitch)));
        graphics.pose().mulPose(this.scratchYaw.rotationY((float) Math.toRadians(this.yaw)));
        Matrix4f modelView = new Matrix4f(graphics.pose().last().pose());

        this.content.render(graphics, graphics.pose(), 180.0F - this.yaw, this.pitch);

        // 辅助线画在内容的后面一层：被内容遮住的部分正常遮挡，露出来的部分照样看得见
        Matrix4f gizmo = new Matrix4f().translate(0.0F, 0.0F, GIZMO_Z).mul(modelView);
        renderGround(graphics, gizmo);
        renderAxes(graphics, gizmo);

        graphics.pose().popPose();

        renderAxisLabels(graphics, modelView);
        renderHints(graphics, ambient, left, bottom);

        graphics.disableScissor();
        graphics.renderOutline(left, top, this.getWidth(), this.getHeight(), COLOR_BORDER);
    }

    /** 地面网格：每 0.5 格一条细长方条，外加方块自身那一格的亮边。 */
    private void renderGround(GuiGraphics graphics, Matrix4f matrix) {
        for (float offset = -GRID_HALF; offset <= GRID_HALF + 1.0E-4F; offset += GRID_STEP) {
            boolean highlight = Math.abs(offset) <= 1.0E-4F || Math.abs(Math.abs(offset) - GRID_HALF) <= 1.0E-4F
                    || Math.abs(Math.abs(offset) - 0.5F) <= 1.0E-4F;
            int color = highlight ? COLOR_GROUND_EDGE : COLOR_GROUND;
            bar(graphics, matrix, offset - GRID_RADIUS, GROUND_Y - GRID_RADIUS, -GRID_HALF,
                    offset + GRID_RADIUS, GROUND_Y + GRID_RADIUS, GRID_HALF, color);
            bar(graphics, matrix, -GRID_HALF, GROUND_Y - GRID_RADIUS, offset - GRID_RADIUS,
                    GRID_HALF, GROUND_Y + GRID_RADIUS, offset + GRID_RADIUS, color);
        }
    }

    /** 三条坐标轴，沿用 Minecraft 调试渲染的 X=红 / Y=绿 / Z=蓝 约定。 */
    private void renderAxes(GuiGraphics graphics, Matrix4f matrix) {
        axis(graphics, matrix, COLOR_AXIS_X, 0);
        axis(graphics, matrix, COLOR_AXIS_Y, 1);
        axis(graphics, matrix, COLOR_AXIS_Z, 2);
    }

    /** 一根轴：从原点沿 {@code axis} 方向伸出一根细长方条。 */
    private void axis(GuiGraphics graphics, Matrix4f matrix, int color, int axis) {
        float minA = -AXIS_RADIUS;
        float maxA = AXIS_RADIUS;
        switch (axis) {
            case 0 -> bar(graphics, matrix, 0.0F, minA, minA, AXIS_LENGTH, maxA, maxA, color);
            case 1 -> bar(graphics, matrix, minA, 0.0F, minA, maxA, AXIS_LENGTH, maxA, color);
            default -> bar(graphics, matrix, minA, minA, 0.0F, maxA, maxA, AXIS_LENGTH, color);
        }
    }

    /** 轴末端的 X/Y/Z 字母：屏幕空间绘制，投影 3D 端点定位。 */
    private void renderAxisLabels(GuiGraphics graphics, Matrix4f modelView) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, LABEL_Z);
        label(graphics, modelView, AXIS_LENGTH + 0.16F, 0.0F, 0.0F, "X", COLOR_AXIS_X);
        label(graphics, modelView, 0.0F, AXIS_LENGTH + 0.16F, 0.0F, "Y", COLOR_AXIS_Y);
        label(graphics, modelView, 0.0F, 0.0F, AXIS_LENGTH + 0.16F, "Z", COLOR_AXIS_Z);
        graphics.pose().popPose();
    }

    private void label(GuiGraphics graphics, Matrix4f modelView, float x, float y, float z, String text, int color) {
        project(modelView, x, y, z, this.projected);
        graphics.drawString(Minecraft.getInstance().font, text, (int) this.projected.x + 1,
                (int) this.projected.y - 3, color, true);
    }

    /** 视口底部的操作提示：画在最前一层，不会被内容挡住。 */
    private void renderHints(GuiGraphics graphics, Matrix4f ambient, int left, int bottom) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, LABEL_Z);
        drawHint(graphics, ambient, left + 3, bottom - 21, "gui.display_block.preview.controls_short");
        drawHint(graphics, ambient, left + 3, bottom - 11, "gui.display_block.preview.axes_short");
        graphics.pose().popPose();
    }

    private void drawHint(GuiGraphics graphics, Matrix4f ambient, int x, int y, String key) {
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        consumer.addVertex(ambient, x - 2, y - 1, LABEL_Z).setColor(0x88000000);
        consumer.addVertex(ambient, x - 2, y + 9, LABEL_Z).setColor(0x88000000);
        consumer.addVertex(ambient, x + this.getWidth() - 4, y + 9, LABEL_Z).setColor(0x88000000);
        consumer.addVertex(ambient, x + this.getWidth() - 4, y - 1, LABEL_Z).setColor(0x88000000);
        graphics.drawString(Minecraft.getInstance().font, Component.translatable(key), x, y, COLOR_HINT, false);
    }

    /**
     * 一个轴对齐长方体。用 {@code debugQuads()}：它显式关掉了背面剔除，
     * 所以手搓的顶点不需要逐面推导绕序。
     */
    private void bar(GuiGraphics graphics, Matrix4f matrix, float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ, int color) {
        float[] xs = { minX, maxX };
        float[] ys = { minY, maxY };
        float[] zs = { minZ, maxZ };
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.debugQuads());
        for (int[] face : BOX_FACES) {
            for (int corner : face) {
                consumer.addVertex(matrix, xs[corner & 1], ys[(corner >> 1) & 1], zs[(corner >> 2) & 1])
                        .setColor(color);
            }
        }
    }

    /** 用 pose 矩阵把 3D 点变换到屏幕坐标（GUI 是正交投影，变换结果直接就是像素）。 */
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
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            this.panX += (float) dragX;
            this.panY += (float) dragY;
            return true;
        }
        this.yaw = wrapDegrees(this.yaw - (float) dragX * 0.8F);
        this.pitch = clamp(this.pitch - (float) dragY * 0.8F, MIN_PITCH, MAX_PITCH);
        if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
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
