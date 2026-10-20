package jp.terminalquest.terminal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerminalEngineTest {
    private TerminalEngine engine;
    @BeforeEach void setUp() { engine = new TerminalEngine(); }

    @Test void navigationSupportsRelativeAbsoluteAndParentPaths() {
        assertEquals("/home/cadet\n", ok("pwd"));
        ok("cd logs"); assertEquals("/home/cadet/logs\n", ok("pwd"));
        ok("cd ../data/./../logs"); assertEquals("/home/cadet/logs", engine.getFileSystem().pwd());
        ok("cd /srv/data/backups"); ok("cd ~"); assertEquals("/home/cadet", engine.getFileSystem().pwd());
        ok("cd ../../../../../"); assertEquals("/", engine.getFileSystem().pwd());
    }

    @Test void lsShowsDirectoryTypesHiddenFilesAndPermissions() {
        ok("touch .secret"); assertFalse(ok("ls").contains(".secret"));
        assertTrue(ok("ls -a").contains(".secret")); assertTrue(ok("ls").contains("logs/"));
        assertEquals("---------- 000 24 config\n", ok("ls -l /etc/web/config"));
    }

    @Test void fileManagementCopiesThenMovesWithoutLosingContents() {
        ok("mkdir -p project/backups"); ok("touch project/notes.txt");
        ok("echo 'incident resolved' > project/notes.txt");
        ok("cp project/notes.txt project/backups"); ok("mv project/backups/notes.txt project/backups/report.txt");
        assertEquals("incident resolved\n", ok("cat project/backups/report.txt"));
        assertFalse(engine.getFileSystem().exists("project/backups/notes.txt"));
        ok("cp -r project copied"); assertEquals("incident resolved\n", ok("cat copied/backups/report.txt"));
    }

    @Test void touchingExistingFilePreservesData() {
        String original = ok("cat readme.txt"); ok("touch readme.txt"); assertEquals(original, ok("cat readme.txt"));
    }

    @Test void movingCurrentDirectoryKeepsCwdValid() {
        ok("mkdir work"); ok("cd work"); ok("mv /home/cadet/work /home/cadet/renamed");
        assertEquals("/home/cadet/renamed\n", ok("pwd"));
    }

    @Test void grepSearchesLiteralTextWithLineNumbersAndCaseOption() {
        String text = ok("grep -ni error logs/app.log");
        assertTrue(text.contains("11:2026-01-15T03:14:00 ERROR disk full"));
        assertTrue(text.contains("12:2026-01-15T03:15:00 ERROR permission denied"));
        assertEquals(1, engine.execute("grep DOES_NOT_EXIST logs/app.log").getExitCode());
        assertEquals("", engine.execute("grep DOES_NOT_EXIST logs/app.log").getOutput());
    }

    @Test void findGlobsAreBoundedVirtualTraversal() {
        String results = ok("find /home -name '*.log' -type f");
        assertEquals("/home/cadet/logs/app.log\n", results);
        assertTrue(ok("find /srv -type d").contains("/srv/data/backups"));
        assertEquals("", ok("find . -name 'does-not-exist*'"));
    }

    @Test void headTailAndWcCountRealText() {
        assertEquals("web\napi\n", ok("head -n 2 data/hosts.txt"));
        assertEquals("db\napi\n", ok("tail -n 2 data/hosts.txt"));
        assertEquals("5 data/hosts.txt\n", ok("wc -l data/hosts.txt"));
        assertEquals("5 5 19 data/hosts.txt\n", ok("wc data/hosts.txt"));
        assertEquals("", ok("head -n 0 data/hosts.txt"));
        assertEquals("", ok("tail -n 0 data/hosts.txt"));
    }

    @Test void pipelineTransfersStdoutAndRedirectWritesOnlyVirtualFile() {
        ok("grep ERROR logs/app.log | head -n 2 > errors.txt");
        assertEquals("2026-01-15T03:14:00 ERROR disk full\n2026-01-15T03:15:00 ERROR permission denied\n", ok("cat errors.txt"));
        ok("echo 'review complete' >> errors.txt"); assertTrue(ok("tail -n 1 errors.txt").contains("review complete"));
        assertEquals("3\n", ok("cat errors.txt | wc -l"));
    }

    @Test void sortAndUniqRetainLinuxAdjacentDuplicateSemantics() {
        assertEquals("api\ndb\nweb\n", ok("sort data/hosts.txt | uniq"));
        assertEquals("2 api\n1 db\n2 web\n", ok("sort data/hosts.txt | uniq -c"));
        assertEquals("web\napi\nweb\ndb\napi\n", ok("uniq data/hosts.txt"));
        assertEquals("web\ndb\napi\n", ok("sort -r -u data/hosts.txt"));
    }

    @Test void failedUpstreamExitStatusStillPassesStdoutToNextCommand() {
        assertEquals("0\n", ok("grep NO_MATCH logs/app.log | wc -l"));
        assertTrue(ok("systemctl status web | grep Active").contains("Active: failed"));
    }

    @Test void duAllLocatesTheLargeFileInsteadOfOnlyShowingAggregate() {
        assertTrue(ok("du -ah /var/log").contains("/var/log/archive.log"));
        assertFalse(ok("du -sh /var/log").contains("archive.log"));
    }

    @Test void safeRecoveryUpdatesAllVirtualServerPrerequisites() {
        CommandResult initial = engine.execute("systemctl status web"); assertEquals(3, initial.getExitCode());
        assertTrue(initial.getOutput().contains("failed")); assertFalse(engine.getServices().isActive("web"));
        assertFalse(engine.execute("systemctl restart web").isSuccess());
        assertTrue(ok("journalctl -u web").contains("/etc/web/config"));
        assertTrue(ok("df -h").contains("96%")); assertTrue(ok("du -sh /var/log").contains("M"));
        ok("kill 42"); assertFalse(engine.getProcesses().isRunning(42));
        ok("chmod 644 /etc/web/config"); ok("cat /dev/null > /var/log/archive.log");
        assertEquals(0, engine.getFileSystem().size("/var/log/archive.log"));
        assertTrue(ok("systemctl restart web").contains("HTTP 200"));
        assertTrue(engine.getServices().isActive("web")); assertTrue(ok("systemctl status web").contains("active (running)"));
        ok("systemctl stop web"); assertFalse(engine.getServices().isActive("web"));
    }

    @Test void recoveryAllowsDifferentValidOrder() {
        ok("echo '' > /var/log/archive.log"); ok("chmod 640 /etc/web/config"); ok("kill -9 42");
        ok("systemctl start web"); assertTrue(engine.getServices().isActive("web"));
    }

    @Test void serviceHealthReactsToNewVirtualFailure() {
        ok("kill 42"); ok("chmod 644 /etc/web/config"); ok("echo fixed > /var/log/archive.log"); ok("systemctl restart web");
        ok("chmod 000 /etc/web/config"); assertFalse(engine.getServices().isActive("web"));
        assertEquals(3, engine.execute("systemctl status web").getExitCode());
    }

    @Test void permissionsAndProcessProtectionHaveMeaningfulErrors() {
        assertFalse(engine.execute("cat /etc/web/config").isSuccess());
        assertFalse(engine.execute("kill 1").isSuccess()); assertTrue(engine.getProcesses().isRunning(1));
        assertFalse(engine.execute("kill 999").isSuccess()); assertTrue(ok("ps aux").contains("42"));
        ok("chmod 444 readme.txt"); assertFalse(engine.execute("echo damage > readme.txt").isSuccess());
        assertEquals("Welcome to TERMINAL QUEST\n", ok("cat readme.txt"));
    }

    @Test void directoryWithoutSearchPermissionBlocksAccessAndEntryOperations() {
        ok("mkdir locked"); ok("echo safe > locked/existing"); ok("chmod 000 locked");
        String[] denied = {"touch locked/new", "cat locked/existing", "cp readme.txt locked/copy", "mkdir locked/nested", "mv readme.txt locked/moved", "cd locked", "chmod 644 locked/existing", "ls -l locked/existing"};
        for (String command : denied) assertFalse(engine.execute(command).isSuccess(), command);
        assertTrue(engine.getFileSystem().exists("readme.txt"));
        assertFalse(engine.getFileSystem().exists("locked/new"));
        // The simulated owner can restore the directory itself through its accessible parent.
        ok("chmod 755 locked"); assertEquals("safe\n", ok("cat locked/existing"));
    }

    @Test void readOnlyDirectoryAllowsExistingWritableFileOverwriteButNoNewEntries() {
        ok("mkdir readonly"); ok("echo old > readonly/existing"); ok("chmod 555 readonly");
        ok("echo updated > readonly/existing"); assertEquals("updated\n", ok("cat readonly/existing"));
        ok("cp readme.txt readonly/existing"); assertEquals("Welcome to TERMINAL QUEST\n", ok("cat readonly/existing"));
        assertFalse(engine.execute("touch readonly/new").isSuccess());
        assertFalse(engine.execute("mkdir readonly/newdir").isSuccess());
        assertFalse(engine.execute("cp readme.txt readonly/newcopy").isSuccess());
        ok("cd readonly");
    }

    @Test void movingEntriesRequiresBothSourceAndDestinationDirectoryWritePermissions() {
        ok("mkdir source destination"); ok("echo data > source/item");
        ok("chmod 555 source");
        assertFalse(engine.execute("mv source/item destination/item").isSuccess());
        assertTrue(engine.getFileSystem().exists("source/item"));
        ok("chmod 755 source"); ok("chmod 555 destination");
        assertFalse(engine.execute("mv source/item destination/item").isSuccess());
        ok("chmod 755 destination"); ok("chmod 000 source/item");
        // Renaming needs directory permissions, not read permission on the file.
        ok("mv source/item destination/item"); assertTrue(engine.getFileSystem().exists("destination/item"));
    }

    @Test void inaccessibleAncestorsCannotBeBypassedWithAbsolutePathsOrRecursiveCopy() {
        ok("mkdir -p outer/inner"); ok("echo secret > outer/inner/item"); ok("chmod 000 outer");
        String[] denied = {"cat /home/cadet/outer/inner/item", "touch outer/inner/new", "cd outer/inner", "cp -r outer cloned", "find outer", "du -a outer", "chmod 755 outer/inner"};
        for (String command : denied) assertFalse(engine.execute(command).isSuccess(), command);
        assertFalse(engine.getFileSystem().exists("cloned"));
        ok("chmod 755 outer"); ok("cp -r outer cloned"); assertEquals("secret\n", ok("cat cloned/inner/item"));
    }

    @Test void recursiveRootCopyIsRejectedWithoutCreatingMalformedPaths() {
        ok("chmod 644 /etc/web/config");
        assertFalse(engine.execute("cp -r / /copy").isSuccess());
        assertFalse(engine.getFileSystem().exists("/copy"));
        assertFalse(engine.getFileSystem().exists("/copyhome"));
    }

    @Test void serviceHealthHandlesInaccessibleConfigurationDirectoryWithoutThrowing() {
        ok("kill 42"); ok("chmod 644 /etc/web/config"); ok("echo fixed > /var/log/archive.log"); ok("systemctl restart web");
        ok("chmod 000 /etc/web"); assertFalse(engine.getServices().isActive("web"));
        assertEquals(3, engine.execute("systemctl status web").getExitCode());
        ok("chmod 755 /etc/web"); ok("systemctl restart web"); assertTrue(engine.getServices().isActive("web"));
    }

    @Test void invalidArgumentsAndNonexistentPathsFailCleanly() {
        String[] invalid = {"cd missing", "cat missing", "cd readme.txt", "mkdir logs", "mkdir missing/child", "cp readme.txt", "mv", "chmod 888 readme.txt", "chmod 9999 readme.txt", "ls -x", "head -n -5 readme.txt", "tail -n 99999999999", "wc -Z", "find . -exec rm", "du -z", "df missing", "systemctl restart ssh", "journalctl -x"};
        for (String command : invalid) assertFalse(engine.execute(command).isSuccess(), command);
    }

    @Test void unsupportedSyntaxIsRejectedBeforeAnyFilesystemMutation() {
        String[] invalid = {"touch unsafe; pwd", "touch unsafe && pwd", "touch unsafe |", "touch unsafe >", "touch unsafe | sh", "touch unsafe $(pwd)", "touch unsafe `pwd`", "touch unsafe > out > other", "touch unsafe < readme.txt"};
        for (String command : invalid) { assertFalse(engine.execute(command).isSuccess(), command); assertFalse(engine.getFileSystem().exists("unsafe"), command); }
    }

    @Test void quotedOperatorsAndShellLikeTextAreOnlyData() {
        ok("echo '$(touch /tmp/host-file); rm -rf /' > literal.txt");
        assertEquals("$(touch /tmp/host-file); rm -rf /\n", ok("cat literal.txt"));
        ok("echo 'x|y>z' > 'quoted file.txt'"); assertEquals("x|y>z\n", ok("cat 'quoted file.txt'"));
        assertEquals(" x\n", ok("echo '' x"));
    }

    @Test void emptyStreamsAndMissingFinalNewlineAreCountedCorrectly() {
        assertEquals("0\n", ok("cat /dev/null | wc -l"));
        assertEquals("", ok("cat /dev/null | sort | uniq"));
        ok("echo -n 'a b' > no-newline"); assertEquals("0 2 3 no-newline\n", ok("wc no-newline"));
        assertEquals("a b\n", ok("head no-newline"));
    }

    @Test void byteCountsUseUtf8WithoutPlatformEncodings() {
        engine.getFileSystem().writeFile("japanese.txt", "日本語\n", false);
        assertEquals("10 japanese.txt\n", ok("wc -c japanese.txt"));
        engine.getFileSystem().writeFile("emoji.txt", "\ud83d\ude80", false);
        assertEquals("4 emoji.txt\n", ok("wc -c emoji.txt"));
    }

    @Test void invalidPathAndOversizedInputAreBounded() {
        assertFalse(engine.execute("touch 'a\u0000b'").isSuccess());
        StringBuilder text = new StringBuilder("echo "); for (int i = 0; i < 4096; i++) text.append('a');
        assertFalse(engine.execute(text.toString()).isSuccess());
        assertThrows(IllegalArgumentException.class, () -> engine.getFileSystem().setVirtualSize("readme.txt", -1));
    }

    @Test void hugeVirtualFilesDoNotAllocateRealMemoryOrOverflowDiskReport() {
        engine.getFileSystem().setVirtualSize("readme.txt", Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, engine.getFileSystem().usedBytes());
        assertTrue(ok("df -h").contains("E"));
        assertEquals("Welcome to TERMINAL QUEST\n", ok("cat readme.txt"));
        ok("echo small > readme.txt"); assertEquals(6, engine.getFileSystem().size("readme.txt"));
    }

    @Test void failedRedirectLeavesPreviousFileUntouched() {
        ok("echo previous > output.txt");
        assertFalse(engine.execute("cat missing > output.txt").isSuccess());
        assertEquals("previous\n", ok("cat output.txt"));
    }

    @Test void resultListsPipelineCommandsAndHelpExplainsScope() {
        CommandResult result = engine.execute("cat data/hosts.txt | sort | uniq");
        assertEquals(3, result.getCommands().size()); assertEquals("uniq", result.getCommands().get(2));
        assertThrows(UnsupportedOperationException.class, () -> result.getCommands().add("sh"));
        assertTrue(ok("help").contains("実OS操作")); assertEquals("", ok("clear"));
    }

    private String ok(String command) {
        CommandResult result = engine.execute(command); assertTrue(result.isSuccess(), command + " -> " + result.getOutput()); return result.getOutput();
    }
}
