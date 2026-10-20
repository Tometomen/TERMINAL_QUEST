package jp.terminalquest.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Timer;
import jp.terminalquest.game.TerminalQuestGame;
import jp.terminalquest.mission.Mission;
import jp.terminalquest.mission.MissionSession;
import jp.terminalquest.terminal.CommandResult;
import jp.terminalquest.ui.GameDialog;
import java.util.ArrayList;
import java.util.List;

/** Terminal remains the main interaction surface; every command is evaluated in memory. */
public final class MissionScreen extends BaseScreen {
    private final Mission mission;
    private final MissionSession session;
    private final List<String> history = new ArrayList<String>();
    private final List<String> newlyLearned = new ArrayList<String>();
    private int historyIndex;
    private String draft = "";
    private final StringBuilder transcript = new StringBuilder();
    private final Label output;
    private final ScrollPane scroll;
    private final TextField input;
    private final Label path;
    private final Label status;
    private final Label hint;
    private final TextButton next;
    private final Cell<TextButton> nextCell;
    private boolean recorded;

    public MissionScreen(final TerminalQuestGame game, final Mission mission) {
        super(game); this.mission = mission; session = new MissionSession(mission);
        header("MISSION / LEVEL " + mission.level + (mission.boss ? " / BOSS" : ""));
        Table top = new Table();
        top.add(heading(mission.title, 1.25f)).expandX().left();
        top.add(button("ミッション一覧", new Runnable() { @Override public void run() { game.setScreen(new LevelSelectScreen(game, mission.level)); }}));
        root.add(top).growX().padBottom(18).row();

        Table workspace = new Table();
        Table terminal = new Table(); terminal.background(game.panel()); terminal.pad(17).top();
        Table terminalBar = new Table();
        terminalBar.add(accent("●  ●  ●   cadet@terminal-quest")).expandX().left();
        Label virtual = muted("VIRTUAL SHELL"); virtual.setFontScale(.65f); terminalBar.add(virtual).right();
        terminal.add(terminalBar).growX().padBottom(13).row();
        output = new Label("", game.skin, "mono"); output.setWrap(true); output.setAlignment(Align.topLeft); output.setFontScale(.86f);
        Table outputTable = new Table(); outputTable.top().left(); outputTable.add(output).growX().top().left().pad(8);
        scroll = new ScrollPane(outputTable, game.skin); scroll.setFadeScrollBars(false); scroll.setScrollingDisabled(true, false); scroll.setOverscroll(false,false); scroll.setSmoothScrolling(!game.progress.getSettings().reducedMotion);
        terminal.add(scroll).grow().minHeight(240).row();
        path = accent(session.getEngine().getFileSystem().pwd() + " $"); path.setFontScale(.75f);
        terminal.add(path).left().padTop(12).padBottom(7).row();
        Table entry = new Table();
        input = new TextField("", game.skin); input.setMessageText("コマンドを入力して Enter"); input.setName("terminal-input");
        input.setFocusTraversal(false);
        input.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) { execute(); return true; }
                if (keycode == Input.Keys.UP) { navigateHistory(-1); return true; }
                if (keycode == Input.Keys.DOWN) { navigateHistory(1); return true; }
                return false;
            }
        });
        entry.add(input).growX().height(47).padRight(10);
        entry.add(button("実行", true, new Runnable() { @Override public void run() { execute(); }})).width(112).height(47);
        terminal.add(entry).growX().row();
        status = muted("READY / 入力できます"); status.setFontScale(.72f);
        terminal.add(status).left().padTop(9);
        workspace.add(terminal).grow().padRight(20);

        Table sidebar = new Table(); sidebar.background(game.panel()); sidebar.pad(20).top().left();
        Table lesson = new Table(); lesson.top().left();
        Label missionTag = accent("OBJECTIVE / ミッション"); missionTag.setFontScale(.73f);
        lesson.add(missionTag).left().padBottom(15).row();
        Label objective = wrap(mission.objective); objective.setFontScale(.9f);
        lesson.add(objective).width(284).left().padBottom(18).row();
        Label explanatory = wrap(mission.level <= 2 ? "入力例を参考に試してみよう。\n失敗しても進捗は失われません。" : "調査と操作を組み合わせて解決しよう。\n必要なときだけヒントを開けます。");
        explanatory.setColor(TerminalQuestGame.MUTED); explanatory.setFontScale(.72f);
        lesson.add(explanatory).width(284).left().padBottom(15).row();
        hint = wrap(mission.level <= 2 ? session.getInitialHint() + "\n\n入力例\n" + formatExamples(mission.exampleCommands) : "ヒントはまだ表示していません。");
        hint.setFontScale(.76f); hint.setColor(TerminalQuestGame.GOLD);
        lesson.add(hint).width(280).left().top().row();
        ScrollPane lessonScroll = new ScrollPane(lesson, game.skin); lessonScroll.setScrollingDisabled(true, false); lessonScroll.setFadeScrollBars(false); lessonScroll.setSmoothScrolling(!game.progress.getSettings().reducedMotion);
        sidebar.add(lessonScroll).width(284).growY().minHeight(180).left().padBottom(12).row();
        Table tools = new Table();
        TextButton hintButton = button("ヒント", new Runnable() {
            @Override public void run() { hint.setText(session.revealHint()); focus(); }
        }); hintButton.getLabel().setFontScale(.85f);
        tools.add(hintButton).width(133).height(42).padRight(9);
        TextButton bookButton = button("コマンド図鑑", new Runnable() {
            @Override public void run() { showCommandBook(); }
        }); bookButton.getLabel().setFontScale(.8f);
        tools.add(bookButton).width(142).height(42);
        sidebar.add(tools).growX().padBottom(9).row();
        sidebar.add(button("ターミナルに戻る", new Runnable() { @Override public void run() { focus(); }})).growX().height(42).padBottom(9).row();
        next = button("結果を見る  >", true, new Runnable() { @Override public void run() { game.setScreen(new ResultScreen(game, session, newlyLearned)); }});
        next.setVisible(false);
        nextCell = sidebar.add(next).growX().height(0).padTop(0); sidebar.row();
        Label noTime = muted("制限時間なし / 自分のペースで"); noTime.setFontScale(.65f);
        sidebar.add(noTime).left().expandY().bottom().padTop(12);
        workspace.add(sidebar).width(324).growY();
        root.add(workspace).grow().row();
        footer("Enter 実行  /  ↑ ↓ 入力履歴  /  ← → カーソル移動  /  help 対応構文");
        append("TERMINAL QUEST · 仮想Linux環境\n" + "実際のOSへのアクセスはありません。help でコマンドを確認できます。\n\n" + "目標：" + mission.objective + "\n");
    }

    private String formatExamples(String[] examples) {
        StringBuilder result = new StringBuilder();
        for (String example : examples) { if (result.length() > 0) result.append("\n"); result.append(example); }
        return result.toString();
    }

    private void showCommandBook() {
        final com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = new GameDialog("コマンド図鑑", game) {
            @Override public void hide() { super.hide(); focus(); }
        };
        Label text = wrap(CommandBookScreen.bookText(mission.level)); text.setFontScale(.77f);
        Table content = new Table(); content.add(text).width(780).left().top();
        ScrollPane pane = new ScrollPane(content, game.skin); pane.setFadeScrollBars(false);
        dialog.getContentTable().pad(20).add(pane).width(810).height(500);
        dialog.button("閉じて入力へ戻る"); dialog.show(stage);
    }

    private void execute() {
        if (session.isComplete()) { focus(); return; }
        String command = input.getText();
        if (command.trim().length() == 0) { focus(); return; }
        history.add(command); historyIndex = history.size(); draft = "";
        append("\n" + session.getEngine().getFileSystem().pwd() + " $ " + command + "\n");
        CommandResult result = session.execute(command);
        if (command.trim().equals("clear")) { transcript.setLength(0); output.setText(""); }
        else if (result.getOutput().length() > 0) append(result.getOutput() + (result.getOutput().endsWith("\n") ? "" : "\n"));
        path.setText(session.getEngine().getFileSystem().pwd() + " $");
        input.setText("");
        status.setText(result.isSuccess() ? "SUCCESS / 実行しました" : "ERROR / " + (mission.level <= 2 ? "パスと引数を確認して再入力。help で使い方を表示できます。" : "出力のエラーを確認してください。"));
        status.setColor(result.isSuccess() ? TerminalQuestGame.CYAN : TerminalQuestGame.RED);
        game.cue(result.isSuccess());
        if (session.isComplete() && !recorded) {
            recorded = true;
            for (String commandName : session.getUsedCommands()) if (!game.progress.getMasteredCommands().contains(commandName)) newlyLearned.add(commandName);
            game.progress.complete(session); boolean saved = game.save();
            append("\n✓ MISSION COMPLETE\n目標を達成しました。" + (saved ? "進捗を保存しました。" : "進捗を保存できませんでした。ブラウザのサイトデータ設定を確認してください。") + "\n");
            status.setText("MISSION COMPLETE / おめでとう、エンジニア！"); status.setColor(TerminalQuestGame.CYAN);
            next.setVisible(true); nextCell.height(45).padTop(12); ((Table) next.getParent()).invalidateHierarchy(); input.setDisabled(true); stage.setKeyboardFocus(next);
        } else focus();
    }

    private void append(String text) {
        transcript.append(text);
        if (transcript.length() > 45000) transcript.delete(0, transcript.length() - 40000);
        output.setText(transcript.toString());
        scroll.layout(); scroll.setScrollPercentY(1); scroll.updateVisualScroll();
        Gdx.app.postRunnable(new Runnable() { @Override public void run() { scroll.layout(); scroll.setScrollPercentY(1); scroll.updateVisualScroll(); }});
    }

    private void navigateHistory(int direction) {
        if (history.size() == 0) return;
        if (historyIndex == history.size()) draft = input.getText();
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        input.setText(historyIndex == history.size() ? draft : history.get(historyIndex));
        input.setCursorPosition(input.getText().length());
    }

    private void focus() { if (!session.isComplete()) stage.setKeyboardFocus(input); }
    @Override public void show() { super.show(); focus(); }
}
