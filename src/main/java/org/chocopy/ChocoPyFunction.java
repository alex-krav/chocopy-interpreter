package org.chocopy;

import java.util.List;

class ChocoPyFunction implements ChocoPyCallable {
    private final Stmt.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;

    ChocoPyFunction(Stmt.Function declaration, Environment closure,
                    boolean isInitializer) {
        this.isInitializer = isInitializer;
        this.closure = closure;
        this.declaration = declaration;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme,
                    arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            if (isInitializer) return closure.getAt(0, "this");

            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "this");
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }

    ChocoPyFunction bind(ChocoPyInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new ChocoPyFunction(declaration, environment, isInitializer);
    }
}