package jp.terminalquest.ui;

import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import jp.terminalquest.game.TerminalQuestGame;

/** Presentation settings also apply to modal transitions, including keyboard focus. */
public class GameDialog extends Dialog {
    protected final TerminalQuestGame game;
    public GameDialog(String title, TerminalQuestGame game) {
        super(title, game.skin); this.game = game; setMovable(false);
    }
    @Override public Dialog show(Stage stage) {
        super.show(stage, game.progress.getSettings().reducedMotion ? null : Actions.fadeIn(.12f));
        setPosition(Math.round((stage.getWidth() - getWidth()) / 2f), Math.round((stage.getHeight() - getHeight()) / 2f));
        return this;
    }
    @Override public void hide() {
        super.hide(game.progress.getSettings().reducedMotion ? null : Actions.fadeOut(.12f));
    }
}
