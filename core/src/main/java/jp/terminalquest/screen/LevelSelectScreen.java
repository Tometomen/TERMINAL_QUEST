package jp.terminalquest.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import jp.terminalquest.game.TerminalQuestGame;
import jp.terminalquest.mission.DifficultyLevel;
import jp.terminalquest.mission.Mission;
import jp.terminalquest.mission.MissionCatalog;

public final class LevelSelectScreen extends BaseScreen {
    private int selected;
    private final Table missionList = new Table();
    private final Table levelList = new Table();
    public LevelSelectScreen(final TerminalQuestGame game) { this(game, 1); }
    public LevelSelectScreen(final TerminalQuestGame game, int selectedLevel) {
        super(game); selected = selectedLevel;
        header("TRAINING / 難易度選択");
        Table title = new Table();
        title.add(heading("次の一歩を、選ぼう。", 1.5f)).expandX().left();
        title.add(accent("XP " + game.progress.getXp())).right().padRight(24);
        title.add(button("ホーム", new Runnable() { @Override public void run() { game.setScreen(new TitleScreen(game)); }}));
        root.add(title).growX().padBottom(8).row();
        root.add(muted("通常ミッション4つ → ボス → 次のレベル。クリア済みの課題は何度でも挑戦できます。" )).left().padBottom(22).row();
        Table content = new Table();
        buildLevels();
        ScrollPane levelScroll = new ScrollPane(levelList, game.skin); levelScroll.setFadeScrollBars(false); levelScroll.setScrollingDisabled(true, false);
        content.add(levelScroll).width(490).growY().top().padRight(22);
        missionList.background(game.panel()); missionList.pad(20).top().left();
        ScrollPane missionsScroll = new ScrollPane(missionList, game.skin); missionsScroll.setFadeScrollBars(false); missionsScroll.setScrollingDisabled(true, false);
        content.add(missionsScroll).grow();
        root.add(content).grow().row();
        buildMissions();
        footer("ボスのランクに関係なく次のレベルが解放されます  /  学ぶペースは自由");
    }

    private void buildLevels() {
        levelList.clear(); levelList.top().left();
        for (final DifficultyLevel level : DifficultyLevel.all()) {
            boolean unlocked = game.progress.isLevelUnlocked(level.id);
            int cleared = game.progress.completedCount(level.id);
            Color color = level.id <= 2 ? TerminalQuestGame.CYAN : level.id <= 4 ? TerminalQuestGame.BLUE : TerminalQuestGame.GOLD;
            Table card = new Table(); card.pad(15, 18, 15, 18);
            card.background(game.colored(level.id == selected ? Color.valueOf("1A3543") : TerminalQuestGame.PANEL));
            Label number = heading(String.valueOf(level.id < 10 ? "0" + level.id : level.id), 1.6f); number.setColor(color);
            card.add(number).width(56).left().padRight(10);
            Table details = new Table(); details.left();
            Label cat = muted(level.category + "   /   " + (level.implemented ? unlocked ? cleared == 5 ? "CLEAR" : "解放済み" : "未解放" : "開発中")); cat.setFontScale(.72f);
            details.add(cat).left().row();
            Label title = label(level.title); title.setFontScale(.9f);
            details.add(title).left().padTop(2).row();
            Label progress = muted(cleared + " / 5 CLEAR  ·  " + mastered(level) + " / " + level.commands.length + " COMMANDS"); progress.setFontScale(.65f);
            details.add(progress).left().padTop(5);
            card.add(details).expandX().left();
            TextButton select = button(level.id == selected ? "選択中" : "見る  >", new Runnable() {
                @Override public void run() { selected = level.id; buildLevels(); buildMissions(); }
            }); select.getLabel().setFontScale(.75f);
            card.add(select).width(92).height(42);
            levelList.add(card).growX().minHeight(97).padBottom(9).row();
        }
    }

    private int mastered(DifficultyLevel level) {
        int count = 0;
        for (String command : level.commands) if (game.progress.getMasteredCommands().contains(command)) count++;
        return count;
    }

    private void buildMissions() {
        missionList.clear();
        DifficultyLevel level = DifficultyLevel.byId(selected);
        Label eyebrow = accent("LEVEL " + selected + " / " + level.category); eyebrow.setFontScale(.75f);
        missionList.add(eyebrow).left().padBottom(7).row();
        missionList.add(heading(level.title, 1.1f)).left().padBottom(8).row();
        Label desc = wrap(level.description); desc.setFontScale(.8f); desc.setColor(TerminalQuestGame.MUTED);
        missionList.add(desc).width(615).left().padBottom(14).row();
        if (!level.implemented) {
            missionList.add(wrap("開発中です。公開済みのレベルから学習を始めてください。" )).width(610).left().row(); return;
        }
        if (!game.progress.isLevelUnlocked(selected)) {
            Label locked = label("解放条件：LEVEL " + (selected - 1) + " のボスをクリア"); locked.setColor(TerminalQuestGame.GOLD); locked.setFontScale(.8f);
            missionList.add(locked).left().padBottom(12).row();
        }
        int index = 1;
        for (final Mission mission : MissionCatalog.forLevel(selected)) {
            boolean unlocked = game.progress.isMissionUnlocked(mission);
            boolean completed = game.progress.isCompleted(mission.id);
            Table row = new Table(); row.background(game.colored(TerminalQuestGame.BACKGROUND)); row.pad(10, 13, 10, 13);
            Label badge = accent(mission.boss ? "BOSS" : "0" + index); badge.setFontScale(.8f);
            if (mission.boss) badge.setColor(TerminalQuestGame.GOLD);
            row.add(badge).width(65).left();
            Table text = new Table(); text.left();
            Label name = label(mission.title); name.setFontScale(.8f);
            text.add(name).left().row();
            String state = completed ? "CLEAR / RANK " + game.progress.bestRank(mission.id) : unlocked ? "挑戦できます" : mission.boss ? "通常ミッション4つで解放" : "前レベルのボスで解放";
            Label status = muted(state); status.setFontScale(.65f);
            text.add(status).left().padTop(4);
            row.add(text).expandX().left();
            TextButton play = button(completed ? "再挑戦" : unlocked ? "開始  >" : "LOCK", unlocked, new Runnable() {
                @Override public void run() { game.setScreen(new MissionScreen(game, mission)); }
            }); play.setDisabled(!unlocked); play.getLabel().setFontScale(.7f);
            row.add(play).width(95).height(39);
            missionList.add(row).growX().height(60).padBottom(6).row();
            index++;
        }
        Label help = muted("習得：" + join(level.commands)); help.setFontScale(.7f); help.setWrap(true);
        missionList.add(help).width(610).left().padTop(8).expandY().bottom();
    }

    static String join(String[] words) {
        StringBuilder result = new StringBuilder();
        for (String word : words) { if (result.length() > 0) result.append("  /  "); result.append(word); }
        return result.toString();
    }
}
