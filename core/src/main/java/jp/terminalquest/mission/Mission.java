package jp.terminalquest.mission;

/** Immutable mission specification, including its isolated scenario and goals. */
public final class Mission {
    public final String id;
    public final String title;
    public final String objective;
    public final int level;
    public final boolean boss;
    public final String[] hints;
    public final String[] exampleCommands;
    public final String scenario;
    public final String[][] initialFiles;
    public final MissionCondition[] conditions;

    public Mission(String id, int level, String title, String objective, String[] hints,
                   String[] exampleCommands, String scenario, String[][] initialFiles,
                   MissionCondition[] conditions) {
        this.id = id;
        this.level = level;
        this.title = title;
        this.objective = objective;
        this.boss = id.endsWith("-5");
        this.hints = hints;
        this.exampleCommands = exampleCommands;
        this.scenario = scenario;
        this.initialFiles = initialFiles;
        this.conditions = conditions;
    }
}
