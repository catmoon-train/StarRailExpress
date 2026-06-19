package io.wifi.starrailexpress.client.render.item;

import org.agmas.noellesroles.client.renderer.SREPlushBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.wifi.starrailexpress.client.model.entity.CustomPlayerPlushModel;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * 自定义玩家 plush 的物品渲染器：让手持 / 背包 / 掉落物也按绑定玩家的皮肤渲染同一个 plush 模型。
 * <p>
 * 配套物品模型为 {@code builtin/entity}（见 {@code custom_player_plush} 物品模型），各场景的朝向 /
 * 缩放
 * 由该模型的 {@code display} 变换负责；这里只把模型画在 {@code [0,1]^3}（与方块渲染同一套坐标变换）。
 */
public class CustomPlayerPlushItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        ResolvableProfile resolvableProfile = stack.get(DataComponents.PROFILE);
        
        RenderType renderType = SREPlushBlockEntityRenderer.getRenderType(resolvableProfile);
        

        poseStack.pushPose();
        // 与方块实体渲染器相同：把模型放进 [0,1]^3（脚在 y=0、头顶 y=1，居中），ModelPart 内部已 ÷16
        // 手持时（第一/第三人称）让脸朝向玩家：模型正面默认 +z（背对持有者），转 180° 面向玩家
        VertexConsumer vc = buffers.getBuffer(renderType);
        CustomPlayerPlushModel.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
