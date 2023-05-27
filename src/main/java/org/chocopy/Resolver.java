package org.chocopy;

import java.util.*;
import java.util.stream.Collectors;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    
    private static final List<Class> staticTypes = List.of(IntType.class, BoolType.class, StrType.class);
    private final Stack<Map<String, ValueType>> scopes = new Stack<>();
    private final Map<String, ClassInfo> classes = new HashMap<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private Set<Integer> targetCounters = new HashSet<>();
    private int targetCounter = 1;

    private static int DECLARATIONS = 0;
    private static int STATEMENTS = 1;
    private static int ERROR = 2;

    Resolver() {}

    private enum FunctionType {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    private enum ClassType {
        NONE,
        CLASS
    }

    private ClassType currentClass = ClassType.NONE;
    private String currentClassName;

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.target);
        
        ValueType targetType = expr.target.inferredType;
        ValueType valueType = expr.value.inferredType;
        
        if (expr.targetCounter > 0 
                && targetCounters.contains(expr.targetCounter) 
                && valueType.equals(new ListValueType(new NoneType()))) {
            ChocoPy.error(expr.target.name, "multiple assignment of '[<None>]' is forbidden", "TypeError");
        }
        targetCounters.add(expr.targetCounter);

        if (targetType != null) {
            if (!isVarDeclaredGlobalInCurrentScope(expr.target.name.lexeme) 
                    && !isVarDeclaredNonlocalInCurrentScope(expr.target.name.lexeme) 
                    && !definedInCurrentScope(expr.target.name.lexeme)) {
                ChocoPy.error(expr.target.name, String.format("name '%s' is not defined in current scope", expr.target.name.lexeme), "NameError");
                return null;
            } else if (isVarDeclaredGlobalInCurrentScope(expr.target.name.lexeme) 
                    && getGlobalType(expr.target.name.lexeme) == null) {
                ChocoPy.error(expr.target.name, String.format("name '%s' is not defined in global scope", expr.target.name.lexeme), "NameError");
                return null;
            } else if (isVarDeclaredNonlocalInCurrentScope(expr.target.name.lexeme) 
                    && getNonLocalType(expr.target.name.lexeme) == null) {
                ChocoPy.error(expr.target.name, String.format("name '%s' is not defined in nonlocal scope", expr.target.name.lexeme), "NameError");
                return null;
            }

            if (valueType!= null && !canAssign(valueType, targetType)) {
                ChocoPy.error(expr.target.name, String.format("expected type '%s', got type '%s'", targetType, valueType), "TypeError");
                return null;
            }
        }
        
        expr.inferredType = targetType;
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        
        TokenType operator = expr.operator.type;
        ValueType leftType = expr.left.inferredType;
        ValueType rightType = expr.right.inferredType;

        switch (operator) {
            case PLUS -> {
                if (leftType instanceof ListValueType && rightType instanceof ListValueType) {
                    ValueType leftElementType = ((ListValueType) leftType).getElementType();
                    ValueType rightElementType = ((ListValueType) rightType).getElementType();
                    expr.inferredType = new ListValueType(join(leftElementType, rightElementType));
                } else if (leftType.equals(rightType) && List.of(StrType.class, IntType.class).contains(leftType.getClass())) {
                    expr.inferredType = leftType;
                } else {
                    ChocoPy.binopError(expr, "TypeError");
                    expr.inferredType = new ObjectType();
                }
            }
            case MINUS, STAR, DOUBLE_SLASH, PERCENT -> {
                if (leftType instanceof IntType && rightType instanceof IntType) {
                    expr.inferredType = new IntType();
                } else {
                    ChocoPy.binopError(expr, "TypeError");
                    expr.inferredType = new ObjectType();
                }
            }
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
                if (leftType instanceof IntType && rightType instanceof IntType) {
                    expr.inferredType = new BoolType();
                } else {
                    ChocoPy.binopError(expr, "TypeError");
                    expr.inferredType = new ObjectType();
                }
            }
            case EQUAL_EQUAL, BANG_EQUAL -> {
                if (leftType.equals(rightType) && staticTypes.contains(leftType.getClass())) {
                    expr.inferredType = new BoolType();
                } else {
                    ChocoPy.binopError(expr, "TypeError");
                    expr.inferredType = new ObjectType();
                }
            }
            case IS -> {
                if (!staticTypes.contains(leftType.getClass()) && !staticTypes.contains(rightType.getClass())) {
                    expr.inferredType = new BoolType();
                } else {
                    ChocoPy.binopError(expr, "TypeError");
                    expr.inferredType = new ObjectType();
                }
            }
            default -> expr.inferredType = new ObjectType();
        }
        
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        expr.callee.callable = true;
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }
        
        FuncType t;
        if (expr.callee instanceof Expr.Variable) {
            String name = ((Expr.Variable)expr.callee).name.lexeme;
            if (classExists(name)) {
                // CONSTRUCTOR
                t = getMethod(name, "__init__");
                if (t != null) {
                    if (t.getParameters().size() != (expr.arguments.size() + 1)) {
                        ChocoPy.error(expr.line, 
                                String.format("expected %d args, got %d", t.getParameters().size() - 1, expr.arguments.size()), 
                                "TypeError");
                    } else {
                        for (int i = 0; i < t.getParameters().size() - 1; i++) {
                            if (!canAssign(expr.arguments.get(i).inferredType, t.getParameters().get(i+1))) {
                                ChocoPy.error(expr.line, 
                                        String.format("expected type '%s', got type '%s'", t.getParameters().get(i+1), expr.arguments.get(i).inferredType), 
                                        "TypeError");
                            }
                        }
                    }
                }
                
                switch (name) {
                    case "object" -> expr.inferredType = new ObjectType();
                    case "int" -> expr.inferredType = new IntType();
                    case "str" -> expr.inferredType = new StrType();
                    case "bool" -> expr.inferredType = new BoolType();
                    default -> expr.inferredType = new ClassValueType(name);
                }
            } 
            else {
                // FUNCTION
                ValueType type = getType(name);
                if (!(type instanceof FuncType)) {
                    ChocoPy.error(expr.line, "'" + name + "' object is not callable", "TypeError");
                    expr.inferredType = new ObjectType();
                    return null;
                }
                t = (FuncType) type;
                if (t.getParameters().size() != expr.arguments.size()) {
                    ChocoPy.error(expr.line, 
                            String.format("expected %d args, got %d", t.getParameters().size(), expr.arguments.size()), 
                            "TypeError");
                } else {
                    for (int i = 0; i < t.getParameters().size(); i++) {
                        if (!canAssign(expr.arguments.get(i).inferredType, t.getParameters().get(i))) {
                            ChocoPy.error(expr.line, 
                                    String.format("expected type '%s', got type '%s'",t.getParameters().get(i), expr.arguments.get(i).inferredType), 
                                    "TypeError");
                        }
                    }
                }
                expr.inferredType = t.getReturnType();
            }
            expr.callee.inferredType = t;
        } 
        else if (expr.callee instanceof Expr.Get) {
            // METHOD
            Expr.Get getExpr = (Expr.Get)expr.callee;
            Expr object = getExpr.object;
            String methodName = getExpr.name.lexeme;
            
            if (staticTypes.contains(object.inferredType.getClass()) || !(object.inferredType.getClass().equals(ClassValueType.class))) {
                ChocoPy.error(expr.line, "expected type 'object', got type '" + object.inferredType + "'", "TypeError");
                expr.inferredType = new ObjectType();
                return null;
            } else {
                String className = ((ClassValueType) object.inferredType).getClassName();
                t = getMethod(className, methodName);
                if (t == null) {
                    expr.inferredType = new ObjectType();
                    return null;
                }
            }
            // self arguments
            if (t.getParameters().size() != expr.arguments.size() + 1) {
                ChocoPy.error(expr.line, 
                        String.format("expected %d args, got %d", t.getParameters().size()-1, expr.arguments.size()), 
                        "TypeError");
            } else {
                for (int i = 0; i < t.getParameters().size()-1; i++) {
                    if (!canAssign(expr.arguments.get(i).inferredType, t.getParameters().get(i+1))) {
                        ChocoPy.error(expr.line, 
                                String.format("expected type '%s', got type '%s'", t.getParameters().get(i+1), expr.arguments.get(i).inferredType), 
                                "TypeError");
                    }
                }
            }
            expr.callee.inferredType = t;
            expr.inferredType = t.getReturnType();
            return null;
        } else {
            ChocoPy.error(expr.line, "object is not callable", "TypeError");
            expr.inferredType = new ObjectType();
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        
        ValueType objInferredType = expr.object.inferredType;
        
        if (staticTypes.contains(objInferredType.getClass())) {
            ChocoPy.error(expr.name, "expected type 'object', got type '" + objInferredType + "'", "TypeError");
        } else {
            String className = ((ClassValueType)objInferredType).getClassName();
            String memberName = expr.name.lexeme;
            
            if (expr.callable) {
                if (getMethod(className, memberName) == null) {
                    ChocoPy.error(expr.name, String.format("'%s' object has no attribute '%s'", className, memberName), "AttributeError");
                    expr.inferredType = new ObjectType();
                } else {
                    expr.inferredType = getMethod(className, memberName);
                }
            } else {
                if (getAttr(className, memberName) == null) {
                    ChocoPy.error(expr.name, String.format("'%s' object has no attribute '%s'", className, memberName), "AttributeError");
                    expr.inferredType = new ObjectType();
                } else {
                    expr.inferredType = getAttr(className, memberName);
                }
            }
        }
        
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        expr.inferredType = expr.expression.inferredType;
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        ValueType type = new NoneType();
        
        if (expr.value != null) {
            switch (expr.value.getClass().getName()) {
                case "java.lang.Integer" -> type = new IntType();
                case "java.lang.String" -> type = new StrType();
                case "java.lang.Boolean" -> type = new BoolType();
            }
        }
        
        expr.inferredType = type;
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);

        ValueType leftType = expr.left.inferredType;
        ValueType rightType = expr.right.inferredType;
        
        if (leftType instanceof BoolType && rightType instanceof BoolType) {
            expr.inferredType = new BoolType();
        } else {
            ChocoPy.binopError(expr, "TypeError");
            expr.inferredType = new ObjectType();
        }
    
        return null;
    }

    @Override
    public Void visitTernaryExpr(Expr.Ternary expr) {
        resolve(expr.condition);
        resolve(expr.onFalse);
        resolve(expr.onTrue);
        
        if (!(expr.condition.inferredType instanceof BoolType)) {
            ChocoPy.error(expr.line, "expected type 'boolean', got type '" + expr.condition.inferredType + "'", "TypeError");
        }
        
        expr.inferredType = join(expr.onTrue.inferredType, expr.onFalse.inferredType);
        return null;
    }

    @Override
    public Void visitListingExpr(Expr.Listing expr) {
        for (Expr element : expr.elements) {
            resolve(element);
        }
        
        if (expr.elements.size() == 0) {
            expr.inferredType = new EmptyType();
        } else {
            ValueType elementType = expr.elements.get(0).inferredType;
            for (Expr element : expr.elements) { 
                elementType = join(elementType, element.inferredType);
            }
            expr.inferredType = new ListValueType(elementType);
        }
        
        return null;
    }

    @Override
    public Void visitIndexExpr(Expr.Index expr) {
        resolve(expr.listing);
        resolve(expr.id);
        
        if (!(expr.id.inferredType instanceof IntType)) {
            ChocoPy.error(expr.line, String.format("list indices must be 'int', not '%s'", expr.id.inferredType), "TypeError");
        }
        
        if (expr.listing.inferredType instanceof StrType) {
            // indexing into a string returns a new string
            expr.inferredType = new StrType();
        } else if (expr.listing.inferredType instanceof ListValueType) {
            // indexing into a list of type T returns a value of type T
            ListValueType listingType = (ListValueType) expr.listing.inferredType;
            expr.inferredType = listingType.getElementType();
        } else {
            ChocoPy.error(expr.line, String.format("'%s' object is not subscriptable", expr.listing.inferredType), "TypeError");
            expr.inferredType = new ObjectType();
        }
        
        return null;
    }

    @Override
    public Void visitListSetExpr(Expr.ListSet expr) {
        resolve(expr.id);
        resolve(expr.listing);
        
        ValueType listType = expr.listing.inferredType;
        ValueType valueType = expr.value.inferredType;

        if (expr.targetCounter > 0
                && targetCounters.contains(expr.targetCounter)
                && valueType.equals(new ListValueType(new NoneType()))) {
            ChocoPy.error(expr.line, "multiple assignment of '[<None>]' is forbidden", "TypeError");
        }
        targetCounters.add(expr.targetCounter);
        
        if (listType instanceof StrType) {
            ChocoPy.error(expr.line ,"'str' object does not support item assignment", "TypeError");
        } else if (!(listType instanceof ListValueType)) {
            ChocoPy.error(expr.line ,"'" + listType + "' object does not support item assignment", "TypeError");
        } else {
            if (!canAssign(expr.value.inferredType, ((ListValueType) listType).getElementType())) {
                ChocoPy.error(expr.line ,String.format("expected type '%s', got type '%s'",
                        ((ListValueType) listType).getElementType(), expr.value.inferredType), "TypeError");
            }
        }
        
        if (!(expr.id.inferredType instanceof IntType)) {
            ChocoPy.error(expr.line, String.format("list indices must be 'int', not '%s'", expr.id.inferredType), "TypeError");
        }
        
        expr.inferredType = listType;
        return null;
    }

    @Override
    public Void visitLenExpr(Expr.Len expr) {
        resolve(expr.expression);

        if (!(expr.expression.inferredType instanceof StrType) 
                && !(expr.expression.inferredType instanceof ListValueType)) {
            ChocoPy.error(expr.line, "expected type 'str' or 'list', got type '" + expr.expression.inferredType + "'", "TypeError");
        }

        expr.inferredType = new IntType();
        return null;
    }

    @Override
    public Void visitInputExpr(Expr.Input expr) {
        expr.inferredType = new StrType();
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.object);

        ValueType objInferredType = expr.object.inferredType;
        ValueType valueInferredType = expr.value.inferredType;

        if (expr.targetCounter > 0
                && targetCounters.contains(expr.targetCounter)
                && valueInferredType.equals(new ListValueType(new NoneType()))) {
            ChocoPy.error(expr.name, "multiple assignment of '[<None>]' is forbidden", "TypeError");
        }
        targetCounters.add(expr.targetCounter);

        if (staticTypes.contains(objInferredType.getClass())) {
            ChocoPy.error(expr.name, "expected type 'object', got type '" + objInferredType + "'", "TypeError");
        } else {
            String className = ((ClassValueType)objInferredType).getClassName();
            String memberName = expr.name.lexeme;

            ValueType attr = getAttr(className, memberName);
            if (attr == null) {
                ChocoPy.error(expr.name, String.format("'%s' object has no attribute '%s'", className, memberName), "AttributeError");
            } else if (attr instanceof FuncType) {
                ChocoPy.error(expr.name, "can't set to class method '" + memberName + "'", "AttributeError");
            } else if (!canAssign(expr.value.inferredType, attr)) {
                ChocoPy.error(expr.object.line, String.format("expected type '%s', got type '%s'", attr, expr.value.inferredType), "TypeError"); 
            }
        }

        expr.inferredType = expr.object.inferredType;
        return null;
    }

    @Override
    public Void visitSelfExpr(Expr.Self expr) {
        if (currentClass == ClassType.NONE) {
            ChocoPy.error(expr.keyword, "'self' outside class", "SyntaxError");
            return null;
        }

        expr.inferredType = getType("self");
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        ValueType operandType = expr.right.inferredType;
        
        switch(expr.operator.type) {
            case MINUS -> {
                if (operandType instanceof IntType) {
                    expr.inferredType = new IntType();
                } else {
                    ChocoPy.error(expr.operator, "expected type 'int', got type '" + operandType + "'", "TypeError");
                    expr.inferredType = new ObjectType();
                }
            }
            case NOT -> {
                if (operandType instanceof BoolType) {
                    expr.inferredType = new BoolType();
                } else {
                    ChocoPy.error(expr.operator, "expected type 'bool', got type '" + operandType + "'", "TypeError");
                    expr.inferredType = new ObjectType();
                }
            }
            default -> expr.inferredType = new ObjectType();
        }
        
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        ValueType type = getType(expr.name.lexeme);
        
        if (type == null) {
            ChocoPy.error(expr.name, String.format("name '%s' is not defined in current scope", expr.name.lexeme), "NameError");
            expr.inferredType = new ObjectType();
            return null;
        } else if (isVarDeclaredGlobalInCurrentScope(expr.name.lexeme) 
                && getGlobalType(expr.name.lexeme) == null) {
            ChocoPy.error(expr.name, String.format("name '%s' is not defined in global scope", expr.name.lexeme), "NameError");
            expr.inferredType = new ObjectType();
            return null;
        } else if (isVarDeclaredNonlocalInCurrentScope(expr.name.lexeme) 
                && getNonLocalType(expr.name.lexeme) == null) {
            ChocoPy.error(expr.name, String.format("name '%s' is not defined in nonlocal scope", expr.name.lexeme), "NameError");
            expr.inferredType = new ObjectType();
            return null;
        }
        
        if (scopes.peek().get(expr.name.lexeme) instanceof StubType) {
            ChocoPy.error(expr.name, "can't read local variable in its own initializer", "NameError");
        }
        
        expr.inferredType = type;
        return null;
    }

    private ValueType getType(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name)) {
                return scopes.get(i).get(name);
            }
        }
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block block) {
        for (Stmt statement : block.statements) {
            resolve(statement);
            if (statement.isReturn) {
                block.isReturn = true;
            }
        }
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        if (stmt.resolverStage == DECLARATIONS) {
            stmt.resolverStage = STATEMENTS;

            String className = stmt.name.lexeme;
            String superClassName = stmt.superclass.lexeme;
            currentClassName = className;

            if (classes.containsKey(className)) {
                ChocoPy.error(stmt.name, String.format("cannot shadow class name: '%s'", className), "SyntaxError");
                stmt.resolverStage = ERROR;
            } else if (scopes.peek().containsKey(className)) {
                ChocoPy.error(stmt.name, String.format("duplicate declaration of id: '%s'", stmt.name.lexeme), "SyntaxError");
                stmt.resolverStage = ERROR;
            }

            if (!classExists(superClassName)) {
                ChocoPy.error(stmt.superclass, "name '" + superClassName + "' is not defined", "NameError");
                superClassName = "object";
                stmt.resolverStage = ERROR;
            } else if (List.of("int", "bool", "str", className).contains(superClassName)) {
                ChocoPy.error(stmt.superclass, "type '" + superClassName + "' is not an acceptable base type", "TypeError");
                superClassName = "object";
                stmt.resolverStage = ERROR;
            } else if (className.equals(stmt.superclass.lexeme)) {
                ChocoPy.error(stmt.superclass, "name '" + className + "' is not defined", "NameError");
                stmt.resolverStage = ERROR;
            }
            declare(stmt.name);
            define(stmt.name, new ClassValueType(className));
            classes.put(className, new ClassInfo(className, superClassName));
        } else if (stmt.resolverStage == STATEMENTS) {
            String className = stmt.name.lexeme;
            ClassType enclosingClass = currentClass;
            currentClass = ClassType.CLASS;
            currentClassName = className;

            beginScope();
            scopes.peek().put("self", new ClassValueType(className));

            for (Stmt member : stmt.members) {
                if (member instanceof Stmt.Function) {
                    Stmt.Function method = (Stmt.Function) member;
                    String methodName = method.name.lexeme;
                    FuncType methodType = getSignature(method);

//                    FunctionType declaration = FunctionType.METHOD;
//                    if (methodName.equals("__init__")) {
//                        declaration = FunctionType.INITIALIZER;
//                    }

                    if (classes.containsKey(className)
                            && (classes.get(className).methods.containsKey(methodName)
                            || classes.get(className).attrs.containsKey(methodName))) {
                        ChocoPy.error(method.name, String.format("duplicate declaration of id: '%s'", methodName), "SyntaxError");
                        method.resolverStage = ERROR;
                        continue;
                    }

                    ValueType type = getAttrOrMethod(className, methodName);
                    if (type != null) {
                        if (!(type instanceof FuncType)) {
                            ChocoPy.error(method.name, String.format("method name shadows attribute: '%s'", methodName), "SyntaxError");
                            method.resolverStage = ERROR;
                            continue;
                        }
                        if (!((FuncType) type).methodEquals(methodType)) {
                            ChocoPy.error(method.name, String.format("redefined method doesn't match superclass signature: '%s'", methodName), "SyntaxError");
                            method.resolverStage = ERROR;
                            continue;
                        }
                    }

                    classes.get(className).methods.put(methodName, methodType);
                    declare(method.name);
                    define(method.name, methodType);
//                    resolveFunction(method, declaration);
                } else if (member instanceof Stmt.Var) {
                    Stmt.Var attr = (Stmt.Var)member;
                    String attrName = attr.name.lexeme;
                    if (getAttrOrMethod(className, attrName) != null) {
                        ChocoPy.error(attr.name, String.format("cannot redefine attribute: '%s'", attrName), "SyntaxError");
                        stmt.resolverStage = ERROR;
                        continue;
                    }
                    classes.get(className).attrs.put(attrName, attr.type);
                    resolve(attr);
                }
            }
            for (Stmt member : stmt.members) {
                if (member instanceof Stmt.Function) {
                    Stmt.Function method = (Stmt.Function) member;
                    String methodName = method.name.lexeme;
                    FunctionType declaration = FunctionType.METHOD;
                    if (methodName.equals("__init__")) {
                        declaration = FunctionType.INITIALIZER;
                    }
                    resolveFunction(method, declaration);
                }
            }

            endScope();

            currentClass = enclosingClass;
            currentClassName = null;
        }
        return null;
    }

    void resolveScript(List<Stmt> statements) {
        beginScope();
        
        define("print", new FuncType(Collections.singletonList(new ObjectType()), new NoneType()));
        define("input", new FuncType(Collections.emptyList(), new StrType()));
        define("len", new FuncType(Collections.singletonList(new ObjectType()), new IntType()));
        
        scopes.peek().put("object", new ObjectType());
        scopes.peek().put("int", new IntType());
        scopes.peek().put("str", new StrType());
        scopes.peek().put("bool", new BoolType());
        
        FuncType defaultConstructor = new FuncType(Collections.singletonList(new ObjectType()), new NoneType());
        ClassInfo objectInfo = new ClassInfo("object");
        objectInfo.methods.put("__init__", defaultConstructor);
        classes.put("object", objectInfo);

        ClassInfo intInfo = new ClassInfo("int", "object");
        intInfo.methods.put("__init__", defaultConstructor);
        classes.put("int", intInfo);

        ClassInfo boolInfo = new ClassInfo("bool", "object");
        boolInfo.methods.put("__init__", defaultConstructor);
        classes.put("bool", boolInfo);

        ClassInfo strInfo = new ClassInfo("str", "object");
        strInfo.methods.put("__init__", defaultConstructor);
        classes.put("str", strInfo);

        for (Stmt statement : statements) {
            resolve(statement);
        }
        for (Stmt statement : statements) {
            resolve(statement);
        }
        endScope();
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        scopes.pop();
    }
    
    private FuncType getMethod(String className, String methodName) {
        if (classes.containsKey(className)) {
            if (!classes.get(className).methods.containsKey(methodName)) {
                if (classes.get(className).superclass == null) {
                    return null;
                }
                return getMethod(classes.get(className).superclass, methodName);
            }
            return classes.get(className).methods.get(methodName);
        } else {
            return null;
        }
    }
    
    // returns type of attribute or method
    // requires className to be the name of a valid class
    private ValueType getAttrOrMethod(String className, String name) {
        if (classes.containsKey(className)) {
            if (classes.get(className).methods.containsKey(name)) {
                return classes.get(className).methods.get(name);
            } else if (classes.get(className).attrs.containsKey(name)) {
                return classes.get(className).attrs.get(name);
            } else {
                if (classes.get(className).superclass == null || classes.get(className).superclass.equals("<None>")) {
                    return null;
                }
                return getAttrOrMethod(classes.get(className).superclass, name);
            }
        } else {
            return null;
        }
    }
    
    // return if the name was defined in the current scope
    private boolean definedInCurrentScope(String var) {
        return scopes.peek().get(var) != null;
    }
    
    private ValueType getGlobalType(String var) {
        return scopes.get(0).get(var);
    }
    
    // get the type of an id outside the current scope, or None if not found
    // ignore global variables
    private ValueType getNonLocalType(String var) {
        int scopesLen = scopes.size();
        if (scopesLen < 3) {
            return null;
        }

        for (int i = scopesLen - 2; i >= 1; i--) {
            if (scopes.get(i).containsKey(var)) {
                return scopes.get(i).get(var);
            }
        }
        return null;
    }
    
    private FuncType getSignature(Stmt.Function node) {
        resolveReturnType(node);
        return new FuncType(
                node.params.stream().map(p -> p.type).collect(Collectors.toList()),
                node.returnType);
    }
    
    private void resolveReturnType(Stmt.Function node) {
        ValueType type = node.returnType;
        
        if (type.getClass().equals(ClassValueType.class)) {
            String className = ((ClassValueType)type).getClassName();
            if (getGlobalType(className) == null) {
                ChocoPy.error(node.name, String.format("unknown return type '%s'", type), "TypeError");
            }
        } else if (type instanceof ListValueType) {
            ValueType elementType = ((ListValueType) type).getElementType();
            while (elementType instanceof ListValueType) {
                elementType = ((ListValueType) elementType).getElementType();
            }
            if (elementType.getClass().equals(ClassValueType.class)) {
                String className = ((ClassValueType)elementType).getClassName();
                if (getGlobalType(className) == null) {
                    ChocoPy.error(node.name, String.format("unknown return type '%s'", type), "TypeError");
                }
            }
        }
    }
    
    private boolean classExists(String className) {
        return classes.containsKey(className);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        if (stmt.resolverStage == DECLARATIONS) {
            stmt.resolverStage = STATEMENTS;
        } else if (stmt.resolverStage == STATEMENTS) {
            resolve(stmt.expression);
        }
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        if (stmt.resolverStage == DECLARATIONS) {
            stmt.resolverStage = STATEMENTS;
            
            String functionName = stmt.name.lexeme;

            if (classExists(functionName)) {
                ChocoPy.error(stmt.name, String.format("functions can't shadow classes: '%s'", functionName), "SyntaxError");
                stmt.resolverStage = ERROR;
                return null;
            } else if (definedInCurrentScope(functionName)) {
                ChocoPy.error(stmt.name, String.format("duplicate declaration of id: '%s'", functionName), "SyntaxError");
                stmt.resolverStage = ERROR;
                return null;
            }

            FuncType funcType = getSignature(stmt);
            declare(stmt.name);
            define(stmt.name, funcType);
        } else if (stmt.resolverStage == STATEMENTS) {
            resolveFunction(stmt, FunctionType.FUNCTION);
        }
        return null;
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        if (function.resolverStage == ERROR) return;
        
        String functionName = function.name.lexeme;
        FuncType funcType = (FuncType) scopes.peek().get(function.name.lexeme);
        function.signature = funcType;

        beginScope();
        scopes.peek().put("expectedReturnType", funcType.getReturnType());
        if (type == FunctionType.FUNCTION) {
            if (classExists(functionName)) {
                ChocoPy.error(function.name, "functions can't shadow classes: " + functionName, "SyntaxError");
                function.resolverStage = ERROR;
//                return;
            } else if (definedInCurrentScope(functionName)) {
                ChocoPy.error(function.name, "duplicate declaration of id " + functionName, "SyntaxError");
                function.resolverStage = ERROR;
//                return;
            }
        } else if (type == FunctionType.METHOD || type == FunctionType.INITIALIZER) {
            if (function.params.size() == 0 
                    || !function.params.get(0).name.lexeme.equals("self") 
                    || !(funcType.getParameters().get(0).getClass().equals(ClassValueType.class))
                    || !((ClassValueType) funcType.getParameters().get(0)).getClassName().equals(currentClassName)) {
                ChocoPy.error(function.name, String.format("missing 'self' param in method: '%s'", functionName), "SyntaxError");
                function.resolverStage = ERROR;
//                return;
            }
        }
        
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        for (Stmt.Var param : function.params) {
            if (classes.containsKey(param.name.lexeme)) {
                ChocoPy.error(param.name, String.format("cannot shadow class name: '%s'", param.name.lexeme), "SyntaxError");
                function.resolverStage = ERROR;
                continue;
            } else if (scopes.peek().containsKey(param.name.lexeme)) {
                ChocoPy.error(param.name, String.format("duplicate declaration of id: '%s'", param.name.lexeme), "SyntaxError");
                function.resolverStage = ERROR;
                continue;
            }
            if (!isTypeDefined(param.type)) {
                ChocoPy.error(param.name, String.format("unknown type: '%s'", param.type), "TypeError");
                function.resolverStage = ERROR;
            }
            declare(param.name);
            define(param.name, param.type);
            param.inferredType = param.type;
        }
        for (Stmt statement : function.body) {
            resolve(statement);
        }
        for (Stmt statement : function.body) {
            resolve(statement);
        }
        
        boolean hasReturn = false;
        for (Stmt s : function.body) {
            if (s.isReturn) {
                hasReturn = true;
                break;
            }
        }
        if (!hasReturn && !canAssign(new NoneType(), scopes.peek().get("expectedReturnType"))) {
            ChocoPy.error(function.name, "expected return statement of type '" + scopes.peek().get("expectedReturnType") + "'", "TypeError");
            function.resolverStage = ERROR;
        }
        scopes.peek().put("expectedReturnType", null);
        endScope();
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (stmt.resolverStage == DECLARATIONS) {
            stmt.resolverStage = STATEMENTS;
            return null;
        }
        
        resolve(stmt.condition);
        if (!(stmt.condition.inferredType instanceof BoolType)) {
            ChocoPy.error(stmt.condition.line,"expected type 'bool', got type '" + stmt.condition.inferredType + "'", "TypeError");
            stmt.resolverStage = ERROR;
            return null;
        }
        
        if (stmt.thenBranch instanceof Stmt.Block) {
            for (Stmt statement : ((Stmt.Block) stmt.thenBranch).statements) {
                statement.resolverStage = stmt.resolverStage;
            }
        } else if (stmt.thenBranch instanceof Stmt.If) {
            stmt.thenBranch.resolverStage = stmt.resolverStage;
        }
        if (stmt.elseBranch instanceof Stmt.Block) {
            for (Stmt statement : ((Stmt.Block) stmt.elseBranch).statements) {
                statement.resolverStage = stmt.resolverStage;
            }
        } else if (stmt.elseBranch instanceof Stmt.If) {
            stmt.elseBranch.resolverStage = stmt.resolverStage;
        }
        
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        
        // isReturn=True if there's >=1 statement in BOTH branches that have isReturn=True
        // if a branch is empty, isReturn=False
        stmt.isReturn = stmt.thenBranch.isReturn && (stmt.elseBranch != null && stmt.elseBranch.isReturn);
        return null;
    }

    @Override
    public Void visitPrintExpr(Expr.Print expr) {
        resolve(expr.expression);
        
        if (!staticTypes.contains(expr.expression.inferredType.getClass())) {
            ChocoPy.error(expr.expression.line, "expected type 'str', 'int' or 'bool', got type '" + expr.expression.inferredType + "'", "TypeError");
        }
        
        expr.inferredType = new NoneType();
        return null;
    }

    @Override
    public Void visitMultiAssignExpr(Expr.MultiAssign multiAssign) {
        Expr value = multiAssign.value;
        resolve(value);
        List<Expr> assignments = new ArrayList<>();

        for (Expr target : multiAssign.targets) {
            if (target instanceof Expr.Variable expr) {
                Expr.Assign assign = new Expr.Assign(new Expr.Variable(expr.name), value);
                assign.line = multiAssign.line;
                if (multiAssign.targets.size() > 1) assign.targetCounter = targetCounter;
                resolve(assign);
                assignments.add(assign);
            } else if (target instanceof Expr.Get expr) {
                Expr.Set set = new Expr.Set(expr.object, expr.name, value);
                set.line = multiAssign.line;
                if (multiAssign.targets.size() > 1) set.targetCounter = targetCounter;
                resolve(set);
                assignments.add(set);
            } else if (target instanceof Expr.Index expr) {
                Expr.ListSet listSet = new Expr.ListSet(expr.listing, expr.id, value);
                listSet.line = multiAssign.line;
                if (multiAssign.targets.size() > 1) listSet.targetCounter = targetCounter;
                resolve(listSet);
                assignments.add(listSet);
            } else {
                ChocoPy.error(multiAssign.line, "invalid assignment target", "SyntaxError");
            }
        }
        if (multiAssign.targets.size() > 1) targetCounter++;
        
        multiAssign.targets = assignments;
        multiAssign.inferredType = value.inferredType;
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            ChocoPy.error(stmt.keyword, "'return' outside function", "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        }
        if (stmt.resolverStage == DECLARATIONS) {
            stmt.resolverStage = STATEMENTS;
            return null;
        }

        if (stmt.value != null) {

            resolve(stmt.value);

            if (!canAssign(stmt.value.inferredType, scopes.peek().get("expectedReturnType"))) {
                ChocoPy.error(stmt.keyword, String.format("expected type '%s', got type '%s'", scopes.peek().get("expectedReturnType"), stmt.value.inferredType), "TypeError");
                stmt.resolverStage = ERROR;
            }
        } else {
            if (!canAssign(new NoneType(), scopes.peek().get("expectedReturnType"))) {
                ChocoPy.error(stmt.keyword, String.format("expected type '%s', got type '<None>'", scopes.peek().get("expectedReturnType")), "TypeError");
                stmt.resolverStage = ERROR;
            }
        }

        stmt.expectedType = scopes.peek().get("expectedReturnType");
        return null;
    }
    
    private boolean isTypeDefined(ValueType type) {
        if (type instanceof ListValueType) {
            ListValueType listValueType = (ListValueType) type;
            ValueType elementType = listValueType.getElementType();
            
            while(elementType instanceof ListValueType) {
                elementType = ((ListValueType) elementType).getElementType();
            }
            return staticTypes.contains(elementType.getClass()) || classes.containsKey(elementType.toString());
        } else {
            return staticTypes.contains(type.getClass()) || classes.containsKey(type.toString());
        }
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        if (stmt.resolverStage > DECLARATIONS) return null;
        stmt.resolverStage = STATEMENTS;
        
        String varName = stmt.name.lexeme;
        if (classes.containsKey(varName)) {
            ChocoPy.error(stmt.name, String.format("cannot shadow class name: '%s'", varName), "SyntaxError");
            stmt.resolverStage = ERROR;
        } else if (scopes.peek().containsKey(varName)) {
            ChocoPy.error(stmt.name, String.format("duplicate declaration of id: '%s'", varName), "SyntaxError");
            stmt.resolverStage = ERROR;
        }
        if (!isTypeDefined(stmt.type)) {
            ChocoPy.error(stmt.name, String.format("unknown type: '%s'", stmt.type), "TypeError");
            stmt.resolverStage = ERROR;
        }
        
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);

            if (!canAssign(stmt.initializer.inferredType, stmt.type)) {
                ChocoPy.error(stmt.name, String.format("expected type '%s', got type '%s'",
                        stmt.type, stmt.initializer.inferredType), "TypeError");
                stmt.resolverStage = ERROR;
            }
        }
        define(stmt.name, stmt.type);
        
        stmt.inferredType = stmt.type;
        return null;
    }

    private void declare(Token name) {
        if (!scopes.peek().containsKey(name.lexeme)) {
            scopes.peek().put(name.lexeme, new StubType());
        }
    }

    private void define(Token name, ValueType type) {
        scopes.peek().put(name.lexeme, type);
    }

    private void define(String name, ValueType type) {
        scopes.peek().put(name, type);
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        if (stmt.resolverStage == DECLARATIONS) {
            stmt.resolverStage = STATEMENTS;
            return null;
        }
        
        resolve(stmt.condition);
        for (Stmt statement : ((Stmt.Block) stmt.body).statements) {
            statement.resolverStage = stmt.resolverStage;
        }
        resolve(stmt.body);
        
        if (!(stmt.condition.inferredType instanceof BoolType)) {
            ChocoPy.error(stmt.condition.line, "expected type 'bool', got type '" + stmt.condition.inferredType + "'", "TypeError");
            stmt.resolverStage = ERROR;
            return null;
        }
        
        Stmt.Block body = (Stmt.Block) stmt.body;
        for (Stmt statement : body.statements) {
            if (statement.isReturn) {
                stmt.isReturn = true;
                break;
            }
        }
        
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        if (stmt.resolverStage == DECLARATIONS) {
            stmt.resolverStage = STATEMENTS;
            return null;
        }
        
        resolve(stmt.id);
        resolve(stmt.iterable);
        for (Stmt statement : ((Stmt.Block) stmt.body).statements) {
            statement.resolverStage = stmt.resolverStage;
        }
        resolve(stmt.body);
        
        ValueType iterableType = stmt.iterable.inferredType;
        if (iterableType instanceof ListValueType) {
            ValueType elementType = ((ListValueType) iterableType).getElementType();
            if (!canAssign(elementType, stmt.id.inferredType)) {
                ChocoPy.error(stmt.id.line, 
                        String.format("expected type '%s', got type '%s'", elementType, stmt.id.inferredType), 
                        "TypeError");
                stmt.resolverStage = ERROR;
                return null;
            }
        } else if (iterableType instanceof StrType) {
            if (!canAssign(iterableType, stmt.id.inferredType)) {
                ChocoPy.error(stmt.id.line, 
                        String.format("expected type 'str', got type '%s'", stmt.id.inferredType), 
                        "TypeError");
                stmt.resolverStage = ERROR;
                return null;
            }
        } else {
            ChocoPy.error(stmt.id.line, 
                    String.format("expected type 'list' or 'str', got type '%s'", stmt.iterable.inferredType), 
                    "TypeError");
            stmt.resolverStage = ERROR;
            return null;
        }
        
        Stmt.Block body = (Stmt.Block) stmt.body;
        for (Stmt statement : body.statements) {
            if (statement.isReturn) {
                stmt.isReturn = true;
                break;
            }
        }
        return null;
    }

    @Override
    public Void visitPassStmt(Stmt.Pass stmt) {
        return null;
    }

    @Override
    public Void visitGlobalStmt(Stmt.Global stmt) {
        if (currentFunction == FunctionType.NONE) {
            ChocoPy.error(stmt.name, "'global' outside function", "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        }
        if (stmt.resolverStage > DECLARATIONS) return null;
        stmt.resolverStage = STATEMENTS;
        
        if (classes.containsKey(stmt.name.lexeme)) {
            ChocoPy.error(stmt.name, String.format("cannot shadow class name: '%s'", stmt.name.lexeme), "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        } else if (scopes.peek().containsKey(stmt.name.lexeme)) {
            ChocoPy.error(stmt.name, String.format("duplicate declaration of id: '%s'", stmt.name.lexeme), "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        }
        
        ValueType type = getGlobalType(stmt.name.lexeme);
        if (type == null || type instanceof FuncType) {
            ChocoPy.error(stmt.name, "no binding for global '" + stmt.name.lexeme + "' found", "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        } else {
            addGlobalVarToCurrentScope(stmt.name.lexeme);
        }

        declare(stmt.name);
        define(stmt.name, type);
        return null;
    }
    
    private void addGlobalVarToCurrentScope(String var) {
        if (scopes.peek().containsKey("__global")) {
            Vars globals = (Vars) scopes.peek().get("__global");
            globals.put(var);
        }
    }
    
    private void addNonlocalVarToCurrentScope(String var) {
        if (scopes.peek().containsKey("__nonlocal")) {
            Vars nonlocals = (Vars) scopes.peek().get("__nonlocal");
            nonlocals.put(var);
        }
    }

    private boolean isVarDeclaredGlobalInCurrentScope(String var) {
        if (scopes.peek().containsKey("__global")) {
            Vars globals = (Vars) scopes.peek().get("__global");
            return globals.contains(var);
        } else {
            return false;
        }
    }

    private boolean isVarDeclaredNonlocalInCurrentScope(String var) {
        if (scopes.peek().containsKey("__nonlocal")) {
            Vars nonlocals = (Vars) scopes.peek().get("__nonlocal");
            return nonlocals.contains(var);
        } else {
            return false;
        }
    }

    @Override
    public Void visitNonlocalStmt(Stmt.Nonlocal stmt) {
        if (currentFunction == FunctionType.NONE) {
            ChocoPy.error(stmt.name, "'nonlocal' outside function", "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        }
        if (stmt.resolverStage > DECLARATIONS) return null;
        stmt.resolverStage = STATEMENTS;
        
        if (classes.containsKey(stmt.name.lexeme)) {
            ChocoPy.error(stmt.name, String.format("cannot shadow class name: '%s'", stmt.name.lexeme), "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        } else if (scopes.peek().containsKey(stmt.name.lexeme)) {
            ChocoPy.error(stmt.name, String.format("duplicate declaration of id: '%s'", stmt.name.lexeme), "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        }
        
        ValueType type = getNonLocalType(stmt.name.lexeme);
        if (type == null || type instanceof FuncType) {
            ChocoPy.error(stmt.name, "no binding for nonlocal '" + stmt.name.lexeme + "' found", "SyntaxError");
            stmt.resolverStage = ERROR;
            return null;
        } else {
            addNonlocalVarToCurrentScope(stmt.name.lexeme);
        }

        declare(stmt.name);
        define(stmt.name, type);
        return null;
    }
    
    // return if value of type a can be assigned/passed to type b (ex: b = a)
    private boolean canAssign(ValueType a, ValueType b) {
        if (isSubtype(a, b)) {
            return true;
        }
        if (a instanceof NoneType && !staticTypes.contains(b.getClass())) {
            return true;
        }
        if (b instanceof ListValueType && a instanceof EmptyType) {
            return true;
        }
        if (b instanceof ListValueType && a instanceof ListValueType) {
            ValueType aElementType = ((ListValueType) a).getElementType();
            ValueType bElementType = ((ListValueType) b).getElementType();
            if (aElementType instanceof NoneType) {
                return canAssign(aElementType, bElementType);
            }
        }
        return false;
    }
    
    // return if a is subtype of b
    private boolean isSubtype(ValueType a, ValueType b) {
        if (b instanceof ObjectType) {
            return true;
        }
        if (a.getClass().equals(ClassValueType.class) && b.getClass().equals(ClassValueType.class)) {
            String aName = ((ClassValueType) a).getClassName();
            String bName = ((ClassValueType) b).getClassName();
            return isSubClass(aName, bName);
        }
        return a.equals(b);
    }

    // requires a and b to be the names of valid classes
    // return if a is the same class or subclass of b
    private boolean isSubClass(String a, String  b) {
        String current = a;
        while (current != null && !current.equals("<None>")) {
            if (current.equals(b)) {
                return true;
            } else {
                current = classes.get(current).superclass;
            }
        }
        return false;
    }

    // return closest mutual ancestor on typing tree
    private ValueType join(ValueType a, ValueType b) {
        if (canAssign(a, b)) {
            return b;
        }
        if (canAssign(b, a)) {
            return a;
        }
        if (b instanceof ListValueType && a instanceof ListValueType) {
            ValueType aElementType = ((ListValueType) a).getElementType();
            ValueType bElementType = ((ListValueType) b).getElementType();
            return new ListValueType(join(bElementType, aElementType));
        }
        // if only 1 of the types is a list then the closest ancestor is object
        if (b instanceof ListValueType || a instanceof ListValueType) {
            return new ObjectType();
        }
        
        // for 2 classes that aren't related by subtyping
        // find paths from A & B to root of typing tree
        String aClassName = ((ClassValueType) a).getClassName();
        String bClassName = ((ClassValueType) b).getClassName();
        List<String> aAncestors = new ArrayList<>();
        List<String> bAncestors = new ArrayList<>();
        
        while (classes.get(aClassName) != null) {
            String superclass = classes.get(aClassName).superclass;
            if (superclass == null || superclass.equals("<None>")) {
                break;
            }
            aAncestors.add(superclass);
            aClassName = superclass;
        }
        
        while (classes.get(bClassName) != null) {
            String superclass = classes.get(bClassName).superclass;
            if (superclass == null || superclass.equals("<None>")) {
                break;
            }
            bAncestors.add(superclass);
            bClassName = superclass;
        }
                    
        // reverse lists to find the lowest common ancestor
        Collections.reverse(aAncestors);
        Collections.reverse(bAncestors);
        
        for (int i = 0; i < Math.min(aAncestors.size(), bAncestors.size()); i++) {
            if (!aAncestors.get(i).equals(bAncestors.get(i))) {
                return classes.get(aAncestors.get(i - 1)).getValueType();
            }
        }
        
        // this really shouldn't be returned
        return new ObjectType();    
    }
    
    // returns type of attribute
    // requires className to be the name of a valid class
    private ValueType getAttr(String className, String attrName) {
        if (classes.containsKey(className)) {
            if (!classes.get(className).attrs.containsKey(attrName)) {
                if (classes.get(className).superclass == null || classes.get(className).superclass.equals("<None>")) {
                    return null;
                }
                return getAttr(classes.get(className).superclass, attrName);
            }
            return classes.get(className).attrs.get(attrName);
        } else {
            return null;
        }
    }
}