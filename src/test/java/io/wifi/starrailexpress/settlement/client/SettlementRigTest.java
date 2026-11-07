package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 人偶腰部联动回归：髋部固定，上半身保持刚性，父变换不会污染双腿。 */
class SettlementRigTest {
    @Test void bendingAndTwistingKeepHipJointFixed() {
        for (float pitch : new float[]{-.3f, 0, .42f, .7f}) {
            for (float yaw : new float[]{-.6f, 0, .6f}) {
                PoseStack pose = new PoseStack();
                SettlementRig.upperBody(pose, pitch, yaw, 0);
                Vector3f hip = pose.last().pose().transformPosition(new Vector3f(0, .75f, 0));
                assertEquals(0, hip.x, 1e-6);
                assertEquals(.75, hip.y, 1e-6, "弯腰不能把躯干的髋部从腿根拉开");
                assertEquals(0, hip.z, 1e-6);
            }
        }
    }

    @Test void headAndShouldersFollowTheSameRigidUpperBody() {
        PoseStack pose = new PoseStack();
        SettlementRig.upperBody(pose, .42f, -.12f, .06f);
        Vector3f shoulder = pose.last().pose().transformPosition(new Vector3f(.3f, .12f, 0));
        Vector3f head = pose.last().pose().transformPosition(new Vector3f(0, -.3f, 0));
        assertEquals(new Vector3f(.3f, .12f, 0).distance(new Vector3f(0, -.3f, 0)),
                shoulder.distance(head), 1e-6, "头和手臂必须随躯干一起动，不能留在原地");
        assertNotEquals(0, head.z, "鞠躬时头部应该随上半身前倾");
    }

    @Test void upperBodyTransformDoesNotLeakIntoTheLegFrame() {
        PoseStack pose = new PoseStack();
        pose.translate(2, 3, -1);
        var legFrame = new org.joml.Matrix4f(pose.last().pose());
        pose.pushPose();
        SettlementRig.upperBody(pose, .5f, .25f, 0);
        pose.popPose();
        assertTrue(legFrame.equals(pose.last().pose(), 1e-6f));
    }
}
