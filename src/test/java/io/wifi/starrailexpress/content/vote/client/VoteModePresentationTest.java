package io.wifi.starrailexpress.content.vote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VoteModePresentationTest {
    @Test
    void normalizesVoteAndRegistryModeIdsForLocalization() {
        assertEquals("role_rotation", VoteModePresentation.path("mode:role_rotation"));
        assertEquals("role_rotation", VoteModePresentation.path("haiman:role_rotation"));
        assertEquals("role_rotation_single_select",
                VoteModePresentation.path("haiman:role_rotation_single_select"));
        assertEquals("", VoteModePresentation.path(null));
    }
}
