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

package io.wifi.starrailexpress.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public interface MatrixUtils {
    static Vec3 matrixToVec(PoseStack matrixStack) {
        matrixStack.translate(0.0F, 0.075F, -0.25F);
        Matrix4f matrix = matrixStack.last().pose();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector4f localPos = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
        matrix.transform(localPos);
        Vec3 cameraPos = camera.getPosition();
        return new Vec3(cameraPos.x + localPos.x(), cameraPos.y + localPos.y(), cameraPos.z + localPos.z());
    }
}
