package jp.terminalquest.terminal;

import java.util.LinkedHashMap;
import java.util.Map;

/** Only simulated PIDs are accepted; there is no host process handle. */
public final class VirtualProcessManager {
    private final Map<Integer, Boolean> running = new LinkedHashMap<Integer, Boolean>();
    public VirtualProcessManager() { running.put(1, true); running.put(42, true); }
    public boolean isRunning(int pid) { return Boolean.TRUE.equals(running.get(pid)); }
    public void kill(int pid) {
        if (pid == 1) throw new IllegalArgumentException("PID 1 は仮想システム保護対象です");
        if (!isRunning(pid)) throw new IllegalArgumentException("PID " + pid + " は実行されていません");
        running.put(pid, false);
    }
    public String describe() {
        StringBuilder text = new StringBuilder("PID   STATE    COMMAND\n1     running  init\n");
        if (isRunning(42)) text.append("42    running  runaway-log (CPU 99%)\n");
        return text.toString();
    }
}
