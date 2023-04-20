package org.chocopy;

public class ChocoPyAttribute {

    private Object value;

    public ChocoPyAttribute(Object value) {
        this.value = value;
    }
    
    public Object getValue() {
        return value;
    }
    
    public void setValue(Object upd) {
        this.value = upd;
    }
}
