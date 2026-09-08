/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
