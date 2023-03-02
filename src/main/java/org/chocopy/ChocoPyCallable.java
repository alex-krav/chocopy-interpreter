package org.chocopy;

import java.util.List;

interface ChocoPyCallable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}