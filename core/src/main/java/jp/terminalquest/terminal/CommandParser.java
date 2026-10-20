package jp.terminalquest.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses the deliberately small, non-executing shell language used by the game.
 * Quoting and escaping produce literal arguments; this class never expands
 * variables, globs, command substitutions, or shell control expressions.
 */
public final class CommandParser {
    public static final int MAX_INPUT_LENGTH = 4096;
    public static final int MAX_PIPELINE_STAGES = 16;

    /** A fully validated pipeline, suitable for execution after parsing succeeds. */
    public static final class Pipeline {
        public final List<List<String>> stages;
        public final String redirectPath;
        public final boolean append;

        private Pipeline(List<List<String>> stages, String redirectPath, boolean append) {
            List<List<String>> copy = new ArrayList<List<String>>();
            for (List<String> stage : stages) {
                copy.add(Collections.unmodifiableList(new ArrayList<String>(stage)));
            }
            this.stages = Collections.unmodifiableList(copy);
            this.redirectPath = redirectPath;
            this.append = append;
        }
    }

    private static final class Token {
        private static final int WORD = 0;
        private static final int PIPE = 1;
        private static final int WRITE = 2;
        private static final int APPEND = 3;
        final int kind;
        final String text;

        Token(int kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    public Pipeline parse(String input) {
        if (input == null) {
            throw invalid("コマンドを入力してください。");
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            throw invalid("コマンドは4096文字以内で入力してください。");
        }
        List<Token> tokens = tokenize(input);
        List<List<String>> stages = new ArrayList<List<String>>();
        List<String> current = new ArrayList<String>();
        String redirectPath = null;
        boolean append = false;

        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token.kind == Token.WORD) {
                current.add(token.text);
            } else if (token.kind == Token.PIPE) {
                requireCommand(current);
                stages.add(current);
                current = new ArrayList<String>();
                if (stages.size() >= MAX_PIPELINE_STAGES) {
                    throw invalid("パイプで連結できるコマンドは16個までです。");
                }
                if (index == tokens.size() - 1) {
                    throw invalid("| の後にコマンドが必要です。例: cat app.log | head -n 3");
                }
            } else {
                requireCommand(current);
                if (index + 1 >= tokens.size() || tokens.get(index + 1).kind != Token.WORD
                        || tokens.get(index + 1).text.length() == 0) {
                    throw invalid("> または >> の後に出力先ファイルが必要です。例: echo ready > report.txt");
                }
                if (index + 2 != tokens.size()) {
                    throw invalid("出力先はパイプラインの末尾に1つだけ指定してください。例: cat app.log | head > report.txt");
                }
                redirectPath = tokens.get(index + 1).text;
                append = token.kind == Token.APPEND;
                break;
            }
        }
        if (!current.isEmpty()) {
            requireCommand(current);
            stages.add(current);
        }
        return new Pipeline(stages, redirectPath, append);
    }

    private static void requireCommand(List<String> command) {
        if (command.isEmpty() || command.get(0).length() == 0) {
            throw invalid("コマンド名が必要です。| の前後にコマンドを書いてください。");
        }
    }

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<Token>();
        StringBuilder word = new StringBuilder();
        boolean wordStarted = false;
        char quote = 0;
        for (int index = 0; index < input.length(); index++) {
            char ch = input.charAt(index);
            if (ch == '\n' || ch == '\r' || ch == '\u0000'
                    || (ch < 32 && ch != '\t')) {
                throw invalid("改行や制御文字は使えません。コマンドは1行ずつ実行してください。");
            }
            if (quote == '\'') {
                if (ch == '\'') {
                    quote = 0;
                } else {
                    word.append(ch);
                }
                continue;
            }
            if (ch == '\\') {
                if (index + 1 == input.length()) {
                    throw invalid("行末の \\ の後には文字が必要です。");
                }
                char escaped = input.charAt(++index);
                if (escaped == '\n' || escaped == '\r' || escaped == '\u0000'
                        || (escaped < 32 && escaped != '\t')) {
                    throw invalid("改行や制御文字は使えません。");
                }
                word.append(escaped);
                wordStarted = true;
                continue;
            }
            if (quote == '"') {
                if (ch == '"') {
                    quote = 0;
                } else {
                    word.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                wordStarted = true;
            } else if (Character.isWhitespace(ch)) {
                if (wordStarted) {
                    tokens.add(new Token(Token.WORD, word.toString()));
                    word.setLength(0);
                    wordStarted = false;
                }
            } else if (ch == '|' || ch == '>') {
                if (wordStarted) {
                    tokens.add(new Token(Token.WORD, word.toString()));
                    word.setLength(0);
                    wordStarted = false;
                }
                if (ch == '|') {
                    tokens.add(new Token(Token.PIPE, "|"));
                } else if (index + 1 < input.length() && input.charAt(index + 1) == '>') {
                    tokens.add(new Token(Token.APPEND, ">>"));
                    index++;
                } else {
                    tokens.add(new Token(Token.WRITE, ">"));
                }
            } else if (ch == ';' || ch == '&' || ch == '<' || ch == '$'
                    || ch == '`' || ch == '(' || ch == ')') {
                throw invalid("未対応のシェル構文です。使える連結は |、出力は > と >> です。文字として使う場合は引用符で囲んでください。");
            } else {
                word.append(ch);
                wordStarted = true;
            }
        }
        if (quote != 0) {
            throw invalid("引用符が閉じられていません。同じ ' または \" で閉じてください。");
        }
        if (wordStarted) {
            tokens.add(new Token(Token.WORD, word.toString()));
        }
        return tokens;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
