package io.wifi.srepackcompat.cobblemon;

import io.wifi.srepackcompat.mixin.cobblemon.KeyMappingAccess;
import io.wifi.srepackcompat.mixin.cobblemon.ScreenAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.slf4j.LoggerFactory;

/** 所有更新均来自客户端主线程的配置包/连接生命周期事件。 */
public final class CobblemonClientGate {
    public static void initialize() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> apply(false));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> apply(false));
    }

    public static void apply(boolean enabled) {
        if (CobblemonPolicy.enabled() == enabled) return;
        CobblemonPolicy.setEnabled(enabled);
        Minecraft client = Minecraft.getInstance();
        // 不改变绑定值或重新注册按键，保留 options.txt 中的玩家自定义键位。
        for (KeyMapping key : client.options.keyMappings) {
            if (!CobblemonPolicy.isKey(key.getName())) continue;
            ((KeyMappingAccess) key).sre$release();
            if (key instanceof ResettableCobblemonKey held) held.sre$resetHeldKey();
        }
        KeyMapping.resetMapping();
        if (!enabled) {
            if (client.screen != null && CobblemonPolicy.isPresentation(client.screen.getClass())) {
                if (client.screen instanceof AbstractContainerScreen<?> && client.player != null) client.player.closeContainer();
                else client.setScreen(null);
            }
            resetPresentation(client);
        }
        if (client.screen instanceof KeyBindsScreen keys) {
            keys.selectedKey = null;
            ((ScreenAccess) keys).sre$rebuildWidgets();
        }
    }

    private static void resetPresentation(Minecraft client) {
        // 仅在状态切换时反射可选目标，不建立 Cobblemon/Kotlin 编译或运行时硬依赖。
        try {
            Class<?> type = Class.forName("com.cobblemon.mod.common.client.CobblemonClient");
            Object cobblemon = type.getField("INSTANCE").get(null);
            Object scanner = type.getMethod("getPokedexUsageContext").invoke(cobblemon);
            scanner.getClass().getMethod("resetState", boolean.class).invoke(scanner, true);
            Class<?> musicType = Class.forName("com.cobblemon.mod.common.client.sound.BattleMusicController");
            Object music = musicType.getMethod("getMusic").invoke(musicType.getField("INSTANCE").get(null));
            if (music instanceof SoundInstance sound && client.getSoundManager().isActive(sound)) {
                client.getSoundManager().stop(sound);
                for (SoundSource category : new SoundSource[]{SoundSource.AMBIENT, SoundSource.MUSIC, SoundSource.RECORDS}) {
                    client.getSoundManager().getClass().getMethod("resumeSounds", ResourceLocation.class, SoundSource.class)
                            .invoke(client.getSoundManager(), null, category);
                }
            }
        } catch (ReflectiveOperationException error) {
            LoggerFactory.getLogger("sre-pack-compat/cobblemon").warn("Cannot reset Cobblemon presentation state", error);
        }
    }

    private CobblemonClientGate() { }
}
