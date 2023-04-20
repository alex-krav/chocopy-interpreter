package org.chocopy;

class RuntimeError extends RuntimeException {
    final int line;

    RuntimeError(Token token, String message) {
        super(message);
        this.line = token.line;
    }

    RuntimeError(int line, String message) {
        super(message);
        this.line = line;
    }
}
