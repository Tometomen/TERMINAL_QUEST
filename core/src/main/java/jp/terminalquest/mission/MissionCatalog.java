package jp.terminalquest.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static jp.terminalquest.mission.MissionCondition.Type.*;

/** All 30 missions live here as data: title, scenario, hints and composable goals. */
public final class MissionCatalog {
    private static final List<Mission> MISSIONS = new ArrayList<Mission>();
    private static final String HOME = "/home/cadet/";
    private static final String LOG = HOME + "logs/app.log";
    private static final String HOSTS = HOME + "data/hosts.txt";
    private static final String[][] NONE = new String[0][0];

    static {
        add(1, 1, "最初の現在地", "現在いるディレクトリの絶対パスを表示してください。",
            hints("pwd は現在地を表示します。", "コマンドは小文字で入力します。", "pwd と入力して Enter。"), cmds("pwd"), "default", NONE,
            goal(OBSERVED, "pwd", "/home/cadet"));
        add(1, 2, "データセンターの入口", "現在地にあるファイルとディレクトリの一覧を表示してください。",
            hints("ls はファイル一覧を表示します。", "ディレクトリはフォルダのことです。", "ls と入力して Enter。"), cmds("ls"), "default", NONE,
            goal(OBSERVED, "ls", "readme.txt"));
        add(1, 3, "ログへの一歩", "logs ディレクトリへ移動してください。現在地は /home/cadet です。",
            hints("cd は現在地を変更します。", "相対パスは現在地を基準に指定します。", "cd logs"), cmds("cd logs"), "default", NONE,
            goal(CURRENT_DIRECTORY, HOME + "logs", ""));
        add(1, 4, "絶対パスの旅", "/srv/data に移動してください。先頭の / は仮想環境のルートです。",
            hints("cd の後に絶対パスを指定できます。", "絶対パスは / から始まります。", "cd /srv/data"), cmds("cd /srv/data"), "default", NONE,
            goal(CURRENT_DIRECTORY, "/srv/data", ""));
        add(1, 5, "BOSS：バックアップ室", "/srv/data の中を探索し、backups ディレクトリへ到達してください。",
            hints("pwd・ls・cd を組み合わせましょう。", "cd /srv/data の後、ls で確認できます。", "cd /srv/data/backups"), cmds("ls /srv/data", "cd /srv/data/backups"), "default", NONE,
            goal(CURRENT_DIRECTORY, "/srv/data/backups", ""));

        add(2, 1, "作業メモ", "/home/cadet/notes.txt というファイルを作成してください。",
            hints("touch で空のファイルを作れます。", "touch の後にファイル名を書きます。", "touch notes.txt"), cmds("touch notes.txt"), "default", NONE,
            goal(FILE_EXISTS, HOME + "notes.txt", ""));
        add(2, 2, "保管場所", "/home/cadet/backup ディレクトリを作成してください。",
            hints("mkdir でディレクトリを作れます。", "現在地は /home/cadet です。", "mkdir backup"), cmds("mkdir backup"), "default", NONE,
            goal(DIRECTORY_EXISTS, HOME + "backup", ""));
        add(2, 3, "引き継ぎを読む", "readme.txt を読み、引き継ぎメッセージを確認してください。",
            hints("cat はテキストファイルを表示します。", "cat の後に対象のパスを書きます。", "cat readme.txt"), cmds("cat readme.txt"), "default", NONE,
            goal(OBSERVED, "cat", "Welcome to TERMINAL QUEST"));
        add(2, 4, "最初のコピー", "readme.txt を archive/readme.txt にコピーし、元のファイルも残してください。",
            hints("cp はコピー元、コピー先の順に指定します。", "archive ディレクトリは用意されています。", "cp readme.txt archive/readme.txt"), cmds("cp readme.txt archive/readme.txt"), "default", NONE,
            goal(FILE_MATCHES_FILE, HOME + "archive/readme.txt", HOME + "readme.txt"));
        add(2, 5, "BOSS：引き継ぎバックアップ", "backup を作り、readme.txt のコピーと、notes.txt を operations.txt に改名して移動したファイルを保管してください。",
            hints("mkdir・cp・mv を使います。", "コピー元の readme.txt は残し、notes.txt は移動します。", "mkdir backup → cp readme.txt backup/readme.txt → mv notes.txt backup/operations.txt"),
            cmds("mkdir backup", "cp readme.txt backup/readme.txt", "mv notes.txt backup/operations.txt"), "default", new String[][]{{HOME + "notes.txt", "Operations handover\n"}},
            goal(DIRECTORY_EXISTS, HOME + "backup", ""), goal(FILE_MATCHES_FILE, HOME + "backup/readme.txt", HOME + "readme.txt"),
            goal(FILE_EQUALS, HOME + "backup/operations.txt", "Operations handover\n"), goal(ABSENT, HOME + "notes.txt", ""));

        add(3, 1, "エラーの痕跡", "logs/app.log の中から ERROR を含む行を表示してください。",
            hints("文字列でログを絞り込めます。", "検索には grep を使います。", "grep ERROR logs/app.log"), cmds("grep ERROR logs/app.log"), "default", NONE,
            goal(OBSERVED, "grep", "ERROR"));
        add(3, 2, "行方不明の報告書", "/srv 以下で crash-report.txt の配置場所を見つけて表示してください。",
            hints("ディレクトリの中を再帰的に検索します。", "find の -name オプションが役立ちます。", "find /srv -name crash-report.txt"), cmds("find /srv -name crash-report.txt"), "default",
            new String[][]{{"/srv/data/backups/crash-report.txt", "Incident 09:42\n"}}, goal(OBSERVED, "find,ls", "/srv/data/backups/crash-report.txt"));
        add(3, 3, "直近の出来事", "logs/app.log の末尾を表示して、最近のイベントを確認してください。",
            hints("大きいログは最後の部分から読むと便利です。", "tail は末尾の行を表示します。", "tail -n 10 logs/app.log"), cmds("tail -n 10 logs/app.log"), "default", NONE,
            goal(OBSERVED, "tail@" + LOG, "@LAST_LINE"));
        add(3, 4, "ログの規模", "logs/app.log の行数を表示してください。",
            hints("テキストの行数を数えるコマンドがあります。", "wc の -l は行数です。", "wc -l logs/app.log"), cmds("wc -l logs/app.log"), "default", NONE,
            goal(OBSERVED, "wc:-l@" + LOG, "@LINE_COUNT"));
        add(3, 5, "BOSS：障害の二つの手掛かり", "logs/app.log を調べ、disk full と permission denied の両方のエラーを確認してください。時刻にも注目しましょう。",
            hints("同じログに複数の原因が記録されています。", "全体を読む方法と、ERROR で絞る方法があります。", "grep ERROR logs/app.log"), cmds("grep ERROR logs/app.log"), "default", NONE,
            goal(OBSERVED, "grep,cat,head,tail", "disk full"), goal(OBSERVED, "grep,cat,head,tail", "permission denied"));

        add(4, 1, "証拠を保存", "logs/app.log の ERROR 行だけを errors.txt に保存してください。",
            hints("標準出力を仮想ファイルに書き込めます。", "> は上書き、>> は追記です。", "grep ERROR logs/app.log > errors.txt"), cmds("grep ERROR logs/app.log > errors.txt"), "default", NONE,
            goal(FILE_EQUALS, HOME + "errors.txt", "@ERROR_LINES"));
        add(4, 2, "整列した一覧", "data/hosts.txt を辞書順に並べ、sorted.txt に保存してください。",
            hints("行を並べ替えるコマンドを使います。", "sort の結果を > で保存できます。", "sort data/hosts.txt > sorted.txt"), cmds("sort data/hosts.txt > sorted.txt"), "default", NONE,
            goal(FILE_EQUALS, HOME + "sorted.txt", "api\napi\ndb\nweb\nweb\n"));
        add(4, 3, "重複のないホスト", "data/hosts.txt から重複を除いた辞書順の一覧を unique.txt に保存してください。",
            hints("uniq は連続する重複だけをまとめます。", "先に sort して | で uniq へ渡しましょう。", "sort data/hosts.txt | uniq > unique.txt"), cmds("sort data/hosts.txt | uniq > unique.txt"), "default", NONE,
            goal(FILE_EQUALS, HOME + "unique.txt", "api\ndb\nweb\n"));
        add(4, 4, "最初のエラー", "logs/app.log の最初の ERROR 行だけを first-error.txt に保存してください。",
            hints("絞り込みと先頭の取り出しを連携します。", "grep の出力を head -n 1 へ渡せます。", "grep ERROR logs/app.log | head -n 1 > first-error.txt"), cmds("grep ERROR logs/app.log | head -n 1 > first-error.txt"), "default", NONE,
            goal(FILE_EQUALS, HOME + "first-error.txt", "@FIRST_ERROR"));
        add(4, 5, "BOSS：調査レポート", "ERROR 行を辞書順に並べ、重複を除き、archive/report.txt に保存してください。",
            hints("結果の内容が正しければ、複数の手順で達成できます。", "grep → sort → uniq の標準出力をつなげます。", "grep ERROR logs/app.log | sort | uniq > archive/report.txt"), cmds("grep ERROR logs/app.log | sort | uniq > archive/report.txt"), "default", NONE,
            goal(FILE_EQUALS, HOME + "archive/report.txt", "@SORTED_ERRORS"));

        add(5, 1, "読めない設定", "/etc/web/config のアクセス権限を調べて表示してください。",
            hints("ファイルの詳細表示で権限を確認できます。", "ls の -l は詳細情報です。", "ls -l /etc/web/config"), cmds("ls -l /etc/web/config"), "default", NONE,
            goal(OBSERVED, "ls", "----------"));
        add(5, 2, "権限の修復", "/etc/web/config を所有者は読み書き、他は読み取りのみ（644）に直してください。",
            hints("権限はメモリ内の仮想データです。", "数値モードで所有者・グループ・その他の権限を指定できます。", "chmod 644 /etc/web/config"), cmds("chmod 644 /etc/web/config"), "default", NONE,
            goal(PERMISSIONS, "/etc/web/config", "644"));
        add(5, 3, "暴走した書き込み", "プロセス一覧を確認し、runaway-log プロセスを停止してください。init は停止しないでください。",
            hints("プロセスには PID という識別番号があります。", "ps で調べ、kill で仮想プロセスを停止します。", "ps → kill 42"), cmds("ps", "kill 42"), "default", NONE,
            goal(PROCESS_STOPPED, "42", ""));
        add(5, 4, "容量を食べるもの", "ディスクの使用量と /var/log 以下の内訳を調べ、archive.log を見つけてください。",
            hints("全体の使用量と各ファイルの使用量を比べましょう。", "df は全体、du -a はファイルを含む内訳です。", "df → du -a /var/log"), cmds("df", "du -a /var/log"), "default", NONE,
            goal(OBSERVED, "df", ""), goal(OBSERVED, "du", "archive.log"));
        add(5, 5, "BOSS：運用を正常化", "設定ファイル /etc/web/config の権限を 644 に修復し、暴走するログ書き込みプロセスを停止してください。",
            hints("問題は権限とプロセスの二つです。", "調査と修復の順序は自由です。", "chmod 644 /etc/web/config → kill 42"), cmds("chmod 644 /etc/web/config", "kill 42"), "default", NONE,
            goal(PERMISSIONS, "/etc/web/config", "644"), goal(PROCESS_STOPPED, "42", ""));

        add(6, 1, "沈黙した Web", "web サービスの状態とサービスログを表示し、停止の原因を調査してください。",
            hints("サービスの状態とログを別々に確認します。", "systemctl status と journalctl -u が使えます。", "systemctl status web → journalctl -u web"), cmds("systemctl status web", "journalctl -u web"), "default", NONE,
            goal(OBSERVED, "systemctl", "web"), goal(OBSERVED, "journalctl", ""));
        add(6, 2, "ディスクを取り戻す", "ログ書き込みは停止済みです。/var/log/archive.log の仮想データを空にし、ディスク使用量を 800 MB 未満にしてください。",
            hints("今回はログを空にして安全に容量を戻せます。実在のファイルには影響しません。", "/dev/null は空の仮想ファイルです。", "cat /dev/null > /var/log/archive.log"), cmds("cat /dev/null > /var/log/archive.log"), "disk", NONE,
            goal(DISK_BELOW, "", "800000000"));
        add(6, 3, "設定を読めるように", "容量とプロセスは正常です。Web 設定ファイルのアクセス権限を 644 に修復してください。",
            hints("/etc/web/config の権限に問題があります。", "所有者 6、グループ 4、その他 4 にします。", "chmod 644 /etc/web/config"), cmds("chmod 644 /etc/web/config"), "permission", NONE,
            goal(PERMISSIONS, "/etc/web/config", "644"));
        add(6, 4, "再起動の条件", "容量とプロセスは正常ですが、設定の権限異常で web が停止中です。問題を修正し、サービスを稼働させてください。",
            hints("再起動だけでは原因は解決しません。", "権限を直してからサービスを再起動できます。", "chmod 644 /etc/web/config → systemctl restart web"), cmds("chmod 644 /etc/web/config", "systemctl restart web"), "permission", NONE,
            goal(SERVICE_ACTIVE, "web", ""));
        add(6, 5, "FINAL BOSS：データセンター復旧", "Web サービス障害が発生。利用者がサイトにアクセスできません。原因を調査し、web を正常稼働に復旧してください。操作順は自由です。",
            hints("権限・容量・暴走プロセスを調べましょう。", "設定を修復し、書き込みを止め、巨大ログを空にしてから再起動します。", "chmod 644 /etc/web/config → kill 42 → cat /dev/null > /var/log/archive.log → systemctl restart web"),
            cmds("systemctl status web", "journalctl -u web", "ps", "df", "du /var/log", "chmod 644 /etc/web/config", "kill 42", "cat /dev/null > /var/log/archive.log", "systemctl restart web"), "default", NONE,
            goal(SERVICE_ACTIVE, "web", ""));
    }

    private MissionCatalog() { }
    private static MissionCondition goal(MissionCondition.Type type, String target, String value) { return new MissionCondition(type, target, value); }
    private static String[] hints(String a, String b, String c) { return new String[]{a, b, c}; }
    private static String[] cmds(String... commands) { return commands; }
    private static void add(int level, int number, String title, String objective, String[] hints,
                            String[] commands, String scenario, String[][] files, MissionCondition... conditions) {
        MISSIONS.add(new Mission("L" + level + "-" + number, level, title, objective, hints, commands, scenario, files, conditions));
    }

    public static List<Mission> all() { return Collections.unmodifiableList(MISSIONS); }
    public static Mission byId(String id) {
        for (Mission mission : MISSIONS) if (mission.id.equals(id)) return mission;
        throw new IllegalArgumentException("Unknown mission: " + id);
    }
    public static List<Mission> forLevel(int level) {
        DifficultyLevel.byId(level);
        List<Mission> found = new ArrayList<Mission>();
        for (Mission mission : MISSIONS) if (mission.level == level) found.add(mission);
        return Collections.unmodifiableList(found);
    }
}
