package io.wifi.starrailexpress.stats;

import io.wifi.starrailexpress.game.data.PlayerStatsData;
import io.wifi.starrailexpress.util.PlayerStatsSerializer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** MVP 次数必须同时存在于玩家总计和职业统计，并且在存档/同步的 JSON 往返后不丢。 */
class PlayerStatsMvpTest {
    @Test
    void mvpCountersSurviveSerializationRoundTrip() {
        UUID id = UUID.randomUUID();
        PlayerStats stats = new PlayerStats(id);
        stats.incrementTotalMvps();
        stats.incrementTotalMvps();
        ResourceLocation role = ResourceLocation.tryParse("starrailexpress:detective");
        assertNotNull(role);
        stats.getOrCreateRoleStats(role).incrementMvpsAsRole();

        PlayerStatsData data = PlayerStatsSerializer.fromJson(PlayerStatsSerializer.toJson(stats, 123L));
        PlayerStats restored = new PlayerStats(id);
        restored.replaceWith(data);

        assertEquals(2, restored.getTotalMvps());
        assertEquals(1, restored.getRoleStats().get(role).getMvpsAsRole());
    }

    @Test
    void missingMvpFieldsReadAsZero() {
        PlayerStatsData legacy = PlayerStatsSerializer.fromJson("{\"totalWins\":3}");
        PlayerStats restored = new PlayerStats(UUID.randomUUID());
        restored.replaceWith(legacy);
        assertEquals(0, restored.getTotalMvps());
        assertEquals(3, restored.getTotalWins());
    }
}
