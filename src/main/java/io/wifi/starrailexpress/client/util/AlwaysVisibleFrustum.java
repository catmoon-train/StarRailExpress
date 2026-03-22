package io.wifi.starrailexpress.client.util;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public class AlwaysVisibleFrustum extends Frustum {
    public AlwaysVisibleFrustum(Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        super(positionMatrix, projectionMatrix);
    }

    public AlwaysVisibleFrustum(Frustum frustum) {
        super(frustum);
    }

    @Override
    public boolean isVisible(AABB box) {
        if (SREClient.isTrainMoving()) {
            if (SREConfig.instance().isUltraPerfMode()) {
                return super.isVisible(box);
            }

            AABB playAres = SREClient.areaComponent.getPlayArea();
            AABB sceneOffset = SREClient.areaComponent.getSceneArea();
            return super.isVisible(box) || playAres.intersects(box) || sceneOffset.intersects(box);
        }
        return super.isVisible(box);
    }
}
