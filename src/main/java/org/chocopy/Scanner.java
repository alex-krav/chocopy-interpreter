package org.chocopy;

import java.util.*;

import static org.chocopy.TokenType.*;

class Scanner {
    private static final Map<String, TokenType> keywords;
    private static final String[] reserved_keywords = new String[] {
            "as", "assert", "async", "await", "break", "continue",
            "del", "except", "finally", "from", "import",
            "lambda", "raise", "try", "with", "yield"
    };

    static {
        keywords = new HashMap<>();
        keywords.put("and",    AND);
        keywords.put("class",  CLASS);
        keywords.put("else",   ELSE);
        keywords.put("False",  FALSE);
        keywords.put("for",    FOR);
        keywords.put("def", DEF);
        keywords.put("if",     IF);
        keywords.put("None",   NONE);
        keywords.put("or",     OR);
        keywords.put("print", PRINT_NATIVE_FUN);
        keywords.put("return", RETURN);
        keywords.put("self",   SELF);
        keywords.put("True",   TRUE);
        keywords.put("while",  WHILE);
        keywords.put("not",    NOT);
        keywords.put("elif",   ELIF);
        keywords.put("pass",   PASS);
        keywords.put("input", INPUT_NATIVE_FUN);
        keywords.put("len", LEN_NATIVE_FUN);
        keywords.put("Empty",  EMPTY);
        keywords.put("object", OBJECT_TYPE);
        keywords.put("int", INT_TYPE);
        keywords.put("bool", BOOL_TYPE);
        keywords.put("str", STR_TYPE);
        keywords.put("global", GLOBAL);
        keywords.put("nonlocal",NONLOCAL);
        keywords.put("in",IN);
        keywords.put("is",IS);
    }

    String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    int line = 1;
    int spaces = 0;
    int tabs = 0;
    private final Stack<Integer> indentation = new Stack<>();
    private boolean lineStart = true;

    Scanner(String source) {
        this.source = source;
        this.indentation.push(0);
        ChocoPy.errors = new ArrayList<>();
        ChocoPy.runtimeErrors = new ArrayList<>();
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        dedentLine();
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(LEFT_PAREN); indentLine(); break;
            case ')': addToken(RIGHT_PAREN); indentLine(); break;
            case '[': addToken(LEFT_BRACKET); indentLine(); break;
            case ']': addToken(RIGHT_BRACKET); indentLine(); break;
            case ',': addToken(COMMA); indentLine(); break;
            case '.': addToken(DOT); indentLine(); break;
            case '+': addToken(PLUS); indentLine(); break;
            case ':': addToken(COLON); indentLine(); break;
            case '*': addToken(STAR); indentLine(); break;
            case '%': addToken(PERCENT); indentLine(); break;
            case '#':
                while (peek() != '\n' && peek() != '\r' && !isAtEnd())
                    advance();
                break;

            case '-':
                indentLine();
                addToken(match('>') ? ARROW : MINUS);
                break;
            case '=':
                indentLine();
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                indentLine();
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                indentLine();
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;

            case '!':
                indentLine();
                if (match('=')) {
                    addToken(BANG_EQUAL);
                } else {
                    ChocoPy.error(line, "invalid syntax", "SyntaxError");
                }
                break;
            case '/':
                indentLine();
                if (match('/')) {
                    addToken(DOUBLE_SLASH);
                } else {
                    ChocoPy.error(line, "invalid syntax", "SyntaxError");
                }
                break;

            case '\r':
                if (match('\n')) {
                    addToken(NEWLINE);
                } else {
                    addToken(NEWLINE);
                }
                incrementLine();
                break;
            case '\n':
                addToken(NEWLINE);
                incrementLine();
                break;
            case ' ':
            case '\t':
                if (!lineStart) {
                    break;
                }
                if (c == ' ') {
                    spaces++;
                } else {
                    tabs++;
                }
                while ((peek() == ' ' || peek() == '\t') && !isAtEnd()) {
                    if (peek() == ' ') {
                        spaces++;
                    } else if (peek() == '\t') {
                        tabs++;
                    }
                    advance();
                }
                if (peek() == '#') {
                    break;
                }
                replaceTabs();
                break;
            case '"': indentLine(); string(); break;

            default:
                indentLine();
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    id();
                } else {
                    ChocoPy.error(line, "invalid syntax", "SyntaxError");
                }
                break;
        }
    }

    private void incrementLine() {
        line++;
        lineStart = true;
        spaces = 0;
        tabs = 0;
    }

    void replaceTabs() {
        if (tabs > 0) {
            spaces += (8 - (spaces % 8));
            spaces += 8 * (tabs - 1);
        }
        tabs = 0;
    }

    private void indentLine() {
        if (!lineStart) {
            return;
        } else {
            lineStart = false;
        }

        if (spaces > indentation.peek()) {
            if (line == 1) {
                ChocoPy.error(line, "unexpected indent", "IndentationError");
            }
            indentation.push(spaces);
            tokens.add(new Token(INDENT, "", null, line));
            spaces = 0;
        } else if (spaces < indentation.peek()) {
            while (indentation.peek() > spaces) {
                indentation.pop();
                tokens.add(new Token(DEDENT, "", null, line));
            }
            if (indentation.peek() != spaces) {
                ChocoPy.error(line, "unindent does not match any outer indentation level", "IndentationError");
            }
            spaces = 0;
        }
    }

    private void dedentLine() {
        while (indentation.peek() > 0) {
            indentation.pop();
            tokens.add(new Token(DEDENT, "", null, line));
        }
    }

    private char advance() {
        return source.charAt(current++);
    }

    private void addToken(TokenType type) {
        if (type.equals(NEWLINE)) {
            if (tokens.isEmpty()) {
                return;
            } else if(tokens.get(tokens.size()-1).type.equals(NEWLINE)) {
                return;
            }
        }
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    void string() {
        while (!(peek() == '"' && source.charAt(current-1) != '\\') && !isAtEnd()) {
            if (peek() < ' ' || peek() > '~') {
                ChocoPy.error(line, "only 32-126 decimal range ASCII characters allowed in strings", "SyntaxError");
            }
            if (peek() == '\n') 
                line++;
            if (peek() == '\\') {
                char next = peekNext();
                if (next != '"' && next != '\\' && next != 't' && next != 'n') {
                    ChocoPy.error(line, "unrecognized escape sequence", "SyntaxError");
                } else {
                    advance();
                }
            }
            advance();
        }

        if (isAtEnd()) {
            ChocoPy.error(line, "unterminated string literal", "SyntaxError");
            return;
        }

        advance();

        String value = source.substring(start + 1, current - 1);
        value = value.replace("\\\\", "\\");
        value = value.replace("\\n", "\n");
        value = value.replace("\\t", "\t");
        value = value.replace("\\\"", "\"");
        
        if (isIdString(value)) {
            addToken(IDSTRING, value);
        } else {
            addToken(STRING, value);
        }
    }
    
    private boolean isIdString(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (value.length() == 1) {
            return isAlpha(value.charAt(0));
        }
        
        char firstChar = value.charAt(0);
        String other = value.substring(1);
        return isAlpha(firstChar) && other.matches("[a-zA-Z0-9_]+");
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void number() {
        while (isDigit(peek())) advance();

        String literal = source.substring(start, current);
        Integer value = null;
        try {
            value = Integer.valueOf(literal);

            if (value < (-Math.pow(2, 31)) || value > (Math.pow(2, 31) - 1)) {
                ChocoPy.error(line, "number value exceeds allowed range of 32 bit signed int", "OverflowError"); 
            } else if (value > 0 && literal.charAt(0) == '0') {
                ChocoPy.error(line, "leading zeros in decimal integer literals are not permitted", "SyntaxError");
            } else if (value < 0 && literal.charAt(1) == '0') {
                ChocoPy.error(line, "leading zeros in decimal integer literals are not permitted", "SyntaxError");
            }
        } catch (NumberFormatException e) {
            ChocoPy.error(line, "number value exceeds allowed range of 32 bit signed int", "OverflowError");
        }
        
        addToken(NUMBER, value);

        if (isAlpha(peek())) {
            ChocoPy.error(line, "invalid decimal literal", "SyntaxError");
        }
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private void id() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) {
            for (String reserved : reserved_keywords) {
                if (reserved.equals(text)) {
                    ChocoPy.error(line, String.format("'%s' keyword is reserved", text), "SyntaxError");
                }
            }
            type = ID;
        }
        addToken(type);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}