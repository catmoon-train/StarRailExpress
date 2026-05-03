package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HudMoodRenderer {
    public static final ResourceLocation ARROW_UP = SRE.watheId("hud/arrow_up");
    public static final ResourceLocation ARROW_DOWN = SRE.watheId("hud/arrow_down");
    public static final ResourceLocation MOOD_HAPPY = SRE.watheId("hud/mood_happy");
    public static final ResourceLocation MOOD_MID = SRE.watheId("hud/mood_mid");
    public static final ResourceLocation MOOD_DEPRESSIVE = SRE.watheId("hud/mood_depressive");
    public static final ResourceLocation MOOD_KILLER = SRE.watheId("hud/mood_killer");
    public static final ResourceLocation MOOD_PSYCHO = SRE.watheId("hud/mood_psycho");
    public static final ResourceLocation MOOD_PSYCHO_HIT = SRE.watheId("hud/mood_psycho_hit");
    public static final ResourceLocation MOOD_PSYCHO_EYES = SRE.watheId("hud/mood_psycho_eyes");
    // 中立和 Vigilante 图标 (来自 noellesroles 资源包)
    public static final ResourceLocation MOOD_NEU = Noellesroles.id("hud/mood_neu");
    public static final ResourceLocation MOOD_VIG = Noellesroles.id("hud/mood_vig");
    // 小丑图标 (来自 noellesroles 资源包)
    public static final ResourceLocation MOOD_JESTER = Noellesroles.id("hud/mood_jester");
    private static final Map<SREPlayerTaskComponent.Task, TaskRenderer> renderers = new HashMap<>();
    // 预分配的列表，避免每帧创建新对象
    private static final List<SREPlayerTaskComponent.Task> toRemoveList = new ArrayList<>();
    // 预计算的颜色常量
    private static final int KILLER_BAR_COLOR = Mth.hsvToRgb(0F, 1.0F, 0.6F);
    private static final int PSYCHO_COLOR = Mth.hsvToRgb(0F, 1.0F, 0.5F);
    
    public static Random random = new Random();
    public static float arrowProgress = 1f;
    public static float moodRender = 0f;
    public static float moodOffset = 0f;
    public static float moodTextWidth = 0f;
    public static float moodAlpha = 0f;

    @Environment(EnvType.CLIENT)
    public static void renderHud(@NotNull Player player, Font textRenderer, FakeGuiGraphics context,
            DeltaTracker tickCounter) {
        SREGameWorldComponent gameWorldComponent = SREClient.gameComponent;
        if (gameWorldComponent == null || !gameWorldComponent.isRunning() || !SREClient.isPlayerAliveAndInSurvival()
                || !gameWorldComponent.getGameMode().hasMood())
            return;
        
        // 因为renderHud只在每tick执行一次，使用固定delta值（1.0f）代替partialTick
        // partialTick在逐帧渲染时是0-1的连续值，但每tick调用时会不连续
        float delta = 1.0f;
        
        SREPlayerMoodComponent component = SREPlayerMoodComponent.KEY.get(player);
        float oldMood = moodRender;
        moodRender = Mth.lerp(delta / 4, moodRender, component.getMood());
        moodAlpha = Mth.lerp(delta / 8, moodAlpha, renderers.isEmpty() ? 0f : 1f);
        
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0) {
            renderPsycho(player, textRenderer, context, psycho);
            return;
        }
        
        // 处理任务渲染器
        Map<SREPlayerTaskComponent.Task, SREPlayerTaskComponent.TrainTask> tasks = component.getTasks();
        for (var task : tasks.keySet()) {
            if (!renderers.containsKey(task)) {
                for (TaskRenderer renderer : renderers.values())
                    renderer.index++;
                renderers.put(task, new TaskRenderer());
            }
        }
        
        // 使用预分配的列表
        toRemoveList.clear();
        for (var taskType : SREPlayerTaskComponent.Task.values()) {
            TaskRenderer task = renderers.get(taskType);
            if (task != null) {
                task.present = false;
                if (task.tick(tasks.get(taskType), delta))
                    toRemoveList.add(taskType);
            }
        }
        for (int i = 0; i < toRemoveList.size(); i++) {
            renderers.remove(toRemoveList.get(i));
        }
        
        // 预计算白色常量
        int whiteColor = Mth.color(1f, 1f, 1f);
        TaskRenderer maxRenderer = null;
        for (Map.Entry<SREPlayerTaskComponent.Task, TaskRenderer> entry : renderers.entrySet()) {
            TaskRenderer renderer = entry.getValue();
            context.pose().pushPose();
            context.pose().translate(0, 10 * renderer.offset, 0);
            context.drawString(textRenderer, renderer.text, 22, 6,
                    whiteColor | ((int) (renderer.alpha * 255) << 24), false);
            context.pose().popPose();
            if (maxRenderer == null || renderer.offset > maxRenderer.offset)
                maxRenderer = renderer;
        }
        
        if (maxRenderer != null) {
            moodOffset = Mth.lerp(delta / 8, moodOffset, maxRenderer.offset);
            moodTextWidth = Mth.lerp(delta / 8, moodTextWidth, textRenderer.width(maxRenderer.text));
        }
        
        SRERole role = gameWorldComponent.getRole(player);
        if (role != null) {
            if (role.getMoodType() == SRERole.MoodType.FAKE) {
                renderKiller(textRenderer, context, role.getMoodColor(), role);
            } else if (role.getMoodType() == SRERole.MoodType.REAL) {
                renderCivilian(textRenderer, context, oldMood, role.getMoodColor(), role);
            }
        }
        arrowProgress = Mth.lerp(delta / 8, arrowProgress, 0f);
    }

    private static void renderCivilian(@NotNull Font textRenderer, @NotNull FakeGuiGraphics context, float prevMood, int color, SRERole role) {
        context.pose().pushPose();
        context.pose().translate(0, 3 * moodOffset, 0);
        
        // 根据阵营选择心情图标
        ResourceLocation mood = MOOD_HAPPY;
        if (role.isVigilanteTeam()) {
            // 警长阵营
            mood = MOOD_VIG;
        } else if (role.isNeutrals() && !role.isNeutralForKiller()) {
            // 中立阵营 (setNeutrals=true 但不是 setNeutralForKiller)
            mood = MOOD_NEU;
        }
        // 其他情况默认使用 MOOD_HAPPY
        
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
            context.blit(7, 6, 0, 10, 13, context.getDefaultGuiGraphics().sprites.getSprite(arrow), 1f, 1f, 1f,
                    (float) Math.sin(Math.abs(arrowProgress) * Math.PI));
            context.pose().popPose();
        }
        context.pose().popPose();
        context.pose().pushPose();
        context.pose().translate(0, 10 * moodOffset, 0);
        context.pose().translate(26, 8 + textRenderer.lineHeight, 0);
        context.pose().scale((moodTextWidth - 8) * moodRender, 1, 1);
        // 使用传入的 color 参数，保留原有的 alpha 计算逻辑
        int finalColor = (color & 0x00FFFFFF) | ((int) (moodAlpha * 255) << 24);
        context.fill(0, 0, 1, 1, finalColor);
        context.pose().popPose();
    }

    private static void renderKiller(@NotNull Font textRenderer, @NotNull FakeGuiGraphics context, int color, SRERole role) {
        if (moodRender < 0)
            moodRender = 0;
        
        // 根据阵营选择心情图标
        ResourceLocation moodIcon = MOOD_KILLER;
        if (role.isNeutrals() && !role.isNeutralForKiller()) {
            // 中立阵营 (setNeutrals=true 但不是 setNeutralForKiller)
            moodIcon = MOOD_NEU;
        } else if (role.isNeutrals() && role.isNeutralForKiller()) {
            // 中立阵营 (setNeutrals=true 且 setNeutralForKiller=true)
            moodIcon = MOOD_JESTER;
        }
        // 其他情况默认使用 MOOD_KILLER
        
        context.pose().pushPose();
        context.pose().translate(0, 3 * moodOffset, 0);
        context.blitSprite(moodIcon, 5, 6, 14, 17);
        context.pose().popPose();
        context.pose().pushPose();
        context.pose().translate(0, 10 * moodOffset, 0);
        context.pose().translate(26, 8 + textRenderer.lineHeight, 0);
        context.pose().scale((moodTextWidth - 8) * moodRender, 1, 1);
        // 使用传入的 color 参数，如果 color 为 0 或默认值则回退到 KILLER_BAR_COLOR，保留原有的 alpha 计算逻辑
        int baseColor = color != 0 ? color : KILLER_BAR_COLOR;
        int finalColor = (baseColor & 0x00FFFFFF) | ((int) (moodAlpha * 255) << 24);
        context.fill(0, 0, 1, 1, finalColor);
        context.pose().popPose();
    }

    private static void renderPsycho(@NotNull Player player, @NotNull Font renderer, @NotNull FakeGuiGraphics context,
            SREPlayerPsychoComponent component) {
        MutableComponent text = Component.translatable("game.psycho_mode.text").withColor(PSYCHO_COLOR);
        int width = renderer.width(text);
        
        // 使用 player.tickCount 作为种子，保持随机性但避免 System.currentTimeMillis()
        long seed = player.tickCount * 31L;
        random.setSeed(seed);

        context.pose().pushPose();
        context.pose().translate(random.nextGaussian() / 3, random.nextGaussian() / 3, 0);
        context.enableScissor(22, 6, 180, 23);
        
        // 每tick执行，直接使用tickCount，不需要加delta插值
        float value = 1 - (player.tickCount / 64f) % 1;
        int colorWithAlpha = PSYCHO_COLOR | (255 << 24);
        for (int i = -1; i <= 3; i++) {
            context.pose().pushPose();
            context.pose().translate(value * (width + 4), 6, 0);
            context.drawString(renderer, text, i * (width + 4), 0, colorWithAlpha, false);
            context.pose().popPose();
        }
        context.disableScissor();
        context.pose().popPose();

        context.pose().pushPose();
        context.pose().translate(random.nextGaussian() / 3, random.nextGaussian() / 3, 0);
        context.pose().pushPose();
        context.pose().translate(26, 8 + renderer.lineHeight, 0);
        // 每tick执行，直接使用psychoTicks，不需要减delta插值
        float duration = Math.max(1f, component.getPsychoTicks()) / GameConstants.getPsychoTimer();
        context.pose().scale(150 * duration, 1, 1);
        context.fill(0, 0, 1, 1, PSYCHO_COLOR | ((int) (0.9f * 255) << 24));
        context.pose().popPose();
        context.pose().popPose();

        context.pose().pushPose();
        context.pose().translate(random.nextGaussian() / 3, random.nextGaussian() / 3, 0);
        
        // 预获取常量，减少循环内方法调用
        int psychoArmour = GameConstants.getPsychoModeArmour();
        ResourceLocation moodSprite = component.armour == psychoArmour ? MOOD_PSYCHO : MOOD_PSYCHO_HIT;
        
        for (int i = 1; i <= 12; i++) {
            if ((player.tickCount - i) % 2 != 0)
                continue;
            
            int tick = (player.tickCount - i) * 40;
            random.setSeed(tick);
            
            float alpha = (12 - i) / 12f;
            context.pose().pushPose();
            float moodScale = 0.2f + (psychoArmour - component.armour) * 0.8f;
            float eyeScale = 0.8f;
            context.pose().translate(
                    (random.nextFloat() - random.nextFloat()) * moodScale * i,
                    (random.nextFloat() - random.nextFloat()) * moodScale * i, -i * 3);
            context.blit(5, 6, 0, 14, 17, context.getDefaultGuiGraphics().sprites.getSprite(moodSprite), 1f, 1f, 1f, alpha);
            context.pose().translate(
                    (random.nextFloat() - random.nextFloat()) * eyeScale * i,
                    (random.nextFloat() - random.nextFloat()) * eyeScale * i, 1);
            context.blit(5, 6, 0, 14, 17, context.getDefaultGuiGraphics().sprites.getSprite(MOOD_PSYCHO_EYES), 1f, 1f, 1f, alpha);
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
            this.alpha = Mth.lerp(delta / 4, this.alpha, present != null ? 1f : 0f);
            this.offset = Mth.lerp(delta / 8, this.offset, this.index);
            return this.alpha < 0.075f || (((int) (this.alpha * 255.0f) << 24) & -67108864) == 0;
        }
    }
}