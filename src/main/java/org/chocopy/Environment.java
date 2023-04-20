package org.chocopy;

import java.util.HashMap;
import java.util.Map;

class Environment {
    final Environment enclosing;
    final Map<String, Object> values = new HashMap<>();
    
    enum Scope {
        GLOBAL,
        NONLOCAL
    }

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

    @Override
    public String toString() {
        String result = values.toString();
        if (enclosing != null) {
            result += " -> " + enclosing;
        }

        return result;
    }
}
