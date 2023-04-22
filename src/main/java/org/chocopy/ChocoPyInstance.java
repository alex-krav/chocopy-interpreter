package org.chocopy;

class ChocoPyInstance {
    private final ChocoPyClass klass;

    ChocoPyInstance(ChocoPyClass klass) {
        this.klass = klass;
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }

    Object get(Token name) {
        ChocoPyAttribute attr = klass.findAttribute(name.lexeme);
        if (attr != null) return attr.getValue();

        ChocoPyFunction method = klass.findMethod(name.lexeme);
        if (method != null) return method.bind(this);

        throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
    }

    void set(Token name, Object value) {
        ChocoPyAttribute attr = klass.findAttribute(name.lexeme);
        if (attr != null) attr.setValue(value);
    }
}
