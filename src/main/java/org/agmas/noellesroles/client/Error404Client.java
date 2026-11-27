package org.agmas.noellesroles.client;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.client.screen.Error404CommandScreen;
import org.agmas.noellesroles.packet.Error404StateS2CPacket;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Error404 战术终端的纯客户端入口。
 *
 * <p>状态包只缓存、不重建已打开的终端，避免刷新丢掉傀儡选择。</p>
 */
@Environment(EnvType.CLIENT)
public final class Error404Client {

    private static boolean high;
    private static final List<Error404StateS2CPacket.Contact> CONTACTS = new ArrayList<>();

    private Error404Client() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Error404StateS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> apply(packet)));
        ClientPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.Error404FeedbackS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof Error404CommandScreen terminal) terminal.onFeedback(packet.message());
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                org.agmas.noellesroles.init.ModEntities.ERROR404_PUPPET,
                org.agmas.noellesroles.client.renderer.Error404PuppetRenderer::new);
        CommonHudRenderCallback.EVENT.register((graphics, delta) -> renderHud(graphics));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(Error404Client::tickPuppetAmbience);
    }

    public static boolean isHigh() {
        return high;
    }

    public static List<Error404StateS2CPacket.Contact> contacts() {
        return CONTACTS;
    }

    private static void apply(Error404StateS2CPacket packet) {
        high = packet.high();
        CONTACTS.clear();
        if (high) {
            CONTACTS.addAll(packet.players());
        }
        Minecraft client = Minecraft.getInstance();
        if (!high) {
            if (client.screen instanceof Error404CommandScreen) {
                client.setScreen(null);
            }
            return;
        }
        Screen screen = client.screen;
        if (screen instanceof Error404CommandScreen terminal) {
            terminal.onStateRefreshed();
        } else if (packet.open() && screen == null) {
            client.setScreen(new Error404CommandScreen());
        }
    }

    private static void clear() {
        high = false;
        CONTACTS.clear();
    }

    /** 终端被关掉后仍留在高维时，给一条重新打开的提示。界面开着时不画，避免和屏幕叠字。 */
    private static void renderHud(FakeGuiGraphics graphics) {
        if (!high) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || client.screen != null) {
            return;
        }
        Component hint = Component.translatable("hud.noellesroles.error404.reopen");
        int x = (graphics.guiWidth() - client.font.width(hint)) / 2;
        int y = graphics.guiHeight() - 58;
        graphics.drawString(client.font, hint, x, y, 0xFFC9A0E8, true);
    }

    /**
     * 错误傀儡的周围黑紫粒子与持续僵尸低吼。
     *
     * <p>粒子直接 {@code addParticle} 到客户端世界，不进网络；声音用
     * {@code playNotifySound} 只播给 24 格内的本地玩家。两者都由
     * {@link ClientTickEvents} 统一驱动，公共实体类不引用任何客户端类型。</p>
     */
    static void tickPuppetAmbience(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof org.agmas.noellesroles.content.entity.Error404PuppetEntity puppet)) {
                continue;
            }
            var random = puppet.getRandom();
            double x = puppet.getX() + (random.nextDouble() - 0.5D) * 0.8D;
            double y = puppet.getY() + random.nextDouble() * 1.85D;
            double z = puppet.getZ() + (random.nextDouble() - 0.5D) * 0.8D;
            client.level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y, z, 0, 0.012D, 0);
            if (random.nextInt(3) == 0) {
                client.level.addParticle(net.minecraft.core.particles.ParticleTypes.SQUID_INK, x, y, z, 0, -0.01D, 0);
            }
            if (puppet.tickCount % 70 == 0
                    && client.player.distanceToSqr(puppet) <= 24.0D * 24.0D) {
                client.player.playNotifySound(net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT,
                        net.minecraft.sounds.SoundSource.HOSTILE, 0.5F, 0.72F);
            }
        }
    }
}
