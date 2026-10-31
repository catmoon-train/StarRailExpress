package org.agmas.noellesroles.game.halloween.village.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 实际烘焙与顶点输出测试，不需要 Minecraft 窗口或图形上下文。 */
class HalloweenWitchModelTest {
    @Test
    void usesVanillaPlayerMeshAndKeepsItsOriginalProportions() {
        var player = new HalloweenWitchModel();
        assertInstanceOf(PlayerModel.class, player);
        assertFalse(player.young, "NPC 和离线预览均须使用成年玩家比例");
        assertEquals(8, extent(player.head, 0), .00001);
        assertEquals(8, extent(player.head, 1), .00001);
        assertEquals(8, extent(player.head, 2), .00001);
        assertEquals(3, extent(player.rightArm, 0), .00001, "Alex 手臂宽三像素");
        assertEquals(3, extent(player.leftArm, 0), .00001);
        assertEquals(12, extent(player.body, 1), .00001);
        assertFalse(player.hat.isEmpty(), "保留皮肤头部外层");
        assertFalse(player.jacket.isEmpty());
        assertFalse(player.leftPants.isEmpty());
        assertFalse(player.rightSleeve.isEmpty());
    }

    @Test
    void ridingPoseKeepsEverySkinOverlayAttachedAndDoesNotAccumulate() {
        var player = new HalloweenWitchModel();
        player.applyRidingPose(0, 0);
        float leg = player.leftLeg.xRot;
        for (int i = 0; i < 10; i++) player.applyRidingPose(0, 0);
        assertEquals(leg, player.leftLeg.xRot);
        assertTrue(player.rightLeg.xRot < -1);
        samePose(player.head, player.hat);
        samePose(player.body, player.jacket);
        samePose(player.leftLeg, player.leftPants);
        samePose(player.rightLeg, player.rightPants);
        samePose(player.leftArm, player.leftSleeve);
        samePose(player.rightArm, player.rightSleeve);
    }

    @Test
    void hatFollowsHeadWhileBroomStaysInBodyFrame() {
        var player = new HalloweenWitchModel();
        var accessories = new HalloweenWitchAccessories();
        player.applyRidingPose(0, 0);
        accessories.setupPose(player.head, player.rightArm, 20);
        float broomRotation = accessories.broom.zRot;
        player.applyRidingPose(22, -12);
        accessories.setupPose(player.head, player.rightArm, 20);
        samePose(player.head, accessories.witchHat);
        assertEquals(broomRotation, accessories.broom.zRot);
        assertEquals(0, accessories.broom.yRot);
        assertEquals(12.3F, accessories.broom.y);
        assertSame(accessories.bristles, accessories.broom.getChild("bristles"));
    }

    @Test
    void hatSitsLowerInHeadSpaceWithoutAccumulatingOffsets() {
        var player = new HalloweenWitchModel();
        var accessories = new HalloweenWitchAccessories();
        player.applyRidingPose(18, 8);
        for (int i = 0; i < 20; i++) accessories.setupPose(player.head, player.rightArm, 20);
        samePose(player.head, accessories.witchHat);
        assertEquals(1.25F, accessories.witchHat.getChild("fitted_hat").y);
    }

    @Test
    void pumpkinFollowsRightHandAndRemainsUpright() {
        var player = new HalloweenWitchModel();
        var accessories = new HalloweenWitchAccessories();
        player.applyRidingPose(0, 0);
        accessories.setupPose(player.head, player.rightArm, 0);
        samePose(player.rightArm, accessories.pumpkinHand);
        assertEquals(0, accessories.pumpkin.xRot + player.rightArm.xRot, .0001F);
        player.head.yRot = .3F;
        player.rightArm.xRot = -.75F;
        for (int i = 0; i < 10; i++) accessories.setupPose(player.head, player.rightArm, 0);
        samePose(player.rightArm, accessories.pumpkinHand);
        assertEquals(.75F, accessories.pumpkin.xRot);
        assertEquals(10, accessories.pumpkin.y);
        assertFalse(accessories.pumpkin.getChild("pumpkin_body").isEmpty());
    }

    @Test
    void legacyCandyNameIsTheSameCanyueName() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("assets/halloween/lang/zh_cn.json")) {
            assertNotNull(stream);
            var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(lang.get("halloween.village.reaper"), lang.get("halloween.village.candy_npc"));
            assertTrue(lang.get("halloween.village.candy_received").getAsString().contains("万圣残月"));
        }
    }

    @Test
    void skinAndAccessoryTexturesHaveIndependentNativeResolutions() throws Exception {
        var skin = texture("halloween_canyue_skin.png");
        var gear = texture("halloween_canyue_accessories.png");
        assertEquals(64, skin.getWidth());
        assertEquals(64, skin.getHeight());
        assertEquals(128, gear.getWidth());
        assertEquals(128, gear.getHeight());
        assertTrue(skin.getColorModel().hasAlpha());
        assertEquals(0, skin.getRGB(55, 24) >>> 24, "细手臂保留未用透明区域");
    }

    @Test
    void rendersBothMeshesWithValidUvsAndExportsTheExactPreviewVertices() throws Exception {
        var player = new HalloweenWitchModel();
        player.applyRidingPose(0, 0);
        var gear = new HalloweenWitchAccessories();
        gear.setupPose(player.head, player.rightArm, 0);
        var skinVertices = new Vertices();
        var gearVertices = new Vertices();
        player.renderToBuffer(new PoseStack(), skinVertices, 15728880, 0, -1);
        gear.render(new PoseStack(), gearVertices, 15728880, 0);
        validate(skinVertices);
        validate(gearVertices);
        BufferedImage image = texture("halloween_canyue_accessories.png");
        // 逐面中心采样检查配件图集，不能再出现 UV 落进透明空白区。
        for (int i = 0; i < gearVertices.rows.size(); i += 4) {
            float u = 0, v = 0;
            for (int j = 0; j < 4; j++) {
                u += gearVertices.rows.get(i + j)[3] / 4;
                v += gearVertices.rows.get(i + j)[4] / 4;
            }
            int x = Math.min(127, (int) (u * 128));
            int y = Math.min(127, (int) (v * 128));
            assertEquals(255, image.getRGB(x, y) >>> 24, "配件 UV 中心不得位于透明图集区");
        }
        Path output = Path.of("build/halloween-witch-preview-mesh.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new Gson().toJson(Map.of(
                "skin", skinVertices.rows, "accessories", gearVertices.rows)), StandardCharsets.UTF_8);
    }

    private static void validate(Vertices vertices) {
        assertTrue(vertices.rows.size() >= 24);
        assertEquals(0, vertices.rows.size() % 4);
        for (float[] row : vertices.rows) {
            for (float value : row) assertTrue(Float.isFinite(value));
            assertTrue(row[3] >= 0 && row[3] <= 1, "归一化 U 越界");
            assertTrue(row[4] >= 0 && row[4] <= 1, "归一化 V 越界");
        }
    }

    private static double extent(ModelPart part, int axis) {
        var vertices = new Vertices();
        part.render(new PoseStack(), vertices, 15728880, 0, -1);
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (float[] row : vertices.rows) {
            min = Math.min(min, row[axis]);
            max = Math.max(max, row[axis]);
        }
        return max - min;
    }

    private static void samePose(ModelPart a, ModelPart b) {
        assertEquals(a.x, b.x); assertEquals(a.y, b.y); assertEquals(a.z, b.z);
        assertEquals(a.xRot, b.xRot); assertEquals(a.yRot, b.yRot); assertEquals(a.zRot, b.zRot);
    }

    private static BufferedImage texture(String name) throws Exception {
        try (var stream = HalloweenWitchModelTest.class.getClassLoader().getResourceAsStream(
                "assets/noellesroles/textures/entity/" + name)) {
            assertNotNull(stream, name);
            return ImageIO.read(stream);
        }
    }

    /** 收集 Minecraft 实际输出的四边形；只在预览数据中把 Y 轴转换为向上。 */
    private static final class Vertices implements VertexConsumer {
        final List<float[]> rows = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            rows.add(new float[]{x * 16, -y * 16, z * 16, 0, 0});
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) {
            rows.getLast()[3] = u; rows.getLast()[4] = v;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
