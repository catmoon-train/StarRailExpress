package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;

/**
 * 受伤与倒地的呼吸/咳嗽/心跳；全部复用原版音效，不新增 ogg 资源。
 * 只在本地播放，不向服务器发任何包。
 */
public final class EchoAtmosphereClient {
    private static int tick;
    private static EchoLifeState last;
    private EchoAtmosphereClient() { }

    public static void initialize() {
        EchoInterceptorSkin.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var data = EchoBagClient.data();
            EchoLifeState life = data == null || data.matchId() == null ? null : data.life();
            if (life != last) { last = life; tick = 0; }
            if (life == null || client.player == null || client.isPaused()) return;
            tick++;
            long bleed = data.bleedRemaining();
            switch (life) {
                case DOWNED -> {
                    int heartbeat = bleed > 200 ? 40 : 20;
                    if (tick % heartbeat == 0) play(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.55F);
                    if (tick % 60 == 0) play(SoundEvents.PLAYER_BREATH, 0.7F, 0.45F);
                }
                case WOUNDED -> {
                    // 原版没有咳嗽音效；熄灭火焰的高音短促气声最接近一声干咳。
                    if (tick % 160 == 0) play(SoundEvents.GENERIC_EXTINGUISH_FIRE, 1.45F, 0.30F);
                    if (tick % 120 == 0) play(SoundEvents.PLAYER_BREATH, 0.9F, 0.30F);
                }
                default -> { }
            }
        });
    }

    /** 相机局部轻颤：只消费服务端心跳授权，菜单/暂停/旁观/结束立即归零。 */
    public static float cameraPressure() {
        var client = Minecraft.getInstance();
        var data = EchoBagClient.data();
        if (client.level == null || client.player == null || client.screen != null || client.isPaused()
                || io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(client.level).isTimeFrozen()
                || client.getCameraEntity() != client.player || client.player.isSpectator() || client.player.isCreative()
                || !client.player.isAlive() || data == null || !data.life().standing()
                || data.faction() == org.agmas.noellesroles.game.modes.echo.state.EchoFaction.INTERCEPTOR
                || !EchoInterceptorSkin.currentMatch(data.matchId())) return 0F;
        return EchoInterceptorSkin.pressure();
    }

    private static void play(net.minecraft.sounds.SoundEvent event, float pitch, float volume) {
        var client = Minecraft.getInstance();
        if (client.getSoundManager() == null) return;
        client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }
}
