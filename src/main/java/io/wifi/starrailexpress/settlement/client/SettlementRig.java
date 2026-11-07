package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** 结算人偶的上半身父变换：围绕腰部旋转，髋部与双腿接缝始终固定。 */
final class SettlementRig {
    private SettlementRig() { }

    static void upperBody(PoseStack pose, float pitch, float yaw, float roll) {
        // 原版人形模型的髋部在 y=12 像素，ModelPart 渲染单位为 1/16 格。
        pose.translate(0, 12 / 16f, 0);
        if (roll != 0) pose.mulPose(Axis.ZP.rotation(roll));
        if (yaw != 0) pose.mulPose(Axis.YP.rotation(yaw));
        if (pitch != 0) pose.mulPose(Axis.XP.rotation(pitch));
        pose.translate(0, -12 / 16f, 0);
    }
}
