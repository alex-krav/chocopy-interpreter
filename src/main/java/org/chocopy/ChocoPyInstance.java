package org.chocopy;

class ChocoPyInstance {
    private final ChocoPyClass klass;

    ChocoPyInstance(ChocoPyClass klass) {
        this.klass = klass;
    }

    @Override
    public String toString() {
        if (klass.name.equals("int")) {
            return "0";
        } else if (klass.name.equals("str")) {
            return "";
        } else if (klass.name.equals("bool")) {
            return "False";
        } else {
            return klass.name + " instance";
        }
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
