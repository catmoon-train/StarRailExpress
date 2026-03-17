package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MoodRenderer {
    public static final ResourceLocation ARROW_UP = SRE.watheId("hud/arrow_up");
    public static final ResourceLocation ARROW_DOWN = SRE.watheId("hud/arrow_down");
    public static final ResourceLocation MOOD_HAPPY = SRE.watheId("hud/mood_happy");
    public static final ResourceLocation MOOD_MID = SRE.watheId("hud/mood_mid");
    public static final ResourceLocation MOOD_DEPRESSIVE = SRE.watheId("hud/mood_depressive");
    public static final ResourceLocation MOOD_KILLER = SRE.watheId("hud/mood_killer");
    public static final ResourceLocation MOOD_PSYCHO = SRE.watheId("hud/mood_psycho");
    public static final ResourceLocation MOOD_PSYCHO_HIT = SRE.watheId("hud/mood_psycho_hit");
    public static final ResourceLocation MOOD_PSYCHO_EYES = SRE.watheId("hud/mood_psycho_eyes");
    private static final Map<SREPlayerTaskComponent.Task, TaskRenderer> renderers = new HashMap<>();
    public static Random random = new Random();
    public static float arrowProgress = 1f;
    public static float moodRender = 0f;
    public static float moodOffset = 0f;
    public static float moodTextWidth = 0f;
    public static float moodAlpha = 0f;

    @Environment(EnvType.CLIENT)
    public static void renderHud(@NotNull Player player, Font textRenderer, GuiGraphics context,
            DeltaTracker tickCounter) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning() || !SREClient.isPlayerAliveAndInSurvival()
                || gameWorldComponent.getGameMode() != SREGameModes.MURDER)
            return;
        SREPlayerMoodComponent component = SREPlayerMoodComponent.KEY.get(player);
        float oldMood = moodRender;
        moodRender = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 8, moodRender, component.getMood());
        moodAlpha = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 16, moodAlpha,
                renderers.isEmpty() ? 0f : 1f);
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0) {
            renderPsycho(player, textRenderer, context, psycho, tickCounter);
            return;
        }
        for (var task : component.getTasks().keySet()) {
            if (!renderers.containsKey(task)) {
                for (TaskRenderer renderer : renderers.values())
                    renderer.index++;
                renderers.put(task, new TaskRenderer());
            }
        }
        ArrayList<SREPlayerTaskComponent.Task> toRemove = new ArrayList<>();
        for (var taskType : SREPlayerTaskComponent.Task.values()) {
            TaskRenderer task = renderers.get(taskType);
            if (task != null) {
                task.present = false;
                if (task.tick(component.getTasks().get(taskType), tickCounter.getGameTimeDeltaPartialTick(true)))
                    toRemove.add(taskType);
            }
        }
        for (var task : toRemove)
            renderers.remove(task);
        if (!toRemove.isEmpty()) {
            ArrayList<TaskRenderer> renderersList = new ArrayList<>(renderers.values());
            renderersList.sort((a, b) -> Float.compare(a.offset, b.offset));
            for (int i = 0; i < renderersList.size(); i++)
                renderersList.get(i).index = i;
        }
        TaskRenderer maxRenderer = null;
        for (Map.Entry<SREPlayerTaskComponent.Task, TaskRenderer> entry : renderers.entrySet()) {
            TaskRenderer renderer = entry.getValue();
            context.pose().pushPose();
            context.pose().translate(0, 10 * renderer.offset, 0);
            context.drawString(textRenderer, renderer.text, 22, 6,
                    Mth.color(1f, 1f, 1f) | ((int) (renderer.alpha * 255) << 24));
            context.pose().popPose();
            if (maxRenderer == null || renderer.offset > maxRenderer.offset)
                maxRenderer = renderer;
        }
        if (maxRenderer != null) {
            moodOffset = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 8, moodOffset, maxRenderer.offset);
            moodTextWidth = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 32, moodTextWidth,
                    textRenderer.width(maxRenderer.text));
        }
        SRERole role = gameWorldComponent.getRole(player);
        if (role != null) {
            if (role.getMoodType() == SRERole.MoodType.FAKE) {
                renderKiller(textRenderer, context);
            } else if (role.getMoodType() == SRERole.MoodType.REAL) {
                renderCivilian(textRenderer, context, oldMood);
            }
        }
        arrowProgress = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 24, arrowProgress, 0f);
    }

    private static void renderCivilian(@NotNull Font textRenderer, @NotNull GuiGraphics context, float prevMood) {
        context.pose().pushPose();
        context.pose().translate(0, 3 * moodOffset, 0);
        ResourceLocation mood = MOOD_HAPPY;
        if (moodRender < GameConstants.DEPRESSIVE_MOOD_THRESHOLD) {
            mood = MOOD_DEPRESSIVE;
        } else if (moodRender < GameConstants.MID_MOOD_THRESHOLD) {
            mood = MOOD_MID;
        }
        if (arrowProgress < 0.1f) {
            if (prevMood >= GameConstants.DEPRESSIVE_MOOD_THRESHOLD
                    && moodRender < GameConstants.DEPRESSIVE_MOOD_THRESHOLD) {
                arrowProgress = -1f;
            } else if (prevMood >= GameConstants.MID_MOOD_THRESHOLD && moodRender < GameConstants.MID_MOOD_THRESHOLD) {
                arrowProgress = -1f;
            }
        }
        if (moodRender < 0)
            moodRender = 0;
        context.blitSprite(mood, 5, 6, 14, 17);
        if (Math.abs(arrowProgress) > 0.01f) {
            boolean up = arrowProgress > 0;
            ResourceLocation arrow = up ? ARROW_UP : ARROW_DOWN;
            context.pose().pushPose();
            if (!up)
                context.pose().translate(0, 4, 0);
            context.pose().translate(0, arrowProgress * 4, 0);
            context.blit(7, 6, 0, 10, 13, context.sprites.getSprite(arrow), 1f, 1f, 1f,
                    (float) Math.sin(Math.abs(arrowProgress) * Math.PI));
            context.pose().popPose();
        }
        context.pose().popPose();
        context.pose().pushPose();
        context.pose().translate(0, 10 * moodOffset, 0);
        context.pose().translate(26, 8 + textRenderer.lineHeight, 0);
        context.pose().scale((moodTextWidth - 8) * moodRender, 1, 1);
        context.fill(0, 0, 1, 1, Mth.hsvToRgb(moodRender / 3.0F, 1.0F, 1.0F) | ((int) (moodAlpha * 255) << 24));
        context.pose().popPose();
    }

    private static void renderKiller(@NotNull Font textRenderer, @NotNull GuiGraphics context) {
        if (moodRender < 0)
            moodRender = 0;
        context.pose().pushPose();
        context.pose().translate(0, 3 * moodOffset, 0);
        context.blitSprite(MOOD_KILLER, 5, 6, 14, 17);
        context.pose().popPose();
        context.pose().pushPose();
        context.pose().translate(0, 10 * moodOffset, 0);
        context.pose().translate(26, 8 + textRenderer.lineHeight, 0);
        context.pose().scale((moodTextWidth - 8) * moodRender, 1, 1);
        context.fill(0, 0, 1, 1, Mth.hsvToRgb(0F, 1.0F, 0.6F) | ((int) (moodAlpha * 255) << 24));
        context.pose().popPose();
    }

    private static void renderPsycho(@NotNull Player player, @NotNull Font renderer, @NotNull GuiGraphics context,
            SREPlayerPsychoComponent component, @NotNull DeltaTracker tickCounter) {
        int colour = Mth.hsvToRgb(0F, 1.0F, 0.5F);
        MutableComponent text = Component.translatable("game.psycho_mode.text").withColor(colour);
        int width = renderer.width(text);
        random.setSeed(System.currentTimeMillis());

        context.pose().pushPose();
        context.pose().translate(random.nextGaussian() / 3, random.nextGaussian() / 3, 0);
        context.enableScissor(22, 6, 180, 23);
        for (int i = -1; i <= 3; i++) {
            float value = 1 - ((player.tickCount + tickCounter.getGameTimeDeltaPartialTick(true)) / 64) % 1;
            context.pose().pushPose();
            context.pose().translate(value * (width + 4), 6, 0);
            context.drawString(renderer, text, i * (width + 4), 0, colour | 255 << 24);
            context.pose().popPose();
        }
        context.disableScissor();
        context.pose().popPose();

        context.pose().pushPose();
        context.pose().translate(random.nextGaussian() / 3, random.nextGaussian() / 3, 0);
        context.pose().pushPose();
        context.pose().translate(26, 8 + renderer.lineHeight, 0);
        float duration = Math.max(1f, component.getPsychoTicks() - tickCounter.getGameTimeDeltaPartialTick(true))
                / GameConstants.getPsychoTimer();
        context.pose().scale(150 * duration, 1, 1);
        context.fill(0, 0, 1, 1, colour | ((int) (0.9f * 255) << 24));
        context.pose().popPose();
        context.pose().popPose();

        context.pose().pushPose();
        context.pose().translate(random.nextGaussian() / 3, random.nextGaussian() / 3, 0);
        for (int i = 1; i <= 12; i++) {
            int tick = (player.tickCount - i) * 40;
            if ((player.tickCount - i) % 2 != 0)
                continue;
            random.setSeed(tick);
            float alpha = (12 - i) / 12f;
            context.pose().pushPose();
            float moodScale = 0.2f + (GameConstants.getPsychoModeArmour() - component.armour) * 0.8f;
            float eyeScale = 0.8f;
            context.pose().translate(
                    (random.nextFloat() - random.nextFloat()) * moodScale * i,
                    (random.nextFloat() - random.nextFloat()) * moodScale * i, -i * 3);
            context.blit(5, 6, 0, 14, 17,
                    context.sprites.getSprite(
                            component.armour == GameConstants.getPsychoModeArmour() ? MOOD_PSYCHO : MOOD_PSYCHO_HIT),
                    1f, 1f, 1f, alpha);
            context.pose().translate(
                    (random.nextFloat() - random.nextFloat()) * eyeScale * i,
                    (random.nextFloat() - random.nextFloat()) * eyeScale * i, 1);
            context.blit(5, 6, 0, 14, 17, context.sprites.getSprite(MOOD_PSYCHO_EYES), 1f, 1f, 1f, alpha);
            context.pose().popPose();
        }
        context.pose().popPose();
    }

    public static class TaskRenderer {
        public int index = 0;
        public float offset = -1f;
        public float alpha = 0.075f;
        public boolean present = false;
        public Component text = Component.empty();

        public boolean tick(SREPlayerTaskComponent.TrainTask present, float delta) {
            if (present != null)
                this.text = Component.translatable("task." + (SREClient.isKiller() ? "fake" : "feel"))
                        .append(Component.translatable("task." + present.getName()));
            this.present = present != null;
            this.alpha = Mth.lerp(delta / 16, this.alpha, present != null ? 1f : 0f);
            this.offset = Mth.lerp(delta / 32, this.offset, this.index);
            return this.alpha < 0.075f || (((int) (this.alpha * 255.0f) << 24) & -67108864) == 0;
        }
    }
}