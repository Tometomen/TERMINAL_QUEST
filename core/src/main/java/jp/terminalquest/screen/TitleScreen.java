package jp.terminalquest.screen;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import jp.terminalquest.ui.GameDialog;
import jp.terminalquest.game.TerminalQuestGame;

public final class TitleScreen extends BaseScreen {
    public TitleScreen(final TerminalQuestGame game) {
        super(game);
        header("VIRTUAL DATACENTER / TRAINING CONSOLE");
        Table columns = new Table();
        Table hero = new Table(); hero.top().left();
        Label program = accent("// ENGINEER TRAINING PROGRAM"); program.setFontScale(.8f);
        hero.add(program).left().padBottom(14).row();
        Label title = heading("TERMINAL\nQUEST", 2.3f); title.setColor(TerminalQuestGame.TEXT);
        hero.add(title).left().padBottom(18).row();
        hero.add(heading("その一行が、サーバーを救う。", 1f)).left().padBottom(12).row();
        Label intro = wrap("仮想データセンターへようこそ。\n新人エンジニアとして探索から障害復旧まで、\n30のミッションでLinuxの力を身につけよう。");
        intro.setColor(TerminalQuestGame.MUTED);
        intro.setFontScale(.9f);
        hero.add(intro).width(590).left().padBottom(23).row();
        Table actions = new Table();
        actions.add(button(game.progress.getXp() > 0 ? "トレーニングを続ける  >" : "トレーニングを始める  >", true, new Runnable() {
            @Override public void run() { game.setScreen(new LevelSelectScreen(game)); }
        })).height(56).padRight(12);
        actions.add(button("60秒タイピング", new Runnable() {
            @Override public void run() { game.setScreen(new ChallengeScreen(game)); }
        })).height(56);
        hero.add(actions).left().row();
        Label features = muted("6 LEVELS     /     30 MISSIONS     /     100% VIRTUAL"); features.setFontScale(.75f);
        hero.add(features).left().padTop(16);
        columns.add(hero).width(685).growY().left();

        Table terminal = new Table(); terminal.background(game.panel()); terminal.pad(24); terminal.top().left();
        Label lights = accent("●  ●  ●     cadet@terminal-quest"); lights.setFontScale(.72f);
        terminal.add(lights).left().padBottom(18).row();
        Label session = muted("SESSION 001 / ONBOARDING"); session.setFontScale(.75f);
        terminal.add(session).left().padBottom(22).row();
        Label pwd = accent("$ pwd"); pwd.setFontScale(.85f);
        terminal.add(pwd).left().padBottom(4).row();
        Label current = label("/home/cadet"); current.setFontScale(.85f);
        terminal.add(current).left().padBottom(18).row();
        Label cat = accent("$ cat readme.txt"); cat.setFontScale(.85f);
        terminal.add(cat).left().padBottom(4).row();
        Label welcome = wrap("Welcome, engineer.\n小さなコマンドから、\n大きな問題解決へ。" ); welcome.setFontScale(.85f);
        terminal.add(welcome).width(350).left().padBottom(20).row();
        Label safe = wrap("すべての操作はゲーム内の仮想環境で実行されます。実際のファイルやサーバーには作用しません。");
        safe.setFontScale(.7f); safe.setColor(TerminalQuestGame.MUTED);
        terminal.add(safe).width(350).left().expandY().bottom();
        columns.add(terminal).width(450).height(540).padLeft(25);
        root.add(columns).grow().row();
        Table bottom = new Table();
        bottom.add(button("コマンド図鑑", new Runnable() { @Override public void run() { game.setScreen(new CommandBookScreen(game, new Runnable() { @Override public void run() { game.setScreen(new TitleScreen(game)); }})); }})).left();
        bottom.add(button("実績 " + game.progress.getAchievements().size() + " / 7", new Runnable() { @Override public void run() { showAchievements(); }})).padLeft(12);
        bottom.add(muted("XP " + game.progress.getXp() + "   /   習得 " + game.progress.getMasteredCommands().size() + " コマンド")).expandX().right();
        root.add(bottom).growX().padTop(10).row();
        footer(!game.saves.isStorageAvailable() ? "保存領域が利用できません。今回は遊べますが、進捗は保存されません。" : game.saves.wasRecovered() ? "保存データを読み込めなかったため、安全な初期状態で開始しました。" : "PC・物理キーボード推奨  /  ミッションに制限時間はありません");
    }

    private void showAchievements() {
        GameDialog dialog = new GameDialog("実績 / ACHIEVEMENTS", game);
        StringBuilder text = new StringBuilder();
        for (String achievement : game.progress.getAchievements()) text.append("✓ ").append(achievement).append("\n\n");
        if (text.length() == 0) text.append("まだ実績がありません。\n最初のミッションをクリアして、最初の一歩を記録しよう。\n\n");
        text.append("全7実績：初クリア・探索・ファイル管理・ログ分析・\n障害復旧・全30クリア・タイピング10問達成");
        Label achievements = wrap(text.toString()); achievements.setFontScale(.85f);
        dialog.getContentTable().pad(25).add(achievements).width(700);
        dialog.button("閉じる"); dialog.show(stage);
    }
}
