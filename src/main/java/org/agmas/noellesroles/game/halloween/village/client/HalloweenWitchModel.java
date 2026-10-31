package org.agmas.noellesroles.game.halloween.village.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.game.halloween.village.VillageNpc;

/** 原版 Alex 玩家模型：使用完整的 64×64 皮肤与外层，不另造头脸或身体几何。 */
@Environment(EnvType.CLIENT)
public final class HalloweenWitchModel extends PlayerModel<VillageNpc> {
    public HalloweenWitchModel() {
        super(createPlayerLayer().bakeRoot(), true);
        young = false;
    }

    /** 直接复用原版细手臂网格，避免手动拼接皮肤 UV 和自定义骨骼解析。 */
    static LayerDefinition createPlayerLayer() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64);
    }

    @Override
    public void setupAnim(VillageNpc npc, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        applyRidingPose(netHeadYaw, headPitch);
    }

    /** 坐扫帚姿态只旋转原版肢体；皮肤外层始终复制对应内层的姿态。 */
    void applyRidingPose(float yaw, float pitch) {
        head.yRot = Mth.clamp(yaw, -25F, 25F) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(pitch, -15F, 15F) * Mth.DEG_TO_RAD;
        hat.copyFrom(head);
        body.xRot = 0;
        body.yRot = 0;
        body.zRot = 0;
        rightLeg.xRot = -1.22F;
        leftLeg.xRot = -1.32F;
        rightLeg.yRot = .10F;
        leftLeg.yRot = -.10F;
        rightLeg.zRot = .035F;
        leftLeg.zRot = -.035F;
        rightArm.xRot = -.95F; // 右手托举南瓜，左手保留自然坐姿。
        leftArm.xRot = -.32F;
        rightArm.yRot = 0;
        leftArm.yRot = 0;
        rightArm.zRot = .06F;
        leftArm.zRot = -.06F;
        jacket.copyFrom(body);
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
        rightPants.copyFrom(rightLeg);
        leftPants.copyFrom(leftLeg);
    }
}
