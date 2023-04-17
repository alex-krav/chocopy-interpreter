package org.chocopy;

import java.util.*;
import java.util.stream.Collectors;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    
    private static final List<Class> staticTypes = List.of(IntType.class, BoolType.class, StrType.class);
    private final Stack<Map<String, ValueType>> scopes = new Stack<>();
    private final Map<String, ClassInfo> classes = new HashMap<>();
    private FunctionType currentFunction = FunctionType.NONE;

    private final Interpreter interpreter;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

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
        resolve(expr.value);
        
        if (expr.value.inferredType!= null 
                && expr.target.inferredType != null 
                && !canAssign(expr.value.inferredType, expr.target.inferredType)) {
            ChocoPy.error(expr.target.name, String.format("Expected %s, got %s",
                    expr.target.inferredType, expr.value.inferredType));
            return null;
        }
        
        expr.inferredType = expr.target.inferredType;
        resolveLocal(expr, expr.target.name);
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
                    ChocoPy.binopError(expr);
                    expr.inferredType = new ObjectType();
                }
            }
            case MINUS, STAR, DOUBLE_SLASH, PERCENT -> {
                if (leftType instanceof IntType && rightType instanceof IntType) {
                    expr.inferredType = new IntType();
                } else {
                    ChocoPy.binopError(expr);
                    expr.inferredType = new ObjectType();
                }
            }
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
                if (leftType instanceof IntType && rightType instanceof IntType) {
                    expr.inferredType = new BoolType();
                } else {
                    ChocoPy.binopError(expr);
                    expr.inferredType = new ObjectType();
                }
            }
            case EQUAL_EQUAL, BANG_EQUAL -> {
                if (leftType.equals(rightType) && staticTypes.contains(leftType.getClass())) {
                    expr.inferredType = new BoolType();
                } else {
                    ChocoPy.binopError(expr);
                    expr.inferredType = new ObjectType();
                }
            }
            case IS -> {
                if (!staticTypes.contains(leftType.getClass()) && !staticTypes.contains(rightType.getClass())) {
                    expr.inferredType = new BoolType();
                } else {
                    ChocoPy.binopError(expr);
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
                        ChocoPy.error(expr.line, String.format("Expected %d args, got %d", t.getParameters().size() - 1, expr.arguments.size()));
                    } else {
                        for (int i = 0; i < t.getParameters().size() - 1; i++) {
                            if (!canAssign(expr.arguments.get(i).inferredType, t.getParameters().get(i+1))) {
                                ChocoPy.error(expr.line, String.format("Expected %s, got %s", t.getParameters().get(i+1), expr.arguments.get(i).inferredType));
                            }
                        }
                    }
                }
                expr.inferredType = new ClassValueType(name);
            } 
            else {
                // FUNCTION
                ValueType type = getType(name);
                if (!(type instanceof FuncType)) {
                    ChocoPy.error(expr.line, "Not a function: " + name);
                    expr.inferredType = new ObjectType();
                    return null;
                }
                t = (FuncType) type;
                if (t.getParameters().size() != expr.arguments.size()) {
                    ChocoPy.error(expr.line, String.format("Expected %d args, got %d", t.getParameters().size(), expr.arguments.size()));
                } else {
                    for (int i = 0; i < t.getParameters().size(); i++) {
                        if (!canAssign(expr.arguments.get(i).inferredType, t.getParameters().get(i))) {
                            ChocoPy.error(expr.line, String.format("Expected %s, got %s",t.getParameters().get(i), expr.arguments.get(i).inferredType));
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
                ChocoPy.error(expr.line, "Expected object, got " + object.inferredType);
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
                ChocoPy.error(expr.line, String.format("Expected %d args, got %d", t.getParameters().size()-1, expr.arguments.size()));
            } else {
                for (int i = 0; i < t.getParameters().size()-1; i++) {
                    if (!canAssign(expr.arguments.get(i).inferredType, t.getParameters().get(i+1))) {
                        ChocoPy.error(expr.line, String.format("Expected %s, got %s", t.getParameters().get(i+1), expr.arguments.get(i).inferredType));
                    }
                }
            }
            expr.callee.inferredType = t;
            expr.inferredType = t.getReturnType();
            return null;
        } else {
            ChocoPy.error(expr.line, "Identifier is not callable");
            expr.inferredType = new ObjectType();
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        
        ValueType objInferredType = expr.object.inferredType;
        
        if (staticTypes.contains(objInferredType.getClass())) {
            ChocoPy.error(expr.name, "Expected object, got " + objInferredType);
        } else {
            String className = ((ClassValueType)objInferredType).getClassName();
            String memberName = expr.name.lexeme;
            
            if (expr.callable) {
                if (getMethod(className, memberName) == null) {
                    ChocoPy.error(expr.name, String.format("Method %s doesn't exist for class %s", memberName, className));
                    expr.inferredType = new ObjectType();
                } else {
                    expr.inferredType = getMethod(className, memberName);
                }
            } else {
                if (getAttr(className, memberName) == null) {
                    ChocoPy.error(expr.name, String.format("Attribute %s doesn't exist for class %s", memberName, className));
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
            ChocoPy.binopError(expr);
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
            ChocoPy.error(expr.line, "Expected boolean, got " + expr.condition.inferredType);
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
            ChocoPy.error(expr.line, "Expected int index, got " + expr.id.inferredType);
        }
        
        if (expr.listing.inferredType instanceof StrType) {
            // indexing into a string returns a new string
            expr.inferredType = new StrType();
        } else if (expr.listing.inferredType instanceof ListValueType) {
            // indexing into a list of type T returns a value of type T
            ListValueType listingType = (ListValueType) expr.listing.inferredType;
            expr.inferredType = listingType.getElementType();
        } else {
            ChocoPy.error(expr.line, "Cannot index into " + expr.listing.inferredType);
            expr.inferredType = new ObjectType();
        }
        
        return null;
    }

    @Override
    public Void visitListSetExpr(Expr.ListSet expr) {
        resolve(expr.listing);
        resolve(expr.id);
        resolve(expr.value);
        
        if (expr.listing.inferredType instanceof StrType) {
            ChocoPy.error(expr.line ,"Cannot assign to index of string");
        }
        if (!(expr.id.inferredType instanceof IntType)) {
            ChocoPy.error(expr.line, "Expected int index, got " + expr.id.inferredType);
        }
        if (expr.listing.inferredType instanceof ListValueType 
                && !canAssign(expr.value.inferredType, ((ListValueType) expr.listing.inferredType).getElementType())) {
            ChocoPy.error(expr.line ,String.format("Expected %s, got %s", 
                    ((ListValueType) expr.listing.inferredType).getElementType(), expr.value.inferredType));
        }
        
        expr.inferredType = expr.listing.inferredType;
        return null;
    }

    @Override
    public Void visitLenExpr(Expr.Len expr) {
        resolve(expr.expression);

        if (!(expr.expression.inferredType instanceof StrType) 
                && !(expr.expression.inferredType instanceof ListValueType)) {
            ChocoPy.error(expr.line, "Expected str or list, got " + expr.expression.inferredType);
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
        resolve(expr.value);
        resolve(expr.object);

        ValueType objInferredType = expr.object.inferredType;

        if (staticTypes.contains(objInferredType.getClass())) {
            ChocoPy.error(expr.name, "Expected object, got " + objInferredType);
        } else {
            String className = ((ClassValueType)objInferredType).getClassName();
            String memberName = expr.name.lexeme;

            ValueType attr = getAttr(className, memberName);
            if (attr == null) {
                ChocoPy.error(expr.name, String.format("Attribute %s doesn't exist for class %s", memberName, className));
            } else if (attr instanceof FuncType) {
                ChocoPy.error(expr.name, "Can't set to class method " + memberName);
            } else if (!canAssign(expr.value.inferredType, attr)) {
                ChocoPy.error(expr.object.line, String.format("Expected %s, got %s", attr, expr.value.inferredType));
            }
        }

        expr.inferredType = expr.object.inferredType;
        return null;
    }

    @Override
    public Void visitSelfExpr(Expr.Self expr) {
        if (currentClass == ClassType.NONE) {
            ChocoPy.error(expr.keyword, "Can't use 'self' outside of a class.");
            return null;
        } else if (!definedInCurrentScope("self")) {
            ChocoPy.error(expr.keyword, "'self' is not defined.");
            return null;
        }

        resolveLocal(expr, expr.keyword);
        expr.inferredType = scopes.peek().get("self");
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
                    ChocoPy.error(expr.operator, "Expected int, got " + operandType);
                    expr.inferredType = new ObjectType();
                }
            }
            case NOT -> {
                if (operandType instanceof BoolType) {
                    expr.inferredType = new BoolType();
                } else {
                    ChocoPy.error(expr.operator, "Expected bool, got " + operandType);
                    expr.inferredType = new ObjectType();
                }
            }
            default -> expr.inferredType = new ObjectType();
        }
        
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!expr.callable && !scopes.peek().containsKey(expr.name.lexeme)) {
            ChocoPy.error(expr.name, "Identifier not defined in current scope: " + expr.name.lexeme);
            expr.inferredType = new ObjectType();
            return null;
        } else if (expr.callable && getType(expr.name.lexeme) == null) {
            ChocoPy.error(expr.name, "Callable not defined: " + expr.name.lexeme);
            expr.inferredType = new ObjectType();
            return null;
        }
        
        if (scopes.peek().get(expr.name.lexeme) instanceof StubType) {
            ChocoPy.error(expr.name,
                    "Can't read local variable in its own initializer.");
        }
        
        expr.inferredType = getType(expr.name.lexeme);
        resolveLocal(expr, expr.name);
        return null;
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
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
        resolve(block.statements);
        for (Stmt statement : block.statements) {
            if (statement.isReturn) {
                block.isReturn = true;
                break;
            }
        }
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        String className = stmt.name.lexeme;
        String superClassName = stmt.superclass.lexeme;
        currentClassName = className;
        
        if (classes.containsKey(className)) {
            ChocoPy.error(stmt.name, "Cannot shadow class name: " + className);
        } else if (scopes.peek().containsKey(className)) {
            ChocoPy.error(stmt.name, "Duplicate declaration of identifier: " + stmt.name.lexeme);
        }
        
        if (!classExists(superClassName)) { // && !List.of("<None>", "<Empty>").contains(superClassName) ?
            ChocoPy.error(stmt.superclass, "Unknown superclass: " + superClassName);
            superClassName = "object";
        } else if (List.of("int", "bool", "str", className).contains(superClassName)) {
            ChocoPy.error(stmt.superclass, "Illegal superclass: " + superClassName);
            superClassName = "object";
        } else if (className.equals(stmt.superclass.lexeme)) {
            ChocoPy.error(stmt.superclass, "A class can't inherit from itself.");
        }
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name, new ClassValueType(className));
        classes.put(className, new ClassInfo(className, superClassName));

        beginScope();
        scopes.peek().put("self", new ClassValueType(className));

        for (Stmt member : stmt.members) {
            if (member instanceof Stmt.Pass) {
                continue;
            } else if (member instanceof Stmt.Function) {
                Stmt.Function method = (Stmt.Function) member;
                String methodName = method.name.lexeme;
                FuncType methodType = getSignature(method);
                
                FunctionType declaration = FunctionType.METHOD;
                if (methodName.equals("init")) {
                    declaration = FunctionType.INITIALIZER;
                }

                if (classes.containsKey(className) 
                        && (classes.get(className).methods.containsKey(methodName) 
                        || classes.get(className).attrs.containsKey(methodName))) {
                    ChocoPy.error(method.name, "Duplicate declaration of identifier: " + methodName);
                    continue;
                }
                
                ValueType type = getAttrOrMethod(className, methodName);
                if (type != null) {
                    if (!(type instanceof FuncType)) {
                        ChocoPy.error(method.name, "Method names shadows attribute: " + methodName);
                        continue;
                    }
                    if (!((FuncType) type).methodEquals(methodType)) {
                        ChocoPy.error(method.name, "Redefined method doesn't match superclass signature: " + methodName);
                        continue;
                    }
                }

                classes.get(className).methods.put(methodName, methodType);
                declare(method.name);
                define(method.name, methodType);
                resolveFunction(method, declaration);
            } else {
                Stmt.Var attr = (Stmt.Var)member;
                String attrName = attr.name.lexeme;
                if (getAttrOrMethod(className, attrName) != null) {
                    ChocoPy.error(attr.name, "Cannot redefine attribute: " + attrName);
                    continue;
                }
                classes.get(className).attrs.put(attrName, attr.type);
//                declare(attr.name);
//                define(attr.name, attr.type);
                resolve(attr);
                resolveLocal(new Expr.Variable(attr.name), attr.name);
            }
        }

        endScope();

        currentClass = enclosingClass;
        currentClassName = null;
        return null;
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    void resolveScript(List<Stmt> statements) {
        beginScope();
        
        define("print", new FuncType(Collections.singletonList(new ObjectType()), new NoneType()));
        define("input", new FuncType(Collections.emptyList(), new StrType()));
        define("len", new FuncType(Collections.singletonList(new ObjectType()), new IntType()));
        scopes.peek().put("object", new ClassValueType("object"));
        
        ClassInfo objectClassInfo = new ClassInfo("object", null);
        objectClassInfo.methods.put("__init__", new FuncType(Collections.singletonList(new ObjectType()), new NoneType()));
        classes.put("object", objectClassInfo);

        resolve(statements);
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
    
    // get the type of an identifier outside the current scope, or None if not found
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
                ChocoPy.error(node.name, "Unknown return type " + type);
            }
        } else if (type instanceof ListValueType) {
            ValueType elementType = ((ListValueType) type).getElementType();
            while (elementType instanceof ListValueType) {
                elementType = ((ListValueType) elementType).getElementType();
            }
            if (elementType.getClass().equals(ClassValueType.class)) {
                String className = ((ClassValueType)elementType).getClassName();
                if (getGlobalType(className) == null) {
                    ChocoPy.error(node.name, "Unknown return type " + type);
                }
            }
        }
    }
    
    private boolean classExists(String className) {
        return classes.containsKey(className);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        String functionName = stmt.name.lexeme;
        
        if (definedInCurrentScope(functionName)) {
            ChocoPy.error(stmt.name, "Duplicate declaration of identifier: " + functionName);
        }

        FuncType funcType = getSignature(stmt);
        declare(stmt.name);
        define(stmt.name, funcType);
        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        String functionName = function.name.lexeme;
        FuncType funcType = (FuncType) scopes.peek().get(function.name.lexeme);
        function.signature = funcType;

        beginScope();
        scopes.peek().put("expectedReturnType", funcType.getReturnType());
        if (type == FunctionType.FUNCTION) {
            if (classExists(functionName)) {
                ChocoPy.error(function.name, "Functions can't shadow classes: " + functionName);
                return;
            } else if (definedInCurrentScope(functionName)) {
                ChocoPy.error(function.name, "Duplicate declaration of identifier " + functionName);
                return;
            }
        } else if (type == FunctionType.METHOD || type == FunctionType.INITIALIZER) {
            if (function.params.size() == 0 
                    || !function.params.get(0).name.lexeme.equals("self") 
                    || !(funcType.getParameters().get(0).getClass().equals(ClassValueType.class))
                    || !((ClassValueType) funcType.getParameters().get(0)).getClassName().equals(currentClassName)) {
                ChocoPy.error(function.name, "Missing self param in method: " + functionName);
                return;
            }
        }
        
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        for (Stmt.Var param : function.params) {
            if (classes.containsKey(param.name.lexeme)) {
                ChocoPy.error(param.name, "Cannot shadow class name: " + param.name.lexeme);
                continue;
            } else if (scopes.peek().containsKey(param.name.lexeme)) {
                ChocoPy.error(param.name, "Duplicate declaration of identifier: " + param.name.lexeme);
                continue;
            }
            if (!isTypeDefined(param.type)) {
                ChocoPy.error(param.name, "Unknown type: " + param.type);
            }
            declare(param.name);
            define(param.name, param.type);
            param.inferredType = param.type;
        }
        resolve(function.body);
        
        boolean hasReturn = false;
        for (Stmt s : function.body) {
            if (s.isReturn) {
                hasReturn = true;
                break;
            }
        }
        if (!hasReturn && !canAssign(new NoneType(), scopes.peek().get("expectedReturnType"))) {
            ChocoPy.error(function.name, "Expected return statement of type " + scopes.peek().get("expectedReturnType"));
        }
        scopes.peek().put("expectedReturnType", null);
        endScope();
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        if (!(stmt.condition.inferredType instanceof BoolType)) {
            ChocoPy.error(stmt.condition.line,"Expected bool, got " + stmt.condition.inferredType);
            return null;
        }
        
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        
        // isReturn=True if there's >=1 statement in BOTH branches that have isReturn=True
        // if a branch is empty, isReturn=False
        stmt.isReturn = stmt.thenBranch.isReturn && (stmt.elseBranch != null && stmt.elseBranch.isReturn);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        
        if (!staticTypes.contains(stmt.expression.inferredType.getClass())) {
            ChocoPy.error(stmt.expression.line, "Expected str, int or bool, got " + stmt.expression.inferredType);
        }
        
        stmt.inferredType = new NoneType();
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            ChocoPy.error(stmt.keyword, "Can't return from top-level code.");
            return null;
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                ChocoPy.error(stmt.keyword,
                        "Can't return a value from an initializer.");
            }

            resolve(stmt.value);

            if (!canAssign(stmt.value.inferredType, scopes.peek().get("expectedReturnType"))) {
                ChocoPy.error(stmt.keyword, String.format("Expected %s, got %s", scopes.peek().get("expectedReturnType"), stmt.value.inferredType));
            }
        } else {
            if (!canAssign(new NoneType(), scopes.peek().get("expectedReturnType"))) {
                ChocoPy.error(stmt.keyword, String.format("Expected %s, got <None>", scopes.peek().get("expectedReturnType")));
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
        String varName = stmt.name.lexeme;
        if (classes.containsKey(varName)) {
            ChocoPy.error(stmt.name, "Cannot shadow class name: " + varName);
        } else if (scopes.peek().containsKey(varName)) {
            ChocoPy.error(stmt.name, "Duplicate declaration of identifier: " + varName);
        }
        if (!isTypeDefined(stmt.type)) {
            ChocoPy.error(stmt.name, "Unknown type: " + stmt.type);
        }
        
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);

            if (!canAssign(stmt.initializer.inferredType, stmt.type)) {
                ChocoPy.error(stmt.name, String.format("Expected %s, got %s",
                        stmt.type, stmt.initializer.inferredType));
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
        resolve(stmt.condition);
        resolve(stmt.body);
        
        if (!(stmt.condition.inferredType instanceof BoolType)) {
            ChocoPy.error(stmt.condition.line, "Expected bool, got " + stmt.condition.inferredType);
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
        resolve(stmt.identifier);
        resolve(stmt.iterable);
        resolve(stmt.body);
        
        ValueType iterableType = stmt.iterable.inferredType;
        if (iterableType instanceof ListValueType) {
            ValueType elementType = ((ListValueType) iterableType).getElementType();
            if (!canAssign(elementType, stmt.identifier.inferredType)) {
                ChocoPy.error(stmt.identifier.line, String.format("Expected %s, got %s", elementType, stmt.identifier.inferredType));
                return null;
            }
        } else if (iterableType instanceof StrType) {
            if (!canAssign(iterableType, stmt.identifier.inferredType)) {
                ChocoPy.error(stmt.identifier.line, String.format("Expected str, got %s", stmt.identifier.inferredType));
                return null;
            }
        } else {
            ChocoPy.error(stmt.identifier.line, String.format("Expected iterable, got %s", stmt.iterable.inferredType));
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
            ChocoPy.error(stmt.name, "Global declaration is outside of function");
            return null;
        }
        if (classes.containsKey(stmt.name.lexeme)) {
            ChocoPy.error(stmt.name, "Cannot shadow class name: " + stmt.name.lexeme);
            return null;
        } else if (scopes.peek().containsKey(stmt.name.lexeme)) {
            ChocoPy.error(stmt.name, "Duplicate declaration of identifier: " + stmt.name.lexeme);
            return null;
        }
        
        ValueType type = getGlobalType(stmt.name.lexeme);
        if (type == null || type instanceof FuncType) {
            ChocoPy.error(stmt.name, "Unknown global variable " + stmt.name.lexeme);
            return null;
        }

        declare(stmt.name);
        define(stmt.name, type);
        interpreter.resolve(new Expr.Variable(stmt.name), scopes.size() - 1);
        return null;
    }

    @Override
    public Void visitNonlocalStmt(Stmt.Nonlocal stmt) {
        if (currentFunction == FunctionType.NONE) {
            ChocoPy.error(stmt.name, "Nonlocal declaration is outside of function");
            return null;
        }
        if (classes.containsKey(stmt.name.lexeme)) {
            ChocoPy.error(stmt.name, "Cannot shadow class name: " + stmt.name.lexeme);
            return null;
        } else if (scopes.peek().containsKey(stmt.name.lexeme)) {
            ChocoPy.error(stmt.name, "Duplicate declaration of identifier: " + stmt.name.lexeme);
            return null;
        }
        
        ValueType type = getNonLocalType(stmt.name.lexeme);
        if (type == null || type instanceof FuncType) {
            ChocoPy.error(stmt.name, "Unknown nonlocal variable " + stmt.name.lexeme);
            return null;
        }

        declare(stmt.name);
        define(stmt.name, type);
        resolveLocal(new Expr.Variable(stmt.name), stmt.name);
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