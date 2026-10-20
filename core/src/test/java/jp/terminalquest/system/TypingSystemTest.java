package jp.terminalquest.system;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TypingSystemTest {
    @Test void sixtySecondClockRejectsLateCommandsAndClampsOvershoot() {
        TypingSystem typing = new TypingSystem(1);
        assertFalse(typing.submit("pwd"));
        typing.start();
        assertTrue(typing.submit("pwd"));
        typing.update(59.5f);
        assertFalse(typing.isFinished());
        assertEquals(0.5f, typing.getRemainingSeconds(), 0.001f);
        typing.update(10f);
        assertTrue(typing.isFinished());
        assertEquals(0f, typing.getRemainingSeconds());
        assertFalse(typing.submit(typing.getPrompt()));
        assertEquals(1, typing.getCompleted());
        assertEquals(1, typing.getAttempts());
    }

    @Test void exactCommandsAdvancePromptAndMistakesResetOnlyCurrentCombo() {
        TypingSystem typing = new TypingSystem(1);
        typing.start();
        assertTrue(typing.submit("pwd"));
        assertTrue(typing.submit("ls"));
        String prompt = typing.getPrompt();
        assertFalse(typing.submit("wrong"));
        assertEquals(prompt, typing.getPrompt());
        assertEquals(0, typing.getCombo());
        assertEquals(2, typing.getMaxCombo());
        assertTrue(typing.submit(prompt));
        assertEquals(3, typing.getCompleted());
        assertEquals(4, typing.getAttempts());
    }

    @Test void accuracyPenalizesMissingExtraAndWrongCharacters() {
        TypingSystem typing = new TypingSystem(1);
        typing.start();
        assertFalse(typing.submit("pw")); // 2 matches / 3 expected.
        assertEquals(200f / 3f, typing.getAccuracy(), 0.001f);
        assertFalse(typing.submit("pwdx")); // 3 matches / 4 entered.
        assertEquals(500f / 7f, typing.getAccuracy(), 0.001f);
        assertFalse(typing.submit("pXd")); // 2 matches / 3 expected.
        assertEquals(70f, typing.getAccuracy(), 0.001f);
        typing.update(30f);
        assertEquals(18f, typing.getKpm(), 0.001f); // 9 characters in half a minute.
    }

    @Test void restartClearsMetricsAndEachOfSixTiersOffersDistinctPractice() {
        for (int level = 1; level <= 6; level++) {
            TypingSystem typing = new TypingSystem(level);
            typing.start();
            assertTrue(typing.getPrompt().length() > 0);
            assertTrue(typing.submit(typing.getPrompt()));
            typing.update(60f);
            typing.start();
            assertEquals(0, typing.getCompleted());
            assertEquals(0, typing.getAttempts());
            assertEquals(100f, typing.getAccuracy());
            assertEquals(60f, typing.getRemainingSeconds());
            assertFalse(typing.isFinished());
        }
        assertThrows(IllegalArgumentException.class, () -> new TypingSystem(0));
        assertThrows(IllegalArgumentException.class, () -> new TypingSystem(7));
    }

    @Test void invalidFrameTimesCannotExtendOrCorruptTimer() {
        TypingSystem typing = new TypingSystem(1);
        typing.start();
        assertThrows(IllegalArgumentException.class, () -> typing.update(-1f));
        assertThrows(IllegalArgumentException.class, () -> typing.update(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> typing.update(Float.POSITIVE_INFINITY));
        assertEquals(60f, typing.getRemainingSeconds());
    }

    @Test void learnedOnlyPracticeNeverOffersUnlearnedCommandsOrFeatures() {
        Set<String> learned = new HashSet<String>(Arrays.asList("pwd", "cd"));
        assertEquals(3, TypingSystem.availablePromptCount(1, learned));
        TypingSystem typing = new TypingSystem(1, learned);
        typing.start();
        for (int i = 0; i < 15; i++) {
            assertFalse(typing.getPrompt().startsWith("ls"));
            assertTrue(typing.submit(typing.getPrompt()));
        }
        assertEquals(0, TypingSystem.availablePromptCount(4, new HashSet<String>(Arrays.asList("grep"))));
        Set<String> advanced = new HashSet<String>(Arrays.asList("sort", "uniq", "|"));
        assertEquals(2, TypingSystem.availablePromptCount(4, advanced));
        TypingSystem pipeline = new TypingSystem(4, advanced);
        pipeline.start();
        assertTrue(pipeline.getPrompt().startsWith("sort"));
        assertFalse(pipeline.getPrompt().contains(">"));
        assertTrue(pipeline.submit(pipeline.getPrompt()));
    }

    @Test void noLearnedCommandsMeansNoInventedPractice() {
        Set<String> none = new HashSet<String>();
        for (int level = 1; level <= 6; level++) {
            assertEquals(0, TypingSystem.availablePromptCount(level, none));
            TypingSystem typing = new TypingSystem(level, none);
            assertEquals("", typing.getPrompt());
            assertThrows(IllegalStateException.class, typing::start);
            assertFalse(typing.submit("pwd"));
        }
    }
}
