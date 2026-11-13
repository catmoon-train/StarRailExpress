package org.agmas.noellesroles.game.minigame.roulette;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class DevilRouletteRoundTest {
    private static final UUID FRONT = new UUID(1, 1);
    private static final UUID BACK = new UUID(2, 2);
    private static final UUID UNKNOWN = new UUID(3, 3);

    @Test
    void constructorChoosesEitherOperatorWithoutLoadingOrDrawingMoreRandomness() {
        for (boolean frontFirst : new boolean[]{false, true}) {
            FixedRandom random = new FixedRandom();
            random.bool = frontFirst;
            DevilRouletteRound round = new DevilRouletteRound(FRONT, BACK, random);
            assertEquals(frontFirst ? FRONT : BACK, round.operator());
            assertEquals(5, round.health(FRONT));
            assertEquals(5, round.health(BACK));
            assertEquals(0, round.remaining());
            assertEquals(0, round.loadedLive());
            assertEquals(0, round.loadedBlanks());
            assertFalse(round.saw());
            assertNull(round.cuffed());
            assertNull(round.winner());
            assertFalse(round.ended());
            assertEquals(1, random.calls);
        }
    }

    @ParameterizedTest
    @CsvSource({"false, true, false, 0", "true, true, true, -1",
            "false, false, true, 0", "true, false, true, -1"})
    void allFourShotCases(boolean live, boolean self, boolean switches, int delta) {
        DevilRouletteRound round = round(live, !live);
        UUID target = self ? FRONT : BACK;
        DevilRouletteRound.Outcome outcome = round.fire(target);
        assertTrue(outcome.valid());
        assertEquals(FRONT, outcome.actor());
        assertEquals(target, outcome.target());
        assertEquals(live ? 1 : 0, outcome.shell());
        assertEquals(delta, outcome.delta());
        assertEquals(switches, outcome.switched());
        assertFalse(outcome.skipped());
        assertNoPrivateInformation(outcome);
        assertTrue(outcome.message().startsWith("roulette.result."));
        assertEquals(switches ? BACK : FRONT, round.operator());
        assertEquals(5 + delta, round.health(target));
        assertEquals(5, round.health(round.opponent(target)));
        assertEquals(1, round.remaining());
        assertEquals(1, round.loadedLive());
        assertEquals(1, round.loadedBlanks());
        assertNull(round.winner());
    }

    @ParameterizedTest
    @CsvSource({"false, true, false", "true, true, true",
            "false, false, true", "true, false, true"})
    void cuffsOnlyConsumeWhenAHandoffWouldOccur(boolean live, boolean self, boolean skipped) {
        DevilRouletteRound round = round(live, false);
        DevilRouletteRound.Outcome cuffs = round.use(RouletteItem.HANDCUFFS);
        assertTrue(cuffs.valid());
        assertEquals(FRONT, cuffs.actor());
        assertEquals(BACK, cuffs.target());
        assertNonTurn(cuffs);
        assertEquals(BACK, round.cuffed());
        assertRejected(round, () -> round.use(RouletteItem.HANDCUFFS));
        DevilRouletteRound.Outcome shot = round.fire(self ? FRONT : BACK);
        assertEquals(skipped, shot.skipped());
        assertFalse(shot.switched());
        assertEquals(FRONT, round.operator());
        assertEquals(skipped ? null : BACK, round.cuffed());
        assertRejected(round, () -> round.use(RouletteItem.HANDCUFFS));
    }

    @Test
    void cuffsCannotBeChainedUntilAnActualTurnChangeEvenAcrossReload() {
        FixedRandom random = new FixedRandom();
        DevilRouletteRound round = round(random, 5, 5, FRONT, false, false, true, false);
        round.use(RouletteItem.HANDCUFFS);
        assertFalse(round.fire(FRONT).skipped());
        assertEquals(BACK, round.cuffed());
        assertTrue(round.fire(BACK).skipped());
        assertNull(round.cuffed());
        round.reload();
        assertEquals(FRONT, round.operator());
        assertRejected(round, () -> round.use(RouletteItem.HANDCUFFS));
        assertTrue(round.fire(BACK).switched());
        assertEquals(BACK, round.operator());
        assertTrue(round.use(RouletteItem.HANDCUFFS).valid());
        assertEquals(FRONT, round.cuffed());
    }

    @Test
    void sawDoesNotStackAndBlankTriggerConsumesIt() {
        DevilRouletteRound round = round(false, true);
        assertNonTurn(round.use(RouletteItem.HANDSAW));
        assertTrue(round.saw());
        assertRejected(round, () -> round.use(RouletteItem.HANDSAW));
        assertEquals(0, round.fire(FRONT).delta());
        assertFalse(round.saw());
        assertEquals(FRONT, round.operator());
        assertEquals(-1, round.fire(BACK).delta());
        assertEquals(4, round.health(BACK));
        assertEquals(0, round.remaining());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void sawDoublesLiveDamageForEitherTarget(boolean self) {
        DevilRouletteRound round = round(true, false);
        round.use(RouletteItem.HANDSAW);
        UUID target = self ? FRONT : BACK;
        assertEquals(-2, round.fire(target).delta());
        assertEquals(3, round.health(target));
        assertFalse(round.saw());
        assertEquals(BACK, round.operator());
    }

    @Test
    void beerOnLastShellRevealsWithoutDamageHandoffSawConsumptionOrAutomaticReload() {
        FixedRandom random = new FixedRandom();
        DevilRouletteRound round = round(random, 5, 5, FRONT, true);
        round.use(RouletteItem.HANDSAW);
        round.use(RouletteItem.HANDCUFFS);
        DevilRouletteRound.Outcome beer = round.use(RouletteItem.BEER);
        assertNonTurn(beer);
        assertEquals(1, beer.shell());
        assertEquals(0, beer.delta());
        assertNoPrivateInformation(beer);
        assertEquals(0, round.remaining());
        assertEquals(1, round.loadedLive());
        assertEquals(0, round.loadedBlanks());
        assertEquals(5, round.health(FRONT));
        assertEquals(5, round.health(BACK));
        assertEquals(FRONT, round.operator());
        assertEquals(BACK, round.cuffed());
        assertTrue(round.saw());
        assertFalse(round.ended());
        assertFalse(round.peek());
        assertRejected(round, () -> round.use(RouletteItem.BEER));
        assertRejected(round, () -> round.fire(FRONT));
        assertEquals(0, random.calls);
        round.reload();
        assertEquals(2, round.remaining());
        assertTrue(round.saw());
        assertEquals(BACK, round.cuffed());
        assertEquals(FRONT, round.operator());
    }

    @Test
    void cigaretteHealsOneWithoutWastingAtFullHealth() {
        DevilRouletteRound round = round(new FixedRandom(), 4, 5, FRONT, false, true);
        assertNull(round.failure(RouletteItem.CIGARETTE));
        DevilRouletteRound.Outcome outcome = round.use(RouletteItem.CIGARETTE);
        assertNonTurn(outcome);
        assertEquals(FRONT, outcome.actor());
        assertEquals(FRONT, outcome.target());
        assertEquals(1, outcome.delta());
        assertEquals(5, round.health(FRONT));
        assertEquals(2, round.remaining());
        assertNoPrivateInformation(outcome);
        assertEquals("roulette.error.full_health", round.failure(RouletteItem.CIGARETTE));
        assertRejected(round, () -> round.use(RouletteItem.CIGARETTE));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void magnifyingGlassReportsOnlyPrivateCurrentShellWithoutConsuming(boolean live) {
        DevilRouletteRound round = round(live, !live);
        Snapshot before = snapshot(round);
        DevilRouletteRound.Outcome outcome = round.use(RouletteItem.MAGNIFYING_GLASS);
        assertNonTurn(outcome);
        assertEquals(FRONT, outcome.actor());
        assertEquals(-1, outcome.shell());
        assertEquals(1, outcome.knownOffset());
        assertEquals(live ? 1 : 0, outcome.knownShell());
        assertEquals(before, snapshot(round));
    }

    @Test
    void inverterDoesNotRevealOrChangeLoadedSnapshotCounts() {
        DevilRouletteRound round = round(true, false);
        DevilRouletteRound.Outcome inverter = round.use(RouletteItem.INVERTER);
        assertNonTurn(inverter);
        assertEquals(-1, inverter.shell());
        assertNoPrivateInformation(inverter);
        assertFalse(round.peek());
        assertEquals(1, round.loadedLive());
        assertEquals(1, round.loadedBlanks());
        assertEquals(2, round.remaining());
        assertEquals(0, round.use(RouletteItem.BEER).shell());
        inverter = round.use(RouletteItem.INVERTER);
        assertEquals(-1, inverter.shell());
        assertNoPrivateInformation(inverter);
        assertTrue(round.peek());
        assertEquals(1, round.use(RouletteItem.BEER).shell());
        assertEquals(1, round.loadedLive());
        assertEquals(1, round.loadedBlanks());
        assertEquals(0, round.remaining());
        assertEquals(FRONT, round.operator());
    }

    @Test
    void telephoneCanSelectEveryFuturePositionIncludingBlanksButNeverCurrent() {
        FixedRandom random = new FixedRandom();
        DevilRouletteRound round = round(random, 5, 5, FRONT, true, false, true, false);
        for (int index = 0; index < 3; index++) {
            random.index = index;
            Snapshot before = snapshot(round);
            DevilRouletteRound.Outcome outcome = round.use(RouletteItem.TELEPHONE);
            assertNonTurn(outcome);
            assertEquals(FRONT, outcome.actor());
            assertEquals(-1, outcome.shell());
            assertEquals(index + 2, outcome.knownOffset());
            assertEquals(index == 1 ? 1 : 0, outcome.knownShell());
            assertEquals(3, random.lastBound);
            assertEquals(before, snapshot(round));
        }
    }

    @Test
    void telephoneOffsetsAreRelativeToCursorAtInspection() {
        FixedRandom random = new FixedRandom();
        DevilRouletteRound round = round(random, 5, 5, FRONT, true, false, true, false);
        round.use(RouletteItem.BEER);
        DevilRouletteRound.Outcome first = round.use(RouletteItem.TELEPHONE);
        assertEquals(2, first.knownOffset());
        assertEquals(1, first.knownShell());
        random.index = 1;
        DevilRouletteRound.Outcome last = round.use(RouletteItem.TELEPHONE);
        assertEquals(3, last.knownOffset());
        assertEquals(0, last.knownShell());
        assertEquals(2, random.lastBound);
        round.use(RouletteItem.BEER);
        round.use(RouletteItem.BEER);
        int calls = random.calls;
        assertEquals("roulette.error.no_future", round.failure(RouletteItem.TELEPHONE));
        assertRejected(round, () -> round.use(RouletteItem.TELEPHONE));
        assertEquals(calls, random.calls);
    }

    @ParameterizedTest
    @CsvSource({"0.0, 3, 2", "0.399999, 4, 1", "0.2, 5, 0", "0.4, 3, -1", "0.999999, 2, -1"})
    void expiredMedicineBranchesAtFortyPercentAndClampsHealing(double chance, int health, int delta) {
        FixedRandom random = new FixedRandom();
        random.chance = chance;
        DevilRouletteRound round = round(random, 5, health, BACK, true, false);
        DevilRouletteRound.Outcome outcome = round.use(RouletteItem.EXPIRED_MEDICINE);
        assertNonTurn(outcome);
        assertEquals(BACK, outcome.actor());
        assertEquals(BACK, outcome.target());
        assertEquals(delta, outcome.delta());
        assertEquals(health + delta, round.health(BACK));
        assertEquals(5, round.health(FRONT));
        assertEquals(BACK, round.operator());
        assertEquals(2, round.remaining());
        assertEquals(-1, outcome.shell());
        assertNoPrivateInformation(outcome);
        assertFalse(round.ended());
        assertEquals(1, random.calls);
    }

    @Test
    void expiredMedicineCanKillWithoutConsumingShellOrReloading() {
        FixedRandom random = new FixedRandom();
        random.chance = 0.4;
        DevilRouletteRound round = round(random, 1, 5, FRONT, true, false);
        DevilRouletteRound.Outcome outcome = round.use(RouletteItem.EXPIRED_MEDICINE);
        assertTrue(outcome.valid());
        assertEquals(-1, outcome.delta());
        assertNonTurn(outcome);
        assertEquals(0, round.health(FRONT));
        assertEquals(BACK, round.winner());
        assertEquals(2, round.remaining());
        assertEquals(1, random.calls);
        assertEndedGuards(round, random);
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, false", "true, true", "false, true"})
    void fatalSawShotClampsDamageAndEndsWithoutReload(boolean self, boolean cuffed) {
        FixedRandom random = new FixedRandom();
        DevilRouletteRound round = round(random, self ? 1 : 5, self ? 5 : 1, FRONT, true);
        round.use(RouletteItem.HANDSAW);
        if (cuffed) round.use(RouletteItem.HANDCUFFS);
        UUID target = self ? FRONT : BACK;
        DevilRouletteRound.Outcome outcome = round.fire(target);
        assertTrue(outcome.valid());
        assertEquals(1, outcome.shell());
        assertEquals(-1, outcome.delta());
        assertEquals(!cuffed, outcome.switched());
        assertEquals(cuffed, outcome.skipped());
        assertEquals(cuffed ? FRONT : BACK, round.operator());
        assertNull(round.cuffed());
        assertEquals(0, round.health(target));
        assertEquals(round.opponent(target), round.winner());
        assertFalse(round.saw());
        assertEquals(0, round.remaining());
        assertEquals(0, random.calls);
        assertEndedGuards(round, random);
    }

    @Test
    void invalidActionsAndFailureQueriesDoNotMutateOrDrawRandomness() {
        FixedRandom random = new FixedRandom();
        DevilRouletteRound round = round(random, 5, 5, FRONT, true, false);
        round.use(RouletteItem.HANDSAW);
        round.use(RouletteItem.HANDCUFFS);
        Snapshot before = snapshot(round);
        assertRejected(round, () -> round.fire(UNKNOWN));
        assertRejected(round, () -> round.fire(null));
        assertRejected(round, () -> round.use(null));
        assertRejected(round, () -> round.use(RouletteItem.ADRENALINE));
        assertRejected(round, () -> round.use(RouletteItem.CIGARETTE));
        assertRejected(round, () -> round.use(RouletteItem.HANDSAW));
        assertRejected(round, () -> round.use(RouletteItem.HANDCUFFS));
        assertEquals("roulette.error.adrenaline", round.failure(RouletteItem.ADRENALINE));
        assertEquals("roulette.error.item", round.failure(null));
        assertNull(round.failure(RouletteItem.BEER));
        assertNull(round.failure(RouletteItem.TELEPHONE));
        assertEquals(0, round.health(UNKNOWN));
        assertEquals(0, round.health(null));
        assertNull(round.opponent(UNKNOWN));
        assertNull(round.opponent(null));
        round.forfeit(UNKNOWN);
        round.forfeit(null);
        assertEquals(before, snapshot(round));
        assertEquals(0, random.calls);
    }

    @Test
    void emptyChamberRejectsAllActionsButAllowsExplicitReload() {
        FixedRandom random = new FixedRandom();
        DevilRouletteRound round = round(random, 5, 5, FRONT);
        assertRejected(round, () -> round.fire(FRONT));
        assertRejected(round, () -> round.fire(BACK));
        for (RouletteItem kind : RouletteItem.values()) {
            assertEquals("roulette.error.empty", round.failure(kind));
            assertRejected(round, () -> round.use(kind));
        }
        assertRejected(round, () -> round.use(null));
        assertFalse(round.peek());
        assertEquals(0, random.calls);
        round.reload();
        assertEquals(2, round.remaining());
        assertFalse(round.ended());
    }

    @Test
    void uuidValuesAreComparedByEqualityIncludingOperatorAndForfeit() {
        FixedRandom random = new FixedRandom();
        UUID copiedFront = new UUID(FRONT.getMostSignificantBits(), FRONT.getLeastSignificantBits());
        UUID copiedBack = UUID.fromString(BACK.toString());
        assertNotSame(FRONT, copiedFront);
        assertNotSame(BACK, copiedBack);
        DevilRouletteRound round = round(random, 5, 5, copiedFront, false, true);
        assertEquals(BACK, round.opponent(copiedFront));
        assertEquals(FRONT, round.opponent(copiedBack));
        assertEquals(5, round.health(copiedFront));
        assertFalse(round.fire(copiedFront).switched());
        assertTrue(round.fire(copiedBack).switched());
        assertEquals(4, round.health(copiedBack));
        round.forfeit(copiedBack);
        assertEquals(FRONT, round.winner());
        assertEquals(0, round.health(BACK));
        assertEndedGuards(round, random);
        assertThrows(IllegalArgumentException.class, () -> new DevilRouletteRound(FRONT, copiedFront, random));
    }

    @Test
    void deterministicConstructorCopiesChamberAndRejectsInvalidState() {
        List<Boolean> shells = new ArrayList<>(List.of(true, false));
        DevilRouletteRound round = new DevilRouletteRound(FRONT, BACK, new FixedRandom(), shells, 5, 5, FRONT);
        shells.set(0, false);
        shells.clear();
        assertTrue(round.peek());
        assertEquals(2, round.remaining());
        assertThrows(IllegalArgumentException.class,
                () -> new DevilRouletteRound(FRONT, BACK, new FixedRandom(), List.of(), 0, 5, FRONT));
        assertThrows(IllegalArgumentException.class,
                () -> new DevilRouletteRound(FRONT, BACK, new FixedRandom(), List.of(), 5, 6, FRONT));
        assertThrows(IllegalArgumentException.class,
                () -> new DevilRouletteRound(FRONT, BACK, new FixedRandom(), List.of(), 5, 5, UNKNOWN));
    }

    @Test
    void reloadUsesInjectedRandomAndFisherYatesShuffle() {
        FixedRandom random = new FixedRandom();
        DevilRouletteRound round = round(random, 5, 5, FRONT);
        round.reload();
        assertEquals(2, round.remaining());
        assertEquals(1, round.loadedLive());
        assertEquals(1, round.loadedBlanks());
        assertEquals(2, random.calls);
        assertEquals(0, round.use(RouletteItem.BEER).shell());
        assertEquals(1, round.use(RouletteItem.BEER).shell());
    }

    @Test
    void reloadsAcrossSeedsAreMixedBalancedBoundedAndReproducible() {
        Set<Integer> totals = new HashSet<>();
        Set<UUID> operators = new HashSet<>();
        for (int seed = 0; seed < 256; seed++) {
            long mixedSeed = seed * 0x9E3779B97F4A7C15L;
            DevilRouletteRound first = new DevilRouletteRound(FRONT, BACK, new Random(mixedSeed));
            DevilRouletteRound second = new DevilRouletteRound(FRONT, BACK, new Random(mixedSeed));
            operators.add(first.operator());
            assertEquals(first.operator(), second.operator());
            for (int reload = 0; reload < 3; reload++) {
                first.reload();
                second.reload();
                int total = first.remaining();
                int live = first.loadedLive();
                int blanks = first.loadedBlanks();
                totals.add(total);
                assertTrue(total >= 2 && total <= 8);
                assertTrue(live > 0 && blanks > 0);
                assertTrue(Math.abs(live - blanks) <= 1);
                assertEquals(total, live + blanks);
                assertEquals(snapshot(first), snapshot(second));
                int observedLive = 0;
                for (int i = 0; i < total; i++) {
                    assertEquals(first.peek(), second.peek());
                    DevilRouletteRound.Outcome a = first.use(RouletteItem.BEER);
                    DevilRouletteRound.Outcome b = second.use(RouletteItem.BEER);
                    assertEquals(a, b);
                    observedLive += a.shell();
                    assertEquals(live, first.loadedLive());
                    assertEquals(blanks, first.loadedBlanks());
                }
                assertEquals(live, observedLive);
                assertEquals(0, first.remaining());
                assertFalse(first.ended());
            }
        }
        assertEquals(Set.of(2, 3, 4, 5, 6, 7, 8), totals);
        assertEquals(Set.of(FRONT, BACK), operators);
    }

    private static DevilRouletteRound round(Boolean... shells) {
        return round(new FixedRandom(), 5, 5, FRONT, shells);
    }

    private static DevilRouletteRound round(RandomGenerator random, int frontHealth, int backHealth,
            UUID operator, Boolean... shells) {
        return new DevilRouletteRound(FRONT, BACK, random, List.of(shells), frontHealth, backHealth, operator);
    }

    private static void assertNonTurn(DevilRouletteRound.Outcome outcome) {
        assertTrue(outcome.valid());
        assertFalse(outcome.switched());
        assertFalse(outcome.skipped());
    }

    private static void assertNoPrivateInformation(DevilRouletteRound.Outcome outcome) {
        assertEquals(-1, outcome.knownOffset());
        assertEquals(-1, outcome.knownShell());
    }

    private static void assertRejected(DevilRouletteRound round, Supplier<DevilRouletteRound.Outcome> action) {
        Snapshot before = snapshot(round);
        DevilRouletteRound.Outcome outcome = action.get();
        assertFalse(outcome.valid());
        assertEquals(round.operator(), outcome.actor());
        assertEquals(-1, outcome.shell());
        assertEquals(0, outcome.delta());
        assertFalse(outcome.switched());
        assertFalse(outcome.skipped());
        assertNoPrivateInformation(outcome);
        assertTrue(outcome.message().startsWith("roulette.error."));
        assertEquals(before, snapshot(round));
    }

    private static void assertEndedGuards(DevilRouletteRound round, FixedRandom random) {
        assertTrue(round.ended());
        assertFalse(round.peek());
        Snapshot before = snapshot(round);
        int calls = random.calls;
        assertRejected(round, () -> round.fire(FRONT));
        assertRejected(round, () -> round.fire(BACK));
        assertRejected(round, () -> round.fire(UNKNOWN));
        assertRejected(round, () -> round.fire(null));
        for (RouletteItem kind : RouletteItem.values()) {
            assertEquals("roulette.error.ended", round.failure(kind));
            assertRejected(round, () -> round.use(kind));
        }
        assertRejected(round, () -> round.use(null));
        round.reload();
        round.forfeit(FRONT);
        round.forfeit(BACK);
        round.forfeit(UNKNOWN);
        round.forfeit(null);
        assertEquals(before, snapshot(round));
        assertEquals(calls, random.calls);
    }

    private static Snapshot snapshot(DevilRouletteRound round) {
        return new Snapshot(round.operator(), round.health(FRONT), round.health(BACK), round.winner(),
                round.ended(), round.remaining(), round.loadedLive(), round.loadedBlanks(),
                round.saw(), round.cuffed(), round.peek());
    }

    private record Snapshot(UUID operator, int frontHealth, int backHealth, UUID winner, boolean ended,
            int remaining, int live, int blanks, boolean saw, UUID cuffed, boolean next) { }

    private static final class FixedRandom implements RandomGenerator {
        private boolean bool;
        private double chance;
        private int index;
        private int calls;
        private int lastBound;

        @Override
        public long nextLong() { calls++; return 0; }

        @Override
        public boolean nextBoolean() { calls++; return bool; }

        @Override
        public double nextDouble() { calls++; return chance; }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("Non-positive bound");
            calls++;
            lastBound = bound;
            return Math.floorMod(index, bound);
        }

        @Override
        public int nextInt(int origin, int bound) {
            return origin + nextInt(bound - origin);
        }
    }
}
