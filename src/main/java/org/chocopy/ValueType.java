package org.chocopy;

import java.util.List;

public abstract class ValueType {
    
}

class StubType extends ValueType {
    
}

class ClassValueType extends ValueType {
    private String className;

    public ClassValueType(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public String toString() {
        return className;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassValueType that = (ClassValueType) o;
        return className.equals(that.className);
    }
}

class ObjectType extends ClassValueType {
    public ObjectType() {
        super("object");
    }
}

class IntType extends ClassValueType {
    public IntType() {
        super("int");
    }
}

class StrType extends ClassValueType {
    public StrType() {
        super("str");
    }
}

class BoolType extends ClassValueType {
    public BoolType() {
        super("bool");
    }
}

class NoneType extends ClassValueType {
    public NoneType() {
        super("<None>");
    }
}

class EmptyType extends ClassValueType {
    public EmptyType() {
        super("<Empty>");
    }
}

class ListValueType extends ValueType {
    private ValueType elementType;

    public ListValueType(ValueType elementType) {
        this.elementType = elementType;
    }

    public ValueType getElementType() {
        return elementType;
    }

    @Override
    public String toString() {
        return "[" + elementType + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListValueType that = (ListValueType) o;
        return elementType.equals(that.elementType);
    }
}

class FuncType extends ValueType {
    private List<ValueType> parameters;
    private ValueType returnType;

    public FuncType(List<ValueType> parameters, ValueType returnType) {
        this.parameters = parameters;
        this.returnType = returnType;
    }

    public List<ValueType> getParameters() {
        return parameters;
    }

    public ValueType getReturnType() {
        return returnType;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            builder.append(parameters.get(i).toString());
            if (i < parameters.size() - 1) {
                builder.append(", ");
            }
        }
        return String.format("[%s] -> %s", builder, returnType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FuncType that = (FuncType) o;
        return parameters.equals(that.parameters) && returnType.equals(that.returnType);
    }
    
    public boolean methodEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FuncType that = (FuncType) o;
        
        if (parameters.size() > 0 && that.parameters.size() > 0) {
            return parameters.subList(1, parameters.size()).equals(that.parameters.subList(1, that.parameters.size())) 
                    && returnType.equals(that.returnType);
        }
        return false;
    }
}