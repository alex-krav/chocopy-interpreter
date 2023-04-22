package org.chocopy;

import java.util.List;
import java.util.Map;

class ChocoPyClass implements ChocoPyCallable {
    final String name;
    final ChocoPyClass superclass;
    private final Map<String, ChocoPyFunction> methods;
    private final Map<String, ChocoPyAttribute> attributes;

    ChocoPyClass(String name, ChocoPyClass superclass,
                 Map<String, ChocoPyFunction> methods,
                 Map<String, ChocoPyAttribute> attributes) {
        this.superclass = superclass;
        this.name = name;
        this.methods = methods;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int arity() {
        ChocoPyFunction initializer = findMethod("__init__");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        switch(this.name) {
            case("int"): return 0;
            case("str"): return "";
            case("bool"): return false;
            case("object"): return null;
        }
        ChocoPyInstance instance = new ChocoPyInstance(this);
        ChocoPyFunction initializer = findMethod("__init__");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }

        return instance;
    }

    ChocoPyFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        if (superclass != null) {
            return superclass.findMethod(name);
        }

        return null;
    }

    ChocoPyAttribute findAttribute(String name) {
        if (attributes.containsKey(name)) {
            return attributes.get(name);
        }

        if (superclass != null) {
            return superclass.findAttribute(name);
        }

        return null;
    }
}