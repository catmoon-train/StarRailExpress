package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.state.EchoPhase;
import org.agmas.noellesroles.game.modes.echo.visual.EchoVisualPayload;
import org.agmas.noellesroles.game.modes.echo.visual.EchoVisualState;
import java.util.UUID;

/**
 * 狂暴外观只读公共视觉快照，不读取其它玩家仅本人同步的 RoleData。
 * 复用现有皮肤/手臂入口，且只在 PlayerRenderer.scale 乘一次矩阵，不修改碰撞箱、眼高或属性。
 * 公共快照不覆盖 Morph 状态本身，因此撤销狂暴外观后自然恢复原有变形。
 */
public final class EchoInterceptorSkin {
    public static final float SCALE = EchoVisualState.INTERCEPTOR_SCALE;
    private static EchoVisualPayload snapshot;
    private static ClientLevel world;
    private static long receivedAt;
    private static boolean initialized;
    private EchoInterceptorSkin() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(EchoVisualPayload.ID, (payload, context) -> context.client().execute(() -> {
            var level = context.client().level;
            if (level == null || !level.dimension().location().equals(payload.dimension())) return;
            if (EchoVisualPayload.NONE.equals(payload.match())) { clear(); return; }
            world = level; snapshot = payload; receivedAt = level.getGameTime();
        }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (snapshot != null && !active()) clear();
        });
    }

    /** 维度/世界实例、活动模式、快照租约都必须仍有效。旁观者也能看到公共外观。 */
    public static boolean active() {
        var client = Minecraft.getInstance();
        if (snapshot == null || client.level == null || client.level != world
                || client.level.getGameTime() - receivedAt > 40) return false;
        var game = SREGameWorldComponent.KEY.get(client.level);
        if (!(game.getGameMode() instanceof EchoGameMode)
                || game.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) return false;
        var data = EchoBagClient.data();
        return data == null || data.matchId() == null || snapshot.match().equals(data.matchId())
                && data.phase() != EchoPhase.SETTLING && data.phase() != EchoPhase.FINISHED;
    }

    public static boolean currentMatch(UUID match) {
        return match != null && active() && match.equals(snapshot.match());
    }

    public static float pressure() { return active() ? snapshot.pressure() / 100F : 0F; }

    public static boolean applies(AbstractClientPlayer player) {
        return player != null && player.level() == world && player.isAlive() && !player.isSpectator()
                && active() && snapshot.interceptors().contains(player.getUUID());
    }

    /** 皮肤只覆盖渲染；即使当前另有 Morph 贴图，仍与放大判定使用同一公共身份。 */
    public static ResourceLocation texture(AbstractClientPlayer player) {
        if (!applies(player)) return null;
        boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        return ResourceLocation.fromNamespaceAndPath("wathe", "textures/entity/psycho" + (slim ? "_thin" : "") + ".png");
    }

    public static ResourceLocation firstPersonTexture() { return texture(Minecraft.getInstance().player); }

    private static void clear() { snapshot = null; world = null; receivedAt = 0; }
}
