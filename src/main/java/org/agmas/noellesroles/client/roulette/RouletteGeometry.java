package org.agmas.noellesroles.client.roulette;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** 使用原版图集的实体立方体部件，不依赖枪械物品模型或持有者。 */
final class RouletteGeometry {
    static final int METAL = 0xFF42464B, WOOD = 0xFF79502F, GOLD = 0xFFD4AF37;
    static final int RED = 0xFFE06B65, BLUE = 0xFF5EB7D8, DARK = 0xFF211B18;
    private RouletteGeometry() {}

    static void box(PoseStack p, MultiBufferSource buffers, int light, int color,
                    double x, double y, double z, double w, double h, double d) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.withDefaultNamespace(color == WOOD ? "block/spruce_planks" : "block/white_concrete"));
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        float a = (float)x, b = (float)y, c = (float)z;
        float X = (float)(x + w), Y = (float)(y + h), Z = (float)(z + d);
        face(out, p.last(), sprite, light, color, 0, 0, -1, a,b,c, a,Y,c, X,Y,c, X,b,c);
        face(out, p.last(), sprite, light, color, 0, 0, 1, X,b,Z, X,Y,Z, a,Y,Z, a,b,Z);
        face(out, p.last(), sprite, light, color, -1, 0, 0, a,b,Z, a,Y,Z, a,Y,c, a,b,c);
        face(out, p.last(), sprite, light, color, 1, 0, 0, X,b,c, X,Y,c, X,Y,Z, X,b,Z);
        face(out, p.last(), sprite, light, color, 0, 1, 0, a,Y,c, a,Y,Z, X,Y,Z, X,Y,c);
        face(out, p.last(), sprite, light, color, 0, -1, 0, a,b,Z, a,b,c, X,b,c, X,b,Z);
    }

    private static void face(VertexConsumer out, PoseStack.Pose pose, TextureAtlasSprite s, int light, int color,
                             float nx, float ny, float nz, float... v) {
        for (int i = 0; i < 4; i++) out.addVertex(pose, v[i*3], v[i*3+1], v[i*3+2]).setColor(color)
                .setUv(i < 2 ? s.getU0() : s.getU1(), i == 0 || i == 3 ? s.getV1() : s.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    static void shell(PoseStack p, MultiBufferSource b, int light, int kind) {
        box(p,b,light,kind < 0 ? DARK : kind == 1 ? RED : BLUE, -.035,0,-.06,.07,.07,.12);
        box(p,b,light,GOLD,-.037,-.003,.034,.074,.076,.028);
    }

    static void shotgun(PoseStack p, MultiBufferSource b, int light, boolean shortBarrel, float pump) {
        double tip = shortBarrel ? -.39 : -.72;
        box(p,b,light,WOOD,-.065,-.055,.15,.13,.11,.3);             // 枪托
        box(p,b,light,DARK,-.07,-.058,.435,.14,.116,.025);          // 肩垫
        box(p,b,light,WOOD,-.04,-.08,.08,.08,.09,.12);             // 握把
        box(p,b,light,METAL,-.065,-.035,-.16,.13,.11,.34);         // 机匣
        box(p,b,light,0xFF92979D,.064,.0,-.10,.006,.045,.095);     // 抛壳窗
        box(p,b,light,METAL,-.036,.025,tip,.072,.065,-tip-.12);    // 枪管
        box(p,b,light,DARK,-.027,.034,tip-.002,.054,.047,.004);    // 黑色枪口
        box(p,b,light,METAL,-.027,-.025,tip+.07,.054,.042,-tip-.2);// 弹仓管
        box(p,b,light,0xFF999B9C,-.012,.09,tip+.04,.024,.026,.025);// 准星
        box(p,b,light,METAL,-.045,-.09,.005,.018,.07,.10);         // 扳机护圈
        box(p,b,light,METAL,.027,-.09,.005,.018,.07,.10);
        box(p,b,light,METAL,-.045,-.09,.005,.09,.015,.10);
        p.pushPose();
        p.translate(0,0,pump*.14);                               // 护木独立滑动
        box(p,b,light,WOOD,-.058,-.04,-.37,.116,.095,.19);
        for (int i=0;i<4;i++) box(p,b,light,DARK,-.06,-.042,-.35+i*.042,.12,.018,.009);
        p.popPose();
    }
}
