package jp.terminalquest.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import jp.terminalquest.game.TerminalQuestGame;

public final class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("TERMINAL QUEST — Linux Training Simulator");
        config.setWindowedMode(1280, 800);
        config.setWindowSizeLimits(960, 640, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(60);
        // CI smoke mode still creates and renders the actual game and GL context.
        final boolean smoke = args.length > 0 && "--smoke".equals(args[0]);
        new Lwjgl3Application(smoke ? new TerminalQuestGame() {
            private int frames;
            @Override public void render() {
                super.render();
                if (++frames == 120) {
                    Gdx.app.log("SMOKE", "TERMINAL QUEST rendered 120 frames");
                    Gdx.app.exit();
                }
            }
        } : new TerminalQuestGame(), config);
    }
}
