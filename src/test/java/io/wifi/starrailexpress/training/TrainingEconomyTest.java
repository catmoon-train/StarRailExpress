package io.wifi.starrailexpress.training;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainingEconomyTest {
    @Test void everyClickAddsOneThousandToExistingCoins() {
        int balance = 0;
        for (int i = 1; i <= 10; i++) {
            balance = TrainingSession.addTrainingCoins(balance);
            assertEquals(i * 1000, balance);
        }
    }

    @Test void largerOrPartlySpentBalancesAreNotReset() {
        assertEquals(11_000, TrainingSession.addTrainingCoins(10_000));
        assertEquals(1_250, TrainingSession.addTrainingCoins(250));
    }

    @Test void addingNearIntegerLimitSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, TrainingSession.addTrainingCoins(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, TrainingSession.addTrainingCoins(Integer.MAX_VALUE - 500));
        assertEquals(Integer.MAX_VALUE, TrainingSession.addTrainingCoins(Integer.MAX_VALUE - 1000));
    }

    @Test void debtReceivesTheSameIncrementWithoutUnexpectedReset() {
        assertEquals(500, TrainingSession.addTrainingCoins(-500));
        assertEquals(-1000, TrainingSession.addTrainingCoins(-2000));
        assertEquals(Integer.MIN_VALUE + 1000, TrainingSession.addTrainingCoins(Integer.MIN_VALUE));
    }
}
