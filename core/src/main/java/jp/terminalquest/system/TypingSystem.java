package jp.terminalquest.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jp.terminalquest.terminal.CommandParser;

/**
 * A deterministic, frame-clock-driven 60-second challenge. It never runs commands.
 * Accuracy is matching character positions divided by the larger of submitted and
 * expected lengths, accumulated across attempts. Missing characters therefore also
 * count as mistakes. KPM counts all submitted characters per elapsed minute.
 */
public final class TypingSystem {
    public static final float DURATION_SECONDS = 60f;
    private static final String[][] PROMPTS = {
        {"pwd", "ls", "cd logs", "cd ..", "ls /home/cadet"},
        {"cat readme.txt", "touch memo.txt", "mkdir backup", "cp readme.txt backup/", "mv memo.txt notes.txt"},
        {"grep ERROR logs/app.log", "find . -name app.log", "head -n 3 logs/app.log", "tail -n 10 logs/app.log", "wc -l logs/app.log"},
        {"grep ERROR logs/app.log | head -n 3", "sort data/hosts.txt | uniq", "grep ERROR logs/app.log > errors.txt", "cat errors.txt >> report.txt", "sort data/hosts.txt | uniq -c"},
        {"chmod 644 /etc/web/config", "ps", "kill 42", "df -h", "du -h /var/log/archive.log"},
        {"systemctl status web", "journalctl -u web", "cat /dev/null > /var/log/archive.log", "systemctl restart web", "systemctl status web | grep active"}
    };

    private final int level;
    private final String[] prompts;
    private boolean started;
    private float elapsed;
    private int promptIndex;
    private int completed;
    private int attempts;
    private int combo;
    private int maxCombo;
    private int keystrokes;
    private int matchingCharacters;
    private int assessedCharacters;

    public TypingSystem(int level) {
        this(level, null, true);
    }

    /** Live gameplay only offers commands and shell features learned in missions. */
    public TypingSystem(int level, Set<String> mastered) {
        this(level, mastered, false);
    }

    private TypingSystem(int level, Set<String> mastered, boolean fullCurriculum) {
        if (level < 1 || level > PROMPTS.length) {
            throw new IllegalArgumentException("Difficulty must be between 1 and 6");
        }
        this.level = level;
        // GWT's Java-8 emulation does not expose array.clone() here.
        // This constant array is never mutated; only the prompt index changes.
        this.prompts = fullCurriculum ? PROMPTS[level - 1] : learnedPrompts(level, mastered);
    }

    public static int availablePromptCount(int level, Set<String> mastered) {
        if (level < 1 || level > PROMPTS.length) throw new IllegalArgumentException("Difficulty must be between 1 and 6");
        return learnedPrompts(level, mastered).length;
    }

    private static String[] learnedPrompts(int level, Set<String> mastered) {
        List<String> available = new ArrayList<String>();
        if (mastered == null) return new String[0];
        for (String prompt : PROMPTS[level - 1]) {
            CommandParser.Pipeline pipeline = new CommandParser().parse(prompt);
            boolean learned = true;
            for (List<String> stage : pipeline.stages) if (!mastered.contains(stage.get(0))) learned = false;
            if (pipeline.stages.size() > 1 && !mastered.contains("|")) learned = false;
            if (pipeline.redirectPath != null && !mastered.contains(pipeline.append ? ">>" : ">")) learned = false;
            if (learned) available.add(prompt);
        }
        return available.toArray(new String[available.size()]);
    }

    public void start() {
        if (prompts.length == 0) throw new IllegalStateException("ミッションでコマンドを習得してから練習を開始してください。");
        started = true;
        elapsed = 0f;
        promptIndex = 0;
        completed = attempts = combo = maxCombo = keystrokes = 0;
        matchingCharacters = assessedCharacters = 0;
    }

    /** @return true only when the entered command exactly matches the prompt. */
    public boolean submit(String entered) {
        if (!started || isFinished()) return false;
        if (entered == null) entered = "";
        String expected = getPrompt();
        attempts++;
        keystrokes += entered.length();
        assessedCharacters += Math.max(expected.length(), entered.length());
        for (int i = 0; i < Math.min(expected.length(), entered.length()); i++) {
            if (expected.charAt(i) == entered.charAt(i)) matchingCharacters++;
        }
        if (expected.equals(entered)) {
            completed++;
            combo++;
            maxCombo = Math.max(maxCombo, combo);
            promptIndex = (promptIndex + 1) % prompts.length;
            return true;
        }
        combo = 0;
        return false;
    }

    public void update(float deltaSeconds) {
        if (Float.isNaN(deltaSeconds) || Float.isInfinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Elapsed time must be finite and non-negative");
        }
        if (started && !isFinished()) elapsed = Math.min(DURATION_SECONDS, elapsed + deltaSeconds);
    }

    public boolean isFinished() { return started && elapsed >= DURATION_SECONDS; }
    public boolean isStarted() { return started; }
    public int getLevel() { return level; }
    public String getPrompt() { return prompts.length == 0 ? "" : prompts[promptIndex]; }
    public float getRemainingSeconds() { return Math.max(0f, DURATION_SECONDS - elapsed); }
    public float getElapsedSeconds() { return elapsed; }
    public int getCompleted() { return completed; }
    public int getAttempts() { return attempts; }
    public int getCombo() { return combo; }
    public int getMaxCombo() { return maxCombo; }
    public int getKeystrokes() { return keystrokes; }
    public float getAccuracy() {
        return assessedCharacters == 0 ? 100f : 100f * matchingCharacters / assessedCharacters;
    }
    public float getKpm() { return elapsed <= 0f ? 0f : keystrokes * 60f / elapsed; }
}
