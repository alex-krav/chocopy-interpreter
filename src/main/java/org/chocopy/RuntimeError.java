package org.chocopy;

class RuntimeError extends RuntimeException {
    final int line;
    final String errorType;

    RuntimeError(Token token, String message) {
        this(token, message, "RuntimeError");
    }

    RuntimeError(Token token, String message, String errorType) {
        super(message);
        this.line = token.line;
        this.errorType = errorType;
    }

    RuntimeError(int line, String message, String errorType) {
        super(message);
        this.line = line;
        this.errorType = errorType;
    }
}
