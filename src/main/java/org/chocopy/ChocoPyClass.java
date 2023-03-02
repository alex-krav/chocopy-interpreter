package org.chocopy;

import java.util.List;
import java.util.Map;

class ChocoPyClass implements ChocoPyCallable {
    final String name;

    ChocoPyClass(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int arity() {
        return 0;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        ChocoPyInstance instance = new ChocoPyInstance(this);
        return instance;
    }
}