package jp.terminalquest.terminal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {
    private final CommandParser parser = new CommandParser();

    @Test
    void parsesWhitespaceArgumentsAndThreeStagePipeline() {
        CommandParser.Pipeline pipeline = parser.parse("  grep\tERROR app.log|head -n 3 | wc -l  ");
        assertEquals(3, pipeline.stages.size());
        assertEquals(Arrays.asList("grep", "ERROR", "app.log"), pipeline.stages.get(0));
        assertEquals(Arrays.asList("head", "-n", "3"), pipeline.stages.get(1));
        assertEquals(Arrays.asList("wc", "-l"), pipeline.stages.get(2));
        assertNull(pipeline.redirectPath);
        assertFalse(pipeline.append);
    }

    @Test
    void quotesPreserveLiteralOperatorsAndEmptyArguments() {
        CommandParser.Pipeline pipeline = parser.parse("echo 'a|b > c;$HOME`id`' \"\" pre' two 'post");
        assertEquals(Arrays.asList("echo", "a|b > c;$HOME`id`", "", "pre two post"), pipeline.stages.get(0));
        assertEquals(Arrays.asList("echo", "$HOME", "`id`", "; & < > |"),
                parser.parse("echo \"$HOME\" \"`id`\" \"; & < > |\"").stages.get(0));
    }

    @Test
    void escapingProducesLiteralArgumentsWithoutExpansion() {
        assertEquals(Arrays.asList("echo", "two words", "a|b", "\"quoted\"", "\\", "$HOME"),
                parser.parse("echo two\\ words a\\|b \\\"quoted\\\" \\\\ \\$HOME").stages.get(0));
        assertEquals(Arrays.asList("echo", "a\\b"), parser.parse("echo 'a\\b'").stages.get(0));
    }

    @Test
    void parsesRedirectAtEndWithQuotedFileName() {
        CommandParser.Pipeline overwrite = parser.parse("grep ERROR app.log|head -n 3>\"error report.txt\"");
        assertEquals(2, overwrite.stages.size());
        assertEquals("error report.txt", overwrite.redirectPath);
        assertFalse(overwrite.append);
        CommandParser.Pipeline append = parser.parse("echo 'ready'>>report.txt");
        assertEquals("report.txt", append.redirectPath);
        assertTrue(append.append);
    }

    @Test
    void rejectsEntireMalformedPipelineBeforeExecution() {
        String[] invalid = {"| pwd", "pwd |", "pwd || ls", "> result.txt", "pwd >", "pwd >>",
                "pwd > ''", "pwd > out extra", "pwd > out | cat", "pwd > a > b", "pwd >>> out",
                "'' arg", "echo 'unterminated", "echo \"unterminated", "echo trailing\\"};
        for (String command : invalid) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(command), command);
        }
    }

    @Test
    void rejectsUnsupportedShellControlAndSubstitution() {
        String[] invalid = {"touch x; pwd", "pwd && ls", "pwd &", "cat < secret", "echo $HOME",
                "echo $(id)", "echo `id`", "(pwd)", "pwd\nls", "echo 'line\nbreak'", "echo \\\nnext",
                "echo \u0000bad"};
        for (String command : invalid) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(command), command);
        }
    }

    @Test
    void boundsInputAndPipelineComplexity() {
        StringBuilder longCommand = new StringBuilder("echo ");
        for (int i = 0; i < 4096; i++) longCommand.append('x');
        assertThrows(IllegalArgumentException.class, () -> parser.parse(longCommand.toString()));
        StringBuilder pipeline = new StringBuilder("pwd");
        for (int i = 1; i < 16; i++) pipeline.append(" | cat");
        assertEquals(16, parser.parse(pipeline.toString()).stages.size());
        pipeline.append(" | cat");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(pipeline.toString()));
    }

    @Test
    void emptyInputIsNoOperationAndOutputCannotBeMutated() {
        assertTrue(parser.parse(" \t ").stages.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        final CommandParser.Pipeline pipeline = parser.parse("pwd");
        assertThrows(UnsupportedOperationException.class, () -> pipeline.stages.clear());
        assertThrows(UnsupportedOperationException.class, () -> pipeline.stages.get(0).add("unexpected"));
    }
}
