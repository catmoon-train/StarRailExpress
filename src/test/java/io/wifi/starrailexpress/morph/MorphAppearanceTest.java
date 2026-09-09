package io.wifi.starrailexpress.morph;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorphAppearanceTest {
    @Test
    void noneIsSingletonAndEmpty() {
        assertTrue(MorphAppearance.NONE.isNone());
        assertFalse(MorphAppearance.NONE.isPlayer());
        assertFalse(MorphAppearance.NONE.isTexture());
        assertNull(MorphAppearance.NONE.targetPlayer());
        assertNull(MorphAppearance.NONE.texture());
        assertSame(MorphAppearance.NONE, MorphAppearance.ofPlayer(null));
    }

    @Test
    void playerAppearanceKeepsTargetUuid() {
        UUID target = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MorphAppearance appearance = MorphAppearance.ofPlayer(target);
        assertTrue(appearance.isPlayer());
        assertFalse(appearance.isNone());
        assertEquals(target, appearance.targetPlayer());
        assertEquals(MorphAppearance.ofPlayer(target), appearance);
        assertEquals(MorphAppearance.ofPlayer(target).hashCode(), appearance.hashCode());
        assertNotEquals(MorphAppearance.NONE, appearance);
    }

    @Test
    void differentPlayersAreNotEqual() {
        UUID a = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID b = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertNotEquals(MorphAppearance.ofPlayer(a), MorphAppearance.ofPlayer(b));
    }

    @Test
    void playerAndNoneHaveDistinctTypes() {
        UUID target = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertEquals(MorphAppearance.Type.NONE, MorphAppearance.NONE.type());
        assertEquals(MorphAppearance.Type.PLAYER, MorphAppearance.ofPlayer(target).type());
        assertFalse(MorphAppearance.ofPlayer(target).slim());
    }
}
