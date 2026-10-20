package jp.terminalquest.terminal;

/** Service health is derived from the virtual machine's state, never a host daemon. */
public final class VirtualServiceManager {
    private final VirtualFileSystem files;
    private final VirtualProcessManager processes;
    private boolean active;
    private String lastEvent = "2026-01-15T03:14:00 ERROR web unavailable: permission denied; disk full; runaway-log PID 42";
    public VirtualServiceManager(VirtualFileSystem files, VirtualProcessManager processes) { this.files = files; this.processes = processes; }
    public boolean isActive(String service) { require(service); return active && failure().length() == 0; }
    public boolean restart(String service) {
        require(service); String reason = failure(); active = reason.length() == 0;
        lastEvent = "2026-01-15T03:20:00 " + (active ? "INFO web active (running); health check HTTP 200" : "ERROR restart failed: " + reason);
        return active;
    }
    public void stop(String service) { require(service); active = false; lastEvent = "INFO web stopped by operator"; }
    public String describe(String service) { require(service); return "web.service - Virtual Web Server\nActive: " + (isActive(service) ? "active (running)\nHealth: HTTP 200\n" : "failed (unavailable)\n" + failure() + "\n"); }
    public String journal(String service) {
        require(service);
        return "2026-01-15T03:14:00 ERROR web startup failed\n" +
            "2026-01-15T03:14:01 ERROR /etc/web/config: permission denied (need readable configuration)\n" +
            "2026-01-15T03:14:02 ERROR disk full: /var/log/archive.log; runaway-log PID 42\n" + lastEvent + "\n";
    }
    private String failure() {
        StringBuilder s = new StringBuilder();
        boolean readable = false;
        try { readable = files.exists("/etc/web/config") && (files.getPermissions("/etc/web/config") & 0444) != 0; }
        catch (IllegalArgumentException denied) { /* An inaccessible parent also prevents service startup. */ }
        if (!readable) s.append("/etc/web/config: permission denied; ");
        if (files.usedBytes() >= files.capacityBytes() / 10 * 9) s.append("disk full: /var/log/archive.log (truncate virtual log); ");
        if (processes.isRunning(42)) s.append("runaway-log PID 42 is still running; ");
        return s.toString();
    }
    private static void require(String service) {
        if (!"web".equals(service) && !"web.service".equals(service)) throw new IllegalArgumentException("仮想サービスは web のみです");
    }
}
