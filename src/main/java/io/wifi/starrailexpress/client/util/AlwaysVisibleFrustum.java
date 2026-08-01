/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.util;

// import io.wifi.starrailexpress.SREConfig;
// import io.wifi.starrailexpress.client.SREClient;

import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;

public class AlwaysVisibleFrustum extends Frustum {
    public AlwaysVisibleFrustum(Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        super(positionMatrix, projectionMatrix);
    }

    public AlwaysVisibleFrustum(Frustum frustum) {
        super(frustum);
    }

    // @Override
    // public boolean isVisible(AABB box) {
    //     if (SREClient.isTrainMoving()) {
    //         if (SREConfig.instance().isUltraPerfMode()) {
    //             return super.isVisible(box);
    //         }

    //         AABB playAres = SREClient.areaComponent.getPlayArea();
    //         AABB sceneOffset = SREClient.areaComponent.getSceneArea();
    //         return super.isVisible(box) || playAres.intersects(box) || sceneOffset.intersects(box);
    //     }
    //     return super.isVisible(box);
    // }
}
