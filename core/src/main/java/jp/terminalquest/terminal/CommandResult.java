package jp.terminalquest.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A command's captured output. No command is ever delegated to a host shell. */
public final class CommandResult {
    private final String output;
    private final int exitCode;
    private final List<String> commands;

    public CommandResult(String output, int exitCode, List<String> commands) {
        this.output = output == null ? "" : output;
        this.exitCode = exitCode;
        this.commands = Collections.unmodifiableList(new ArrayList<String>(commands));
    }

    public String getOutput() { return output; }
    public int getExitCode() { return exitCode; }
    public boolean isSuccess() { return exitCode == 0; }
    public List<String> getCommands() { return commands; }
}
