package jp.terminalquest.mission;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.terminalquest.system.ProgressionSystem;
import jp.terminalquest.system.RankConfig;
import jp.terminalquest.terminal.CommandResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class MissionTest {
    static List<Mission> missions() { return MissionCatalog.all(); }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("missions")
    void everyMissionHasAnExecutableDocumentedSolution(Mission mission) {
        MissionSession session = new MissionSession(mission);
        assertFalse(session.isComplete(), "A mission must require player action: " + mission.id);
        for (String command : mission.exampleCommands) {
            CommandResult result = session.execute(command);
            assertTrue(result.isSuccess() || (command.equals("systemctl status web") && result.getExitCode() == 3),
                mission.id + " command: " + command + " => " + result.getOutput());
        }
        assertTrue(session.isComplete(), "Documented solution failed: " + mission.id);
        assertNotEquals("-", session.getRank());
    }

    @Test void curriculumHasSixLevelsFiveMissionsEachAndUniqueIds() {
        assertEquals(6, DifficultyLevel.all().size());
        assertEquals(30, MissionCatalog.all().size());
        Set<String> ids = new HashSet<String>();
        for (DifficultyLevel level : DifficultyLevel.all()) {
            assertEquals(5, MissionCatalog.forLevel(level.id).size());
            assertTrue(level.implemented);
            assertFalse(level.description.isEmpty());
            int bosses = 0;
            for (Mission mission : MissionCatalog.forLevel(level.id)) {
                assertTrue(ids.add(mission.id));
                assertEquals(3, mission.hints.length);
                assertTrue(mission.conditions.length > 0);
                assertSame(mission, MissionCatalog.byId(mission.id));
                if (mission.boss) bosses++;
            }
            assertEquals(1, bosses);
        }
        assertThrows(IllegalArgumentException.class, () -> MissionCatalog.byId("missing"));
        assertThrows(IllegalArgumentException.class, () -> DifficultyLevel.byId(0));
    }

    @Test void finalBossAcceptsDifferentRecoveryOrdersAndChecksActualServiceState() {
        Mission mission = MissionCatalog.byId("L6-5");
        MissionSession first = new MissionSession(mission);
        first.execute("systemctl restart web");
        assertFalse(first.isComplete());
        first.execute("chmod 644 /etc/web/config");
        first.execute("kill 42");
        first.execute("cat /dev/null > /var/log/archive.log");
        assertFalse(first.isComplete(), "Fixing prerequisites alone is not service recovery");
        first.execute("systemctl start web");
        assertTrue(first.isComplete());
        assertTrue(first.getEngine().getServices().isActive("web"));
        MissionSession second = new MissionSession(mission);
        second.execute("echo recovered > /var/log/archive.log");
        second.execute("kill 42");
        second.execute("chmod 644 /etc/web/config");
        second.execute("systemctl restart web");
        assertTrue(second.isComplete());
    }

    @Test void copyingInsteadOfMovingDoesNotCompleteFileManagementBoss() {
        MissionSession session = new MissionSession(MissionCatalog.byId("L2-5"));
        session.execute("mkdir backup");
        session.execute("cp readme.txt backup/readme.txt");
        session.execute("cp notes.txt backup/operations.txt");
        assertFalse(session.isComplete());
        session.execute("mv notes.txt backup/operations.txt");
        assertTrue(session.isComplete());
    }

    @Test void observationsMustInspectTheRequestedDataAndOptions() {
        MissionSession tail = new MissionSession(MissionCatalog.byId("L3-3"));
        tail.execute("tail readme.txt");
        assertFalse(tail.isComplete());
        tail.execute("cat logs/app.log | tail -n 2");
        assertTrue(tail.isComplete());
        MissionSession wc = new MissionSession(MissionCatalog.byId("L3-4"));
        wc.execute("wc -c logs/app.log");
        wc.execute("wc -l readme.txt");
        assertFalse(wc.isComplete());
        wc.execute("cat logs/app.log | wc -l");
        assertTrue(wc.isComplete());
        MissionSession permissions = new MissionSession(MissionCatalog.byId("L5-1"));
        permissions.execute("ls /etc/web/config");
        assertFalse(permissions.isComplete());
        permissions.execute("ls -l /etc/web/config");
        assertTrue(permissions.isComplete());
    }

    @Test void failedServiceStatusIsValidDiagnosisNotAPlayerExecutionError() {
        MissionSession session = new MissionSession(MissionCatalog.byId("L6-1"));
        assertEquals(3, session.execute("systemctl status web").getExitCode());
        assertEquals(0, session.getErrorCount());
        session.execute("journalctl -u web");
        assertTrue(session.isComplete());
        assertTrue(session.getUsedCommands().contains("systemctl"));
    }

    @Test void beginnerHintsAreAutomaticButOptionalHintsAreCappedAndRanked() {
        MissionSession beginner = new MissionSession(MissionCatalog.byId("L1-1"));
        assertFalse(beginner.getInitialHint().isEmpty());
        assertEquals(0, beginner.getHintCount());
        beginner.revealHint();
        beginner.revealHint();
        beginner.revealHint();
        beginner.revealHint();
        assertEquals(3, beginner.getHintCount());
        beginner.execute("pwd");
        assertEquals("B", beginner.getRank());
        MissionSession advanced = new MissionSession(MissionCatalog.byId("L6-5"));
        assertEquals("", advanced.getInitialHint());
    }

    @Test void ranksAreConfigurableAndFailuresNeverPreventCompletion() {
        RankConfig config = new RankConfig();
        config.cMinErrors = 2;
        MissionSession session = new MissionSession(MissionCatalog.byId("L1-1"), config);
        session.execute("missing");
        session.execute("cat missing");
        session.execute("pwd");
        assertTrue(session.isComplete());
        assertEquals("C", session.getRank());
        assertEquals(2, session.getErrorCount());
        assertEquals(3, session.getCommandCount());
    }

    @Test void replayStartsWithAnIsolatedFreshWorld() {
        Mission mission = MissionCatalog.byId("L6-5");
        MissionSession first = new MissionSession(mission);
        first.execute("chmod 644 /etc/web/config");
        first.execute("kill 42");
        first.execute("echo cleared > /var/log/archive.log");
        MissionSession second = new MissionSession(mission);
        assertEquals(0, second.getEngine().getFileSystem().getPermissions("/etc/web/config"));
        assertTrue(second.getEngine().getProcesses().isRunning(42));
        assertTrue(second.getEngine().getFileSystem().usedBytes() > 900000000L);
    }

    @Test void pipelineAndRedirectFeaturesAreRecordedAsLearnedSkills() {
        MissionSession session = new MissionSession(MissionCatalog.byId("L4-3"));
        session.execute("echo marker >> practice.txt");
        session.execute("sort data/hosts.txt | uniq > unique.txt");
        assertTrue(session.isComplete());
        assertTrue(session.getUsedCommands().contains("|"));
        assertTrue(session.getUsedCommands().contains(">"));
        assertTrue(session.getUsedCommands().contains(">>"));
    }
}
