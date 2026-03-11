package io.wifi.starrailexpress.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public interface MatrixUtils {
    static Vec3 matrixToVec(PoseStack matrixStack) {
        matrixStack.translate(0f, 0.075f, -0.25f); //this is needed purely for this use, remove if going to be reused, i was too lazy to figure out where to put the actual translate - Cup
        Matrix4f matrix = matrixStack.last().pose();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        Vector4f localPos = new Vector4f(0, 0, 0, 1);
        matrix.transform(localPos);

        Vec3 cameraPos = camera.getPosition();
        return new Vec3(
                cameraPos.x + localPos.x(),
                cameraPos.y + localPos.y(),
                cameraPos.z + localPos.z());
    }
}
