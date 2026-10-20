package jp.terminalquest.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A deliberately bounded, entirely in-memory filesystem shared by every backend. */
public final class VirtualFileSystem {
    private static final int MAX_ENTRIES = 2048;
    private static final int MAX_TEXT = 1024 * 1024;
    private final Map<String, Node> nodes = new LinkedHashMap<String, Node>();
    private String cwd = "/";
    private final long capacity = 1000000000L;

    private static final class Node {
        boolean directory;
        String content = "";
        int permissions;
        long size;
        Node(boolean directory) { this.directory = directory; permissions = directory ? 0755 : 0644; }
        Node duplicate() {
            Node n = new Node(directory); n.content = content; n.permissions = permissions; n.size = size; return n;
        }
    }

    public VirtualFileSystem() { nodes.put("/", new Node(true)); }
    public String pwd() { return cwd; }
    public long capacityBytes() { return capacity; }

    public String normalize(String path) {
        if (path == null || path.length() == 0) throw error("パスを指定してください");
        if (path.length() > 1024 || path.indexOf('\0') >= 0 || path.indexOf('\n') >= 0) throw error("パスが不正です");
        if (path.equals("~")) path = "/home/cadet";
        else if (path.startsWith("~/")) path = "/home/cadet/" + path.substring(2);
        String full = path.startsWith("/") ? path : cwd + "/" + path;
        List<String> parts = new ArrayList<String>();
        for (String part : full.split("/")) {
            if (part.length() == 0 || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.remove(parts.size() - 1); }
            else parts.add(part);
        }
        StringBuilder result = new StringBuilder();
        for (String part : parts) result.append('/').append(part);
        return result.length() == 0 ? "/" : result.toString();
    }

    public boolean exists(String path) { return nodes.containsKey(normalize(path)); }
    public boolean isDirectory(String path) { Node node = nodes.get(normalize(path)); return node != null && node.directory; }
    public void cd(String path) {
        String target = normalize(path); requireSearch(target); Node node = required(target);
        if (!node.directory) throw error(path + ": ディレクトリではありません");
        if ((node.permissions & 0111) == 0) throw error(path + ": アクセス権限がありません");
        cwd = target;
    }

    public String readFile(String path) {
        String target = normalize(path); requireSearch(target); Node n = required(target);
        if (n.directory) throw error(path + ": ディレクトリです。ls を使ってください");
        if ((n.permissions & 0444) == 0) throw error(path + ": 読み取り権限がありません (chmod 644 パス)");
        return target.equals("/dev/null") ? "" : n.content;
    }

    public void writeFile(String path, String text, boolean append) {
        String target = normalize(path);
        if (text == null) text = "";
        requireParent(target);
        Node n = nodes.get(target);
        if (n != null && n.directory) throw error(path + ": ディレクトリには出力できません");
        if (n != null && (n.permissions & 0222) == 0) throw error(path + ": 書き込み権限がありません");
        if (target.equals("/dev/null") && n != null) return;
        String content = append && n != null ? n.content + text : text;
        if (content.length() > MAX_TEXT) throw error("仮想ファイルの本文は1Mi文字までです");
        if (n == null) { requireWritableParent(target); checkRoom(1); n = new Node(false); nodes.put(target, n); }
        n.size = append ? saturatedAdd(n.size, utf8Length(text)) : utf8Length(text);
        n.content = content;
    }

    public void touch(String path) { requireSearch(normalize(path)); if (!exists(path)) writeFile(path, "", false); }

    public void mkdir(String path, boolean parents) {
        String target = normalize(path);
        requireSearchIfPresent(target);
        if (nodes.containsKey(target)) {
            if (parents && nodes.get(target).directory) return;
            throw error(path + ": すでに存在します");
        }
        String parent = parent(target);
        if (!nodes.containsKey(parent) && parents) mkdir(parent, true);
        requireWritableParent(target); checkRoom(1); nodes.put(target, new Node(true));
    }

    public List<String> list(String path) {
        String target = normalize(path); requireSearch(target); Node n = required(target);
        if (!n.directory) return Collections.singletonList(base(target));
        if ((n.permissions & 0444) == 0) throw error(path + ": 読み取り権限がありません");
        List<String> result = new ArrayList<String>();
        for (String item : nodes.keySet()) if (!item.equals(target) && parent(item).equals(target)) result.add(base(item));
        Collections.sort(result); return result;
    }

    public List<String> pathsUnder(String path) {
        String target = normalize(path); required(target);
        List<String> result = new ArrayList<String>();
        for (String item : nodes.keySet()) if (item.equals(target) || item.startsWith(target.equals("/") ? "/" : target + "/")) result.add(item);
        Collections.sort(result); return result;
    }

    /** A command-facing traversal, unlike the privileged metadata used for disk accounting. */
    public List<String> accessiblePathsUnder(String path) {
        String target = normalize(path); requireSearch(target);
        List<String> result = pathsUnder(target);
        for (String item : result) {
            requireSearch(item); Node node = required(item);
            if (node.directory && ((node.permissions & 0444) == 0 || (node.permissions & 0111) == 0)) throw error(item + ": ディレクトリを調査する権限がありません");
        }
        return result;
    }

    public int getPermissions(String path) { String target = normalize(path); requireSearch(target); return required(target).permissions; }
    public void setPermissions(String path, int mode) {
        if (mode < 0 || mode > 0777) throw error("権限は000〜777の3桁の8進数で指定してください");
        String target = normalize(path); requireSearch(target); required(target).permissions = mode;
    }
    public void setVirtualSize(String path, long bytes) {
        if (bytes < 0) throw error("ファイルサイズは0以上です");
        Node n = required(normalize(path));
        if (n.directory) throw error("ディレクトリのサイズはファイルから計算します");
        n.size = bytes;
    }
    public long size(String path) {
        String target = normalize(path); Node n = required(target);
        if (!n.directory) return n.size;
        long size = 0;
        for (String item : pathsUnder(target)) if (!nodes.get(item).directory) size = saturatedAdd(size, nodes.get(item).size);
        return size;
    }
    public long usedBytes() { return size("/"); }

    public void copy(String source, String destination, boolean recursive) {
        String src = normalize(source); requireSearch(src); Node n = required(src);
        if (src.equals("/") && n.directory) throw error("ルートディレクトリの再帰コピーは対応していません");
        String dst = destinationPath(src, destination);
        if (src.equals(dst)) throw error("コピー元とコピー先が同じです");
        if (n.directory && !recursive) throw error("ディレクトリのコピーは cp -r 元 先 です");
        if (n.directory && dst.startsWith(src + "/")) throw error("ディレクトリを自身の中へコピーできません");
        requireParent(dst);
        List<String> selected = n.directory ? pathsUnder(src) : Collections.singletonList(src);
        List<String> targets = new ArrayList<String>();
        for (String p : selected) {
            String out = dst + p.substring(src.length());
            requireSearch(p);
            Node input = nodes.get(p);
            if (input.directory && ((input.permissions & 0444) == 0 || (input.permissions & 0111) == 0)) throw error(p + ": ディレクトリをコピーする権限がありません");
            if (nodes.containsKey(out)) {
                requireSearch(out);
                Node old = nodes.get(out);
                if (old.directory != nodes.get(p).directory) throw error("コピー先の種類が異なります: " + out);
                if (!old.directory && (old.permissions & 0222) == 0) throw error(out + ": 書き込み権限がありません");
            } else if (nodes.containsKey(parent(out))) requireWritableParent(out);
            targets.add(out);
        }
        int added = 0; for (String p : targets) if (!nodes.containsKey(p)) added++;
        checkRoom(added);
        List<Node> clones = new ArrayList<Node>();
        for (String p : selected) {
            Node entry = nodes.get(p);
            if (!entry.directory && (entry.permissions & 0444) == 0) throw error(p + ": 読み取り権限がありません");
            Node clone = entry.duplicate();
            String target = targets.get(clones.size());
            // Copying into existing entries does not change their permissions.
            if (nodes.containsKey(target)) clone.permissions = nodes.get(target).permissions;
            clones.add(clone);
        }
        for (int i = 0; i < selected.size(); i++) nodes.put(targets.get(i), clones.get(i));
    }

    public void move(String source, String destination) {
        String src = normalize(source); requireSearch(src); Node node = required(src);
        if (src.equals("/")) throw error("ルートは移動できません");
        String dst = destinationPath(src, destination);
        if (src.equals(dst)) return;
        if (dst.startsWith(src + "/")) throw error("自身の中へ移動できません");
        requireWritableParent(src); requireWritableParent(dst);
        if (nodes.containsKey(dst) && nodes.get(dst).directory) throw error("移動先ディレクトリが存在します");
        if (nodes.containsKey(dst) && node.directory) throw error("移動先ファイルが存在します");
        List<String> selected = pathsUnder(src);
        List<Node> moved = new ArrayList<Node>(); for (String p : selected) moved.add(nodes.get(p));
        for (String p : selected) nodes.remove(p);
        for (int i = 0; i < selected.size(); i++) nodes.put(dst + selected.get(i).substring(src.length()), moved.get(i));
        if (cwd.equals(src) || cwd.startsWith(src + "/")) cwd = dst + cwd.substring(src.length());
    }

    private String destinationPath(String source, String destination) {
        String dst = normalize(destination);
        if (isDirectory(dst)) dst = (dst.equals("/") ? "" : dst) + "/" + base(source);
        return dst;
    }
    private void requireParent(String path) {
        requireSearch(path);
        String parent = parent(path); Node n = required(parent);
        if (!n.directory) throw error(parent + ": 親がディレクトリではありません");
    }
    private void requireWritableParent(String path) {
        requireParent(path); Node n = required(parent(path));
        if ((n.permissions & 0222) == 0 || (n.permissions & 0111) == 0) throw error(parent(path) + ": ディレクトリに書き込み・探索権限がありません");
    }
    private void requireSearch(String path) {
        if (path.equals("/")) return;
        String ancestor = parent(path);
        while (true) {
            Node node = required(ancestor);
            if (!node.directory) throw error(ancestor + ": 親がディレクトリではありません");
            if ((node.permissions & 0111) == 0) throw error(ancestor + ": ディレクトリの探索権限がありません");
            if (ancestor.equals("/")) break;
            ancestor = parent(ancestor);
        }
    }
    private void requireSearchIfPresent(String path) {
        // mkdir -p may create missing ancestors; existing ancestors still need search permission.
        String ancestor = parent(path);
        while (!nodes.containsKey(ancestor) && !ancestor.equals("/")) ancestor = parent(ancestor);
        if (!ancestor.equals("/")) requireSearch(ancestor);
        Node n = required(ancestor);
        if (!n.directory || (n.permissions & 0111) == 0) throw error(ancestor + ": ディレクトリの探索権限がありません");
    }
    private Node required(String path) {
        Node node = nodes.get(path); if (node == null) throw error(path + ": パスが見つかりません。ls で確認してください"); return node;
    }
    private void checkRoom(int extra) { if (nodes.size() + extra > MAX_ENTRIES) throw error("仮想環境のファイル数上限(2048)に達しました"); }
    private static String parent(String path) { int p = path.lastIndexOf('/'); return p <= 0 ? "/" : path.substring(0, p); }
    public static String base(String path) { int p = path.lastIndexOf('/'); return path.substring(p + 1); }
    private static IllegalArgumentException error(String text) { return new IllegalArgumentException(text); }
    static long utf8Length(String text) {
        long count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) count++;
            else if (c < 0x800) count += 2;
            else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) { count += 4; i++; }
            else count += 3;
        }
        return count;
    }
    private static long saturatedAdd(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
}
