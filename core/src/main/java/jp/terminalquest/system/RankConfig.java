package jp.terminalquest.system;

/** Adjustable rank thresholds. No rank can block curriculum progress. */
public final class RankConfig {
    public float sSeconds = 180f;
    public int sMaxCommands = 12;
    public int sMaxErrors = 0;
    public int cMinErrors = 6;
    public int cMinCommands = 40;

    public String rank(int commands, int errors, int hints, float seconds) {
        if (errors >= cMinErrors || commands >= cMinCommands) return "C";
        if (hints > 0) return "B";
        if (errors <= sMaxErrors && commands <= sMaxCommands && seconds <= sSeconds) return "S";
        return "A";
    }
}
