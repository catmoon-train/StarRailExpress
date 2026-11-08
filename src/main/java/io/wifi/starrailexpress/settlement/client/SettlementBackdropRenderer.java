package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** 默认皮肤的三层纸景摄影棚；全部是客户端合批几何，不改世界、不创建实体。 */
final class SettlementBackdropRenderer {
    private SettlementBackdropRenderer() { }
    private static final int PAPER=0xFF968E80, FLOOR=0xFF75796F, INK=0xFF282F2D, FAINT=0xFF899587;
    static final float FLOOR_Y=.025f, FRONT_Z=3.25f, BACK_Z=3.65f;
    // 舞台以方块中心为原点，±14 立柱内沿是 ±13.5；覆纸必须在内沿前方而非埋进方块。
    static final float INNER_X=13.25f, NEAR_Z=-14.25f, FAR_Z=13.8f, CEILING_Y=12.85f;
    static final float SCREEN_HALF_WIDTH=INNER_X-.18f, SCREEN_HEIGHT=3.6f;

    static void render(PoseStack pose, MultiBufferSource buffers, SettlementPresentation.Frame frame) {
        VertexConsumer v=buffers.getBuffer(SettlementPaperRenderType.PAPER);
        // 远层：盖住黑箱内壁。侧墙也覆纸，窄屏/大阵容不会从边缘露出黑石立柱。
        wall(pose,v,-INNER_X,-.02f,INNER_X,CEILING_Y,FAR_Z,PAPER);
        quad(pose,v,-INNER_X,0,NEAR_Z,-INNER_X,CEILING_Y,NEAR_Z,-INNER_X,CEILING_Y,FAR_Z,-INNER_X,0,FAR_Z,PAPER);
        quad(pose,v,INNER_X,0,FAR_Z,INNER_X,CEILING_Y,FAR_Z,INNER_X,CEILING_Y,NEAR_Z,INNER_X,0,NEAR_Z,PAPER);
        wall(pose,v,-INNER_X,0,INNER_X,CEILING_Y,NEAR_Z,PAPER);
        // 合上纸景顶盖，抬高/广角机位也不会露出黑箱顶面；不改真实灯光和碰撞。
        quad(pose,v,-INNER_X,CEILING_Y,NEAR_Z,INNER_X,CEILING_Y,NEAR_Z,INNER_X,CEILING_Y,FAR_Z,-INNER_X,CEILING_Y,FAR_Z,0xFFA09887);
        quad(pose,v,-INNER_X,FLOOR_Y,NEAR_Z,-INNER_X,FLOOR_Y,FAR_Z,INNER_X,FLOOR_Y,FAR_Z,INNER_X,FLOOR_Y,NEAR_Z,FLOOR);
        // 地面只留低对比接缝，移除贴近镜头的黑色装饰体，留出完整演员与脚下接地空间。
        for (int i=-5;i<=5;i++) {
            float x=i*2.3f;
            quad(pose,v,x,FLOOR_Y+.012f,NEAR_Z,x,FLOOR_Y+.012f,FAR_Z,x+.012f,FLOOR_Y+.012f,FAR_Z,x+.012f,FLOOR_Y+.012f,NEAR_Z,0xFF656B61);
        }
        // 固定网点只出现在布景角落；168 个小四边形共用一个缓冲，无逐帧随机粒子。
        for (int row=0;row<12;row++) for (int col=0;col<14;col++) {
            float x=-13+col*.36f+row*.12f,y=2.8f+row*.33f;
            wall(pose,v,x,y,x+.035f,y+.035f,13.76f,0xFF77786D);
        }
        // 中层：列车窗框和一条横贯的墨色腰线，与远景产生自然视差。
        wall(pose,v,-INNER_X,1.15f,INNER_X,1.19f,11.8f,0xFF646E63);
        for (int i=-3;i<=3;i++) {
            float x=i*3.35f;
            wall(pose,v,x-1.25f,1.7f,x+1.25f,4.5f,11.6f,INK);
            wall(pose,v,x-1.19f,1.76f,x+1.19f,4.44f,11.55f,FAINT);
            wall(pose,v,x-1.17f,1.78f,x+1.17f,1.84f,11.50f,0xFF6E796A);
            wall(pose,v,x-.02f,1.78f,x+.02f,4.42f,11.49f,0xFF737E70);
        }
        // 深色装饰压回后墙，只做窄边框/腰线，不在舞台中间竖起斜切巨板。
        wall(pose,v,-INNER_X,.08f,INNER_X,.18f,13.65f,0xFF6D7164);
        wall(pose,v,-12.8f,5.0f,12.8f,5.035f,11.48f,0xFF777160);
        wall(pose,v,-11.4f,5.0f,-8.4f,5.055f,11.42f,0xFF853E36);
        wall(pose,v,8.4f,5.0f,11.4f,5.055f,11.42f,0xFF853E36);
        float rise=(float)SettlementPresentation.backdropRise(frame.detail());
        if (rise+SCREEN_HEIGHT > -.05f) {
            // 固定横向列车屏风：与后墙窗景呼应，不再用跟随焦点的巨型斜切白板。
            // 保留前后层的真实深度间隔与升降入场，避免透明排序和近共面闪烁。
            wall(pose,v,-SCREEN_HALF_WIDTH-.05f,rise-.02f,SCREEN_HALF_WIDTH+.05f,rise+SCREEN_HEIGHT+.05f,BACK_Z,0xFF727A6B);
            wall(pose,v,-SCREEN_HALF_WIDTH,rise,SCREEN_HALF_WIDTH,rise+SCREEN_HEIGHT,FRONT_Z,0xFFAAA18D);
            wall(pose,v,-SCREEN_HALF_WIDTH,rise+.28f,SCREEN_HALF_WIDTH,rise+.31f,FRONT_Z-.04f,0xFF727E6E);
            wall(pose,v,-SCREEN_HALF_WIDTH,rise+3.30f,SCREEN_HALF_WIDTH,rise+3.34f,FRONT_Z-.04f,0xFF727E6E);
            for (int i=-5;i<=5;i++) {
                float x=i*2.34f;
                wall(pose,v,x-1.06f,rise+1.38f,x+1.06f,rise+3.04f,FRONT_Z-.07f,0xFF59675B);
                wall(pose,v,x-1.02f,rise+1.42f,x+1.02f,rise+3.00f,FRONT_Z-.12f,0xFF84917F);
                wall(pose,v,x-.98f,rise+2.94f,x+.98f,rise+2.97f,FRONT_Z-.16f,0xFFA6B29C);
                wall(pose,v,x-.012f,rise+1.44f,x+.012f,rise+2.96f,FRONT_Z-.18f,0xFF75836F);
            }
            wall(pose,v,-3.2f,rise+3.30f,3.2f,rise+3.35f,FRONT_Z-.09f,0xFF853E36);
        }
    }

    /** 由外至内的椭圆软影，最高一层不超过约三分之一不透明度。 */
    static void shadow(PoseStack pose, MultiBufferSource buffers, double x, double z, double height, boolean physical) {
        if (height>2.5) return;
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        float fade=(float)(1-SettlementPresentation.clamp(height/3,0,1));
        for(int layer=0;layer<5;layer++) {
            double rx=(physical?.68:.48)*(1-layer*.13),rz=(physical?.34:.26)*(1-layer*.13);
            int color=((int)(fade*(9+layer*3))<<24)|0x252D2B;
            float y=FLOOR_Y+.05f+layer*.006f;
            for(int i=0;i<16;i++) {
                double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
                quad(pose,v,(float)x,y,(float)z,(float)(x+Math.cos(a)*rx),y,(float)(z+Math.sin(a)*rz),
                        (float)(x+Math.cos(b)*rx),y,(float)(z+Math.sin(b)*rz),(float)x,y,(float)z,color);
            }
        }
    }

    private static void wall(PoseStack p,VertexConsumer v,float x0,float y0,float x1,float y1,float z,int color) {
        quad(p,v,x0,y0,z,x0,y1,z,x1,y1,z,x1,y0,z,color);
    }
    private static void quad(PoseStack p,VertexConsumer v,float x0,float y0,float z0,float x1,float y1,float z1,
                             float x2,float y2,float z2,float x3,float y3,float z3,int color) {
        v.addVertex(p.last().pose(),x0,y0,z0).setColor(color);v.addVertex(p.last().pose(),x1,y1,z1).setColor(color);
        v.addVertex(p.last().pose(),x2,y2,z2).setColor(color);v.addVertex(p.last().pose(),x3,y3,z3).setColor(color);
    }
}
