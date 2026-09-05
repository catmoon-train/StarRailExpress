package org.agmas.noellesroles.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlindVisionVisibilityTest {
    @Test
    void nearbyBlockInEchoTurnsVisible() {
        float vis = echoVisibility(12.0f, 1.0f, 3.0f);
        assertTrue(vis > 0.7f);
    }

    @Test
    void farBlockStaysBlack() {
        float vis = echoVisibility(12.0f, 1.0f, 40.0f);
        assertTrue(vis < 0.01f);
    }

    @Test
    void onlyBlocksAroundSourceLight() {
        float radius = 4.2f;
        assertTrue(echoVisibility(radius, 1.0f, 0.8f) > 0.7f);
        assertTrue(echoVisibility(radius, 1.0f, 12.0f) < 0.02f);
    }

    @Test
    void nearPlaneCollapseDoesNotCountAsWorld() {
        float viewDist = 0.05f;
        float viewZ = -0.05f;
        boolean lightable = viewZ < -0.35f && viewDist > 0.35f && viewDist < 96.0f;
        assertTrue(!lightable);
    }

    @Test
    void echoEnergyLerpDoesNotJumpToFullInOneTick() {
        float energy = 0.0f;
        energy += (1.0f - energy) * 0.10f;
        assertTrue(energy < 0.2f);
        assertTrue(energy > 0.05f);
    }

    @Test
    void perspectiveDepthReconstructsViewDistance() {
        float near = 0.05f;
        float far = 256.0f;
        float fovy = (float) Math.toRadians(70.0);
        float aspect = 16.0f / 9.0f;
        Matrix4f proj = new Matrix4f().perspective(fovy, aspect, near, far);
        Vector4f clip = new Vector4f(0.0f, 0.0f, -10.0f, 1.0f);
        clip.mul(proj);
        clip.div(clip.w);
        float depth = clip.z * 0.5f + 0.5f;
        float zNdc = depth * 2.0f - 1.0f;
        float dist = (2.0f * near * far) / (far + near - zNdc * (far - near));
        assertEquals(10.0f, dist, 0.08f);
    }

    private static float echoVisibility(float radius, float fade, float dist) {
        float inner = radius * 0.18f;
        float vis = 1.0f - smoothstep(inner, radius, dist);
        return vis * vis * fade;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.min(1.0f, Math.max(0.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
}
