package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** 随头部骨骼佩戴的小型立体金冠；色块分面提供明暗，死亡剪影不会染黑皇冠。 */
final class SettlementCrown {
    private SettlementCrown() { }
    private static final int[][] FACES = {
            {0,4,6,2}, {1,3,7,5}, {0,1,5,4}, {2,6,7,3}, {0,2,3,1}, {4,5,7,6}
    };
    private static final int[] GOLD = {0xFF9D6B1F,0xFFE9BB4F,0xFF80551C,0xFFFFE6A1,0xFFD4AF37,0xFFBE8B2D};

    static void render(PoseStack pose, MultiBufferSource buffers, ModelPart head) {
        pose.pushPose();
        head.translateAndRotate(pose);
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        // 玩家模型坐标以 1/16 方块为一像素，头顶在 y=-8 像素。
        pose.scale(1/16f,1/16f,1/16f);
        box(pose,vertices,-4.6f,-8.65f,-4.6f,4.6f,-7.5f,-3.95f,GOLD);
        box(pose,vertices,-4.6f,-8.65f,3.95f,4.6f,-7.5f,4.6f,GOLD);
        box(pose,vertices,-4.6f,-8.65f,-3.95f,-3.95f,-7.5f,3.95f,GOLD);
        box(pose,vertices,3.95f,-8.65f,-3.95f,4.6f,-7.5f,3.95f,GOLD);
        for (int i=-1;i<=1;i++) {
            float x=i*3.65f;
            tooth(pose,vertices,x,-4.3f,i==0?3.1f:2.35f);
            tooth(pose,vertices,x,4.3f,i==0?2.4f:1.9f);
        }
        tooth(pose,vertices,-4.3f,0,2.25f);
        tooth(pose,vertices,4.3f,0,2.25f);
        int[] ruby={0xFF6B1322,0xFFBD3047,0xFF681021,0xFFFF8192,0xFFE14D60,0xFF9E253C};
        box(pose,vertices,-.65f,-9.3f,-4.95f,.65f,-8.0f,-4.55f,ruby);
        pose.popPose();
    }

    private static void tooth(PoseStack p, VertexConsumer v,float x,float z,float height) {
        box(p,v,x-.5f,-8.5f-height,z-.36f,x+.5f,-8.4f,z+.36f,GOLD);
        box(p,v,x-.25f,-8.9f-height,z-.25f,x+.25f,-8.5f-height,z+.25f,GOLD);
    }

    private static void box(PoseStack pose,VertexConsumer v,float x0,float y0,float z0,
                            float x1,float y1,float z1,int[] colors) {
        for(int face=0;face<FACES.length;face++) for(int corner:FACES[face])
            v.addVertex(pose.last().pose(),(corner&1)==0?x0:x1,(corner&2)==0?y0:y1,(corner&4)==0?z0:z1)
                    .setColor(colors[face]);
    }
}
