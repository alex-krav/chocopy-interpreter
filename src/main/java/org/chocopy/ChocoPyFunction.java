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
        if (declaration.params.size() > 0 && declaration.params.get(0).name.lexeme.equals("self")) {
            return declaration.params.size() - 1;
        } else {
            return declaration.params.size();
        }
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);
        
        if (declaration.params.size() > 0) {
            if (arguments.size() == declaration.params.size()) {
                // function
                for (int i = 0; i < declaration.params.size(); i++) {
                    environment.define(declaration.params.get(i).name.lexeme, arguments.get(i));
                }
            } else {
                // method
                environment.define(declaration.params.get(0).name.lexeme, 
                        closure.values.get("self"));
                for (int i = 1; i < declaration.params.size(); i++) {
                    environment.define(declaration.params.get(i).name.lexeme, arguments.get(i-1));
                }
            }
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            if (isInitializer) return closure.values.get("self");

            return returnValue.value;
        }

        if (isInitializer) return closure.values.get("self");
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }

    ChocoPyFunction bind(ChocoPyInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("self", instance);
        return new ChocoPyFunction(declaration, environment, isInitializer);
    }
}