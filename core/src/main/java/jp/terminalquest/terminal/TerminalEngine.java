package jp.terminalquest.terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A small educational shell. Its only capabilities are the three virtual managers;
 * it deliberately has no filesystem, process, service, or network access to the OS.
 */
public final class TerminalEngine {
    private static final List<String> COMMANDS = Collections.unmodifiableList(Arrays.asList(
        "pwd", "ls", "cd", "cat", "touch", "mkdir", "cp", "mv", "grep", "find", "head", "tail", "wc",
        "sort", "uniq", "chmod", "ps", "kill", "df", "du", "systemctl", "journalctl", "echo", "help", "clear"));
    private static final int MAX_OUTPUT = 1024 * 1024;
    private final VirtualFileSystem files = new VirtualFileSystem();
    private final VirtualProcessManager processes = new VirtualProcessManager();
    private final VirtualServiceManager services = new VirtualServiceManager(files, processes);
    private final CommandParser parser = new CommandParser();

    public TerminalEngine() {
        files.mkdir("/home/cadet/logs", true); files.mkdir("/home/cadet/data", true); files.mkdir("/home/cadet/archive", true);
        files.mkdir("/srv/data/backups", true); files.mkdir("/etc/web", true); files.mkdir("/var/log", true); files.mkdir("/dev", true);
        files.writeFile("/dev/null", "", false);
        files.writeFile("/home/cadet/readme.txt", "Welcome to TERMINAL QUEST\n", false);
        files.writeFile("/home/cadet/data/hosts.txt", "web\napi\nweb\ndb\napi\n", false);
        files.writeFile("/home/cadet/logs/app.log",
            "2026-01-15T03:00:00 INFO boot\n2026-01-15T03:01:00 INFO network ready\n" +
            "2026-01-15T03:02:00 INFO worker ready\n2026-01-15T03:03:00 INFO request accepted\n" +
            "2026-01-15T03:04:00 INFO request accepted\n2026-01-15T03:05:00 WARN disk usage rising\n" +
            "2026-01-15T03:06:00 INFO request accepted\n2026-01-15T03:07:00 WARN archive growing\n" +
            "2026-01-15T03:08:00 INFO request accepted\n2026-01-15T03:09:00 WARN runaway-log PID 42\n" +
            "2026-01-15T03:14:00 ERROR disk full\n2026-01-15T03:15:00 ERROR permission denied\n" +
            "2026-01-15T03:16:00 ERROR web unavailable\n2026-01-15T03:17:00 INFO incident opened\n", false);
        files.writeFile("/etc/web/config", "port=8080\nroot=/srv/web\n", false); files.setPermissions("/etc/web/config", 0000);
        files.writeFile("/var/log/archive.log", "Archived simulated logs: safe to truncate after stopping runaway-log.\n", false);
        files.setVirtualSize("/var/log/archive.log", 950000000L);
        files.cd("/home/cadet");
    }

    public VirtualFileSystem getFileSystem() { return files; }
    public VirtualProcessManager getProcesses() { return processes; }
    public VirtualServiceManager getServices() { return services; }
    public static List<String> supportedCommands() { return COMMANDS; }

    public CommandResult execute(String input) {
        List<String> commands = new ArrayList<String>();
        try {
            CommandParser.Pipeline pipeline = parser.parse(input);
            // Validate every command name before allowing any mutation in a pipeline.
            for (List<String> stage : pipeline.stages) {
                String name = stage.get(0);
                if (!COMMANDS.contains(name)) throw error(name + ": 未対応のコマンドです。help で対応範囲を確認してください");
                commands.add(name);
            }
            String stdout = ""; int exitCode = 0;
            for (List<String> stage : pipeline.stages) {
                Output result = run(stage, stdout);
                if (result.text.length() > MAX_OUTPUT) throw error("出力上限(1Mi文字)を超えました。head や grep で絞り込んでください");
                stdout = result.text;
                // Like a shell without pipefail, an upstream status is not stdin:
                // even an empty grep result flows to wc, and the last stage wins.
                exitCode = result.code;
            }
            if (pipeline.redirectPath != null) { files.writeFile(pipeline.redirectPath, stdout, pipeline.append); stdout = ""; }
            return new CommandResult(stdout, exitCode, commands);
        } catch (IllegalArgumentException ex) {
            return new CommandResult("エラー: " + ex.getMessage() + "\n", 2, commands);
        }
    }

    private Output run(List<String> argv, String stdin) {
        String name = argv.get(0); List<String> args = argv.subList(1, argv.size());
        if (name.equals("pwd")) { arity(args, 0, 0, "pwd"); return out(files.pwd() + "\n"); }
        if (name.equals("cd")) { arity(args, 0, 1, "cd [パス]"); files.cd(args.isEmpty() ? "/home/cadet" : args.get(0)); return out(""); }
        if (name.equals("ls")) return out(ls(args));
        if (name.equals("cat")) return out(readSources(args, stdin));
        if (name.equals("touch")) { arity(args, 1, 64, "touch ファイル..."); rejectOptions(args); for (String p : args) files.touch(p); return out(""); }
        if (name.equals("mkdir")) {
            boolean parents = !args.isEmpty() && args.get(0).equals("-p"); List<String> paths = parents ? args.subList(1, args.size()) : args;
            arity(paths, 1, 64, "mkdir [-p] ディレクトリ..."); rejectOptions(paths); for (String p : paths) files.mkdir(p, parents); return out("");
        }
        if (name.equals("cp")) {
            boolean recursive = !args.isEmpty() && (args.get(0).equals("-r") || args.get(0).equals("-R"));
            List<String> operands = recursive ? args.subList(1, args.size()) : args;
            arity(operands, 2, 2, "cp [-r] コピー元 コピー先"); rejectOptions(operands);
            files.copy(operands.get(0), operands.get(1), recursive); return out("");
        }
        if (name.equals("mv")) { arity(args, 2, 2, "mv 移動元 移動先"); rejectOptions(args); files.move(args.get(0), args.get(1)); return out(""); }
        if (name.equals("grep")) return grep(args, stdin);
        if (name.equals("find")) return out(find(args));
        if (name.equals("head") || name.equals("tail")) return out(slice(args, stdin, name.equals("tail")));
        if (name.equals("wc")) return out(wc(args, stdin));
        if (name.equals("sort")) return out(sort(args, stdin));
        if (name.equals("uniq")) return out(uniq(args, stdin));
        if (name.equals("chmod")) {
            arity(args, 2, 2, "chmod 644 パス"); String mode = args.get(0);
            if (mode.length() != 3 || !octal(mode)) throw error("chmod: 権限は644のような3桁の8進数(000〜777)で指定してください");
            files.setPermissions(args.get(1), Integer.parseInt(mode, 8)); return out("");
        }
        if (name.equals("ps")) { if (!args.isEmpty() && !(args.size() == 1 && (args.get(0).equals("aux") || args.get(0).equals("-ef") || args.get(0).equals("-aux")))) throw error("使い方: ps [aux|-ef]"); return out(processes.describe()); }
        if (name.equals("kill")) {
            List<String> pids = args;
            if (!args.isEmpty() && (args.get(0).equals("-9") || args.get(0).equals("-15"))) pids = args.subList(1, args.size());
            arity(pids, 1, 1, "kill [-9|-15] PID"); processes.kill(number(pids.get(0), "PID")); return out("");
        }
        if (name.equals("df")) return out(df(args));
        if (name.equals("du")) return out(du(args));
        if (name.equals("systemctl")) return systemctl(args);
        if (name.equals("journalctl")) {
            if (args.isEmpty()) return out(services.journal("web"));
            if (args.size() == 2 && args.get(0).equals("-u")) return out(services.journal(args.get(1)));
            throw error("使い方: journalctl [-u web]");
        }
        if (name.equals("echo")) {
            boolean noNewline = !args.isEmpty() && args.get(0).equals("-n");
            List<String> words = noNewline ? args.subList(1, args.size()) : args;
            if (!words.isEmpty() && words.get(0).equals("-e")) throw error("echo -e は未対応です。引用符で文字列を指定してください");
            return out(join(words, " ") + (noNewline ? "" : "\n"));
        }
        if (name.equals("clear")) { arity(args, 0, 0, "clear"); return out(""); }
        arity(args, 0, 0, "help"); return out(help());
    }

    private String ls(List<String> args) {
        boolean detailed = false, all = false; String path = "."; boolean operand = false;
        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.length() < 2) throw error("使い方: ls [-l|-a|-la] [パス]");
                for (int i = 1; i < arg.length(); i++) { char c = arg.charAt(i); if (c == 'l') detailed = true; else if (c == 'a') all = true; else throw error("ls: 対応オプションは -l と -a です"); }
            } else { if (operand) throw error("ls: パスは1つ指定してください"); path = arg; operand = true; }
        }
        List<String> entries = files.list(path); String target = files.normalize(path); StringBuilder text = new StringBuilder();
        if (all && files.isDirectory(path)) { List<String> extra = new ArrayList<String>(); extra.add("."); extra.add(".."); extra.addAll(entries); entries = extra; }
        for (String entry : entries) {
            if (!all && entry.startsWith(".")) continue;
            String full = files.isDirectory(path) ? target + "/" + entry : target;
            if (detailed) text.append(permissions(files.getPermissions(full), files.isDirectory(full))).append(" ").append(octalText(files.getPermissions(full))).append(" ").append(files.size(full)).append(" ");
            text.append(entry); if (files.isDirectory(full)) text.append('/'); text.append('\n');
        }
        return text.toString();
    }

    private Output grep(List<String> args, String stdin) {
        boolean insensitive = false, lineNumbers = false; int i = 0;
        for (; i < args.size() && args.get(i).startsWith("-") && !args.get(i).equals("-"); i++) {
            String opt = args.get(i); if (opt.equals("--")) { i++; break; }
            for (int c = 1; c < opt.length(); c++) { if (opt.charAt(c) == 'i') insensitive = true; else if (opt.charAt(c) == 'n') lineNumbers = true; else throw error("grep: 対応オプションは -n と -i です。検索語は文字列として照合します"); }
        }
        if (i >= args.size()) throw error("使い方: grep [-n|-i] 検索文字列 [ファイル...] (文字列検索)");
        String pattern = args.get(i++); if (insensitive) pattern = pattern.toLowerCase();
        List<String> sources = args.subList(i, args.size()); StringBuilder text = new StringBuilder(); boolean match = false;
        if (sources.isEmpty()) sources = Collections.singletonList("-");
        for (String source : sources) {
            String data = source.equals("-") ? stdin : files.readFile(source); List<String> lines = lines(data); int number = 0;
            for (String line : lines) { number++; String candidate = insensitive ? line.toLowerCase() : line;
                if (candidate.contains(pattern)) { match = true; if (sources.size() > 1) text.append(source).append(':'); if (lineNumbers) text.append(number).append(':'); text.append(line).append('\n'); }
            }
        }
        return new Output(text.toString(), match ? 0 : 1);
    }

    private String find(List<String> args) {
        String path = ".", pattern = null, type = null; int i = 0;
        if (!args.isEmpty() && !args.get(0).startsWith("-")) path = args.get(i++);
        while (i < args.size()) {
            String option = args.get(i++);
            if (i == args.size()) throw error("find: " + option + " の後に値が必要です");
            String value = args.get(i++);
            if (option.equals("-name") && pattern == null) pattern = value;
            else if (option.equals("-type") && type == null && (value.equals("f") || value.equals("d"))) type = value;
            else throw error("使い方: find [パス] [-name '名前または*.log'] [-type f|d]");
        }
        StringBuilder text = new StringBuilder();
        for (String p : files.accessiblePathsUnder(path)) {
            if (pattern != null && !glob(pattern, VirtualFileSystem.base(p))) continue;
            if (type != null && files.isDirectory(p) != type.equals("d")) continue;
            text.append(p).append('\n');
        }
        return text.toString();
    }

    private String slice(List<String> args, String stdin, boolean tail) {
        int count = 10; int offset = 0;
        if (!args.isEmpty() && args.get(0).equals("-n")) { if (args.size() < 2) throw error("使い方: " + (tail ? "tail" : "head") + " [-n 行数] [ファイル...]"); count = number(args.get(1), "行数"); offset = 2; }
        List<String> paths = args.subList(offset, args.size()); rejectOptionsExceptDash(paths);
        List<String> sources = paths.isEmpty() ? Collections.singletonList("-") : paths; StringBuilder text = new StringBuilder();
        for (String source : sources) {
            List<String> lines = lines(source.equals("-") ? stdin : files.readFile(source));
            if (sources.size() > 1) text.append("==> ").append(source).append(" <==\n");
            int from = tail ? Math.max(0, lines.size() - count) : 0; int to = tail ? lines.size() : Math.min(lines.size(), count);
            for (int i = from; i < to; i++) text.append(lines.get(i)).append('\n');
        }
        return text.toString();
    }

    private String wc(List<String> args, String stdin) {
        boolean lines = false, words = false, chars = false; List<String> paths = new ArrayList<String>();
        for (String a : args) {
            if (a.startsWith("-") && !a.equals("-")) { for (int i = 1; i < a.length(); i++) { if (a.charAt(i) == 'l') lines = true; else if (a.charAt(i) == 'w') words = true; else if (a.charAt(i) == 'c') chars = true; else throw error("wc: 対応オプションは -l、-w、-c です"); } }
            else paths.add(a);
        }
        if (!lines && !words && !chars) { lines = true; words = true; chars = true; }
        List<String> sources = paths.isEmpty() ? Collections.singletonList("-") : paths; StringBuilder text = new StringBuilder();
        long totalLines = 0, totalWords = 0, totalChars = 0;
        for (String source : sources) {
            String value = source.equals("-") ? stdin : files.readFile(source); long lc = 0, wc = 0; boolean inWord = false;
            for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c == '\n') lc++; if (Character.isWhitespace(c)) inWord = false; else if (!inWord) { wc++; inWord = true; } }
            long cc = VirtualFileSystem.utf8Length(value); totalLines += lc; totalWords += wc; totalChars += cc;
            text.append(countText(lines, words, chars, lc, wc, cc)); if (!paths.isEmpty() && !source.equals("-")) text.append(" ").append(source); text.append('\n');
        }
        if (sources.size() > 1) text.append(countText(lines, words, chars, totalLines, totalWords, totalChars)).append(" total\n");
        return text.toString();
    }
    private static String countText(boolean lines, boolean words, boolean chars, long lc, long wc, long cc) {
        List<String> counts = new ArrayList<String>(); if (lines) counts.add(String.valueOf(lc)); if (words) counts.add(String.valueOf(wc)); if (chars) counts.add(String.valueOf(cc)); return join(counts, " ");
    }

    private String sort(List<String> args, String stdin) {
        boolean reverse = false, unique = false; List<String> paths = new ArrayList<String>();
        for (String arg : args) {
            if (arg.equals("-r")) reverse = true; else if (arg.equals("-u")) unique = true;
            else { if (arg.startsWith("-") && !arg.equals("-")) throw error("sort: 対応オプションは -r と -u です"); paths.add(arg); }
        }
        List<String> sorted = lines(readSources(paths, stdin)); Collections.sort(sorted); if (reverse) Collections.reverse(sorted);
        StringBuilder text = new StringBuilder(); String previous = null;
        for (String line : sorted) { if (!unique || !line.equals(previous)) text.append(line).append('\n'); previous = line; }
        return text.toString();
    }

    private String uniq(List<String> args, String stdin) {
        boolean count = !args.isEmpty() && args.get(0).equals("-c"); List<String> paths = count ? args.subList(1, args.size()) : args;
        arity(paths, 0, 1, "uniq [-c] [ファイル]"); rejectOptionsExceptDash(paths); List<String> lines = lines(readSources(paths, stdin));
        StringBuilder text = new StringBuilder(); int i = 0;
        while (i < lines.size()) { String line = lines.get(i); int end = i + 1; while (end < lines.size() && line.equals(lines.get(end))) end++;
            if (count) text.append(end - i).append(' '); text.append(line).append('\n'); i = end;
        }
        return text.toString();
    }

    private String df(List<String> args) {
        boolean human = false; String path = null;
        for (String arg : args) { if (arg.equals("-h")) human = true; else if (arg.startsWith("-")) throw error("使い方: df [-h] [パス]"); else if (path == null) path = arg; else throw error("df: パスは1つまでです"); }
        if (path != null && !files.exists(path)) throw error(path + ": パスが見つかりません");
        long used = files.usedBytes(), size = files.capacityBytes(), available = Math.max(0, size - used);
        long percent = (long) Math.ceil(((double) used / size) * 100);
        return "Filesystem Size Used Available Use% Mounted\nvirtual " + display(size, human) + " " + display(used, human) + " " + display(available, human) + " " + percent + "% /\n";
    }
    private String du(List<String> args) {
        boolean human = false, all = false, summary = false; List<String> paths = new ArrayList<String>();
        for (String arg : args) {
            if (arg.startsWith("-")) { for (int i = 1; i < arg.length(); i++) { if (arg.charAt(i) == 'h') human = true; else if (arg.charAt(i) == 'a') all = true; else if (arg.charAt(i) == 's') summary = true; else throw error("使い方: du [-a|-s|-h|-sh] [パス...]"); } }
            else paths.add(arg);
        }
        if (paths.isEmpty()) paths.add("."); StringBuilder text = new StringBuilder();
        if (all && summary) throw error("du: -a と -s は同時に指定できません");
        for (String path : paths) {
            if (files.isDirectory(path) && !summary) {
                List<String> descendants = files.accessiblePathsUnder(path); Collections.reverse(descendants);
                for (String child : descendants) if (all || files.isDirectory(child)) text.append(display(files.size(child), human)).append('\t').append(child).append('\n');
            } else text.append(display(files.size(path), human)).append('\t').append(path).append('\n');
        }
        return text.toString();
    }
    private Output systemctl(List<String> args) {
        arity(args, 2, 2, "systemctl status|start|restart|stop web"); String operation = args.get(0), service = args.get(1);
        if (operation.equals("status")) return new Output(services.describe(service), services.isActive(service) ? 0 : 3);
        if (operation.equals("start") || operation.equals("restart")) { boolean ok = services.restart(service); return new Output(services.describe(service), ok ? 0 : 1); }
        if (operation.equals("stop")) { services.stop(service); return out(""); }
        throw error("systemctl: 対応操作は status、start、restart、stop です");
    }

    private String readSources(List<String> paths, String stdin) {
        if (paths.isEmpty()) return stdin;
        rejectOptionsExceptDash(paths); StringBuilder data = new StringBuilder();
        for (String path : paths) { data.append(path.equals("-") ? stdin : files.readFile(path)); if (data.length() > MAX_OUTPUT) throw error("入力が大きすぎます。1Mi文字までです"); }
        return data.toString();
    }

    private static String help() {
        return "TERMINAL QUEST 仮想Linux / 対応コマンド\n" +
            "探索: pwd / ls [-la] [パス] / cd [パス]\n" +
            "ファイル: cat [ファイル...] / touch ファイル / mkdir [-p] パス / cp [-r] 元 先 / mv 元 先\n" +
            "調査: grep [-ni] 文字列 [ファイル] / find [パス] [-name '*.log'] [-type f|d]\n" +
            "テキスト: head・tail [-n 行数] [ファイル] / wc [-lwc] [ファイル] / sort [-r|-u] / uniq [-c]\n" +
            "運用: chmod 644 パス / ps [aux] / kill [-9] PID / df [-h] / du [-ash] パス\n" +
            "復旧: systemctl status|restart|start|stop web / journalctl [-u web]\n" +
            "補助: echo [-n] 文字列 / clear / help\n" +
            "連携: コマンド | コマンド > 仮想ファイル / >> で追記\n" +
            "引用符とバックスラッシュに対応。grep は文字列検索、find -name は * と ? に対応。\n" +
            "変数展開、コマンド置換、&&、;、入力リダイレクト、実OS操作・通信は対応していません。\n";
    }

    private static List<String> lines(String text) {
        List<String> result = new ArrayList<String>(); if (text.length() == 0) return result;
        int start = 0; for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') { result.add(text.substring(start, i)); start = i + 1; }
        if (start < text.length()) result.add(text.substring(start)); return result;
    }
    private static boolean glob(String pattern, String word) {
        int p = 0, w = 0, star = -1, mark = 0;
        while (w < word.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == word.charAt(w))) { p++; w++; }
            else if (p < pattern.length() && pattern.charAt(p) == '*') { star = p++; mark = w; }
            else if (star >= 0) { p = star + 1; w = ++mark; } else return false;
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++; return p == pattern.length();
    }
    private static String permissions(int mode, boolean directory) {
        StringBuilder text = new StringBuilder(directory ? "d" : "-"); char[] bits = {'r','w','x'};
        for (int shift = 8; shift >= 0; shift--) text.append((mode & (1 << shift)) != 0 ? bits[(8 - shift) % 3] : '-'); return text.toString();
    }
    private static String octalText(int mode) { return "" + ((mode >> 6) & 7) + ((mode >> 3) & 7) + (mode & 7); }
    private static boolean octal(String mode) { for (int i = 0; i < mode.length(); i++) if (mode.charAt(i) < '0' || mode.charAt(i) > '7') return false; return true; }
    private static String display(long bytes, boolean human) {
        if (!human || bytes < 1024) return String.valueOf(bytes);
        String[] units = {"B", "K", "M", "G", "T", "P", "E"}; double n = bytes; int i = 0;
        while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
        long tenths = Math.round(n * 10); return (tenths / 10) + "." + (tenths % 10) + units[i];
    }
    private static int number(String value, String label) {
        if (value.length() == 0 || value.length() > 9) throw error(label + "は0以上の整数で指定してください");
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') throw error(label + "は0以上の整数で指定してください");
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { throw error(label + "が大きすぎます"); }
    }
    private static void arity(List<String> args, int minimum, int maximum, String usage) { if (args.size() < minimum || args.size() > maximum) throw error("使い方: " + usage); }
    private static void rejectOptions(List<String> args) { for (String a : args) if (a.startsWith("-")) throw error("未対応オプション: " + a + " (ファイル名の場合は ./ を付けてください)"); }
    private static void rejectOptionsExceptDash(List<String> args) { for (String a : args) if (a.startsWith("-") && !a.equals("-")) throw error("未対応オプション: " + a); }
    private static String join(List<String> values, String separator) { StringBuilder text = new StringBuilder(); for (int i = 0; i < values.size(); i++) { if (i > 0) text.append(separator); text.append(values.get(i)); } return text.toString(); }
    private static IllegalArgumentException error(String text) { return new IllegalArgumentException(text); }
    private static Output out(String value) { return new Output(value, 0); }
    private static final class Output { final String text; final int code; Output(String text, int code) { this.text = text; this.code = code; } }
}
