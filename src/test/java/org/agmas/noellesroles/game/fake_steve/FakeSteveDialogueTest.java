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

package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveDialogueTest {
    @Test
    void commonPhraseLibraryIsLargeEnoughToAvoidObviousRepetition() {
        assertTrue(FakeSteveDialogue.commonPhraseCount() >= 100);
    }

    @Test
    void roleQuestionsAreRecognizedAsDirectedConversation() {
        assertTrue(FakeSteveDialogue.isDirectedRoleQuestion("你什么职业？"));
        assertTrue(FakeSteveDialogue.isDirectedRoleQuestion("你是干什么的"));
        assertFalse(FakeSteveDialogue.isDirectedRoleQuestion("有人看到尸体吗"));
        assertFalse(FakeSteveDialogue.directedRoleReply(3).isBlank());
    }
}
