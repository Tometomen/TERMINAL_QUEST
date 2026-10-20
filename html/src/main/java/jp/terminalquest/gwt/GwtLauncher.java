package jp.terminalquest.gwt;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.badlogic.gdx.backends.gwt.GwtFiles;
import com.badlogic.gdx.backends.gwt.GwtInput;
import com.google.gwt.dom.client.CanvasElement;
import jp.terminalquest.game.TerminalQuestGame;

public class GwtLauncher extends GwtApplication {
    @Override public GwtApplicationConfiguration getConfig() {
        GwtApplicationConfiguration config = new GwtApplicationConfiguration(true);
        config.padHorizontal = 0;
        config.padVertical = 0;
        return config;
    }
    @Override public ApplicationListener createApplicationListener() {
        return new TerminalQuestGame(GwtFiles.LocalStorage != null);
    }
    @Override protected GwtInput createInput(CanvasElement canvas, GwtApplicationConfiguration config) {
        return new ModernGwtInput(canvas, config);
    }
}
