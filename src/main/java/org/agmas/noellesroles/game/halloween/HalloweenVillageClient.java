package org.agmas.noellesroles.game.halloween;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.agmas.noellesroles.game.halloween.village.VillageGhostAudio;
import org.agmas.noellesroles.game.halloween.village.VillagePackets;
import org.agmas.noellesroles.game.halloween.village.client.VillageNpcRenderer;
import org.agmas.noellesroles.game.halloween.village.client.VillageShopScreen;

/** 万圣村客户端商店；亡魂夜的暗闪、鬼脸和鬼声授权已移除。 */
@Environment(EnvType.CLIENT)
public final class HalloweenVillageClient {
    private static boolean registered;
    private HalloweenVillageClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        EntityRendererRegistry.register(HalloweenVillage.NPC, VillageNpcRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(VillagePackets.Open.ID, (packet, context) -> context.client().execute(() -> {
            if (context.client().screen == null || context.client().screen instanceof VillageShopScreen) {
                context.client().setScreen(new VillageShopScreen(packet));
            }
        }));
        // 保留旧包的接收兼容性，但不再激活任何亡魂夜视觉或音频效果。
        ClientPlayNetworking.registerGlobalReceiver(VillagePackets.Night.ID, (packet, context) -> context.client().execute(
                VillageGhostAudio::clear));
    }

    /** 保留旧 lightmap mixin 的调用签名，光照始终原样返回。 */
    public static int shadeLightmap(int color) { return color; }
}
