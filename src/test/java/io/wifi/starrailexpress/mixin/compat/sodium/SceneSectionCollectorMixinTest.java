package io.wifi.starrailexpress.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;

class SceneSectionCollectorMixinTest {
    private void record(SceneSectionCollectorMixin collector, RenderSection section, int flags) throws Exception {
        var method = SceneSectionCollectorMixin.class.getDeclaredMethod("sre$recordSectionVisit",
                RenderSection.class, int.class, CallbackInfo.class);
        method.setAccessible(true);
        method.invoke(collector, section, flags, new CallbackInfo("visit", false));
    }

    @Test void treeVisitsAreRecognizedWithoutChangingGlobalVisibleFrame() throws Exception {
        var collector = new SceneSectionCollectorMixin();
        var section = new RenderSection(null, 3, 2, 1);
        assertFalse(collector.sre$hasVisited(section));
        record(collector, section, 7);
        assertTrue(collector.sre$hasVisited(section));
        assertEquals(-1, section.getLastVisibleFrame());
    }

    @Test void all256RegionSlotsAreRecordedAndRepeatedSceneVisitsAreSkipped() throws Exception {
        var collector = new SceneSectionCollectorMixin();
        int appended = 0;
        var sections = new java.util.ArrayList<RenderSection>();
        for (int x = 0; x < 8; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 8; z++) {
            var section = new RenderSection(null, x, y, z);
            sections.add(section);
            record(collector, section, 7);
        }
        for (int pass = 0; pass < 100; pass++) {
            for (var section : sections) {
                if (!collector.sre$hasVisited(section)) appended++;
            }
        }
        assertEquals(256, sections.size());
        assertEquals(0, appended, "场景追加不能向已满的 region 写第257个条目");
    }

    @Test void newMainAndShadowCollectorsHaveIndependentMembershipEvenInSameFrame() throws Exception {
        var main = new SceneSectionCollectorMixin();
        var shadow = new SceneSectionCollectorMixin();
        var nextMain = new SceneSectionCollectorMixin();
        var section = new RenderSection(null, 0, 0, 0);
        section.setLastVisibleFrame(42);
        record(main, section, 7);
        assertFalse(shadow.sre$hasVisited(section));
        record(shadow, section, 7);
        assertTrue(main.sre$hasVisited(section));
        assertFalse(nextMain.sre$hasVisited(section));
    }

    @Test void zeroFlagAndIncrementalVisitsAreTrackedWithoutCancellingThem() throws Exception {
        var collector = new SceneSectionCollectorMixin();
        var section = new RenderSection(null, -8, -4, -8);
        record(collector, section, 0);
        assertTrue(collector.sre$hasVisited(section));
        record(collector, section, 1);
        record(collector, section, 6);
        assertTrue(collector.sre$hasVisited(section));
    }

    @Test void unrelatedAndRecoveredSectionsAreNotSuppressed() throws Exception {
        var collector = new SceneSectionCollectorMixin();
        var old = new RenderSection(null, 0, 0, 0);
        var recovered = new RenderSection(null, 0, 0, 0);
        record(collector, old, 7);
        assertFalse(collector.sre$hasVisited(new RenderSection(null, 8, 0, 0)));
        assertFalse(collector.sre$hasVisited(recovered));
        record(collector, recovered, 7);
        assertTrue(collector.sre$hasVisited(recovered));
    }
}
