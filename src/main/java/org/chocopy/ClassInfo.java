package org.chocopy;

import java.util.HashMap;
import java.util.Map;

public class ClassInfo {
    protected String name;
    protected String superclass;
    protected Map<String, ValueType> attrs = new HashMap<>();
    protected Map<String, FuncType> methods = new HashMap<>();

    public ClassInfo(String name) {
        this.name = name;
    }

    public ClassInfo(String name, String superclass) {
        this.name = name;
        this.superclass = superclass;
    }
    
    public ValueType getValueType() {
        return new ClassValueType(name);
    }

    @Override
    public String toString() {
        return String.format("class %s(%s):\n  %s\n  %s", name, superclass, attrs.toString(), methods.toString());
    }
}
