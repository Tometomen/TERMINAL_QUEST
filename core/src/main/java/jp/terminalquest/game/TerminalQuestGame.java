package jp.terminalquest.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import jp.terminalquest.save.SaveManager;
import jp.terminalquest.screen.TitleScreen;
import jp.terminalquest.system.ProgressionSystem;

/** Shared resources. All game commands remain in the memory-only terminal engine. */
public class TerminalQuestGame extends Game {
    public static final Color BACKGROUND = Color.valueOf("080F1C");
    public static final Color PANEL = Color.valueOf("101D2D");
    public static final Color PANEL_LIGHT = Color.valueOf("17283B");
    public static final Color TEXT = Color.valueOf("DCE8F0");
    public static final Color MUTED = Color.valueOf("8DA5B8");
    public static final Color CYAN = Color.valueOf("56E8CD");
    public static final Color BLUE = Color.valueOf("7BABFF");
    public static final Color GOLD = Color.valueOf("F5CE7A");
    public static final Color RED = Color.valueOf("FF8C9A");

    public Skin skin;
    public SaveManager saves;
    public ProgressionSystem progress;
    private Sound confirmSound;
    private Sound errorSound;
    private final boolean persistentStorageAvailable;

    public TerminalQuestGame() { this(true); }

    public TerminalQuestGame(boolean persistentStorageAvailable) {
        this.persistentStorageAvailable = persistentStorageAvailable;
    }

    @Override public void create() {
        skin = createSkin();
        saves = new SaveManager(Gdx.app.getPreferences("terminal-quest"), persistentStorageAvailable);
        progress = saves.load();
        if (Gdx.files.internal("audio/confirm.wav").exists()) confirmSound = Gdx.audio.newSound(Gdx.files.internal("audio/confirm.wav"));
        if (Gdx.files.internal("audio/error.wav").exists()) errorSound = Gdx.audio.newSound(Gdx.files.internal("audio/error.wav"));
        setScreen(new TitleScreen(this));
    }

    public boolean save() { saves.save(progress); return saves.wasLastSaveSuccessful(); }

    public void cue(boolean success) {
        if (progress.getSettings().muted) return;
        Sound sound = success ? confirmSound : errorSound;
        if (sound != null) sound.play(progress.getSettings().volume * 0.25f);
    }

    @Override public void setScreen(com.badlogic.gdx.Screen screen) {
        com.badlogic.gdx.Screen old = getScreen();
        super.setScreen(screen);
        if (old != null && old != screen) old.dispose();
    }

    private Skin createSkin() {
        Skin result = new Skin();
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(Color.WHITE); pixel.fill();
        result.add("pixel", new Texture(pixel)); pixel.dispose();
        BitmapFont ui = new BitmapFont(Gdx.files.internal("fonts/ui.fnt"));
        BitmapFont mono = new BitmapFont(Gdx.files.internal("fonts/mono.fnt"));
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : ui.getRegions()) region.getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : mono.getRegions()) region.getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        ui.getData().markupEnabled = false;
        mono.getData().markupEnabled = false;
        result.add("default-font", ui);
        result.add("mono", mono);
        result.add("default", new Label.LabelStyle(ui, TEXT));
        result.add("mono", new Label.LabelStyle(mono, TEXT));
        result.add("muted", new Label.LabelStyle(ui, MUTED));
        result.add("accent", new Label.LabelStyle(ui, CYAN));
        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.font = ui; button.fontColor = TEXT;
        button.up = result.newDrawable("pixel", PANEL_LIGHT);
        button.over = result.newDrawable("pixel", Color.valueOf("234354"));
        button.down = result.newDrawable("pixel", Color.valueOf("27675E"));
        button.disabled = result.newDrawable("pixel", Color.valueOf("12202E"));
        button.disabledFontColor = Color.valueOf("627585");
        button.checked = result.newDrawable("pixel", Color.valueOf("25554D"));
        result.add("default", button);
        TextButton.TextButtonStyle primary = new TextButton.TextButtonStyle(button);
        primary.up = result.newDrawable("pixel", CYAN); primary.fontColor = BACKGROUND;
        primary.over = result.newDrawable("pixel", Color.valueOf("8CFBE6"));
        primary.down = result.newDrawable("pixel", Color.valueOf("29B49C"));
        result.add("primary", primary);
        TextField.TextFieldStyle input = new TextField.TextFieldStyle();
        input.font = mono; input.fontColor = TEXT; input.messageFontColor = MUTED;
        input.background = result.newDrawable("pixel", Color.valueOf("0B1522"));
        input.focusedBackground = result.newDrawable("pixel", Color.valueOf("142B36"));
        input.disabledBackground = input.background;
        input.cursor = result.newDrawable("pixel", CYAN); input.cursor.setMinWidth(2);
        input.selection = result.newDrawable("pixel", Color.valueOf("28645E"));
        result.add("default", input);
        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScroll = result.newDrawable("pixel", Color.valueOf("122535"));
        scroll.vScrollKnob = result.newDrawable("pixel", Color.valueOf("3A6170"));
        scroll.vScroll.setMinWidth(5); scroll.vScrollKnob.setMinWidth(5);
        result.add("default", scroll);
        Window.WindowStyle window = new Window.WindowStyle(ui, CYAN, result.newDrawable("pixel", Color.valueOf("152638")));
        window.stageBackground = result.newDrawable("pixel", new Color(0, 0, 0, .78f));
        result.add("default", window);
        CheckBox.CheckBoxStyle check = new CheckBox.CheckBoxStyle();
        check.font = ui; check.fontColor = TEXT;
        check.checkboxOff = result.newDrawable("pixel", Color.valueOf("355064"));
        check.checkboxOn = result.newDrawable("pixel", CYAN);
        check.checkboxOff.setMinWidth(18); check.checkboxOff.setMinHeight(18);
        check.checkboxOn.setMinWidth(18); check.checkboxOn.setMinHeight(18);
        result.add("default", check);
        Slider.SliderStyle slider = new Slider.SliderStyle();
        slider.background = result.newDrawable("pixel", Color.valueOf("355064"));
        slider.background.setMinHeight(5);
        slider.knob = result.newDrawable("pixel", CYAN);
        slider.knob.setMinWidth(16); slider.knob.setMinHeight(20);
        result.add("default-horizontal", slider);
        return result;
    }

    public Drawable panel() { return skin.newDrawable("pixel", PANEL); }
    public Drawable colored(Color color) { return skin.newDrawable("pixel", color); }

    @Override public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        if (confirmSound != null) confirmSound.dispose();
        if (errorSound != null) errorSound.dispose();
        if (skin != null) skin.dispose();
    }
}
