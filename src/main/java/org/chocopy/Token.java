package org.chocopy;

import java.util.Objects;

import static org.chocopy.TokenType.*;

class Token {
    final TokenType type;
    final String lexeme;
    final Object literal;
    final int line;

    Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return switch (type) {
            case LEFT_PAREN -> LEFT_PAREN + " (";
            case RIGHT_PAREN -> RIGHT_PAREN + " )";
            case LEFT_BRACKET -> LEFT_BRACKET + " [";
            case RIGHT_BRACKET -> RIGHT_BRACKET + " ]";
            case COMMA -> COMMA + " ,";
            case DOT -> DOT + " .";
            case MINUS -> MINUS + " -";
            case PLUS -> PLUS + " +";
            case DOUBLE_SLASH -> DOUBLE_SLASH + " //";
            case STAR -> STAR + " *";
            case COLON -> COLON + " :";
            case PERCENT -> PERCENT + " %";
            case BANG_EQUAL -> BANG_EQUAL + " !=";
            case EQUAL -> EQUAL + " =";
            case EQUAL_EQUAL -> EQUAL_EQUAL + " ==";
            case GREATER -> GREATER + " >";
            case GREATER_EQUAL -> GREATER_EQUAL + " >=";
            case LESS -> LESS + " <";
            case LESS_EQUAL -> LESS_EQUAL + " <=";
            case ARROW -> ARROW + " ->";
            case ID -> ID + " " + lexeme;
            case NUMBER -> NUMBER + " " + lexeme;
            case STRING -> STRING + " \"" + literal + "\"";
            case IDSTRING -> IDSTRING + " \"" + literal + "\"";
            default -> type.toString();
        };
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((this.literal == null) ? 0 : this.literal.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Token)) {
            return false;
        }

        Token token = (Token) obj;

        return (Objects.equals(this.type, token.type))
                && Objects.equals(this.lexeme, token.lexeme)
                && Objects.equals(this.literal, token.literal)
                && this.line == token.line;
    }
}