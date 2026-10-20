package jp.terminalquest.save;

import jp.terminalquest.mission.MissionCatalog;
import jp.terminalquest.mission.DifficultyLevel;
import jp.terminalquest.mission.MissionSession;
import jp.terminalquest.system.ProgressionSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaveManagerTest {
    @Test void unsupportedStorageNeverReadsMutatesOrFlushesAndGameplayRemainsAvailable() {
        class ObservedPreferences extends MemoryPreferences {
            int reads;
            int clears;
            int flushes;
            @Override public boolean contains(String key) { reads++; return super.contains(key); }
            @Override public void clear() { clears++; super.clear(); }
            @Override public void flush() { flushes++; super.flush(); }
        }
        ObservedPreferences store = new ObservedPreferences();
        store.putInteger("version", SaveManager.SAVE_VERSION);
        store.putString("completed", "L1-1");
        store.putString("rank.L1-1", "S");
        SaveManager saves = new SaveManager(store, false);
        assertFalse(saves.isStorageAvailable());
        assertFalse(saves.wasLastSaveSuccessful());
        ProgressionSystem progress = saves.load();
        assertTrue(progress.getCompletedIds().isEmpty());
        assertFalse(saves.wasRecovered(), "Unsupported storage is not corrupt data");
        MissionSession session = new MissionSession(MissionCatalog.byId("L1-1"));
        session.execute("pwd");
        assertTrue(progress.complete(session));
        saves.save(progress);
        assertTrue(progress.isCompleted("L1-1"), "Unavailable persistence must not discard this session's progress");
        assertFalse(saves.wasLastSaveSuccessful());
        saves.reset();
        assertFalse(saves.wasLastSaveSuccessful());
        assertEquals(0, store.reads);
        assertEquals(0, store.clears);
        assertEquals(0, store.flushes);
        assertEquals("L1-1", store.getString("completed"));
    }

    @Test void newStoreHasSafeDefaults() {
        SaveManager saves = new SaveManager(new MemoryPreferences());
        ProgressionSystem loaded = saves.load();
        assertTrue(loaded.getCompletedIds().isEmpty());
        assertEquals(0, loaded.getXp());
        assertTrue(loaded.isLevelUnlocked(1));
        assertFalse(loaded.isLevelUnlocked(2));
        assertFalse(saves.wasRecovered());
    }

    @Test void progressSettingsAndTypingRoundTripIndependently() {
        MemoryPreferences store = new MemoryPreferences();
        SaveManager saves = new SaveManager(store);
        ProgressionSystem progress = new ProgressionSystem();
        String mission = MissionCatalog.all().get(0).id;
        progress.restoreComplete(mission, "A");
        progress.restoreXp(DifficultyLevel.byId(1).rewardXp);
        progress.restoreMastered("pwd", 3);
        progress.restoreMastered("|", 2);
        progress.restoreMastered(">", 2);
        progress.restoreMastered(">>", 1);
        progress.recordTyping(1, 24, 98.5f, 180f, 17);
        progress.setTypingBest(6, 7);
        progress.getSettings().muted = true;
        progress.getSettings().reducedMotion = true;
        progress.getSettings().volume = 0.3f;
        saves.save(progress);
        assertTrue(saves.wasLastSaveSuccessful());
        ProgressionSystem loaded = new SaveManager(store).load();
        assertTrue(loaded.isCompleted(mission));
        assertEquals("A", loaded.bestRank(mission));
        assertEquals(DifficultyLevel.byId(1).rewardXp, loaded.getXp());
        assertTrue(loaded.getMasteredCommands().contains("pwd"));
        assertEquals(3, loaded.getCommandMastery("pwd"));
        assertEquals(2, loaded.getCommandMastery("|"));
        assertEquals(2, loaded.getCommandMastery(">"));
        assertEquals(1, loaded.getCommandMastery(">>"));
        assertEquals(24, loaded.getTypingBest(1));
        assertEquals(98.5f, loaded.getTypingAccuracy(1), 0.001f);
        assertEquals(180f, loaded.getTypingKpm(1), 0.001f);
        assertEquals(17, loaded.getTypingCombo(1));
        assertEquals(7, loaded.getTypingBest(6));
        assertEquals(0, loaded.getTypingBest(2));
        assertTrue(loaded.getSettings().muted);
        assertTrue(loaded.getSettings().reducedMotion);
        assertEquals(0.3f, loaded.getSettings().volume, 0.001f);
    }

    @Test void unknownVersionAndMalformedDataResetWholeProgress() {
        MemoryPreferences store = new MemoryPreferences();
        store.putInteger("version", 999);
        store.putInteger("xp", 200);
        SaveManager saves = new SaveManager(store);
        assertEquals(0, saves.load().getXp());
        assertTrue(saves.wasRecovered());
        store.putInteger("version", 1);
        store.putString("completed", "nonexistent-mission");
        assertTrue(saves.load().getCompletedIds().isEmpty());
        assertTrue(saves.wasRecovered());
        store.putString("completed", "");
        store.putFloat("volume", Float.NaN);
        assertEquals(0, saves.load().getXp());
        assertTrue(saves.wasRecovered());
    }

    @Test void wrongTypedValueAndNegativeScoreCannotLeakIntoProgress() {
        MemoryPreferences store = new MemoryPreferences();
        store.putInteger("version", 1);
        store.putString("xp", "not a number");
        SaveManager saves = new SaveManager(store);
        assertEquals(0, saves.load().getXp());
        assertTrue(saves.wasRecovered());
        store.putInteger("xp", 10);
        store.putInteger("typing.3", -1);
        assertEquals(0, saves.load().getXp());
        assertTrue(saves.wasRecovered());
    }

    @Test void resetClearsProgressAndUnavailableStorageDoesNotCrash() {
        MemoryPreferences store = new MemoryPreferences();
        SaveManager saves = new SaveManager(store);
        ProgressionSystem progress = new ProgressionSystem();
        progress.restoreComplete("L1-1", "S");
        progress.restoreXp(DifficultyLevel.byId(1).rewardXp);
        progress.restoreMastered("pwd", 4);
        progress.recordTyping(1, 10, 100f, 100f, 10);
        progress.getSettings().muted = true;
        saves.save(progress);
        assertTrue(store.contains("version"));
        saves.reset();
        assertEquals(SaveManager.SAVE_VERSION, store.getInteger("version"));
        assertTrue(saves.wasLastSaveSuccessful());
        // Simulate stale keys retained by a browser Preferences implementation.
        store.putString("rank.L1-1", "S");
        store.putInteger("mastery.pwd", 4);
        ProgressionSystem reset = new SaveManager(store).load();
        assertTrue(reset.getCompletedIds().isEmpty());
        assertEquals(0, reset.getXp());
        assertTrue(reset.getMasteredCommands().isEmpty());
        assertEquals(0, reset.getTypingBest(1));
        assertFalse(reset.getSettings().muted);
        store.failFlush = true;
        assertDoesNotThrow(() -> saves.save(new ProgressionSystem()));
        assertFalse(saves.wasLastSaveSuccessful());
        assertDoesNotThrow(saves::reset);
        assertFalse(saves.wasLastSaveSuccessful());
    }

    @Test void impossibleUnlocksAndXpMismatchAreRejectedAsOneSnapshot() {
        MemoryPreferences store = new MemoryPreferences();
        store.putInteger("version", 1);
        store.putString("completed", "L1-5");
        store.putString("rank.L1-5", "S");
        store.putInteger("xp", 200);
        SaveManager saves = new SaveManager(store);
        assertTrue(saves.load().getCompletedIds().isEmpty());
        assertTrue(saves.wasRecovered());
        store.putString("completed", "L1-1");
        store.putString("rank.L1-1", "A");
        store.putInteger("xp", 0);
        assertTrue(saves.load().getCompletedIds().isEmpty());
        assertTrue(saves.wasRecovered());
        assertEquals("L1-1", store.getString("completed")); // Load does not destroy recoverable data.
    }

    @Test void invalidMasteryAndMalformedListsSafelyRecover() {
        MemoryPreferences store = new MemoryPreferences();
        store.putInteger("version", 1);
        store.putString("mastered", "pwd");
        store.putInteger("mastery.pwd", -5);
        SaveManager saves = new SaveManager(store);
        assertTrue(saves.load().getMasteredCommands().isEmpty());
        assertTrue(saves.wasRecovered());
        store.putString("mastered", "pwd,,ls");
        assertTrue(saves.load().getMasteredCommands().isEmpty());
        assertTrue(saves.wasRecovered());
    }
}
