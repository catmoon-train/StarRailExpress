package org.agmas.noellesroles.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class KnifeGlintRulesTest {
    @Test void realKnifeGlowsForCivilianOrNeutralButFakeNeverDoes() {
        assertEquals(Boolean.TRUE, KnifeGlintRules.foil(true, false, true));
        // 杀手等非平民/中立阵营必须覆盖原有光泽，而不是回退到物品默认值。
        assertEquals(Boolean.FALSE, KnifeGlintRules.foil(true, false, false));
        assertNull(KnifeGlintRules.foil(false, false, false));
        assertNull(KnifeGlintRules.foil(false, false, true));
        assertEquals(Boolean.FALSE, KnifeGlintRules.foil(true, true, true));
        assertEquals(Boolean.FALSE, KnifeGlintRules.foil(true, true, false));
    }

    @Test void contextUsesRenderedPlayerAndRestoresNestedStateWithoutMutatingItems() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles");
        String context = Files.readString(root.resolve("client/KnifeGlintContext.java"));
        assertTrue(context.contains("held.player.level()"));
        assertTrue(context.contains("held.stack != stack"));
        assertTrue(context.contains("finally"));
        assertTrue(context.contains("CURRENT.remove()"));
        assertFalse(context.contains("ENCHANTMENT_GLINT_OVERRIDE"));
        assertTrue(Files.readString(root.resolve("content/item/NiaoshoushouKnifeItem.java")).contains("extends KnifeItem"));
        String render = Files.readString(root.resolve("mixin/client/KnifeGlintRenderMixin.java"));
        assertTrue(render.contains("KnifeGlintContext.render(entity, stack"));
        assertTrue(render.contains("renderStatic(Lnet/minecraft/world/entity/LivingEntity;"));
    }
}
