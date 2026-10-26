package org.agmas.noellesroles.game.modes.echo.anim;

import net.minecraft.resources.ResourceLocation;

/** 回声攻击特效用到的自定义粒子 id（服务端只发 id，形状由客户端画）。 */
public final class EchoHuntFxIds {
    /** 信标占位圈：客户端沿半径铺一圈粒子，标示需要站立的范围。 */
    public static final ResourceLocation BEACON_RING = id("beacon_ring");
    private EchoHuntFxIds() { }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("noellesroles", path);
    }

    /** 出击刀光：沿挥砍平面的一段弧形。 */
    public static final ResourceLocation SLASH = id("echo_hunt_slash");
    /** 命中血花：从命中点向外飞散的血珠与血雾。 */
    public static final ResourceLocation BLOOD = id("echo_hunt_blood");
    /** 扑击的地面冲击环。 */
    public static final ResourceLocation IMPACT = id("echo_hunt_impact");
    /** 擦刀时从刀身甩下的血线。 */
    public static final ResourceLocation WIPE = id("echo_hunt_wipe");
    /** 裂爪连段的爪痕：第二、三段在刀光之上再叠一道三道并行抓痕（参数带段数）。 */
    public static final ResourceLocation REND_CLAW = id("echo_rend_claw");
    /** 织网出手：掌心甩出的一束蛛丝。 */
    public static final ResourceLocation WEB_CAST = id("echo_web_cast");
    /** 蛛丝落点／命中：贴地铺开的一小片黏网。 */
    public static final ResourceLocation WEB_SPLAT = id("echo_web_splat");
    /** 蛛网阵：以参数给出半径的一圈缓慢扩张的蛛丝环。 */
    public static final ResourceLocation WEB_FIELD = id("echo_web_field");
}