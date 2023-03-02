package org.chocopy;

import java.util.HashMap;
import java.util.Map;

class ChocoPyInstance {
    private final ChocoPyClass klass;

    ChocoPyInstance(ChocoPyClass klass) {
        this.klass = klass;
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}
