package jp.terminalquest.system;

import jp.terminalquest.mission.DifficultyLevel;
import jp.terminalquest.mission.Mission;
import jp.terminalquest.mission.MissionCatalog;
import jp.terminalquest.mission.MissionSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionSystemTest {
    private MissionSession solve(String id) {
        Mission mission = MissionCatalog.byId(id);
        MissionSession session = new MissionSession(mission);
        for (String command : mission.exampleCommands) session.execute(command);
        assertTrue(session.isComplete(), id);
        return session;
    }

    @Test void normalsUnlockBossThenBossUnlocksNextLevel() {
        ProgressionSystem progress = new ProgressionSystem();
        Mission boss = MissionCatalog.byId("L1-5");
        assertTrue(progress.isLevelUnlocked(1));
        assertFalse(progress.isLevelUnlocked(2));
        assertFalse(progress.isMissionUnlocked(boss));
        assertFalse(progress.complete(solve("L2-1")), "Locked sessions cannot award progress");
        for (int i = 1; i <= 4; i++) {
            assertTrue(progress.complete(solve("L1-" + i)));
            assertEquals(i == 4, progress.isMissionUnlocked(boss));
        }
        assertFalse(progress.isLevelUnlocked(2));
        progress.complete(solve(boss.id));
        assertTrue(progress.isLevelUnlocked(2));
        assertEquals(5, progress.completedCount(1));
        assertEquals(600, progress.getXp());
        assertTrue(progress.isConsistent());
    }

    @Test void replayCanImproveRankButCannotFarmXpOrDuplicateOneSession() {
        ProgressionSystem progress = new ProgressionSystem();
        MissionSession hinted = new MissionSession(MissionCatalog.byId("L1-1"));
        hinted.revealHint();
        hinted.execute("pwd");
        assertTrue(progress.complete(hinted));
        assertEquals("B", progress.bestRank("L1-1"));
        assertFalse(progress.complete(hinted));
        assertEquals(1, progress.getCommandMastery("pwd"));
        assertFalse(progress.complete(solve("L1-1")));
        assertEquals("S", progress.bestRank("L1-1"));
        assertEquals(100, progress.getXp());
        assertEquals(2, progress.getCommandMastery("pwd"));
    }

    @Test void wholeCurriculumCompletesWithAllUnlocksAndExpectedXp() {
        ProgressionSystem progress = new ProgressionSystem();
        int expectedXp = 0;
        for (Mission mission : MissionCatalog.all()) {
            assertTrue(progress.isMissionUnlocked(mission), mission.id);
            assertTrue(progress.complete(solve(mission.id)));
            expectedXp += ProgressionSystem.rewardXp(mission);
        }
        assertEquals(30, progress.getCompletedIds().size());
        assertEquals(expectedXp, progress.getXp());
        assertTrue(progress.isConsistent());
        assertTrue(progress.getAchievements().contains("ALL CLEAR：全30ミッションをクリア"));
        assertTrue(progress.getMasteredCommands().contains("systemctl"));
        assertFalse(progress.isLevelUnlocked(7));
        assertFalse(progress.isLevelUnlocked(0));
    }

    @Test void typingScoresCannotAffectMissionProgress() {
        ProgressionSystem progress = new ProgressionSystem();
        progress.recordTyping(6, 12, 96f, 200f, 9);
        assertEquals(12, progress.getTypingBest(6));
        assertEquals(96f, progress.getTypingAccuracy(6));
        progress.recordTyping(6, 3, 100f, 20f, 3);
        assertEquals(12, progress.getTypingBest(6));
        assertEquals(0, progress.getXp());
        assertFalse(progress.isLevelUnlocked(2));
        assertThrows(IllegalArgumentException.class, () -> progress.recordTyping(6, 1, 100f, 100f, 2));
        assertThrows(IllegalArgumentException.class, () -> progress.recordTyping(6, 1, Float.NaN, 100f, 1));
    }
}
