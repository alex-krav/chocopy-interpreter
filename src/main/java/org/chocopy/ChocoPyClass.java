package org.chocopy;

import java.util.List;
import java.util.Map;

class ChocoPyClass {
    final String name;

    ChocoPyClass(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}