package org.agmas.noellesroles.game.night;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 将独立的生产规则穷举检查纳入正常 Gradle 测试。 */
class NightUndeadRulesTest {
    @Test void halloweenAndLordHaveDistinctColorsWithoutChangingOtherSources() {
        assertEquals(0xFFB35C, NightUndeadRules.visualColor(NightUndeadRules.Source.HALLOWEEN));
        assertEquals(0xB58AFF, NightUndeadRules.visualColor(NightUndeadRules.Source.LORD));
        assertEquals(0xFFFFFF, NightUndeadRules.visualColor(NightUndeadRules.Source.NIGHT));
        assertEquals(0xFFFFFF, NightUndeadRules.visualColor(NightUndeadRules.Source.WILD));
    }

    @Test void colorIsSyncedAtSetupAndLoadAndRendererKeepsAlpha() throws Exception {
        String entity = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/agmas/noellesroles/content/entity/UndeadEntity.java"));
        org.junit.jupiter.api.Assertions.assertTrue(entity.contains("builder.define(VISUAL_COLOR"));
        assertEquals(2, entity.split("entityData.set\\(VISUAL_COLOR, NightUndeadRules.visualColor\\(source\\)\\)", -1).length - 1);
        org.junit.jupiter.api.Assertions.assertTrue(entity.contains("getVisualColor() { return this.entityData.get(VISUAL_COLOR); }"));
        String renderer = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/agmas/noellesroles/client/renderer/UndeadEntityRenderer.java"));
        org.junit.jupiter.api.Assertions.assertTrue(renderer.contains("invisibleToOthers ? 0x26000000 : 0x80000000"));
        org.junit.jupiter.api.Assertions.assertTrue(renderer.contains("livingEntity.getVisualColor() & 0xFFFFFF"));
    }

    @Test void validatesSourceMigrationAlertBoundariesAndSpawnAnchors() throws Exception {
        NightUndeadRulesCheck.main(new String[0]);
    }

    @Test void lifetimeExtensionsCannotOverflowAnEntireRoundGhost() {
        assertEquals(Integer.MAX_VALUE, NightUndeadRules.extendLifetime(Integer.MAX_VALUE - 20, 200));
        assertEquals(200, NightUndeadRules.extendLifetime(100, 100));
        assertEquals(0, NightUndeadRules.extendLifetime(100, -200));
    }
}
