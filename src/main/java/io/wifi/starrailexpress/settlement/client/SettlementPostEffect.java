package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;

/** 独立后处理链：不触碰 GameRenderer.postEffect，也不装卸 Iris 管线。 */
public final class SettlementPostEffect {
    private static PostChain chain;
    private static boolean failed;
    private static int width, height;
    private SettlementPostEffect() { }

    static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return SRE.id("settlement_filter"); }
            @Override public void onResourceManagerReload(ResourceManager manager) {
                Runnable reload = () -> { close(); failed = false; SettlementStageRenderer.invalidate(); };
                if (RenderSystem.isOnRenderThread()) reload.run(); else RenderSystem.recordRenderCall(reload::run);
            }
        });
    }

    static void render(float partialTick) {
        if (!SettlementClient.isPlaying() || failed) return;
        Minecraft mc = Minecraft.getInstance();
        // 到这里 Iris/原版已完成本帧合成；仅取最终主目标，不替换用户已有 post chain。
        try {
            if (chain == null) {
                // 1.21.1 EffectInstance 将 program 名拼到 minecraft:shaders/program 下；
                // 使用私有 ResourceProvider 映射到本模组命名空间，不覆盖原版资源。
                ResourceProvider resources = id -> mc.getResourceManager().getResource(
                        id.getNamespace().equals("minecraft") && id.getPath().startsWith("shaders/program/settlement_")
                                ? SRE.id(id.getPath()) : id);
                chain = new PostChain(mc.getTextureManager(), resources, mc.getMainRenderTarget(),
                        SRE.id("shaders/post/settlement_film.json"));
                width = height = 0;
            }
            int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
            if (w != width || h != height) { chain.resize(w, h); width = w; height = h; }
            chain.setUniform("SettlementTime", (SettlementClient.tick() + partialTick) / 20f);
            chain.setUniform("SettlementBlur", (float) (1 - SettlementClient.smooth((SettlementClient.tick() + partialTick) / 45)) * 3.5f);
            chain.process(partialTick);
        } catch (Exception error) {
            close(); failed = true;
            SRE.LOGGER.warn("Settlement post effect disabled until resource reload", error);
        }
    }

    public static void close() {
        if (chain != null) { chain.close(); chain = null; }
        width = height = 0;
    }
}
