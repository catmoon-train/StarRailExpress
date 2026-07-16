package net.exmo.sre.sixtyseconds.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsTurretBlockEntity;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;

/**
 * 哨戒炮渲染器：底座由方块 JSON 模型渲染，本 BER 只画<b>会旋转的炮头</b>——
 * 每 10 tick 扫描一次射程内最近的 60s 怪并锁定（逐帧读实时位置平滑追踪）；
 * 无目标时慢速巡逻旋转。纯客户端视觉，与服务端射击结算（{@code SixtySecondsPveSystem}）互不影响。
 * 炮头 ModelPart 内联 bake，不占用 ModelLayer 注册表。
 */
public class SixtySecondsTurretRenderer implements BlockEntityRenderer<SixtySecondsTurretBlockEntity> {

    private static final ResourceLocation TEXTURE =
            Noellesroles.id("textures/entity/sixty_seconds_turret.png");
    /** 目标重扫间隔（tick）与巡逻转速（度/帧）。 */
    private static final int SCAN_INTERVAL = 10;
    private static final float IDLE_SPIN_SPEED = 0.6F;

    private final ModelPart head;

    public SixtySecondsTurretRenderer(BlockEntityRendererProvider.Context context) {
        this.head = buildHead();
    }

    /** 炮头：机匣 + 前伸双炮管（几何上下对称，避免模型 y 翻转可见）。 */
    private static ModelPart buildHead() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -2.5F, -5.0F, 7.0F, 5.0F, 10.0F)   // 机匣
                .texOffs(0, 16).addBox(-2.0F, -1.0F, 5.0F, 1.5F, 2.0F, 8.0F)    // 左炮管
                .texOffs(20, 16).addBox(0.5F, -1.0F, 5.0F, 1.5F, 2.0F, 8.0F),   // 右炮管
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("head");
    }

    @Override
    public void render(SixtySecondsTurretBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        updateAim(be);
        // 平滑逼近目标朝向（锁定时快、巡逻时匀速）
        if (be.clientHasTarget) {
            be.clientYaw = Mth.rotLerp(0.25F, be.clientYaw, be.clientTargetYaw);
        } else {
            be.clientYaw = Mth.wrapDegrees(be.clientYaw + IDLE_SPIN_SPEED);
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 0.875, 0.5); // 炮头架在底座顶（底座 JSON 模型高 12/16）
        poseStack.mulPose(Axis.YP.rotationDegrees(be.clientYaw));
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        head.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** 每 {@value #SCAN_INTERVAL} tick 重扫最近的 60s 怪；帧间用缓存目标的实时位置更新朝向。 */
    private void updateAim(SixtySecondsTurretBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - be.clientLastScan >= SCAN_INTERVAL) {
            be.clientLastScan = now;
            be.clientTarget = findNearestMonster(level, be.getBlockPos());
        }
        LivingEntity target = be.clientTarget;
        if (target != null && target.isAlive()
                && target.distanceToSqr(be.getBlockPos().getCenter())
                        <= SixtySecondsBalance.TURRET_RANGE * SixtySecondsBalance.TURRET_RANGE) {
            double dx = target.getX() - (be.getBlockPos().getX() + 0.5);
            double dz = target.getZ() - (be.getBlockPos().getZ() + 0.5);
            be.clientTargetYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            be.clientHasTarget = true;
        } else {
            be.clientTarget = null;
            be.clientHasTarget = false;
        }
    }

    private static LivingEntity findNearestMonster(Level level, BlockPos pos) {
        double range = SixtySecondsBalance.TURRET_RANGE;
        AABB area = new AABB(pos).inflate(range);
        LivingEntity nearest = null;
        double best = range * range;
        for (SixtySecondsMonsterEntity monster
                : level.getEntitiesOfClass(SixtySecondsMonsterEntity.class, area)) {
            if (!monster.isAlive()) {
                continue;
            }
            double dist = monster.distanceToSqr(pos.getCenter());
            if (dist < best) {
                best = dist;
                nearest = monster;
            }
        }
        return nearest;
    }
}
