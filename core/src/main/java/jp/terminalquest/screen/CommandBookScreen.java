package jp.terminalquest.screen;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import jp.terminalquest.game.TerminalQuestGame;

public final class CommandBookScreen extends BaseScreen {
    public CommandBookScreen(final TerminalQuestGame game, final Runnable back) {
        super(game);
        header("REFERENCE / コマンド図鑑");
        Table title = new Table(); title.add(heading("困ったときの、コマンド図鑑。", 1.35f)).expandX().left();
        title.add(button("戻る", back)); root.add(title).growX().padBottom(20).row();
        Label text = wrap(bookText(6)); text.setFontScale(.86f);
        Table content = new Table(); content.background(game.panel()); content.pad(25); content.add(text).width(1100).top().left();
        ScrollPane scroll = new ScrollPane(content, game.skin); scroll.setScrollingDisabled(true, false); scroll.setFadeScrollBars(false);
        root.add(scroll).grow().row();
        footer("コマンドはゲーム内のメモリだけを操作します  /  フル機能のBashではありません");
    }

    public static String bookText(int maximumLevel) {
        StringBuilder text = new StringBuilder();
        text.append("LEVEL 1 / 入門・探索\n");
        text.append("pwd                    現在地（カレントディレクトリ）を表示\n");
        text.append("ls  /  ls -la          ファイルとディレクトリを一覧表示\n");
        text.append("cd logs                相対パスでlogsへ移動\n");
        text.append("cd /srv/data/backups   /から始まる絶対パスで移動\n");
        text.append("cd ..                  親ディレクトリへ移動\n\n");
        if (maximumLevel >= 2) {
            text.append("LEVEL 2 / 基礎・ファイル管理\n");
            text.append("cat readme.txt         ファイルの内容を読む\n");
            text.append("touch memo.txt         空のファイルを作成（既存内容は維持）\n");
            text.append("mkdir backup           ディレクトリを作成\n");
            text.append("mkdir -p backup/logs   親ディレクトリもまとめて作成\n");
            text.append("cp readme.txt backup/  ファイルをコピー\n");
            text.append("mv memo.txt notes.txt  ファイルの移動・名前変更\n\n");
        }
        if (maximumLevel >= 3) {
            text.append("LEVEL 3 / 実践・ログ調査\n");
            text.append("grep ERROR logs/app.log   該当する行を検索（-iで大文字小文字を無視）\n");
            text.append("find . -name app.log      名前でファイルを探す\n");
            text.append("head -n 3 logs/app.log    先頭3行\n");
            text.append("tail -n 10 logs/app.log   最後の10行\n");
            text.append("wc -l logs/app.log        行数を集計\n\n");
        }
        if (maximumLevel >= 4) {
            text.append("LEVEL 4 / 応用・標準入出力\n");
            text.append("sort data/hosts.txt             行を並び替える\n");
            text.append("sort data/hosts.txt | uniq      隣接する重複行を取り除く\n");
            text.append("sort data/hosts.txt | uniq -c   重複の数も表示する\n");
            text.append("grep ERROR logs/app.log | head -n 3 > errors.txt\n");
            text.append("    | は左の出力を右の入力へ渡す。> は上書き、>> は追記。\n");
            text.append("echo '調査メモ' >> report.txt   文字列を仮想ファイルへ追記\n\n");
        }
        if (maximumLevel >= 5) {
            text.append("LEVEL 5 / 運用・サーバー管理\n");
            text.append("ls -l /etc/web/config   権限を確認\n");
            text.append("chmod 644 /etc/web/config   読み取り権限を修復（数字は8進数）\n");
            text.append("ps                     仮想プロセスの一覧\n");
            text.append("kill 42                異常プロセスを停止\n");
            text.append("df                     仮想ディスクの空き容量\n");
            text.append("du /var/log            ディレクトリの使用量を調査\n");
            text.append("cat /dev/null > /var/log/archive.log   大きな仮想ログを空にする\n\n");
        }
        if (maximumLevel >= 6) {
            text.append("LEVEL 6 / 障害復旧・サービス\n");
            text.append("systemctl status web   サービスの稼働状態を調査\n");
            text.append("journalctl -u web      サービスの障害ログを確認\n");
            text.append("systemctl restart web  原因を修復したあとサービスを再起動\n");
            text.append("    再起動だけでは直りません。権限・容量・異常プロセスを調べよう。\n\n");
        }
        text.append("対応範囲と安全性\n");
        text.append("引用符つきの引数、パイプ、> と >> に対応します。\n");
        text.append("&&、;、変数展開、コマンド置換、外部プログラムの実行は未対応です。\n");
        text.append("未対応のコマンド・構文はエラーとして拒否します。help も利用できます。\n");
        text.append("ミッションは文字列の暗記ではなく、調査結果や仮想環境の状態で判定します。");
        return text.toString();
    }
}
