package jp.terminalquest.mission;

import jp.terminalquest.terminal.VirtualFileSystem;

/** Generic predicates: adding mission data never requires changing a screen. */
public final class MissionCondition {
    public enum Type { CURRENT_DIRECTORY, FILE_EXISTS, ABSENT, DIRECTORY_EXISTS, FILE_EQUALS,
        FILE_MATCHES_FILE, PERMISSIONS, PROCESS_STOPPED, DISK_BELOW, SERVICE_ACTIVE, OBSERVED }

    public final Type type;
    public final String target;
    public final String value;

    public MissionCondition(Type type, String target, String value) {
        this.type = type;
        this.target = target;
        this.value = value;
    }

    public boolean test(MissionSession session) {
        VirtualFileSystem fs = session.getEngine().getFileSystem();
        try {
            switch (type) {
                case CURRENT_DIRECTORY: return fs.pwd().equals(target);
                case FILE_EXISTS: return fs.exists(target) && !fs.isDirectory(target);
                case ABSENT: return !fs.exists(target);
                case DIRECTORY_EXISTS: return fs.exists(target) && fs.isDirectory(target);
                case FILE_EQUALS: return fs.exists(target) && !fs.isDirectory(target) && fs.readFile(target).equals(session.expectedContent(value));
                case FILE_MATCHES_FILE: return fs.exists(target) && !fs.isDirectory(target) && fs.readFile(target).equals(fs.readFile(value));
                case PERMISSIONS: return fs.getPermissions(target) == Integer.parseInt(value, 8);
                case PROCESS_STOPPED: return !session.getEngine().getProcesses().isRunning(Integer.parseInt(target));
                case DISK_BELOW: return fs.usedBytes() < Long.parseLong(value);
                case SERVICE_ACTIVE: return session.getEngine().getServices().isActive(target);
                case OBSERVED: return session.hasObserved(target, session.expectedContent(value));
                default: return false;
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
