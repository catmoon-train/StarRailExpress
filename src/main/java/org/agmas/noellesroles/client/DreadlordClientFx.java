package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.agmas.noellesroles.game.command_block.DreadlordRole;
import org.agmas.noellesroles.role_data.killer.DreadlordRoleData;

/**
 * 恐惧魔王能看见旁观者的头。黑暗游走时本体改成贴地的一团黑。
 */
@Environment(EnvType.CLIENT)
public final class DreadlordClientFx {
    private DreadlordClientFx() {
    }

    public static void register() {
        // 独立于本地玩家的冒险/观战状态，否则死亡后特效不再清理。
        ClientTickEvents.END_CLIENT_TICK.register(DreadlordClientFx::tickFloats);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearFloats(client));
    }

    public static boolean renderDark(AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffer,
            int light) {
        DreadlordRoleData data = DreadlordRole.dataOf(player);
        if (data == null || !data.isDark()) {
            return false;
        }
        var consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(
                ResourceLocation.withDefaultNamespace("textures/misc/shadow.png")));
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.05F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(1.6F, 1.6F, 1.6F);
        var pose = poseStack.last();
        consumer.addVertex(pose, -0.5F, -0.5F, 0.0F).setColor(20, 0, 30, 200).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, -0.5F, 0.5F, 0.0F).setColor(20, 0, 30, 200).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, 0.5F, 0.5F, 0.0F).setColor(20, 0, 30, 200).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, 0.5F, -0.5F, 0.0F).setColor(20, 0, 30, 200).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        poseStack.popPose();
        return true;
    }

    public static void renderSouls(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !DreadlordRole.isDreadlord(client.player)) {
            return;
        }
        var camera = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) {
            return;
        }
        MultiBufferSource buffer = client.renderBuffers().bufferSource();
        for (Player other : client.level.players()) {
            if (!other.isSpectator() || other == client.player) {
                continue;
            }
            if (!(other instanceof AbstractClientPlayer soul)) {
                continue;
            }
            double x = Mth.lerp(context.tickCounter().getGameTimeDeltaPartialTick(false), soul.xo, soul.getX()) - camera.x;
            double y = Mth.lerp(context.tickCounter().getGameTimeDeltaPartialTick(false), soul.yo, soul.getY()) - camera.y;
            double z = Mth.lerp(context.tickCounter().getGameTimeDeltaPartialTick(false), soul.zo, soul.getZ()) - camera.z;
            poseStack.pushPose();
            poseStack.translate(x, y + 1.6D, z);
            poseStack.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(0.35F, 0.35F, 0.35F);
            ResourceLocation skin = soul.getSkin().texture();
            var consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(skin));
            var pose = poseStack.last();
            float u0 = 8.0F / 64.0F;
            float v0 = 8.0F / 64.0F;
            float u1 = 16.0F / 64.0F;
            float v1 = 16.0F / 64.0F;
            consumer.addVertex(pose, -0.5F, 0.5F, 0.0F).setColor(180, 180, 255, 220).setUv(u0, v0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, -0.5F, -0.5F, 0.0F).setColor(180, 180, 255, 220).setUv(u0, v1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, 0.5F, -0.5F, 0.0F).setColor(180, 180, 255, 220).setUv(u1, v1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, 0.5F, 0.5F, 0.0F).setColor(180, 180, 255, 220).setUv(u1, v0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
            poseStack.popPose();
        }
    }

    private static final List<FloatBlock> FLOATS = new ArrayList<>();
    /** 区块编译可能在工作线程读取，不能遍历主线程正在修改的 FLOATS。 */
    private static volatile Set<Long> hiddenBlocks = Set.of();
    private static final Set<SectionPos> DIRTY = new HashSet<>();
    private static ClientLevel floatLevel;
    private static long nextLift;

    public static boolean hides(int x, int y, int z) {
        return hiddenBlocks.contains(BlockPos.asLong(x, y, z));
    }

    public static void tickFloats(Minecraft client) {
        if (floatLevel != client.level) {
            clearFloats(client);
            floatLevel = client.level;
        }
        if (client.level == null || client.player == null
                || !io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).isRunning()) {
            clearFloats(client);
            return;
        }
        List<Player> sources = new ArrayList<>();
        Set<UUID> active = new HashSet<>();
        for (Player other : client.level.players()) {
            DreadlordRoleData data = DreadlordRole.dataOf(other);
            if (other.isAlive() && !other.isSpectator() && DreadlordRole.isDreadlord(other)
                    && data != null && data.isKilling() && !data.isDark()) {
                sources.add(other);
                active.add(other.getUUID());
            }
        }
        Iterator<FloatBlock> it = FLOATS.iterator();
        while (it.hasNext()) {
            FloatBlock block = it.next();
            block.age++;
            if (!active.contains(block.owner) || block.age >= 50) {
                it.remove();
                markDirty(block.pos);
            }
        }
        long now = client.level.getGameTime();
        if (!sources.isEmpty() && now >= nextLift) {
            for (Player source : sources) {
                liftAround(client, source);
            }
            nextLift = now + 12;
        }
        // 先发布最新隐藏集合，再重建网格，恢复方块时不再烘焙成空气。
        flushDirty(client);
    }

    private static void liftAround(Minecraft client, Player source) {
        BlockPos origin = source.blockPosition();
        for (int i = 0; i < 4; i++) {
            int dx = client.player.getRandom().nextInt(7) - 3;
            int dz = client.player.getRandom().nextInt(7) - 3;
            BlockPos pos = origin.offset(dx, -1, dz);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos at = pos.offset(0, dy, 0);
                BlockState state = client.level.getBlockState(at);
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                boolean exists = false;
                for (FloatBlock block : FLOATS) {
                    if (block.pos.equals(at)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    FLOATS.add(new FloatBlock(at, state, source.getUUID()));
                    markDirty(at);
                }
                break;
            }
        }
    }

    public static void renderFloats(WorldRenderContext context) {
        if (FLOATS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.level != floatLevel) {
            return;
        }
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource buffer = context.consumers();
        if (poseStack == null || buffer == null) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        var dispatcher = client.getBlockRenderer();
        for (FloatBlock block : FLOATS) {
            float t = Mth.clamp((block.age + partial) / 50.0F, 0.0F, 1.0F);
            float rise = (float) Math.sin(t * Math.PI) * 1.15F;
            poseStack.pushPose();
            poseStack.translate(block.pos.getX() - camera.x, block.pos.getY() - camera.y + rise,
                    block.pos.getZ() - camera.z);
            dispatcher.renderSingleBlock(block.state, poseStack, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    /** 清除本世界的视觉副本，不修改真实方块；切世界和断线也必须清理。 */
    private static void clearFloats(Minecraft client) {
        for (FloatBlock block : FLOATS) {
            markDirty(block.pos);
        }
        FLOATS.clear();
        flushDirty(client);
        hiddenBlocks = Set.of();
        floatLevel = null;
        nextLift = 0L;
    }

    private static void markDirty(BlockPos pos) {
        DIRTY.add(SectionPos.of(pos));
        // 区段边界的相邻面也要重建，防止留下空洞或错误遮挡。
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            DIRTY.add(SectionPos.of(pos.relative(direction)));
        }
    }

    private static void flushDirty(Minecraft client) {
        if (DIRTY.isEmpty()) {
            return;
        }
        hiddenBlocks = FLOATS.stream().map(block -> block.key).collect(Collectors.toUnmodifiableSet());
        if (client.level == floatLevel && client.levelRenderer != null) {
            for (SectionPos section : DIRTY) {
                client.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
            }
        }
        DIRTY.clear();
    }

    private static final class FloatBlock {
        private final BlockPos pos;
        private final long key;
        private final BlockState state;
        private final UUID owner;
        private int age;

        private FloatBlock(BlockPos pos, BlockState state, UUID owner) {
            this.pos = pos.immutable();
            this.key = this.pos.asLong();
            this.state = state;
            this.owner = owner;
        }
    }
}
