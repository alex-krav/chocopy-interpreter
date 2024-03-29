package org.chocopy;

public enum TokenType {
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACKET, RIGHT_BRACKET,
    COMMA, DOT, MINUS, PLUS, COLON, PERCENT, STAR,

    BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
    ARROW, DOUBLE_SLASH,

    ID, IDSTRING, STRING, NUMBER,

    BOOL_TYPE, STR_TYPE, INT_TYPE, OBJECT_TYPE, NONE_TYPE, EMPTY_LIST_TYPE, LIST_TYPE,

    PRINT_NATIVE_FUN, INPUT_NATIVE_FUN, LEN_NATIVE_FUN,

    AND, CLASS, ELSE, FALSE, DEF, FOR, IF, NONE, OR,
    RETURN, SELF, TRUE, WHILE,
    NOT, ELIF, PASS, EMPTY, IS, IN, GLOBAL, NONLOCAL,
    NEWLINE, INDENT, DEDENT,

    EOF
}
