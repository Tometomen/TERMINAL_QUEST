package jp.terminalquest.mission;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The curriculum is data, shared by menus, progression and the command book. */
public final class DifficultyLevel {
    public final int id;
    public final String category;
    public final String title;
    public final String description;
    public final String[] commands;
    public final int rewardXp;
    public final boolean implemented;
    public final String unlockCondition;
    public final String bossMissionId;
    public final int initialHintCount;

    private DifficultyLevel(int id, String category, String title, String description,
                            String[] commands, int rewardXp) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.description = description;
        this.commands = commands;
        this.rewardXp = rewardXp;
        this.implemented = true;
        this.unlockCondition = id == 1 ? "最初からプレイ可能" : "LEVEL " + (id - 1) + " のボスをクリア";
        this.bossMissionId = "L" + id + "-5";
        this.initialHintCount = id <= 2 ? 1 : 0;
    }

    private static final List<DifficultyLevel> LEVELS = Collections.unmodifiableList(Arrays.asList(
        new DifficultyLevel(1, "初学者Ⅰ", "入門・探索訓練", "現在地、一覧、パスを知る", new String[]{"pwd", "ls", "cd"}, 100),
        new DifficultyLevel(2, "初学者Ⅱ", "基礎・ファイル管理", "作成・コピー・移動を練習する", new String[]{"cat", "touch", "mkdir", "cp", "mv"}, 150),
        new DifficultyLevel(3, "中級者Ⅰ", "実践・ログ調査", "ログから必要な情報を見つける", new String[]{"grep", "find", "head", "tail", "wc"}, 200),
        new DifficultyLevel(4, "中級者Ⅱ", "応用・コマンド連携", "パイプと出力でレポートを作る", new String[]{"sort", "uniq", "|", ">", ">>"}, 250),
        new DifficultyLevel(5, "上級者Ⅰ", "運用・サーバー管理", "権限・プロセス・容量を管理する", new String[]{"chmod", "ps", "kill", "df", "du"}, 300),
        new DifficultyLevel(6, "上級者Ⅱ", "達人・障害復旧", "原因を切り分け、サービスを復旧する", new String[]{"systemctl", "journalctl"}, 400)
    ));

    public static List<DifficultyLevel> all() { return LEVELS; }

    public static DifficultyLevel byId(int id) {
        if (id < 1 || id > LEVELS.size()) throw new IllegalArgumentException("Unknown level: " + id);
        return LEVELS.get(id - 1);
    }
}
