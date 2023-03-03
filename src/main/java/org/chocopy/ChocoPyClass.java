package org.chocopy;

import java.util.List;
import java.util.Map;

class ChocoPyClass implements ChocoPyCallable {
    final String name;
    private final Map<String, ChocoPyFunction> methods;

    ChocoPyClass(String name, Map<String, ChocoPyFunction> methods) {
        this.name = name;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int arity() {
        ChocoPyFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        ChocoPyInstance instance = new ChocoPyInstance(this);
        ChocoPyFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }

        return instance;
    }

    ChocoPyFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        return null;
    }
}