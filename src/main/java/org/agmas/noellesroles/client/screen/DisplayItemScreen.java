package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;


public class DisplayItemScreen extends AbstractPixelScreen {
    public DisplayItemScreen(ItemStack itemStack) {
        this(itemStack, null);
    }
    public DisplayItemScreen(ItemStack itemStack, Screen parent) {
        super(Component.empty());
        this.displayItem = itemStack;
        this.parent = parent;
    }
    @Override
    protected void init() {
        super.init();
        while (itemSize * (pixelSize + 1) < width) {
            ++pixelSize;
        }
    }
    @Override
    public void onClose() {
        if (parent != null && minecraft != null)
            minecraft.setScreen(parent);
        else
            super.onClose();
    }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) {
            rotationY += (float) (deltaX * 0.5f);
            rotationX -= (float) (deltaY * 0.5f);

            rotationX = Math.clamp(rotationX, -180f, 360f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmound, double verticalAmound) {
        scale *= (verticalAmound > 0) ? 1.1f : 0.9f;
        return true;
    }

    @Override
    public void render(@NonNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);

        // 设置渲染区域
        RenderSystem.enableScissor(0, 0, width, height);

        // 渲染3D物品
        render3DItem(guiGraphics, mouseX, mouseY);

        RenderSystem.disableScissor();

        // TODO : 显示控制提示

        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void render3DItem(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        PoseStack pose = guiGraphics.pose();

        pose.pushPose();
        // 移动到渲染区域中心
        pose.translate(centerX, centerY, 100);
        // 应用缩放
        pose.scale(scale, scale, scale);
        // 应用旋转
        pose.mulPose(Axis.XP.rotationDegrees(rotationX));
        pose.mulPose(Axis.YP.rotationDegrees(rotationY));

        // 设置光照（重要：让物品看起来更立体）
        setupLighting();

        // 渲染物品
        // TODO : 应用光照

        // 使用物品渲染器
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        BakedModel model = itemRenderer.getModel(displayItem, null, null, 0);

        // 渲染物品
        pose.pushPose();

        itemRenderer.render(
                displayItem,
                ItemDisplayContext.GUI,
                false,
                pose,
                // 缓冲区源
                guiGraphics.bufferSource(),
                // 光照值
                0xF000F0,
                // 覆盖层值
                OverlayTexture.NO_OVERLAY,
                model
        );

        pose.popPose();
        pose.popPose();
    }

    private void setupLighting() {
        RenderSystem.setShaderLights(
                new Vector3f(-0.2f, 1.0f, -0.3f),
                new Vector3f(-0.2f, 1.0f, -0.3f)
        );
    }

    private final Screen parent;
    private final ItemStack displayItem;
    private float rotationX = 180f;
    private float rotationY = 0f;
    private float scale = 50.0f;
    /** 显示物品像素大小 */
    private static final int itemSize = 16;
}
