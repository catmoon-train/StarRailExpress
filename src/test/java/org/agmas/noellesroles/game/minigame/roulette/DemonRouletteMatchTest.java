package org.agmas.noellesroles.game.minigame.roulette;

import org.agmas.noellesroles.game.minigame.DevilRouletteGame.DemonMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DemonRouletteMatchTest {
    private static final UUID DEMON = new UUID(1, 1);
    private static final UUID OPPONENT = new UUID(2, 2);

    @Test void asymmetricInitialAndMaximumHealthDoNotChangeOrdinaryDefaults() {
        var round = new DevilRouletteRound(DEMON, OPPONENT, new Random(0), 6, 5);
        assertEquals(6, round.health(DEMON));
        assertEquals(6, round.maxHealth(DEMON));
        assertEquals(5, round.health(OPPONENT));
        assertEquals(5, round.maxHealth(OPPONENT));
        assertEquals(0, round.maxHealth(new UUID(3, 3)));
        var ordinary = new DevilRouletteRound(DEMON, OPPONENT, new Random(0));
        assertEquals(5, ordinary.health(DEMON));
        assertEquals(5, ordinary.maxHealth(DEMON));
        assertEquals(5, ordinary.maxHealth(OPPONENT));
        round.forfeit(DEMON);
        assertEquals(0, round.health(DEMON));
        assertEquals(OPPONENT, round.winner());
    }

    @Test void cigarettesHealEachSideToItsOwnMaximum() {
        for (UUID operator : List.of(DEMON, OPPONENT)) {
            var round = new DevilRouletteRound(DEMON, OPPONENT, new Random(0), List.of(false, true),
                    5, 4, operator, 6, 5);
            assertNull(round.failure(RouletteItem.CIGARETTE));
            assertEquals(1, round.use(RouletteItem.CIGARETTE).delta());
            assertEquals(round.maxHealth(operator), round.health(operator));
            assertEquals("roulette.error.full_health", round.failure(RouletteItem.CIGARETTE));
            assertFalse(round.use(RouletteItem.CIGARETTE).valid());
        }
    }

    @Test void medicineCanReachSixButNeverRaisesOpponentAboveFive() {
        for (UUID operator : List.of(DEMON, OPPONENT)) {
            Random healing = new Random(0) {
                @Override public double nextDouble() { return 0; }
            };
            var round = new DevilRouletteRound(DEMON, OPPONENT, healing, List.of(false, true),
                    4, 4, operator, 6, 5);
            assertEquals(operator.equals(DEMON) ? 2 : 1, round.use(RouletteItem.EXPIRED_MEDICINE).delta());
            assertEquals(round.maxHealth(operator), round.health(operator));
            assertEquals(0, round.use(RouletteItem.EXPIRED_MEDICINE).delta());
        }
    }

    @Test void initialBonusIsOneItemAndNeverRepeatsOnReload() {
        for (int amount = 2; amount <= 5; amount++) {
            var match = new DemonMatch(DEMON, OPPONENT, (winner, loser) -> fail("Unexpected settlement"));
            var random = new Random(0);
            match.reserve(amount, 0, 0, random);
            assertEquals(amount + 1, drain(match, DEMON));
            assertEquals(amount, drain(match, OPPONENT));
            for (int reload = 2; reload <= 5; reload++) {
                match.reserve(reload, 0, 0, random);
                assertEquals(reload, drain(match, DEMON));
                assertEquals(reload, drain(match, OPPONENT));
            }
        }
    }

    @Test void localSuppliesCountPendingAndCarriedItemsAndResetDiscardsEverything() {
        var calls = new AtomicInteger();
        var match = new DemonMatch(DEMON, OPPONENT, (winner, loser) -> calls.incrementAndGet());
        match.reserve(5, 7, 7, new Random(0));
        assertEquals(1, drain(match, DEMON));
        assertEquals(1, drain(match, OPPONENT));
        match.reserve(2, 0, 0, new Random(0));
        assertEquals(2, match.pending(DEMON)); // 首次被容量截断的奖励不顺延。
        assertEquals(2, match.pending(OPPONENT));
        match.reserve(5, 1, 1, new Random(0));
        assertEquals(7, match.pending(DEMON));
        assertEquals(7, match.pending(OPPONENT));
        match.reserve(5, 1, 1, new Random(0));
        assertEquals(7, match.pending(DEMON));
        assertEquals(7, match.pending(OPPONENT));
        match.reset();
        assertEquals(0, match.pending(DEMON));
        assertEquals(0, match.pending(OPPONENT));
        assertNull(match.peek(DEMON));
        match.reserve(5, 0, 0, new Random(0));
        assertEquals(0, match.pending(DEMON));
        assertFalse(match.settle(DEMON, OPPONENT));
        assertEquals(0, calls.get());
    }

    @Test void settlementClearsQueuesBeforeCallbackAndRejectsReentrantOrRepeatedDelivery() {
        for (UUID winner : List.of(DEMON, OPPONENT)) {
            UUID loser = winner.equals(DEMON) ? OPPONENT : DEMON;
            var calls = new AtomicInteger();
            var reference = new DemonMatch[1];
            reference[0] = new DemonMatch(DEMON, OPPONENT, (actualWinner, actualLoser) -> {
                calls.incrementAndGet();
                assertEquals(winner, actualWinner);
                assertEquals(loser, actualLoser);
                assertEquals(0, reference[0].pending(DEMON));
                assertEquals(0, reference[0].pending(OPPONENT));
                assertFalse(reference[0].settle(winner, loser));
                reference[0].reset();
            });
            reference[0].reserve(5, 0, 0, new Random(0));
            assertFalse(reference[0].settle(winner, winner));
            assertFalse(reference[0].settle(new UUID(3, 3), loser));
            assertTrue(reference[0].settle(winner, loser));
            assertFalse(reference[0].settle(winner, loser));
            assertEquals(1, calls.get());
        }
    }

    @Test void throwingCallbackCannotBeRetried() {
        var calls = new AtomicInteger();
        var match = new DemonMatch(DEMON, OPPONENT, (winner, loser) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Settlement failure");
        });
        assertThrows(IllegalStateException.class, () -> match.settle(DEMON, OPPONENT));
        assertFalse(match.settle(DEMON, OPPONENT));
        assertEquals(1, calls.get());
    }

    @Test void snapshotRoundTripPreservesAsymmetricMaximaAndOldTagsDefaultToFive() {
        var tag = RouletteView.idle().toTag();
        tag.putUUID("Front", DEMON);
        tag.putUUID("Back", OPPONENT);
        tag.putInt("FrontMaxHealth", 6);
        tag.putInt("FrontHealth", 6);
        tag.putInt("BackHealth", 6);
        var view = RouletteView.fromTag(tag);
        assertEquals(6, view.health(DEMON));
        assertEquals(6, view.maxHealth(DEMON));
        assertEquals(5, view.health(OPPONENT));
        assertEquals(5, view.maxHealth(OPPONENT));
        assertEquals(view, RouletteView.fromTag(view.toTag()));
        tag.remove("FrontMaxHealth");
        tag.remove("BackMaxHealth");
        var old = RouletteView.fromTag(tag);
        assertEquals(5, old.maxHealth(DEMON));
        assertEquals(5, old.maxHealth(OPPONENT));
        assertEquals(5, old.health(DEMON));
    }

    private static int drain(DemonMatch match, UUID player) {
        int count = 0;
        while (match.peek(player) != null) {
            match.delivered(player);
            count++;
        }
        assertEquals(0, match.pending(player));
        return count;
    }
}
