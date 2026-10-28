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

package org.agmas.noellesroles.client.renderer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.content.entity.StarProjectileEntity;
import org.joml.Matrix4f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 摘星者的星星：用 Blockbench 建的五角星模型（{@code models/entity/star.json}）渲染。
 * <p>
 * 模型由 183 个像素级小方块拼成一张实心五角星（厚度 2 单位、外接半径约 7.5），
 * 贴图 {@code textures/entity/star.png} 是纯黄色。渲染时整体缩放成实体大小并自转 + 浮动。
 * <p>
 * 只对召唤者自己（以及旁观者）可见 —— 受害者不该看到一颗悬在自己头顶的星星，
 * 但星星砸下来的粒子与音效是服务端广播的，所有人都能看到。
 */
public class StarProjectileRenderer extends EntityRenderer<StarProjectileEntity> {

    /** 模型的贴图（16×16 纯黄）。 */
    private static final ResourceLocation TEXTURE = ResourceLocation
            .fromNamespaceAndPath("noellesroles", "textures/entity/star.png");
    /** 模型 JSON 路径。 */
    private static final ResourceLocation MODEL = ResourceLocation
            .fromNamespaceAndPath("noellesroles", "models/entity/star.json");

    /** 模型单位 → 世界大小的缩放（模型半径 ≈7.5，缩放到半径 ≈0.75 格）。 */
    private static final float MODEL_SCALE = 0.1F;

    /** 模型里的方块（解析一次后缓存）。 */
    private static List<Box> boxes;

    /** 一个模型方块。 */
    private record Box(float x0, float y0, float z0, float x1, float y1, float z1) {
    }

    public StarProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.35F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public boolean shouldRender(StarProjectileEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
            double camX, double camY, double camZ) {
        if (!isVisibleToLocalPlayer(entity)) {
            return false;
        }
        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    /** 召唤者与旁观者可见；其他存活玩家看不到模型。 */
    private boolean isVisibleToLocalPlayer(StarProjectileEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (mc.player.isSpectator()) {
            return true;
        }
        return mc.player.getUUID().equals(entity.getOwnerId());
    }

    @Override
    public void render(StarProjectileEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        if (!isVisibleToLocalPlayer(entity)) {
            return;
        }
        List<Box> model = boxes();
        if (model.isEmpty()) {
            return;
        }

        matrices.pushPose();
        // 上下轻微浮动
        double bob = Math.sin((entity.tickCount + tickDelta) * 0.25D) * 0.12D;
        matrices.translate(0.0D, 0.55D + bob, 0.0D);
        // 自转（始终把星面转向镜头一点，避免薄片感）
        float spin = (entity.tickCount + tickDelta) * 8.0F;
        matrices.mulPose(Axis.YP.rotationDegrees(spin));
        matrices.mulPose(Axis.XP.rotationDegrees(-18.0F));
        matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        VertexConsumer consumer = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutout(TEXTURE));
        Matrix4f pose = matrices.last().pose();
        // 星星自带发光，用满亮度光照，夜里也能看见
        int packedLight = LightTexture.FULL_BRIGHT;
        for (Box box : model) {
            drawBox(consumer, pose, box, packedLight);
        }
        matrices.popPose();

        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    /**
     * 画出一个方块的 6 个面。
     * <p>
     * 贴图是 16×16 纯色，采样必须落在贴图内部：UV 用 (0.5, 0.5) 单点即可，
     * 之前的 0..1 会落在贴图最外圈像素边界上，容易采到邻接/边缘而发虚。
     */
    private static void drawBox(VertexConsumer c, Matrix4f m, Box b, int light) {
        float u0 = 0.5F, u1 = 0.5F, v0 = 0.5F, v1 = 0.5F;
        int alpha = 255;
        int r = 255, g = 255, bl = 255;
        int overlay = OverlayTexture.NO_OVERLAY;

        // 下 (y0)
        quad(c, m, b.x0(), b.y0(), b.z0(), b.x1(), b.y0(), b.z0(), b.x1(), b.y0(), b.z1(), b.x0(), b.y0(), b.z1(),
                u0, v0, u1, v1, r, g, bl, alpha, light, overlay, 0.0F, -1.0F, 0.0F);
        // 上 (y1)
        quad(c, m, b.x0(), b.y1(), b.z1(), b.x1(), b.y1(), b.z1(), b.x1(), b.y1(), b.z0(), b.x0(), b.y1(), b.z0(),
                u0, v0, u1, v1, r, g, bl, alpha, light, overlay, 0.0F, 1.0F, 0.0F);
        // 北 (z0)
        quad(c, m, b.x1(), b.y0(), b.z0(), b.x0(), b.y0(), b.z0(), b.x0(), b.y1(), b.z0(), b.x1(), b.y1(), b.z0(),
                u0, v0, u1, v1, r, g, bl, alpha, light, overlay, 0.0F, 0.0F, -1.0F);
        // 南 (z1)
        quad(c, m, b.x0(), b.y0(), b.z1(), b.x1(), b.y0(), b.z1(), b.x1(), b.y1(), b.z1(), b.x0(), b.y1(), b.z1(),
                u0, v0, u1, v1, r, g, bl, alpha, light, overlay, 0.0F, 0.0F, 1.0F);
        // 西 (x0)
        quad(c, m, b.x0(), b.y0(), b.z0(), b.x0(), b.y0(), b.z1(), b.x0(), b.y1(), b.z1(), b.x0(), b.y1(), b.z0(),
                u0, v0, u1, v1, r, g, bl, alpha, light, overlay, -1.0F, 0.0F, 0.0F);
        // 东 (x1)
        quad(c, m, b.x1(), b.y0(), b.z1(), b.x1(), b.y0(), b.z0(), b.x1(), b.y1(), b.z0(), b.x1(), b.y1(), b.z1(),
                u0, v0, u1, v1, r, g, bl, alpha, light, overlay, 1.0F, 0.0F, 0.0F);
    }

    private static void quad(VertexConsumer c, Matrix4f m,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1,
            int r, int g, int b, int alpha, int light, int overlay,
            float nx, float ny, float nz) {
        vertex(c, m, ax, ay, az, u1, v1, r, g, b, alpha, light, overlay, nx, ny, nz);
        vertex(c, m, bx, by, bz, u0, v1, r, g, b, alpha, light, overlay, nx, ny, nz);
        vertex(c, m, cx, cy, cz, u0, v0, r, g, b, alpha, light, overlay, nx, ny, nz);
        vertex(c, m, dx, dy, dz, u1, v0, r, g, b, alpha, light, overlay, nx, ny, nz);
    }

    private static void vertex(VertexConsumer c, Matrix4f m, float x, float y, float z,
            float u, float v, int r, int g, int b, int alpha, int light, int overlay,
            float nx, float ny, float nz) {
        c.addVertex(m, x, y, z)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }

    /**
     * 读取并缓存模型方块。模型坐标是 Blockbench 的 y 向下，这里翻转成 Minecraft 的 y 向上。
     * <p>
     * 读取失败时**不缓存空结果**并打日志，否则一次资源重载前的失败会让星星永久隐形。
     */
    private static List<Box> boxes() {
        if (boxes != null && !boxes.isEmpty()) {
            return boxes;
        }
        List<Box> list = new ArrayList<>();
        var rm = Minecraft.getInstance().getResourceManager();
        try (var stream = rm.open(MODEL)) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray elements = root.getAsJsonArray("elements");
            if (elements != null) {
                for (var element : elements) {
                    JsonObject obj = element.getAsJsonObject();
                    JsonArray from = obj.getAsJsonArray("from");
                    JsonArray to = obj.getAsJsonArray("to");
                    float fx = from.get(0).getAsFloat(), fy = from.get(1).getAsFloat(), fz = from.get(2).getAsFloat();
                    float tx = to.get(0).getAsFloat(), ty = to.get(1).getAsFloat(), tz = to.get(2).getAsFloat();
                    // Blockbench y 向下 → Minecraft y 向上：取负并交换上下界
                    list.add(new Box(fx, -ty, fz, tx, -fy, tz));
                }
            }
        } catch (Exception e) {
            // 不要把失败结果缓存下来，资源重载后还能重试；同时打日志便于排查
            org.agmas.noellesroles.Noellesroles.LOGGER.error(
                    "Failed to load star model {}: {}", MODEL, e.toString());
            return List.of();
        }
        boxes = list;
        return list;
    }

    /**
     * 实体渲染管线的贴图：必须返回星星自己的贴图。
     * <p>
     * 之前返回的是 {@link TextureAtlas#LOCATION_BLOCKS}（方块图集），与 render 里
     * {@code entityCutout(TEXTURE)} 用的贴图不一致，导致整颗星星渲染失败（全透明）。
     */
    @Override
    public ResourceLocation getTextureLocation(StarProjectileEntity entity) {
        return TEXTURE;
    }
}
