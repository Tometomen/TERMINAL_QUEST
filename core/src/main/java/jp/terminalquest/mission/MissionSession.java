package jp.terminalquest.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jp.terminalquest.system.RankConfig;
import jp.terminalquest.terminal.CommandResult;
import jp.terminalquest.terminal.CommandParser;
import jp.terminalquest.terminal.TerminalEngine;
import jp.terminalquest.terminal.VirtualFileSystem;

/** A replay always has a fresh virtual world, isolated from all other missions. */
public final class MissionSession {
    private final Mission mission;
    private final TerminalEngine engine;
    private final MissionEvaluator evaluator = new MissionEvaluator();
    private final RankConfig rankConfig;
    private final Set<String> usedCommands = new LinkedHashSet<String>();
    private final List<Observation> observations = new ArrayList<Observation>();
    private final long startedAt;
    private final String originalLog;
    private int hintCount;
    private int errorCount;
    private int commandCount;
    private boolean complete;
    private boolean recorded;
    private float completedSeconds;

    public MissionSession(Mission mission) { this(mission, new RankConfig()); }

    public MissionSession(Mission mission, RankConfig rankConfig) {
        if (mission == null || rankConfig == null) throw new IllegalArgumentException("Mission and rank configuration are required");
        this.mission = mission;
        this.rankConfig = rankConfig;
        this.engine = new TerminalEngine();
        VirtualFileSystem fs = engine.getFileSystem();
        for (String[] file : mission.initialFiles) {
            int slash = file[0].lastIndexOf('/');
            fs.mkdir(file[0].substring(0, slash), true);
            fs.writeFile(file[0], file[1], false);
        }
        if ("disk".equals(mission.scenario)) {
            fs.setPermissions("/etc/web/config", 0644);
            engine.getProcesses().kill(42);
        } else if ("permission".equals(mission.scenario)) {
            fs.writeFile("/var/log/archive.log", "", false);
            engine.getProcesses().kill(42);
        }
        originalLog = fs.readFile("/home/cadet/logs/app.log");
        startedAt = System.currentTimeMillis();
    }

    public TerminalEngine getEngine() { return engine; }
    public Mission getMission() { return mission; }

    public CommandResult execute(String input) {
        String originalDirectory = engine.getFileSystem().pwd();
        CommandParser.Pipeline pipeline = null;
        try { pipeline = new CommandParser().parse(input); } catch (IllegalArgumentException invalid) { /* engine reports syntax errors */ }
        CommandResult result = engine.execute(input);
        if (!complete && input != null && input.trim().length() > 0) {
            commandCount++;
            boolean validObservation = result.isSuccess() || isServiceStatusObservation(pipeline, result);
            if (!validObservation) errorCount++;
            for (String command : result.getCommands()) if (validObservation) usedCommands.add(command);
            if (validObservation && pipeline != null) {
                if (pipeline.stages.size() > 1) usedCommands.add("|");
                if (pipeline.redirectPath != null) usedCommands.add(pipeline.append ? ">>" : ">");
            }
            if (validObservation) {
                observations.add(new Observation(result.getCommands(), result.getOutput(), pipeline, originalDirectory));
            }
            if (evaluator.evaluate(this)) {
                complete = true;
                completedSeconds = elapsedNow();
            }
        }
        return result;
    }

    private boolean isServiceStatusObservation(CommandParser.Pipeline pipeline, CommandResult result) {
        if (pipeline == null || result.getExitCode() != 3) return false;
        for (List<String> stage : pipeline.stages) {
            if (stage.size() == 3 && "systemctl".equals(stage.get(0)) && "status".equals(stage.get(1)) && "web".equals(stage.get(2))) return true;
        }
        return false;
    }

    boolean hasObserved(String acceptedCommands, String fragment) {
        String path = "";
        int pathAt = acceptedCommands.indexOf('@');
        if (pathAt >= 0) { path = acceptedCommands.substring(pathAt + 1); acceptedCommands = acceptedCommands.substring(0, pathAt); }
        String option = "";
        int optionAt = acceptedCommands.indexOf(':');
        if (optionAt >= 0) { option = acceptedCommands.substring(optionAt + 1); acceptedCommands = acceptedCommands.substring(0, optionAt); }
        String[] commands = acceptedCommands.split(",");
        for (Observation observation : observations) {
            boolean matches = false;
            for (String command : commands) if (observation.commands.contains(command)) matches = true;
            if (!matches || observation.output.length() == 0) continue;
            if (path.length() > 0 && !observation.hasSource(path)) continue;
            if (option.length() > 0 && !observation.hasOption(commands[0], option)) continue;
            if ("wc".equals(commands[0])) {
                if (observation.output.trim().split("\\s+")[0].equals(fragment)) return true;
            } else if (observation.output.indexOf(fragment) >= 0) return true;
        }
        return false;
    }

    /** Resolve fixture-dependent expectations without changing global mission data. */
    String expectedContent(String code) {
        if (!code.startsWith("@")) return code;
        String[] lines = originalLog.split("\n");
        List<String> errors = new ArrayList<String>();
        for (String line : lines) if (line.indexOf("ERROR") >= 0) errors.add(line);
        if ("@LAST_LINE".equals(code)) return lines.length == 0 ? "" : lines[lines.length - 1];
        if ("@LINE_COUNT".equals(code)) {
            int count = 0;
            for (int i = 0; i < originalLog.length(); i++) if (originalLog.charAt(i) == '\n') count++;
            return String.valueOf(count);
        }
        if ("@FIRST_ERROR".equals(code)) return errors.isEmpty() ? "" : errors.get(0) + "\n";
        if ("@SORTED_ERRORS".equals(code)) {
            Collections.sort(errors);
            errors = new ArrayList<String>(new LinkedHashSet<String>(errors));
        }
        StringBuilder result = new StringBuilder();
        for (String error : errors) result.append(error).append('\n');
        return result.toString();
    }

    public boolean isComplete() { return complete; }
    public String revealHint() {
        if (hintCount < mission.hints.length) hintCount++;
        return hintCount == 0 ? "" : mission.hints[hintCount - 1];
    }
    public String getInitialHint() { return mission.level <= 2 ? mission.hints[0] : ""; }
    public int getHintCount() { return hintCount; }
    public int getErrorCount() { return errorCount; }
    public int getCommandCount() { return commandCount; }
    public float getElapsedSeconds() { return complete ? completedSeconds : elapsedNow(); }
    private float elapsedNow() { return Math.max(0f, (System.currentTimeMillis() - startedAt) / 1000f); }
    public String getRank() { return complete ? rankConfig.rank(commandCount, errorCount, hintCount, completedSeconds) : "-"; }
    public List<String> getUsedCommands() { return Collections.unmodifiableList(new ArrayList<String>(usedCommands)); }
    public boolean isRecorded() { return recorded; }
    public void markRecorded() { recorded = true; }

    private final class Observation {
        final List<String> commands;
        final String output;
        final CommandParser.Pipeline pipeline;
        final String directory;
        Observation(List<String> commands, String output, CommandParser.Pipeline pipeline, String directory) {
            this.commands = new ArrayList<String>(commands);
            this.output = output == null ? "" : output;
            this.pipeline = pipeline;
            this.directory = directory;
        }
        boolean hasOption(String command, String option) {
            if (pipeline == null) return false;
            for (List<String> stage : pipeline.stages) if (stage.get(0).equals(command) && stage.contains(option)) return true;
            return false;
        }
        boolean hasSource(String path) {
            if (pipeline == null) return false;
            for (List<String> stage : pipeline.stages) {
                for (int i = 1; i < stage.size(); i++) {
                    String argument = stage.get(i);
                    if (argument.startsWith("-")) continue;
                    String absolute = argument.startsWith("/") ? argument : directory + "/" + argument;
                    try { if (engine.getFileSystem().normalize(absolute).equals(path)) return true; }
                    catch (IllegalArgumentException ignored) { /* argument was not a path */ }
                }
            }
            return false;
        }
    }
}
