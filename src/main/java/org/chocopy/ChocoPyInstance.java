package org.chocopy;

import java.util.HashMap;
import java.util.Map;

class ChocoPyInstance {
    private final ChocoPyClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    ChocoPyInstance(ChocoPyClass klass) {
        this.klass = klass;
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }

    Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }

        throw new RuntimeError(name,
                "Undefined property '" + name.lexeme + "'.");
    }

    void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }
}
