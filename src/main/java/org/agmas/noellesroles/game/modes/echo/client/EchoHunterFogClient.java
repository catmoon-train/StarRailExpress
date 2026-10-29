package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;
import org.agmas.noellesroles.game.modes.echo.state.EchoPhase;

/** 追捕者视野的无状态门控；由现有 VISION_FOG 世界雾管线消费，不依赖 HUD/F1。 */
public final class EchoHunterFogClient {
    public static final float RADIUS=12F;
    private EchoHunterFogClient() { }

    /** 非本局本人视角返回 0；不向药水、地图配置或全局渲染距离写入任何持久状态。 */
    public static float radius(Camera camera) {
        var client=Minecraft.getInstance();
        if(client.player==null || client.level==null || camera.getEntity()!=client.player
                || !client.player.isAlive() || client.player.isCreative()) return 0F;
        var view=EchoHud.view();
        var data=EchoBagClient.data();
        boolean current=view!=null && EchoHud.age()<=100 && data!=null && view.match().equals(data.matchId())
                && client.level.dimension().location().equals(view.dimension())
                && SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode
                && view.state().phase()!=EchoPhase.SETTLING && view.state().phase()!=EchoPhase.FINISHED;
        return applies(current,client.player.isSpectator(),data==null?null:data.faction(),data==null?null:data.life())?RADIUS:0F;
    }

    /** 纯门控供测试验证阵营与离场边界；旁观者绝不继承被观察玩家的视野。 */
    public static boolean applies(boolean currentMatch,boolean spectator,EchoFaction faction,EchoLifeState life) {
        return currentMatch && !spectator && faction==EchoFaction.INTERCEPTOR && life!=null && life.standing();
    }

    /** 更强的既有视野迷雾保留优先级，更弱的效果不能放宽 12 格上限。 */
    public static float visionEnd(float radius,float effectEnd) { return Math.min(radius,effectEnd); }
}
