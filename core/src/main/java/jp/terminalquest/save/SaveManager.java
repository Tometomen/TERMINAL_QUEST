package jp.terminalquest.save;

import com.badlogic.gdx.Preferences;
import jp.terminalquest.mission.Mission;
import jp.terminalquest.mission.MissionCatalog;
import jp.terminalquest.system.ProgressionSystem;
import java.util.HashSet;
import java.util.Set;

/** Versioned, value-validated libGDX Preferences storage; never writes host files directly. */
public final class SaveManager {
    public static final int SAVE_VERSION = 1;
    private final Preferences preferences;
    private final boolean storageAvailable;
    private boolean recovered;
    private boolean lastSaveSuccessful = true;

    public SaveManager(Preferences preferences) {
        this(preferences, true);
    }

    public SaveManager(Preferences preferences, boolean storageAvailable) {
        if (preferences == null) throw new IllegalArgumentException("Preferences are required");
        this.preferences = preferences;
        this.storageAvailable = storageAvailable;
        this.lastSaveSuccessful = storageAvailable;
    }

    public ProgressionSystem load() {
        recovered = false;
        if (!storageAvailable) return new ProgressionSystem();
        try {
            if (!preferences.contains("version")) return new ProgressionSystem();
            if (preferences.getInteger("version") != SAVE_VERSION) throw new IllegalArgumentException("Unsupported save version");
            ProgressionSystem progress = new ProgressionSystem();
            String completed = preferences.getString("completed", "");
            Set<String> knownIds = new HashSet<String>();
            for (Mission mission : MissionCatalog.all()) knownIds.add(mission.id);
            Set<String> loadedIds = new HashSet<String>();
            for (String id : splitList(completed)) {
                if (!knownIds.contains(id) || !loadedIds.add(id)) throw new IllegalArgumentException("Invalid mission ID");
                String rank = preferences.getString("rank." + id, "");
                if (!isRank(rank)) throw new IllegalArgumentException("Invalid rank");
                progress.restoreComplete(id, rank);
            }
            int xp = preferences.getInteger("xp", 0);
            if (xp < 0 || xp > 1000000) throw new IllegalArgumentException("Invalid XP");
            progress.restoreXp(xp);
            for (String command : splitList(preferences.getString("mastered", ""))) {
                if ((!command.matches("[a-z][a-z0-9]*") && !"|".equals(command) && !">".equals(command) && !">>".equals(command))
                    || command.length() > 32) throw new IllegalArgumentException("Invalid command");
                int mastery = preferences.getInteger("mastery." + command, 1);
                if (mastery < 1 || mastery > 1000000) throw new IllegalArgumentException("Invalid mastery");
                progress.restoreMastered(command, mastery);
            }
            for (int level = 1; level <= 6; level++) {
                int best = preferences.getInteger("typing." + level, 0);
                if (best < 0 || best > 100000) throw new IllegalArgumentException("Invalid typing score");
                float accuracy = preferences.getFloat("typingAccuracy." + level, 0f);
                float kpm = preferences.getFloat("typingKpm." + level, 0f);
                int combo = preferences.getInteger("typingCombo." + level, 0);
                progress.restoreTyping(level, best, accuracy, kpm, combo);
            }
            Settings settings = progress.getSettings();
            settings.muted = preferences.getBoolean("muted", false);
            settings.reducedMotion = preferences.getBoolean("reducedMotion", false);
            settings.volume = preferences.getFloat("volume", 0.6f);
            if (Float.isNaN(settings.volume) || Float.isInfinite(settings.volume) || settings.volume < 0f || settings.volume > 1f) {
                throw new IllegalArgumentException("Invalid volume");
            }
            if (!progress.isConsistent()) throw new IllegalArgumentException("Inconsistent progression");
            return progress;
        } catch (RuntimeException invalidSave) {
            recovered = true;
            return new ProgressionSystem();
        }
    }

    public void save(ProgressionSystem progress) {
        if (progress == null) throw new IllegalArgumentException("Progress is required");
        lastSaveSuccessful = false;
        if (!storageAvailable) return;
        try {
            preferences.clear();
            preferences.putInteger("version", SAVE_VERSION);
            preferences.putString("completed", join(progress.getCompletedIds()));
            for (String id : progress.getCompletedIds()) preferences.putString("rank." + id, progress.bestRank(id));
            preferences.putInteger("xp", progress.getXp());
            preferences.putString("mastered", join(progress.getMasteredCommands()));
            for (String command : progress.getMasteredCommands()) preferences.putInteger("mastery." + command, progress.getCommandMastery(command));
            for (int level = 1; level <= 6; level++) {
                preferences.putInteger("typing." + level, progress.getTypingBest(level));
                preferences.putFloat("typingAccuracy." + level, progress.getTypingAccuracy(level));
                preferences.putFloat("typingKpm." + level, progress.getTypingKpm(level));
                preferences.putInteger("typingCombo." + level, progress.getTypingCombo(level));
            }
            Settings settings = progress.getSettings();
            preferences.putBoolean("muted", settings.muted);
            preferences.putBoolean("reducedMotion", settings.reducedMotion);
            preferences.putFloat("volume", settings.volume);
            preferences.flush();
            lastSaveSuccessful = true;
        } catch (RuntimeException storageUnavailable) {
            // Private browsing / denied local storage must not stop gameplay.
        }
    }

    public void reset() {
        // GWT localStorage deletion may retain unrelated old keys. Write a complete
        // canonical empty snapshot so stale ranks/mastery can never restore progress.
        save(new ProgressionSystem());
        if (lastSaveSuccessful) recovered = false;
    }

    public boolean wasRecovered() { return recovered; }
    public boolean wasLastSaveSuccessful() { return lastSaveSuccessful; }
    public boolean isStorageAvailable() { return storageAvailable; }

    private static boolean isRank(String rank) {
        return "S".equals(rank) || "A".equals(rank) || "B".equals(rank) || "C".equals(rank);
    }

    private static String[] splitList(String list) {
        if (list == null || list.length() > 10000) throw new IllegalArgumentException("Invalid saved list");
        if (list.isEmpty()) return new String[0];
        if (list.startsWith(",") || list.endsWith(",") || list.contains(",,")) throw new IllegalArgumentException("Malformed saved list");
        return list.split(",");
    }

    private static String join(Iterable<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(',');
            result.append(value);
        }
        return result.toString();
    }
}
