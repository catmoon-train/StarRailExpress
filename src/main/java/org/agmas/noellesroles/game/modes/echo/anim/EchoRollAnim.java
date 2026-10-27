package org.agmas.noellesroles.game.modes.echo.anim;

/** 翻滚表现曲线：完整一周，起落收身，不旋转第一人称镜头。 */
public final class EchoRollAnim {
    private EchoRollAnim() { }
    public static float curl(float progress) { return (float)Math.sin(Math.PI * clamp(progress)); }
    public static float turn(float progress) { float p=clamp(progress); return 360*p*p*(3-2*p); }
    private static float clamp(float value) { return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0; }
}
