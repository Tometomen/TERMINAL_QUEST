package jp.terminalquest.screen;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import jp.terminalquest.game.TerminalQuestGame;
import jp.terminalquest.mission.Mission;
import jp.terminalquest.mission.MissionCatalog;
import jp.terminalquest.mission.MissionSession;
import java.util.List;
import java.util.Collections;

public final class ResultScreen extends BaseScreen {
    public ResultScreen(final TerminalQuestGame game, final MissionSession session) {
        this(game, session, Collections.<String>emptyList());
    }

    public ResultScreen(final TerminalQuestGame game, final MissionSession session, List<String> newlyLearned) {
        super(game);
        header("MISSION REPORT / 達成記録");
        final Mission mission = session.getMission();
        Table panel = new Table(); panel.background(game.panel()); panel.pad(28);
        panel.add(accent("MISSION COMPLETE")).colspan(2).left().padBottom(15).row();
        panel.add(heading(mission.title, 1.35f)).colspan(2).left().padBottom(20).row();
        Label rank = heading(session.getRank(), 4f); rank.setColor(TerminalQuestGame.CYAN);
        panel.add(rank).width(230).height(150).left();
        Table stats = new Table(); stats.defaults().left().padBottom(8);
        stats.add(label("クリア時間")).width(250); stats.add(accent(formatTime(session.getElapsedSeconds()))).row();
        stats.add(label("実行コマンド")).width(250); stats.add(label(String.valueOf(session.getCommandCount()))).row();
        stats.add(label("実行エラー")).width(250); stats.add(label(String.valueOf(session.getErrorCount()))).row();
        stats.add(label("ヒント利用")).width(250); stats.add(label(String.valueOf(session.getHintCount()))).row();
        stats.add(label("プレイヤーXP")).width(250); stats.add(accent(String.valueOf(game.progress.getXp()))).row();
        panel.add(stats).growX().left().row();
        StringBuilder used = new StringBuilder();
        for (String command : session.getUsedCommands()) { if (used.length() > 0) used.append("  /  "); used.append(command); }
        Label commands = wrap("使用したコマンド：" + used); commands.setFontScale(.85f);
        panel.add(commands).width(940).colspan(2).left().padTop(24).padBottom(14).row();
        if (!newlyLearned.isEmpty()) {
            Label learned = accent("新しく習得：" + LevelSelectScreen.join(newlyLearned.toArray(new String[newlyLearned.size()]))); learned.setFontScale(.8f);
            panel.add(learned).colspan(2).left().padBottom(12).row();
        }
        Label learning = wrap("ランクにかかわらずクリアです。試行錯誤も学びの一部。\n別のコマンドや手順でもう一度解いて、理解を深めてみよう。");
        learning.setColor(TerminalQuestGame.MUTED); learning.setFontScale(.8f);
        panel.add(learning).width(940).colspan(2).left().padBottom(28).row();
        Table actions = new Table();
        final Mission following = nextMission(mission);
        actions.add(button(following != null ? "次のミッションへ  >" : "ミッション一覧へ  >", true, new Runnable() {
            @Override public void run() { game.setScreen(following != null ? new MissionScreen(game, following) : new LevelSelectScreen(game, mission.level)); }
        })).height(52).padRight(12);
        actions.add(button("もう一度挑戦", new Runnable() { @Override public void run() { game.setScreen(new MissionScreen(game, mission)); }})).height(52).padRight(12);
        actions.add(button("レベル選択", new Runnable() { @Override public void run() { game.setScreen(new LevelSelectScreen(game, mission.level)); }})).height(52);
        panel.add(actions).colspan(2).left().row();
        ScrollPane report = new ScrollPane(panel, game.skin); report.setScrollingDisabled(true, false); report.setFadeScrollBars(false); report.setSmoothScrolling(!game.progress.getSettings().reducedMotion);
        root.add(report).grow().row();
        footer(game.saves.wasLastSaveSuccessful() ? "進捗を保存しました  /  ブラウザのサイトデータを削除すると記録も消えます" : "保存できませんでした  /  ブラウザのサイトデータ設定を確認してください");
    }

    private Mission nextMission(Mission current) {
        boolean seen = false;
        for (Mission mission : MissionCatalog.all()) {
            if (seen && game.progress.isMissionUnlocked(mission) && !game.progress.isCompleted(mission.id)) return mission;
            if (mission.id.equals(current.id)) seen = true;
        }
        return null;
    }

    static String formatTime(float seconds) {
        int time = Math.max(0, (int)seconds);
        return (time / 60) + "分 " + (time % 60) + "秒";
    }
}
