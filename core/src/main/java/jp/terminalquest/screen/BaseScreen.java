package jp.terminalquest.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import jp.terminalquest.game.TerminalQuestGame;
import jp.terminalquest.save.Settings;
import jp.terminalquest.ui.GameDialog;

/** Fixed logical desktop canvas with responsive scaling and generous keyboard targets. */
public abstract class BaseScreen extends ScreenAdapter {
    protected final TerminalQuestGame game;
    protected final Stage stage;
    protected final Table root;
    private final ShapeRenderer grid = new ShapeRenderer();

    protected BaseScreen(TerminalQuestGame game) {
        this.game = game;
        stage = new Stage(new FitViewport(1280, 800)) {
            private final Vector2 wheelPosition = new Vector2();
            @Override public boolean scrolled(float amountX, float amountY) {
                screenToStageCoordinates(wheelPosition.set(Gdx.input.getX(), Gdx.input.getY()));
                Actor hovered = hit(wheelPosition.x, wheelPosition.y, true);
                while (hovered != null && !(hovered instanceof ScrollPane)) hovered = hovered.getParent();
                // Resolve the pointer when the wheel arrives, without changing keyboard focus.
                setScrollFocus(hovered);
                return super.scrolled(amountX, amountY);
            }
        };
        root = new Table(); root.setFillParent(true); root.pad(30, 42, 24, 42);
        stage.addActor(root);
    }

    protected Label label(String text) { return new Label(text, game.skin); }
    protected Label muted(String text) { return new Label(text, game.skin, "muted"); }
    protected Label accent(String text) { return new Label(text, game.skin, "accent"); }
    protected Label wrap(String text) { Label l = label(text); l.setWrap(true); return l; }
    protected Label heading(String text, float scale) { Label l = label(text); l.setFontScale(scale); return l; }
    protected TextButton button(String text, final Runnable action) { return button(text, false, action); }
    protected TextButton button(String text, boolean primary, final Runnable action) {
        TextButton button = new TextButton(text, game.skin, primary ? "primary" : "default");
        button.pad(9, 16, 9, 16);
        button.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { action.run(); }
        });
        return button;
    }

    protected void header(String section) {
        Table bar = new Table();
        Label brand = accent("TQ / TERMINAL QUEST"); brand.setFontScale(.9f);
        bar.add(brand).left();
        Label sectionLabel = muted(section); sectionLabel.setFontScale(.8f);
        bar.add(sectionLabel).expandX().right().padRight(24);
        bar.add(button("設定", new Runnable() { @Override public void run() { openSettings(); }})).height(38);
        root.add(bar).growX().padBottom(25).row();
    }

    protected void footer(String tip) {
        Table foot = new Table();
        Label l = muted(tip); l.setFontScale(.72f); l.setEllipsis(true);
        foot.add(l).minWidth(0).growX().left();
        Label sandbox = accent("SIMULATION / 仮想環境"); sandbox.setFontScale(.72f);
        foot.add(sandbox).right();
        root.add(foot).growX().padTop(17).height(24).row();
    }

    protected void openSettings() {
        final Settings settings = game.progress.getSettings();
        final Dialog dialog = new GameDialog("設定 / SETTINGS", game);
        dialog.getContentTable().pad(28).defaults().left().padBottom(18);
        final CheckBox mute = new CheckBox("  ミュート", game.skin); mute.setChecked(settings.muted);
        final CheckBox reduce = new CheckBox("  演出を軽減", game.skin); reduce.setChecked(settings.reducedMotion);
        final Slider volume = new Slider(0, 1, .05f, false, game.skin); volume.setValue(settings.volume);
        dialog.getContentTable().add(mute).colspan(2).row();
        dialog.getContentTable().add(label("音量")).width(110); dialog.getContentTable().add(volume).width(280).row();
        dialog.getContentTable().add(reduce).colspan(2).row();
        Label note = muted("進捗はこのブラウザ・端末に保存されます。"); note.setFontScale(.8f);
        dialog.getContentTable().add(note).colspan(2).row();
        dialog.getContentTable().add(button("セーブをリセット…", new Runnable() {
            @Override public void run() {
                Dialog confirm = new GameDialog("セーブのリセット", game) {
                    @Override protected void result(Object object) {
                        if (Boolean.TRUE.equals(object)) {
                            game.saves.reset();
                            if (game.saves.wasLastSaveSuccessful()) {
                                game.progress = game.saves.load(); game.setScreen(new TitleScreen(game));
                            } else {
                                GameDialog warning = new GameDialog("保存エラー", game);
                                warning.getContentTable().pad(20).add(wrap("保存領域へのアクセスが拒否されました。\nブラウザのサイトデータ設定を確認してください。" )).width(510);
                                warning.button("閉じる"); warning.show(stage);
                            }
                        }
                    }
                };
                confirm.getContentTable().pad(25).add(wrap("ミッション・XP・タイピング記録を削除します。\nこの操作は取り消せません。" )).width(470);
                confirm.button("キャンセル", false); confirm.button("削除する", true); confirm.show(stage);
            }
        })).colspan(2).row();
        dialog.getButtonTable().add(button("保存して閉じる", true, new Runnable() {
            @Override public void run() {
                settings.muted = mute.isChecked(); settings.volume = volume.getValue(); settings.reducedMotion = reduce.isChecked();
                boolean saved = game.save(); dialog.hide();
                if (!saved) {
                    GameDialog warning = new GameDialog("保存エラー", game);
                    warning.getContentTable().pad(20).add(wrap("設定は今回のプレイに適用しましたが、保存できませんでした。\nブラウザのサイトデータ設定を確認してください。" )).width(540);
                    warning.button("閉じる"); warning.show(stage);
                }
            }
        })).pad(10);
        dialog.show(stage);
    }

    @Override public void show() { Gdx.input.setInputProcessor(stage); }
    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void render(float delta) {
        Gdx.gl.glClearColor(TerminalQuestGame.BACKGROUND.r, TerminalQuestGame.BACKGROUND.g, TerminalQuestGame.BACKGROUND.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.getViewport().apply();
        grid.setProjectionMatrix(stage.getCamera().combined);
        grid.begin(ShapeRenderer.ShapeType.Line);
        grid.setColor(.09f, .16f, .22f, 1);
        for (int x = 0; x <= 1280; x += 64) grid.line(x, 0, x, 800);
        for (int y = 0; y <= 800; y += 64) grid.line(0, y, 1280, y);
        grid.end();
        stage.act(Math.min(delta, .1f)); stage.draw();
    }
    @Override public void dispose() { stage.dispose(); grid.dispose(); }
}
