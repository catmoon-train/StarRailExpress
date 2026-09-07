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
