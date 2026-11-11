package org.agmas.noellesroles.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role_data.killer.MaChenXuRoleData;

/**
 * 全图里世界的本地氛围状态：只读同步字段，不推断死亡、不改变相机或操作。
 * 所有回调在客户端初始化时统一注册一次；HUD 与音效不依赖后处理成功。
 */
public final class SackGhostRealmFx {
    private static final int STAGE_3_TICKS = 600;
    private static final int STAGE_4_TICKS = 800;
    private static final Component CONDEMNED = Component.translatable("hud.noellesroles.realm.condemned");
    private static final Component RESCUE = Component.translatable("hud.noellesroles.realm.rescue");
    private static boolean registered;
    private static ClientLevel world;
    private static LocalPlayer localPlayer;
    private static boolean active;
    private static boolean condemned;
    private static int remainingTicks;
    private static int totalTicks;
    private static int sessionTicks;
    private static int cachedSeconds = -1;
    private static Component timerText = Component.empty();
    private static float strength;
    private static float previousStrength;
    private static float pressure;
    private static float previousPressure;
    private static float mark;
    private static float previousMark;
    private static int heartbeatCooldown;
    private static int whisperCooldown;
    private static SoundInstance heartbeat;
    private static SoundInstance whisper;

    private SackGhostRealmFx() {
    }

    /** 在 NoellesrolesClient 初始化中调用一次，替换旧的里世界场景/逐玩家粒子 tick。 */
    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(SackGhostRealmFx::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        CommonHudRenderCallback.EVENT.register(SackGhostRealmFx::renderHud);
        WorldRenderEvents.AFTER_ENTITIES.register(OtherworldSceneManager.INSTANCE::render);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath("noellesroles", "sack_ghost_realm");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                Runnable reload = () -> OtherworldShader.instance.reload();
                if (RenderSystem.isOnRenderThread()) reload.run();
                else RenderSystem.recordRenderCall(reload::run);
            }
        });
    }

    private static boolean playable(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && SREClient.gameComponent != null && SREClient.gameComponent.isRunning();
    }

    private static void tick(Minecraft mc) {
        if (world != mc.level || localPlayer != mc.player) {
            reset();
            world = mc.level;
            localPlayer = mc.player;
        }
        if (!playable(mc)) {
            reset();
            return;
        }
        if (mc.isPaused()) {
            stopSounds(mc);
            return;
        }
        previousStrength = strength;
        previousPressure = pressure;
        previousMark = mark;
        boolean nextActive = OtherworldShader.isAnyOtherworldActive();
        if (nextActive && !active) {
            sessionTicks = 0;
            pressure = previousPressure = 0.0F;
            mark = previousMark = 0.0F;
            heartbeatCooldown = 40;
            whisperCooldown = 12;
            cachedSeconds = -1;
            stopSounds(mc);
            OtherworldSceneManager.INSTANCE.activate();
        } else if (!nextActive && active) {
            stopSounds(mc);
            OtherworldSceneManager.INSTANCE.deactivate();
        }
        active = nextActive;
        boolean nextCondemned = active && mc.player.hasEffect(ModEffects.GHOST_CONDEMNED);
        if (condemned != nextCondemned) {
            stopSounds(mc);
            heartbeatCooldown = nextCondemned ? 8 : 40;
            whisperCooldown = nextCondemned ? 20 : 180;
        }
        condemned = nextCondemned;
        if (active) {
            sampleCountdown(mc);
            sessionTicks++;
            strength = Math.min(1.0F, strength + 1.0F / 18.0F);
            float progress = 1.0F - remainingTicks / (float) Math.max(1, totalTicks);
            pressure = Mth.lerp(0.12F, pressure, Mth.clamp(progress, 0.0F, 1.0F));
            mark = Mth.approach(mark, condemned ? 1.0F : 0.0F, 0.12F);
            int seconds = (remainingTicks + 19) / 20;
            if (seconds != cachedSeconds) {
                cachedSeconds = seconds;
                timerText = Component.translatable("hud.noellesroles.realm.timer", seconds);
            }
            tickSounds(mc);
            // 外部旧调用即使先清理了场景，也可以在同一客户端 tick 恢复。
            if (!OtherworldSceneManager.INSTANCE.isActive()) OtherworldSceneManager.INSTANCE.activate();
            OtherworldSceneManager.INSTANCE.tick();
        } else {
            strength = Math.max(0.0F, strength - 1.0F / 12.0F);
            mark = Mth.approach(mark, 0.0F, 0.15F);
            if (strength > 0.0F) sessionTicks++;
            if (strength == 0.0F && previousStrength == 0.0F) {
                pressure = previousPressure = 0.0F;
                mark = previousMark = 0.0F;
                sessionTicks = 0;
                remainingTicks = totalTicks = 0;
                OtherworldShader.instance.clearPostProcessor();
            }
        }
    }

    /** 光环在本地自然递减，避免施法者两次 RoleData 同步之间倒计时停住。 */
    private static void sampleCountdown(Minecraft mc) {
        if (SackGhostClient.hasActiveRealm()) {
            totalTicks = SackGhostClient.realmDuration();
            remainingTicks = SackGhostClient.realmRemaining();
            return;
        }
        var aura = mc.player.getEffect(ModEffects.OTHERWORLD_AURA);
        MaChenXuRoleData data = OtherworldShader.getLocalMaChenXuComponent();
        if (aura != null) {
            totalTicks = aura.getAmplifier() >= 1 ? STAGE_4_TICKS : STAGE_3_TICKS;
            if (data != null && data.otherworldActive && data.otherworldTotalTicks > 0) {
                totalTicks = data.otherworldTotalTicks;
            }
            remainingTicks = Mth.clamp(aura.getDuration(), 0, totalTicks);
        } else if (data != null && data.otherworldActive) {
            totalTicks = data.otherworldTotalTicks > 0 ? data.otherworldTotalTicks
                    : data.stage >= 4 ? STAGE_4_TICKS : STAGE_3_TICKS;
            remainingTicks = Mth.clamp(Math.min(data.otherworldDuration,
                    totalTicks - Math.max(0, data.otherworldTimer)), 0, totalTicks);
        }
    }

    private static void tickSounds(Minecraft mc) {
        if (mc.screen != null || mc.options.getSoundSourceVolume(SoundSource.AMBIENT) <= 0.0F
                || mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F) {
            stopSounds(mc);
            return;
        }
        if (--heartbeatCooldown <= 0) {
            if (heartbeat != null) mc.getSoundManager().stop(heartbeat);
            heartbeat = new RealmSound(SoundEvents.WARDEN_HEARTBEAT,
                    0.82F + pressure * 0.12F, 0.10F + pressure * 0.05F + (condemned ? 0.04F : 0.0F));
            mc.getSoundManager().play(heartbeat);
            heartbeatCooldown = 54 - (int) (pressure * 20.0F) - (condemned ? 8 : 0);
        }
        if (--whisperCooldown <= 0) {
            if (whisper != null) mc.getSoundManager().stop(whisper);
            whisper = new RealmSound(SoundEvents.SOUL_ESCAPE.value(), 0.65F,
                    0.045F + pressure * 0.015F + (condemned ? 0.015F : 0.0F));
            mc.getSoundManager().play(whisper);
            whisperCooldown = 220 - (int) (pressure * 60.0F);
        }
    }

    private static void stopSounds(Minecraft mc) {
        if (heartbeat != null) mc.getSoundManager().stop(heartbeat);
        if (whisper != null) mc.getSoundManager().stop(whisper);
        heartbeat = whisper = null;
    }

    /** 常驻小条与判死提示不检测 shader 是否成功，关闭后处理也能获知危险。 */
    private static void renderHud(FakeGuiGraphics gui, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!hasVisuals() || mc.options.hideGui) return;
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float breath = 0.5F + 0.5F * Mth.sin(time(partial) * (1.6F + pressure(partial) * 0.8F));
        float edge = strength(partial) * (0.035F + pressure(partial) * 0.025F
                + markStrength(partial) * 0.025F + breath * 0.015F);
        int edgeColor = ((int) (edge * 255.0F) << 24) | 0x36030A;
        int band = Math.max(8, height / 7);
        gui.fillGradient(0, 0, width, band, edgeColor, 0x0036030A);
        gui.fillGradient(0, height - band, width, height, 0x0036030A, edgeColor);
        int side = Math.max(6, width / 12);
        for (int i = 0; i < 4; i++) {
            int alpha = (int) (edge * 255.0F * (1.0F - i / 4.0F));
            int color = (alpha << 24) | 0x36030A;
            gui.fill(i * side / 4, 0, (i + 1) * side / 4, height, color);
            gui.fill(width - (i + 1) * side / 4, 0, width - i * side / 4, height, color);
        }
        if (!active) return;
        int center = width / 2;
        int panelWidth = Math.max(130, mc.font.width(timerText) + 16);
        if (condemned) panelWidth = Math.max(panelWidth, Math.max(mc.font.width(CONDEMNED), mc.font.width(RESCUE)) + 16);
        panelWidth = Math.min(panelWidth, width - 12);
        int left = center - panelWidth / 2;
        int top = 38;
        int bottom = top + (condemned ? 39 : 15);
        gui.fill(left, top, left + panelWidth, bottom, 0xB00C060A);
        gui.fill(left, top, left + 2, bottom, condemned ? 0xFFE35C58 : 0xFF904249);
        gui.drawCenteredString(mc.font, timerText, center, top + 3, 0xFFEAD2D2);
        int barWidth = (int) ((panelWidth - 4) * Mth.clamp(remainingTicks / (float) Math.max(1, totalTicks), 0.0F, 1.0F));
        gui.fill(left + 2, bottom - 2, left + 2 + barWidth, bottom, condemned ? 0xFFDE5A54 : 0xFF97434E);
        if (condemned) {
            gui.drawCenteredString(mc.font, CONDEMNED, center, top + 15, 0xFFFF8880);
            gui.drawCenteredString(mc.font, RESCUE, center, top + 26, 0xFFE4C6B5);
        }
    }

    static boolean hasVisuals() {
        Minecraft mc = Minecraft.getInstance();
        return world == mc.level && localPlayer == mc.player && playable(mc)
                && (strength > 0.0F || previousStrength > 0.0F);
    }

    static float strength(float partial) {
        return Mth.lerp(Mth.clamp(partial, 0.0F, 1.0F), previousStrength, strength);
    }

    static float pressure(float partial) {
        return Mth.lerp(Mth.clamp(partial, 0.0F, 1.0F), previousPressure, pressure);
    }

    static float markStrength(float partial) {
        return Mth.lerp(Mth.clamp(partial, 0.0F, 1.0F), previousMark, mark);
    }

    static float time(float partial) {
        return (Math.max(0, sessionTicks - 1) + Mth.clamp(partial, 0.0F, 1.0F)) / 20.0F;
    }

    static float entry(float partial) {
        return active ? 1.0F - Mth.clamp((sessionTicks + partial) / 32.0F, 0.0F, 1.0F) : 0.0F;
    }

    /** 立即清理所有本地会话状态；不用等待玩家、世界或连接继续存在。 */
    public static void reset() {
        stopSounds(Minecraft.getInstance());
        active = condemned = false;
        world = null;
        localPlayer = null;
        remainingTicks = totalTicks = sessionTicks = 0;
        cachedSeconds = -1;
        timerText = Component.empty();
        strength = previousStrength = pressure = previousPressure = mark = previousMark = 0.0F;
        heartbeatCooldown = whisperCooldown = 0;
        OtherworldSceneManager.INSTANCE.reset();
        OtherworldShader.instance.clearPostProcessor();
    }

    /** 使用环境音分类与相对声源，低音量、非循环，最多各保留一个心跳和低语实例。 */
    private static final class RealmSound extends AbstractSoundInstance {
        private RealmSound(SoundEvent event, float soundPitch, float soundVolume) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            pitch = soundPitch;
            volume = soundVolume;
            relative = true;
            looping = false;
            attenuation = Attenuation.NONE;
        }
    }
}
