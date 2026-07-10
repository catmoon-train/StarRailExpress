package org.agmas.noellesroles.client;

import com.mojang.blaze3d.platform.Window;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Vector3f;

public final class PointerClientHandle {
    private static final double POINTER_RANGE = 96.0D;
    private static final double ENTITY_PICK_PADDING = 1.0D;
    private static final float ENTITY_PICK_MARGIN = 0.25F;
    private static final double POINTER_SPEED = 1.0D;
    private static final int POINTER_MARK_SIZE = 5;
    /** 玩家模型朝指针方向转动的每 tick 缓动系数（指数平滑，越小越平滑）。 */
    private static final float ROTATION_SMOOTHING = 0.35F;
    private static boolean active;
    private static boolean pointerInitialized;
    private static boolean hasMouseSample;
    private static double pointerX;
    private static double pointerY;
    private static double lastMouseX;
    private static double lastMouseY;
    private static PointerTarget currentTarget;

    private PointerClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PointerClientHandle::tick);
        HudRenderCallback.EVENT.register(PointerClientHandle::renderHud);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        boolean shouldBeActive = isPointerActive(client);
        if (!shouldBeActive) {
            deactivate();
            return;
        }
        if (!active) {
            active = true;
            resetPointer(client);
        }
        clampPointer(client);
        if (client.screen != null || !client.isWindowActive()) {
            hasMouseSample = false;
            return;
        }

        PointerTarget target = findPointerTarget(client, player);
        currentTarget = target;
        if (target == null) {
            return;
        }
        lookAt(player, aimPoint(target));
        client.hitResult = target.hitResult();
        client.crosshairPickEntity = target.entity();
    }

    public static boolean onMouseMove(double x, double y) {
        Minecraft client = Minecraft.getInstance();
        if (!shouldCaptureMouse(client)) {
            hasMouseSample = false;
            return false;
        }

        ensurePointerInitialized(client);
        if (!hasMouseSample) {
            lastMouseX = x;
            lastMouseY = y;
            hasMouseSample = true;
            return true;
        }

        double dx = x - lastMouseX;
        double dy = y - lastMouseY;
        lastMouseX = x;
        lastMouseY = y;

        Window window = client.getWindow();
        double width = Math.max(1.0D, window.getScreenWidth());
        double height = Math.max(1.0D, window.getScreenHeight());
        pointerX = Mth.clamp(pointerX + dx * POINTER_SPEED, 0.0D, width);
        pointerY = Mth.clamp(pointerY + dy * POINTER_SPEED, 0.0D, height);
        return true;
    }

    private static void renderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (!isPointerActive(client) || client.screen != null || client.options.hideGui) {
            return;
        }
        ensurePointerInitialized(client);

        Window window = client.getWindow();
        double screenWidth = Math.max(1.0D, window.getScreenWidth());
        double screenHeight = Math.max(1.0D, window.getScreenHeight());
        int guiX = (int) Math.round(pointerX / screenWidth * window.getGuiScaledWidth());
        int guiY = (int) Math.round(pointerY / screenHeight * window.getGuiScaledHeight());
        int color = 0xE0FFE082;
        int shadow = 0x90000000;

        guiGraphics.fill(guiX - POINTER_MARK_SIZE - 1, guiY, guiX - 1, guiY + 1, shadow);
        guiGraphics.fill(guiX + 2, guiY, guiX + POINTER_MARK_SIZE + 2, guiY + 1, shadow);
        guiGraphics.fill(guiX, guiY - POINTER_MARK_SIZE - 1, guiX + 1, guiY - 1, shadow);
        guiGraphics.fill(guiX, guiY + 2, guiX + 1, guiY + POINTER_MARK_SIZE + 2, shadow);

        guiGraphics.fill(guiX - POINTER_MARK_SIZE, guiY, guiX, guiY + 1, color);
        guiGraphics.fill(guiX + 1, guiY, guiX + POINTER_MARK_SIZE + 1, guiY + 1, color);
        guiGraphics.fill(guiX, guiY - POINTER_MARK_SIZE, guiX + 1, guiY, color);
        guiGraphics.fill(guiX, guiY + 1, guiX + 1, guiY + POINTER_MARK_SIZE + 1, color);
    }

    private static boolean shouldCaptureMouse(Minecraft client) {
        return isPointerActive(client) && client.screen == null && client.isWindowActive();
    }

    private static boolean isPointerActive(Minecraft client) {
        return client != null
                && client.player != null
                && client.level != null
                && client.player.hasEffect(ModEffects.POINTER);
    }

    private static void deactivate() {
        active = false;
        pointerInitialized = false;
        hasMouseSample = false;
        currentTarget = null;
    }

    /**
     * 指针效果会把玩家 yaw 强行掰向指针命中点（见 {@link #lookAt}），而原版 WASD 是相对 yaw 的，
     * 于是移动方向会跟着指针乱转。二维视角下改用固定的镜头朝向做移动基准：W 永远是屏幕上方。
     * 由 {@code PointerMovementYawMixin} 在 {@code Entity#moveRelative} 里调用，仅对本地玩家生效。
     */
    public static boolean isMovementYawLocked(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        return client != null
                && entity == client.player
                && isPointerActive(client)
                && TwoDimensionalCameraClientHandle.isActive();
    }

    /** 见 {@link #isMovementYawLocked}：锁定后玩家移动所用的偏航角。 */
    public static float lockedMovementYaw() {
        return TwoDimensionalCameraClientHandle.cameraYaw();
    }

    /** 当前指针命中的实体（无则 null），供指引渲染（{@link PointerGuidanceRenderer}）读取。 */
    public static Entity currentTargetEntity() {
        PointerTarget target = currentTarget;
        return target == null ? null : target.entity();
    }

    /** 当前指针命中结果（可能为 miss / null），供指引渲染读取。 */
    public static HitResult currentHitResult() {
        PointerTarget target = currentTarget;
        return target == null ? null : target.hitResult();
    }

    private static void ensurePointerInitialized(Minecraft client) {
        if (!pointerInitialized) {
            resetPointer(client);
        } else {
            clampPointer(client);
        }
    }

    private static void resetPointer(Minecraft client) {
        Window window = client.getWindow();
        pointerX = Math.max(1.0D, window.getScreenWidth()) * 0.5D;
        pointerY = Math.max(1.0D, window.getScreenHeight()) * 0.5D;
        pointerInitialized = true;
        hasMouseSample = false;
    }

    private static void clampPointer(Minecraft client) {
        Window window = client.getWindow();
        pointerX = Mth.clamp(pointerX, 0.0D, Math.max(1.0D, window.getScreenWidth()));
        pointerY = Mth.clamp(pointerY, 0.0D, Math.max(1.0D, window.getScreenHeight()));
    }

    private static PointerTarget findPointerTarget(Minecraft client, LocalPlayer player) {
        Camera camera = client.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return null;
        }
        Vec3 cameraPos = camera.getPosition();
        Vec3 direction = pointerDirection(client, camera);
        if (direction.lengthSqr() <= 1.0E-7D) {
            return null;
        }

        // 从镜头近裁剪面起步即可：被隐藏的方块（屋顶 / 近墙 / 二楼）由下面的 HakoniwaClipContext
        // 判成空体素，射线不会打在看不见的东西上。
        Vec3 rayStart = cameraPos.add(direction.scale(0.05D));
        Vec3 rayEnd = cameraPos.add(direction.scale(POINTER_RANGE));

        // 被箱庭视野剔除的方块（已隐藏的屋顶 / 二楼）对射线透明，否则指针会打在看不见的东西上。
        BlockHitResult blockHit = client.level.clip(new HakoniwaClipContext(
                rayStart,
                rayEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));
        HitResult bestHit = blockHit;
        Entity bestEntity = null;
        double bestDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : rayStart.distanceToSqr(blockHit.getLocation());

        AABB searchBox = new AABB(rayStart, rayEnd).inflate(ENTITY_PICK_PADDING);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                client.level,
                player,
                rayStart,
                rayEnd,
                searchBox,
                entity -> entity != player && !entity.isSpectator() && entity.isPickable()
                        && !HakoniwaVisionClientHandle.shouldCullEntity(entity),
                ENTITY_PICK_MARGIN);
        if (entityHit != null) {
            double entityDistance = rayStart.distanceToSqr(entityHit.getLocation());
            if (entityDistance <= bestDistance) {
                bestHit = entityHit;
                bestEntity = entityHit.getEntity();
            }
        }

        if (bestHit != null && bestHit.getType() != HitResult.Type.MISS) {
            return new PointerTarget(bestHit.getLocation(), bestHit, bestEntity);
        }

        Vec3 fallback = pointerPlaneTarget(cameraPos, direction, camera, player);
        HitResult miss = BlockHitResult.miss(
                fallback,
                Direction.getNearest(direction),
                BlockPos.containing(fallback));
        return new PointerTarget(fallback, miss, null);
    }

    private static Vec3 pointerDirection(Minecraft client, Camera camera) {
        Window window = client.getWindow();
        double width = Math.max(1.0D, window.getScreenWidth());
        double height = Math.max(1.0D, window.getScreenHeight());
        ensurePointerInitialized(client);
        double mouseX = Mth.clamp(pointerX, 0.0D, width);
        double mouseY = Mth.clamp(pointerY, 0.0D, height);
        double ndcX = mouseX / width * 2.0D - 1.0D;
        double ndcY = 1.0D - mouseY / height * 2.0D;
        double fov = currentFov(client);
        double tanY = Math.tan(Math.toRadians(fov) * 0.5D);
        double tanX = tanY * width / height;

        Vec3 look = vec(camera.getLookVector());
        Vec3 left = vec(camera.getLeftVector());
        Vec3 up = vec(camera.getUpVector());
        return look
                .add(left.scale(-ndcX * tanX))
                .add(up.scale(ndcY * tanY))
                .normalize();
    }

    private static double currentFov(Minecraft client) {
        float partialTick = client.getTimer().getGameTimeDeltaPartialTick(false);
        float advancedFov = AdvancedCameraDirector.getFovOverride(partialTick);
        if (advancedFov > 0.0F) {
            return advancedFov;
        }
        return client.options.fov().get();
    }

    private static Vec3 pointerPlaneTarget(Vec3 cameraPos, Vec3 direction, Camera camera, LocalPlayer player) {
        Vec3 cameraLook = vec(camera.getLookVector()).normalize();
        Vec3 playerEye = player.getEyePosition(1.0F);
        double denominator = dot(direction, cameraLook);
        if (Math.abs(denominator) <= 1.0E-5D) {
            return cameraPos.add(direction.scale(16.0D));
        }
        double t = dot(playerEye.subtract(cameraPos), cameraLook) / denominator;
        if (t <= 0.0D || !Double.isFinite(t)) {
            t = 16.0D;
        }
        return cameraPos.add(direction.scale(Mth.clamp(t, 1.0D, POINTER_RANGE)));
    }

    /**
     * 指针命中生物（尤其是玩家）时，瞄准其眼睛高度而非命中盒上的原始落点——否则从 2D 俯视 /
     * 侧视相机射出的指针常落在对方脚下，导致本地玩家「低头看脚下」。命中方块 / 空处时仍用原落点。
     */
    private static Vec3 aimPoint(PointerTarget target) {
        Entity entity = target.entity();
        if (entity instanceof LivingEntity living) {
            return living.getEyePosition(1.0F);
        }
        return target.location();
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal <= 1.0E-5D && Math.abs(delta.y) <= 1.0E-5D) {
            return;
        }
        float targetYaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        targetPitch = Mth.clamp(targetPitch, -90.0F, 90.0F);
        // 每 tick 向目标朝向缓动，而非瞬间对齐：叠加渲染插值后玩家模型转动更平滑。
        // 瞄准仍由指针射线（hitResult）决定，这里只影响模型朝向，不影响命中判定。
        float yaw = Mth.rotLerp(ROTATION_SMOOTHING, player.getYRot(), targetYaw);
        float pitch = Mth.lerp(ROTATION_SMOOTHING, player.getXRot(), targetPitch);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
    }

    private static Vec3 vec(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static double dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private record PointerTarget(Vec3 location, HitResult hitResult, Entity entity) {
    }
}
