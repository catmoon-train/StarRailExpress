package org.agmas.noellesroles.game.night;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 将独立的生产规则穷举检查纳入正常 Gradle 测试。 */
class NightUndeadRulesTest {
    @Test void validatesSourceMigrationAlertBoundariesAndSpawnAnchors() throws Exception {
        NightUndeadRulesCheck.main(new String[0]);
    }

    @Test void lifetimeExtensionsCannotOverflowAnEntireRoundGhost() {
        assertEquals(Integer.MAX_VALUE, NightUndeadRules.extendLifetime(Integer.MAX_VALUE - 20, 200));
        assertEquals(200, NightUndeadRules.extendLifetime(100, 100));
        assertEquals(0, NightUndeadRules.extendLifetime(100, -200));
    }
}
