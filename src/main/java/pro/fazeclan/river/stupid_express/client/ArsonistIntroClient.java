package pro.fazeclan.river.stupid_express.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistPackets;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 纵火开场的独立舞台时钟、道具和字幕；运镜及视角恢复完全由服务端高级相机轨道负责。 */
@Environment(EnvType.CLIENT)
public final class ArsonistIntroClient {
    private static final int DURATION = 120;
    private static final int[] TITLE_TICKS = {76, 84, 92, 100};
    private static final String TITLE_KEY = "ui.stupid_express.arsonist.intro.title";
    private static final String SUBTITLE_KEY = "ui.stupid_express.arsonist.intro.subtitle";
    private static final ResourceLocation FIRE = ResourceLocation.withDefaultNamespace("block/fire_0");
    private static final SoundEvent[] IMPACTS = {SoundEvents.FLINTANDSTEEL_USE, SoundEvents.FIRECHARGE_USE,
            SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundEvents.PLAYER_ATTACK_STRONG};
    private static final List<SimpleSoundInstance> SOUNDS = new ArrayList<>(4);
    private static boolean registered;
    private static ClientLevel world;
    private static UUID owner;
    private static Vec3 start = Vec3.ZERO;
    private static Vec3 landing = Vec3.ZERO;
    private static AABB bounds;
    private static float yaw;
    private static int ticks;
    private static int sounded;
    private static int shakeAt = -100;
    private static float shakeStrength;
    private static ItemStack lighter = ItemStack.EMPTY;
    private static String translatedTitle = "";
    private static String[] titleParts = {"", "", "", ""};

    private ArsonistIntroClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(ArsonistPackets.IntroS2C.ID,
                (packet, context) -> context.client().execute(() -> receive(packet)));
        ClientTickEvents.END_CLIENT_TICK.register(ArsonistIntroClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        HudRenderCallback.EVENT.register((graphics, delta) ->
                renderHud(graphics, delta.getGameTimeDeltaPartialTick(false)));
        WorldRenderEvents.AFTER_ENTITIES.register(ArsonistIntroClient::renderLighter);
    }

    public static boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        return owner != null && ticks < DURATION && world != null && client.level == world && client.player != null;
    }

    public static float elapsedTicks(float partial) {
        return isActive() ? Mth.clamp(ticks + Mth.clamp(partial, 0, 1), 0, DURATION) : 0;
    }

    /** 供场地假火读取：近景不燃烧，24至64tick渐燃，之后保持全燃。 */
    public static float mapBurnProgress() {
        return isActive() ? smooth((ticks - 24) / 40.0F) : 0;
    }

    /** 非过场期间返回null，调用者应先检查isActive。 */
    public static AABB mapBounds() {
        return isActive() ? bounds : null;
    }

    public static float shakeYaw(float partial) {
        float age = elapsedTicks(partial) - shakeAt;
        return shakeEnvelope(age) * Mth.sin(age * 3.7F + 0.8F);
    }

    public static float shakePitch(float partial) {
        float age = elapsedTicks(partial) - shakeAt;
        return shakeEnvelope(age) * Mth.sin(age * 4.3F + 2.1F) * 0.7F;
    }

    private static float shakeEnvelope(float age) {
        if (!isActive() || age < 0 || age >= 5) return 0;
        float remaining = 1 - age / 5;
        return shakeStrength * remaining * remaining;
    }

    private static void receive(ArsonistPackets.IntroS2C packet) {
        Minecraft client = Minecraft.getInstance();
        if (!packet.active() || packet.elapsedTicks() >= DURATION || client.level == null || client.player == null) {
            clear(client);
            return;
        }
        boolean continuing = isActive() && packet.owner().equals(owner);
        if (!continuing) clear(client);
        world = client.level;
        owner = packet.owner();
        bounds = packet.mapBounds();
        yaw = packet.yaw();
        ticks = continuing ? Math.max(ticks, packet.elapsedTicks()) : Math.max(0, packet.elapsedTicks());
        if (!continuing) {
            double angle = Math.toRadians(yaw);
            Vec3 forward = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            Vec3 right = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            start = packet.position().add(0, 1.18, 0).add(forward.scale(0.35)).add(right.scale(0.3));
            landing = packet.position().add(forward.scale(2.8)).add(right.scale(0.3));
            // 只查一次已加载地面；道具不创建ItemEntity，也不加载远处区块。
            Vec3 from = landing.add(0, 2, 0);
            Vec3 to = landing.add(0, -5, 0);
            if (world.hasChunkAt(BlockPos.containing(from)) && world.hasChunkAt(BlockPos.containing(to))) {
                var hit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
                if (hit.getType() != HitResult.Type.MISS) landing = hit.getLocation();
            }
            landing = landing.add(0, 0.08, 0);
            lighter = SEItems.LIGHTER.getDefaultInstance();
        }
        // 中途加入/向前校准不补播已经错过的冲击声，也不倒放已经播放的字幕。
        for (int i = 0; i < TITLE_TICKS.length; i++) {
            if (TITLE_TICKS[i] < ticks) sounded |= 1 << i;
        }
        playCues(client);
    }

    private static void tick(Minecraft client) {
        if (owner == null) return;
        if (client.level != world || client.player == null || ++ticks >= DURATION) {
            clear(client);
            return;
        }
        // 不读比赛时钟/技能开关，服务端冻结对局时这120个客户端tick仍照常推进。
        playCues(client);
    }

    private static void playCues(Minecraft client) {
        for (int i = 0; i < TITLE_TICKS.length; i++) {
            if (ticks < TITLE_TICKS[i] || (sounded & (1 << i)) != 0) continue;
            sounded |= 1 << i;
            SimpleSoundInstance sound = SimpleSoundInstance.forUI(IMPACTS[i], 0.95F + i * 0.1F, 0.7F);
            SOUNDS.add(sound);
            client.getSoundManager().play(sound);
            shakeAt = ticks;
            shakeStrength = 0.75F + i * 0.18F;
        }
    }

    private static void clear(Minecraft client) {
        for (SimpleSoundInstance sound : SOUNDS) client.getSoundManager().stop(sound);
        SOUNDS.clear();
        world = null;
        owner = null;
        bounds = null;
        start = Vec3.ZERO;
        landing = Vec3.ZERO;
        lighter = ItemStack.EMPTY;
        ticks = 0;
        yaw = 0;
        sounded = 0;
        shakeAt = -100;
        shakeStrength = 0;
        translatedTitle = "";
        titleParts = new String[]{"", "", "", ""};
    }

    private static void renderLighter(WorldRenderContext context) {
        if (!isActive() || context.matrixStack() == null || context.consumers() == null) return;
        float time = elapsedTicks(context.tickCounter().getGameTimeDeltaPartialTick(false));
        if (time >= 64) return;
        float flight = Mth.clamp((time - 4) / 19, 0, 1);
        Vec3 at = start.lerp(landing, flight).add(0, 3.6 * flight * (1 - flight), 0);
        Vec3 camera = context.camera().getPosition();
        PoseStack poses = context.matrixStack();
        poses.pushPose();
        poses.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        poses.mulPose(Axis.YP.rotationDegrees(-yaw + 360 * flight));
        poses.mulPose(Axis.XP.rotationDegrees(-20 + 830 * flight));
        BlockPos block = BlockPos.containing(at);
        int light = world.hasChunkAt(block) ? LevelRenderer.getLightColor(world, block) : LightTexture.FULL_BRIGHT;
        // 使用与RoadRollerClient相同的无LivingEntity重载，规避空entity物品渲染mixin。
        Minecraft.getInstance().getItemRenderer().renderStatic(lighter, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, poses, context.consumers(), world, owner.hashCode());
        poses.popPose();
    }

    private static void renderHud(GuiGraphics graphics, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (!isActive() || client.options.hideGui) return;
        float time = elapsedTicks(partial);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // 不画全屏底色；顶部44px保留给主流程的全局状态栏，字幕也不占用底部快捷栏。
        int safeTop = Math.min(44, height / 4);
        graphics.enableScissor(0, safeTop, width, height);
        try {
            if (time >= 64 && time < 76) renderWipe(graphics, client, time);
            if (time >= 76) renderTitle(graphics, client.font, time, safeTop);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            graphics.disableScissor();
        }
    }

    private static void renderWipe(GuiGraphics graphics, Minecraft client, float time) {
        float cover = smooth((time - 64) / 5) * (1 - smooth((time - 72) / 4));
        TextureAtlasSprite sprite = client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FIRE);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int tileWidth = Math.max(48, width / 7);
        int tileHeight = Math.max(80, height / 2);
        graphics.setColor(1, 0.94F, 0.83F, Mth.clamp(cover * 1.6F, 0, 1));
        for (int x = -tileWidth, column = 0; x < width; x += tileWidth, column++) {
            float stagger = Mth.sin(column * 2.3F + time * 0.45F) * 24 * cover * (1 - cover);
            int top = height - Mth.ceil(height * 1.5F * cover + stagger);
            // 交叠贴图的透明火舌，覆盖阶段仍然是原版atlas中的动画火焰，而非橙色矩形。
            for (int y = top; y < height; y += tileHeight * 2 / 3) {
                graphics.blit(x, y, 0, tileWidth + 1, tileHeight, sprite);
            }
        }
        graphics.setColor(1, 1, 1, 1);
    }

    private static void renderTitle(GuiGraphics graphics, Font font, float time, int safeTop) {
        String title = Component.translatable(TITLE_KEY).getString();
        if (!title.equals(translatedTitle)) {
            translatedTitle = title;
            String[] explicitParts = title.split("\\|", -1);
            if (explicitParts.length == 4) titleParts = explicitParts;
            else {
                int[] points = title.codePoints().toArray();
                for (int i = 0; i < 4; i++) {
                    int first = points.length * i / 4;
                    titleParts[i] = new String(points, first, points.length * (i + 1) / 4 - first);
                }
            }
        }
        int totalWidth = 0;
        for (String part : titleParts) totalWidth += font.width(part);
        totalWidth += 18;
        float scale = Math.min(4, (graphics.guiWidth() - 36.0F) / Math.max(1, totalWidth));
        if (scale <= 0) return;
        float titleY = Math.max(safeTop + 18, graphics.guiHeight() * 0.43F);
        float cursor = -totalWidth / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2.0F, titleY, 0);
        graphics.pose().scale(scale, scale, 1);
        for (int i = 0; i < 4; i++) {
            int partWidth = font.width(titleParts[i]);
            float age = time - TITLE_TICKS[i];
            if (age >= 0) {
                float entrance = smooth(age / 4);
                float pop = 1 + (1 - entrance) * 0.22F;
                graphics.pose().pushPose();
                graphics.pose().translate(cursor + partWidth / 2.0F, (1 - entrance) * 8, 0);
                graphics.pose().scale(pop, pop, 1);
                renderRing(graphics, partWidth, font.lineHeight, age);
                int x = -partWidth / 2;
                graphics.drawString(font, titleParts[i], x + 2, 3, 0xE5160803, false);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx != 0 || dy != 0) graphics.drawString(font, titleParts[i], x + dx, dy,
                                dy < 0 ? 0xFFFFD36B : 0xFFE87716, false);
                    }
                }
                graphics.drawString(font, titleParts[i], x, 0, 0xFFFFF1C4, false);
                graphics.pose().popPose();
            }
            cursor += partWidth + 6;
        }
        graphics.pose().popPose();
        // 最后一字100tick出现后，完整标题一直保留到120tick，不随108tick的回镜提前淡出。
        if (time >= 100) {
            String subtitle = Component.translatable(SUBTITLE_KEY).getString();
            float subtitleScale = Math.min(1, (graphics.guiWidth() - 32.0F) / Math.max(1, font.width(subtitle)));
            int alpha = Math.round(255 * smooth((time - 100) / 4));
            if (alpha < 4 || subtitleScale <= 0) return;
            graphics.pose().pushPose();
            graphics.pose().translate(graphics.guiWidth() / 2.0F,
                    titleY + font.lineHeight * scale + 16, 0);
            graphics.pose().scale(subtitleScale, subtitleScale, 1);
            graphics.drawString(font, subtitle, -font.width(subtitle) / 2, 0, (alpha << 24) | 0xFFD397, true);
            graphics.pose().popPose();
        }
    }

    private static void renderRing(GuiGraphics graphics, int width, int height, float age) {
        if (age >= 8) return;
        float progress = age / 8;
        int color = (Math.round((1 - progress) * 180) << 24) | 0xFFCA63;
        float radiusX = width / 2.0F + 3 + progress * 12;
        float radiusY = height / 2.0F + 2 + progress * 8;
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI * 2 / 64;
            int x = Math.round((float) Math.cos(angle) * radiusX);
            int y = Math.round(height / 2.0F + (float) Math.sin(angle) * radiusY);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
