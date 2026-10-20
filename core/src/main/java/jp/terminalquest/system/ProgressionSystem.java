package jp.terminalquest.system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.terminalquest.mission.DifficultyLevel;
import jp.terminalquest.mission.Mission;
import jp.terminalquest.mission.MissionCatalog;
import jp.terminalquest.mission.MissionSession;
import jp.terminalquest.save.Settings;

/** Mission ranks never gate progress; only the four normal goals and boss do. */
public final class ProgressionSystem {
    private final Map<String, String> completed = new LinkedHashMap<String, String>();
    private final Map<String, Integer> mastery = new LinkedHashMap<String, Integer>();
    private final int[] typingBest = new int[6];
    private final float[] typingAccuracy = new float[6];
    private final float[] typingKpm = new float[6];
    private final int[] typingCombo = new int[6];
    private final Settings settings = new Settings();
    private int xp;

    public boolean isLevelUnlocked(int level) {
        if (level < 1 || level > 6) return false;
        return level == 1 || isCompleted("L" + (level - 1) + "-5");
    }

    public boolean isMissionUnlocked(Mission mission) {
        if (mission == null || !isLevelUnlocked(mission.level) || !DifficultyLevel.byId(mission.level).implemented) return false;
        if (isCompleted(mission.id) || !mission.boss) return true;
        for (int i = 1; i <= 4; i++) if (!isCompleted("L" + mission.level + "-" + i)) return false;
        return true;
    }

    public boolean isCompleted(String id) { return completed.containsKey(id); }
    public int completedCount(int level) {
        int count = 0;
        for (Mission mission : MissionCatalog.forLevel(level)) if (isCompleted(mission.id)) count++;
        return count;
    }
    public int getXp() { return xp; }
    public Set<String> getCompletedIds() { return Collections.unmodifiableSet(new LinkedHashSet<String>(completed.keySet())); }
    public Set<String> getMasteredCommands() { return Collections.unmodifiableSet(new LinkedHashSet<String>(mastery.keySet())); }
    public int getCommandMastery(String command) { Integer value = mastery.get(command); return value == null ? 0 : value; }
    public String bestRank(String id) { String rank = completed.get(id); return rank == null ? "-" : rank; }
    public Settings getSettings() { return settings; }

    /** Returns true when a newly cleared mission awards XP. Replays can improve ranks. */
    public boolean complete(MissionSession session) {
        if (session == null || !session.isComplete() || session.isRecorded() || !isMissionUnlocked(session.getMission())) return false;
        Mission mission = session.getMission();
        boolean firstClear = !isCompleted(mission.id);
        if (firstClear) xp += rewardXp(mission);
        String oldRank = bestRank(mission.id);
        if (rankOrder(session.getRank()) < rankOrder(oldRank)) completed.put(mission.id, session.getRank());
        for (String command : session.getUsedCommands()) {
            int current = getCommandMastery(command);
            mastery.put(command, Math.min(1000000, current + 1));
        }
        session.markRecorded();
        return firstClear;
    }

    public static int rewardXp(Mission mission) { return DifficultyLevel.byId(mission.level).rewardXp * (mission.boss ? 2 : 1); }
    private static int rankOrder(String rank) {
        if ("S".equals(rank)) return 0;
        if ("A".equals(rank)) return 1;
        if ("B".equals(rank)) return 2;
        if ("C".equals(rank)) return 3;
        return 4;
    }

    public int getTypingBest(int level) { checkLevel(level); return typingBest[level - 1]; }
    public void setTypingBest(int level, int completedCount) {
        checkLevel(level);
        if (completedCount < 0 || completedCount > 100000) throw new IllegalArgumentException("Invalid typing score");
        typingBest[level - 1] = Math.max(typingBest[level - 1], completedCount);
    }
    public void recordTyping(int level, int completedCount, float accuracy, float kpm, int maxCombo) {
        checkLevel(level);
        if (completedCount < 0 || completedCount > 100000 || accuracy < 0 || accuracy > 100 || Float.isNaN(accuracy)
            || kpm < 0 || Float.isNaN(kpm) || Float.isInfinite(kpm) || maxCombo < 0 || maxCombo > completedCount) {
            throw new IllegalArgumentException("Invalid typing result");
        }
        if (completedCount >= typingBest[level - 1]) {
            setTypingBest(level, completedCount);
            typingAccuracy[level - 1] = accuracy;
            typingKpm[level - 1] = kpm;
            typingCombo[level - 1] = maxCombo;
        }
    }
    public float getTypingAccuracy(int level) { checkLevel(level); return typingAccuracy[level - 1]; }
    public float getTypingKpm(int level) { checkLevel(level); return typingKpm[level - 1]; }
    public int getTypingCombo(int level) { checkLevel(level); return typingCombo[level - 1]; }
    public void restoreTyping(int level, int completedCount, float accuracy, float kpm, int maxCombo) {
        recordTyping(level, completedCount, accuracy, kpm, maxCombo);
    }
    private void checkLevel(int level) { DifficultyLevel.byId(level); }

    /** Small achievements derive from saved progress, so no duplicate save state is needed. */
    public List<String> getAchievements() {
        List<String> result = new ArrayList<String>();
        if (!completed.isEmpty()) result.add("FIRST STEP：最初のミッションをクリア");
        if (isCompleted("L1-5")) result.add("EXPLORER：探索訓練を修了");
        if (isCompleted("L2-5")) result.add("KEEPER：バックアップを作成");
        if (isCompleted("L4-5")) result.add("ANALYST：調査レポートを完成");
        if (isCompleted("L6-5")) result.add("RECOVERY HERO：データセンターを復旧");
        if (completed.size() == 30) result.add("ALL CLEAR：全30ミッションをクリア");
        for (int best : typingBest) if (best >= 10) { result.add("TEN STREAK：タイピング10問達成"); break; }
        return Collections.unmodifiableList(result);
    }

    // Validated restoration API used only by SaveManager; no untrusted Java deserialization.
    public void restoreComplete(String id, String rank) {
        MissionCatalog.byId(id);
        if (rankOrder(rank) == 4) throw new IllegalArgumentException("Invalid saved rank");
        completed.put(id, rank);
    }
    public void restoreMastered(String command) { restoreMastered(command, 1); }
    public void restoreMastered(String command, int count) {
        if (command == null || command.length() == 0 || command.length() > 40 || count < 1 || count > 1000000
            || command.indexOf(',') >= 0 || command.indexOf('\n') >= 0) throw new IllegalArgumentException("Invalid mastery");
        mastery.put(command, count);
    }
    public void restoreXp(int xp) {
        if (xp < 0 || xp > 1000000) throw new IllegalArgumentException("Invalid XP");
        this.xp = xp;
    }
    public boolean isConsistent() {
        int expectedXp = 0;
        for (String id : completed.keySet()) {
            Mission mission = MissionCatalog.byId(id);
            if (!isLevelUnlocked(mission.level)) return false;
            if (mission.boss) for (int i = 1; i <= 4; i++) if (!isCompleted("L" + mission.level + "-" + i)) return false;
            expectedXp += rewardXp(mission);
        }
        return xp == expectedXp;
    }
}
