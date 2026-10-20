package jp.terminalquest.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import jp.terminalquest.game.TerminalQuestGame;
import jp.terminalquest.mission.DifficultyLevel;
import jp.terminalquest.system.TypingSystem;

/** Independent, optional typing challenge; timing never gates mission progress. */
public final class ChallengeScreen extends BaseScreen {
    private int selected = 1;
    private TypingSystem typing;
    private boolean recorded;
    private final Label prompt;
    private final Label timer;
    private final Label metrics;
    private final Label feedback;
    private final Label best;
    private final Label available;
    private final TextField input;
    private final Table choices = new Table();
    private final TextButton start;

    public ChallengeScreen(final TerminalQuestGame game) {
        super(game);
        header("OPTIONAL PRACTICE / 60秒タイピング");
        Table top = new Table(); top.add(heading("コマンドを、指になじませる。", 1.2f)).expandX().left();
        top.add(button("ホーム", new Runnable() { @Override public void run() { game.setScreen(new TitleScreen(game)); }})).height(42);
        root.add(top).growX().padBottom(8).row();
        Label intro = muted("表示されたコマンドを正確に入力してEnter。入力内容は実行されません。"); intro.setFontScale(.82f);
        root.add(intro).left().padBottom(8).row();
        buildChoices(); root.add(choices).growX().padBottom(8).row();

        Table training = new Table(); training.background(game.panel()); training.pad(16).top().left();
        Table summary = new Table();
        timer = accent("60.0 SEC"); timer.setFontScale(1.3f);
        summary.add(timer).expandX().left();
        best = muted("自己ベスト  " + game.progress.getTypingBest(selected) + " コマンド"); best.setFontScale(.75f); summary.add(best).right();
        training.add(summary).growX().padBottom(8).row();
        Label tag = muted("TYPE THE COMMAND / 出題"); tag.setFontScale(.65f);
        training.add(tag).left().padBottom(6).row();
        prompt = new Label("開始するとコマンドが表示されます", game.skin, "mono"); prompt.setWrap(true); prompt.setFontScale(.95f); prompt.setColor(TerminalQuestGame.CYAN);
        training.add(prompt).width(1080).minHeight(40).left().padBottom(8).row();
        input = new TextField("", game.skin); input.setDisabled(true); input.setFocusTraversal(false); input.setName("typing-input"); input.setMessageText("表示どおりに入力して Enter");
        input.addListener(new InputListener() { @Override public boolean keyDown(InputEvent event, int keycode) { if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) { submit(); return true; } return false; }});
        training.add(input).growX().height(48).padBottom(10).row();
        feedback = muted("速さより正確さを大切に。タイピングの成績はミッションと別に保存されます。"); feedback.setFontScale(.7f); feedback.setWrap(true);
        training.add(feedback).width(1080).left().padBottom(8).row();
        metrics = label("完成 0    正確率 0.0%    KPM 0.0    最大コンボ 0"); metrics.setFontScale(.85f);
        training.add(metrics).left().padBottom(8).row();
        available = muted(""); available.setFontScale(.7f);
        training.add(available).left().padBottom(8).row();
        start = button("60秒チャレンジを開始  >", true, new Runnable() { @Override public void run() { begin(); }});
        training.add(start).height(46).left().expandY().bottom();
        root.add(training).grow().row();
        footer("KPM = 入力文字数 ÷ 経過分数  /  正確率 = 一致した文字数 ÷ 評価文字数（不足分もミスとして集計）");
        updateAvailability();
    }

    private void buildChoices() {
        choices.clear();
        for (final DifficultyLevel level : DifficultyLevel.all()) {
            TextButton choice = button("LEVEL " + level.id, level.id == selected, new Runnable() {
                @Override public void run() {
                    if (typing != null && !typing.isFinished()) return;
                    selected = level.id; typing = null; buildChoices(); updateAvailability();
                }
            });
            choice.getLabel().setFontScale(.8f);
            choices.add(choice).growX().height(42).padRight(level.id == 6 ? 0 : 10);
        }
    }

    private void begin() {
        if (typing != null && !typing.isFinished()) return;
        if (TypingSystem.availablePromptCount(selected, game.progress.getMasteredCommands()) == 0) return;
        typing = new TypingSystem(selected, game.progress.getMasteredCommands()); typing.start(); recorded = false;
        input.setDisabled(false); input.setText(""); stage.setKeyboardFocus(input);
        prompt.setText(typing.getPrompt()); feedback.setText("Enterで送信。間違えたら同じコマンドに再挑戦できます。");
        feedback.setColor(TerminalQuestGame.MUTED); start.setDisabled(true); start.setText("チャレンジ中…");
        updateMetrics();
    }

    private void updateAvailability() {
        int count = TypingSystem.availablePromptCount(selected, game.progress.getMasteredCommands());
        available.setText("ミッションで習得済みのコマンドから出題：" + count + " パターン");
        best.setText("自己ベスト " + game.progress.getTypingBest(selected) + "問  /  " + oneDecimal(game.progress.getTypingAccuracy(selected)) + "%  /  " + oneDecimal(game.progress.getTypingKpm(selected)) + " KPM");
        start.setDisabled(count == 0);
        start.setText(count == 0 ? "ミッションでコマンドを習得しよう" : "60秒チャレンジを開始  >");
        prompt.setText(count == 0 ? "このレベルのミッションでコマンドを習得すると練習できます。" : "開始するとコマンドが表示されます");
        timer.setText("60.0 SEC");
        metrics.setText("完成 0    正確率 0.0%    KPM 0.0    最大コンボ 0");
    }

    private void submit() {
        if (typing == null || typing.isFinished()) return;
        boolean correct = typing.submit(input.getText());
        game.cue(correct);
        feedback.setText(correct ? "GOOD / 正確に入力できました！" : "RETRY / 文字・空白・大文字小文字を確認しよう。");
        feedback.setColor(correct ? TerminalQuestGame.CYAN : TerminalQuestGame.RED);
        input.setText(""); prompt.setText(typing.getPrompt()); updateMetrics(); stage.setKeyboardFocus(input);
    }

    private void updateMetrics() {
        if (typing == null) return;
        timer.setText(oneDecimal(typing.getRemainingSeconds()) + " SEC");
        metrics.setText("完成 " + typing.getCompleted() + "    正確率 " + oneDecimal(typing.getAccuracy()) + "%    KPM " + oneDecimal(typing.getKpm()) + "    最大コンボ " + typing.getMaxCombo());
    }

    private String oneDecimal(float number) { int value = Math.round(number * 10); return (value / 10) + "." + Math.abs(value % 10); }

    @Override public void render(float delta) {
        if (typing != null && !typing.isFinished()) { typing.update(delta); updateMetrics(); }
        if (typing != null && typing.isFinished() && !recorded) {
            recorded = true;
            game.progress.recordTyping(selected, typing.getCompleted(), typing.getAccuracy(), typing.getKpm(), typing.getMaxCombo()); boolean saved = game.save();
            best.setText("自己ベスト " + game.progress.getTypingBest(selected) + "問  /  " + oneDecimal(game.progress.getTypingAccuracy(selected)) + "%  /  " + oneDecimal(game.progress.getTypingKpm(selected)) + " KPM");
            input.setDisabled(true); start.setDisabled(false); start.setText("もう一度挑戦  >");
            prompt.setText("TIME UP / おつかれさまでした！"); feedback.setText(saved ? "今回の成績を保存しました。難易度を選び直して練習できます。" : "成績を保存できませんでした。ブラウザのサイトデータ設定を確認してください。"); feedback.setColor(saved ? TerminalQuestGame.CYAN : TerminalQuestGame.RED);
            timer.setText("0.0 SEC"); stage.setKeyboardFocus(start);
        }
        super.render(delta);
    }
}
