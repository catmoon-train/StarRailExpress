package org.agmas.noellesroles.game.minigame.roulette;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.agmas.noellesroles.client.roulette.RouletteAmmoLayout;
import org.agmas.noellesroles.client.roulette.RouletteHudLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouletteAmmoTest {
    private static final UUID A = new UUID(1, 1), B = new UUID(2, 2);
    @Test void eachShotRemovesOnlyTheRevealedType() {
        var round = round(true, false, true);
        assertCounts(round.publicAmmo(), 2, 1, 0);
        round.fire(B); assertCounts(round.publicAmmo(), 1, 1, 0);
        round.fire(round.operator()); assertCounts(round.publicAmmo(), 1, 0, 0);
        round.fire(round.opponent(round.operator())); assertCounts(round.publicAmmo(), 0, 0, 0);
        assertEquals(0, round.remaining());
        assertEquals(2, round.loadedLive()); assertEquals(1, round.loadedBlanks());
    }
    @Test void beerEjectsAndRemovesTheSameTypeWithoutPassingTurn() {
        var round = round(true, false, true);
        assertEquals(1, round.use(RouletteItem.BEER).shell());
        assertCounts(round.publicAmmo(), 1, 1, 0);
        assertEquals(0, round.use(RouletteItem.BEER).shell());
        assertCounts(round.publicAmmo(), 1, 0, 0);
        assertEquals(A, round.operator());
    }
    @Test void privateInspectionsAndNonConsumingActionsDoNotChangeCounts() {
        var round = round(true, false, true);
        var before = round.publicAmmo();
        for (var item : List.of(RouletteItem.MAGNIFYING_GLASS, RouletteItem.TELEPHONE,
                RouletteItem.HANDCUFFS, RouletteItem.HANDSAW)) {
            assertTrue(round.use(item).valid()); assertEquals(before, round.publicAmmo());
        }
        assertFalse(round.use(RouletteItem.CIGARETTE).valid());
        assertFalse(round.fire(UUID.randomUUID()).valid());
        assertEquals(before, round.publicAmmo());
    }
    @Test void inversionCannotRevealWhichHiddenTypeWasChanged() {
        var first = round(true, false, true, false);
        var second = round(false, true, false, true);
        first.use(RouletteItem.INVERTER); second.use(RouletteItem.INVERTER);
        assertNotEquals(first.peek(), second.peek());
        assertEquals(first.publicAmmo(), second.publicAmmo());
        assertCounts(first.publicAmmo(), 1, 1, 2);
        assertEquals(0, first.use(RouletteItem.BEER).shell());
        assertEquals(1, second.use(RouletteItem.BEER).shell());
        assertCounts(first.publicAmmo(), 1, 2, 0);
        assertCounts(second.publicAmmo(), 2, 1, 0);
    }
    @Test void twoInversionsOnTheSameShellRestoreCertainCounts() {
        var round = round(true, true, false, true, false);
        var before = round.publicAmmo();
        round.use(RouletteItem.INVERTER); round.use(RouletteItem.INVERTER);
        assertEquals(before, round.publicAmmo());
        assertTrue(round.peek());
    }
    @Test void singleKnownTypeCanBeInvertedWithoutArtificialUncertainty() {
        var ammo = new RouletteAmmo();
        ammo.reload(2, 0); ammo.invert(); assertCounts(ammo.snapshot(), 1, 1, 0);
        ammo.consume(false); assertCounts(ammo.snapshot(), 1, 0, 0);
        ammo.reload(0, 2); ammo.invert(); assertCounts(ammo.snapshot(), 1, 1, 0);
        ammo.consume(true); assertCounts(ammo.snapshot(), 0, 1, 0);
        ammo.reload(0, 0);
        assertThrows(IllegalStateException.class, ammo::invert);
        assertThrows(IllegalStateException.class, () -> ammo.consume(true));
    }
    @Test void publicCountsAlwaysRemainSoundAcrossRandomMixedShotsAndInversions() {
        for (int seed = 0; seed < 512; seed++) {
            var random = new Random(seed);
            int total = random.nextInt(2, 9), initialLive = 0;
            boolean[] shells = new boolean[total];
            for (int i = 0; i < total; i++) { shells[i] = random.nextBoolean(); if (shells[i]) initialLive++; }
            var ammo = new RouletteAmmo(); ammo.reload(initialLive, total - initialLive);
            for (int cursor = 0; cursor < total; cursor++) {
                for (int times = random.nextInt(4); times > 0; times--) {
                    ammo.invert(); shells[cursor] = !shells[cursor];
                }
                int realLive = 0;
                for (int i = cursor; i < total; i++) if (shells[i]) realLive++;
                var counts = ammo.snapshot();
                assertEquals(total - cursor, counts.total());
                assertTrue(counts.live() <= realLive); assertTrue(counts.blanks() <= total - cursor - realLive);
                if (counts.unknown() == 0) assertEquals(realLive, counts.live());
                ammo.consume(shells[cursor]);
            }
            assertCounts(ammo.snapshot(), 0, 0, 0);
        }
    }
    @Test void snapshotCarriesOnlyPublicCountsAndRestoresUncertaintyForLateViewers() throws Exception {
        var tag = RouletteView.idle().toTag();
        tag.putInt("Live", 1); tag.putInt("Blanks", 1); tag.putInt("Remaining", 4);
        var view = RouletteView.fromTag(tag);
        assertEquals(2, view.unknownAmmo());
        assertEquals(view, RouletteView.fromTag(view.toTag()));
        String table = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/content/block_entity/DevilRouletteTableEntity.java"));
        assertTrue(table.contains("r.publicAmmo().live()")); assertTrue(table.contains("r.publicAmmo().blanks()"));
    }
    @Test void shellCardStaysRightAndDoesNotCoverBottomPanelOrCrosshair() {
        for (int width : new int[]{320, 480, 960}) for (int height : new int[]{240, 360, 540}) {
            for (int count = 0; count <= 8; count++) for (int rows = 0; rows <= 3; rows++) {
                var ammo = RouletteAmmoLayout.right(width, height, count);
                var bottom = RouletteHudLayout.bottom(width, height, rows);
                assertTrue(ammo.x() > width / 2 + 8);
                assertTrue(ammo.x() + ammo.width() <= width - 8);
                assertTrue(ammo.y() >= 8); assertTrue(ammo.y() + ammo.height() < bottom.y());
            }
        }
    }
    @Test void generatedShellAtlasIsTransparentAndHasDistinctRedAndGrayIcons() throws Exception {
        var image = ImageIO.read(Path.of("src/main/resources/assets/noellesroles/textures/gui/roulette/shells.png").toFile());
        assertEquals(128, image.getWidth()); assertEquals(160, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha()); assertEquals(0, image.getRGB(0, 0) >>> 24);
        int live = image.getRGB(32, 80), blank = image.getRGB(96, 80);
        assertTrue((live >>> 24) > 128); assertTrue((blank >>> 24) > 128);
        assertTrue((live >> 16 & 255) > (live >> 8 & 255) + 40);
        assertTrue(Math.abs((blank >> 16 & 255) - (blank >> 8 & 255)) < 30);
    }
    @Test void sawnShotConsumesOneShellEvenWhenDamageIsDoubled() {
        var round = round(true, true, false);
        round.use(RouletteItem.HANDSAW);
        assertEquals(-2, round.fire(B).delta());
        assertCounts(round.publicAmmo(), 1, 1, 0);
        assertEquals(2, round.remaining());
    }
    private static DevilRouletteRound round(Boolean... shells) {
        return new DevilRouletteRound(A, B, new Random(0), Arrays.asList(shells), 3, 3, A);
    }
    private static void assertCounts(RouletteAmmo.Counts counts, int live, int blanks, int unknown) {
        assertEquals(new RouletteAmmo.Counts(live, blanks, unknown), counts);
    }
}
