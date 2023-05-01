package org.chocopy;

import java.util.*;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    final Environment globals = new Environment();
    private Environment environment = globals;

    Interpreter() {
        globals.define("print", new ChocoPyCallable() {
            @Override
            public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                System.out.println(stringify(arguments.get(0)));
                return null;
            }

            @Override
            public String toString() { return "native print"; }
        });
        globals.define("input", new ChocoPyCallable() {
            
            private java.util.Scanner scanner = new java.util.Scanner(System.in);
            
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                if (scanner.hasNextLine()) {
                    return scanner.nextLine() + "\n";
                } else {
                    return "";
                }
            }

            @Override
            public String toString() { return "native input"; }
        });
        globals.define("len", new ChocoPyCallable() {
            @Override
            public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                Object arg = arguments.get(0);
                if (arg instanceof String) {
                    return arg.toString().length();
                } else if (arg instanceof List) {
                    return ((List<?>) arg).size();
                } else  {
                    throw new RuntimeException();
                }
            }

            @Override
            public String toString() { return "native len"; }
        });

        Stmt.Function objectInitFunction = new Stmt.Function(
                new Token(TokenType.IDSTRING, "__init__", null, -1),
                Collections.emptyList(),
                new NoneType(),
                Collections.singletonList(new Stmt.Pass(new Token(TokenType.PASS, "pass", null, -1)))
        );
        ChocoPyFunction objectConstructor = new ChocoPyFunction(objectInitFunction, environment, true);
        ChocoPyClass objectClass = new ChocoPyClass("object", null, Collections.singletonMap("__init__", objectConstructor), Collections.emptyMap());
        globals.define("object", objectClass);

        Stmt.Function intInitFunction = new Stmt.Function(
                new Token(TokenType.IDSTRING, "__init__", null, -1),
                Collections.emptyList(),
                new NoneType(),
                Collections.singletonList(new Stmt.Return(new Token(TokenType.RETURN, "return", null, -1), new Expr.Literal(0)))
        );
        ChocoPyFunction intConstructor = new ChocoPyFunction(intInitFunction, environment, true);
        ChocoPyClass intClass = new ChocoPyClass("int", objectClass, Collections.singletonMap("__init__", intConstructor), Collections.emptyMap());
        globals.define("int", intClass);

        Stmt.Function boolInitFunction = new Stmt.Function(
                new Token(TokenType.IDSTRING, "__init__", null, -1),
                Collections.emptyList(),
                new NoneType(),
                Collections.singletonList(new Stmt.Return(new Token(TokenType.RETURN, "return", null, -1), new Expr.Literal(false)))
        );
        ChocoPyFunction boolConstructor = new ChocoPyFunction(boolInitFunction, environment, true);
        ChocoPyClass boolClass = new ChocoPyClass("bool", objectClass, Collections.singletonMap("__init__", boolConstructor), Collections.emptyMap());
        globals.define("bool", boolClass);

        Stmt.Function strInitFunction = new Stmt.Function(
                new Token(TokenType.IDSTRING, "__init__", null, -1),
                Collections.emptyList(),
                new NoneType(),
                Collections.singletonList(new Stmt.Return(new Token(TokenType.RETURN, "return", null, -1), new Expr.Literal("")))
        );
        ChocoPyFunction strConstructor = new ChocoPyFunction(strInitFunction, environment, true);
        ChocoPyClass strClass = new ChocoPyClass("str", objectClass, Collections.singletonMap("__init__", strConstructor), Collections.emptyMap());
        globals.define("str", strClass);
        
//        globals.define("<None>", new ClassInfo("<None>", "object"));
//        globals.define("<Empty>", new ClassInfo("<Empty>", "object"));
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        throw new RuntimeError(expr.line, "use Expr.MultiAssign", "NotImplementedError");
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
            case IS: return isEqual(left, right);
            case GREATER:
                checkNumberOperands(expr, left, right);
                return (int)left > (int)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr, left, right);
                return (int)left >= (int)right;
            case LESS:
                checkNumberOperands(expr, left, right);
                return (int)left < (int)right;
            case LESS_EQUAL:
                checkNumberOperands(expr, left, right);
                return (int)left <= (int)right;
            case MINUS:
                checkNumberOperands(expr, left, right);
                return (int)left - (int)right;
            case PLUS:
                if (left == null || right == null) {
                    ChocoPy.exitCode = 4;
                    throw new RuntimeError(expr.operator, 
                            String.format("unsupported operand type(s) for +: '%s' and '%s'", expr.left.inferredType, expr.right.inferredType), 
                            "TypeError");
                }
                
                if (left instanceof Integer && right instanceof Integer) {
                    return (int)left + (int)right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }

                if (left instanceof List && right instanceof List) {
                    ((List)left).addAll((List)right);
                    return left;
                }

                throw new RuntimeError(expr.operator,
                        String.format("unsupported operand type(s) for %s: '%s' and '%s'", expr.operator.lexeme, expr.left.inferredType, expr.right.inferredType),
                        "TypeError");
                
            case DOUBLE_SLASH:
                checkNumberOperands(expr, left, right);
                try {
                    return (int)left / (int)right;
                } catch (ArithmeticException e) {
                    ChocoPy.exitCode = 2;
                    throw new RuntimeError(expr.operator, "division by zero", "ZeroDivisionError");
                }
            case PERCENT:
                checkNumberOperands(expr, left, right);
                try {
                    return (int)left % (int)right;
                } catch (ArithmeticException e) {
                    ChocoPy.exitCode = 2;
                    throw new RuntimeError(expr.operator, "integer modulo by zero", "ZeroDivisionError");
                }
            case STAR:
                checkNumberOperands(expr, left, right);
                return (int)left * (int)right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof ChocoPyCallable)) {
            throw new RuntimeError(expr.paren,
                    String.format("'%s' object is not callable", callee.toString()), "TypeError");
        }

        ChocoPyCallable function = (ChocoPyCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, 
                    String.format("expected %d arguments, but got %d", function.arity(), arguments.size()),
                    "TypeError");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        
        if (object == null) {
            ChocoPy.exitCode = 4;
            throw new RuntimeError(expr.name, String.format("'<None>' object has no attribute '%s'", expr.name.lexeme),
                    "AttributeError");
        } else if (object instanceof ChocoPyInstance) {
            return ((ChocoPyInstance) object).get(expr.name);
        } else {
            throw new RuntimeError(expr.name, String.format("'%s' object has no attribute '%s'", object, expr.name.lexeme), 
                    "AttributeError");
        }
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitTernaryExpr(Expr.Ternary expr) {
        if (isTruthy(evaluate(expr.condition))) {
            return evaluate(expr.onTrue);
        } else {
            return evaluate(expr.onFalse);
        }
    }

    @Override
    public Object visitListingExpr(Expr.Listing expr) {
        if (expr.elements == null) {
            ChocoPy.exitCode = 4;
            throw new RuntimeError(expr.line, "operation on '<None>'", "RuntimeError"); 
        }
        
        List list = new ArrayList();
        for (Expr element : expr.elements) {
            list.add(evaluate(element));
        }
        
        return list;
    }

    @Override
    public Object visitIndexExpr(Expr.Index expr) {
        Object listObject = evaluate(expr.listing);
        Object idObject = evaluate(expr.id);
        
        if (listObject == null || idObject == null) {
            ChocoPy.exitCode = 4;
            throw new RuntimeError(expr.line, "'<None>' object is not subscriptable", "TypeError");
        } else if (!(idObject instanceof Integer)) {
            ChocoPy.exitCode = 4;
            throw new RuntimeError(expr.line, "list indices must be 'int', not '<None>'", "TypeError");
        }

        Integer id = (Integer) idObject;
        if (listObject instanceof List) {
            List list = (List) listObject;
            if (list.isEmpty() || id < 0 || id >= list.size()) {
                ChocoPy.exitCode = 3;
                throw new RuntimeError(expr.line, "list index out of range", "IndexError");
            } else {
                return list.get(id);
            }
        } else if (listObject instanceof String) {
            String str = (String) listObject;
            if (str.isEmpty() || id < 0 || id >= str.length()) {
                ChocoPy.exitCode = 3;
                throw new RuntimeError(expr.line, "string index out of range", "IndexError");
            } else {
                return Character.toString(str.charAt(id));
            }
        } else {
            throw new RuntimeError(expr.line, "expected type 'str' or 'list'", "TypeError");
        }
    }

    @Override
    public Object visitListSetExpr(Expr.ListSet expr) {
        throw new RuntimeError(expr.line, "use Expr.MultiAssign", "NotImplementedError");
    }

    @Override
    public Object visitLenExpr(Expr.Len expr) {
        Object object = evaluate(expr.expression);
        
        if (object == null) {
            ChocoPy.exitCode = 1;
            throw new RuntimeError(expr.line, "'<None>' object has no len()", "TypeError");
        }

        Expr.Call lenCall = new Expr.Call(
                new Expr.Variable(new Token(TokenType.LEN_NATIVE_FUN, "len", null, expr.line)),
                new Token(TokenType.RIGHT_PAREN, ")", null, expr.line),
                Collections.singletonList(expr.expression)
        );
        return visitCallExpr(lenCall);
    }

    @Override
    public Object visitInputExpr(Expr.Input expr) {
        Expr.Call inputCall = new Expr.Call(
                new Expr.Variable(new Token(TokenType.INPUT_NATIVE_FUN, "input", null, expr.line)),
                new Token(TokenType.RIGHT_PAREN, ")", null, expr.line),
                Collections.emptyList()
        );
        return visitCallExpr(inputCall);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        throw new RuntimeError(expr.line, "use Expr.MultiAssign", "NotImplementedError");
    }

    @Override
    public Object visitSelfExpr(Expr.Self expr) {
        return lookUpVariable(expr.keyword);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        if (expr.operator.type == TokenType.MINUS) {
            checkNumberOperand(expr.operator, right);
            return -(int)right;
        } else if (expr.operator.type == TokenType.NOT) {
            checkBooleanOperand(expr.operator, right);
            return !(boolean) right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Integer) return;
        throw new RuntimeError(operator, 
                String.format("unsupported operand type for %s: '%s'", operator.lexeme, operand.toString()), 
                "TypeError");
    }

    private void checkBooleanOperand(Token operator, Object operand) {
        if (operand instanceof Boolean) return;
        throw new RuntimeError(operator,
                String.format("unsupported operand type for %s: '%s'", operator.lexeme, operand.toString()),
                "TypeError");
    }

    private void checkNumberOperands(Expr.Binary expr,
                                     Object left, Object right) {
        if (left instanceof Integer && right instanceof Integer) return;

        throw new RuntimeError(expr.operator,
                String.format("unsupported operand type(s) for %s: '%s' and '%s'", expr.operator.lexeme, expr.left.inferredType, expr.right.inferredType),
                "TypeError");
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            ChocoPy.runtimeError(error);
            ChocoPy.runtimeErrors.add(String.format("Exited with error code %d", ChocoPy.exitCode));
        } catch (Throwable e) {
            try {
                String msg = (e.getMessage() != null) ? e.getMessage() : e.toString();
                throw new RuntimeError(0, msg, "RuntimeError");
            } catch (RuntimeError error) {
                ChocoPy.exitCode = 70;
                ChocoPy.runtimeError(error);
                ChocoPy.runtimeErrors.add(String.format("Exited with error code %d", ChocoPy.exitCode));
            }
        }
    }

    private String stringify(Object object) {
        if (object == null) return "None";

        if (object instanceof Boolean) {
            String text = object.toString();
            return text.substring(0, 1).toUpperCase() + text.substring(1);
        }

        return object.toString();
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        for (Stmt statement : stmt.statements) {
            execute(statement);
        }
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(new Expr.Variable(stmt.superclass));
            if (!(superclass instanceof ChocoPyClass)) {
                throw new RuntimeError(stmt.superclass,
                        "superclass must be a class", "TypeError"); 
            }
        }

        environment.define(stmt.name.lexeme, null);
        environment = new Environment(environment);

        Map<String, ChocoPyFunction> methods = new HashMap<>();
        Map<String, ChocoPyAttribute> attributes = new HashMap<>();
        for (Stmt member : stmt.members) {
            if (member instanceof Stmt.Function) {
                Stmt.Function stmtFun = (Stmt.Function) member;
                ChocoPyFunction function = new ChocoPyFunction(stmtFun, environment,
                        stmtFun.name.lexeme.equals("__init__"));
                methods.put(stmtFun.name.lexeme, function);
            } else if (member instanceof Stmt.Var) {
                Stmt.Var stmtVar = (Stmt.Var) member;
                Object value = evaluate(stmtVar.initializer);
                ChocoPyAttribute attr = new ChocoPyAttribute(value);
                attributes.put(stmtVar.name.lexeme, attr);
            }
        }

        ChocoPyClass klass = new ChocoPyClass(stmt.name.lexeme, (ChocoPyClass)superclass, methods, attributes);
        environment.define("self", klass);
        environment = environment.enclosing;

        environment.define(stmt.name.lexeme, klass);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        ChocoPyFunction function = new ChocoPyFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintExpr(Expr.Print expr) {
        Expr.Call printCall = new Expr.Call(
                new Expr.Variable(new Token(TokenType.PRINT_NATIVE_FUN, "print", null, expr.line)),
                new Token(TokenType.RIGHT_PAREN, ")", null, expr.line),
                Collections.singletonList(expr.expression)
        );
        visitCallExpr(printCall);
        return null;
    }

    @Override
    public Object visitMultiAssignExpr(Expr.MultiAssign multiAssign) {
        Object value = evaluate(multiAssign.value);

        for (Expr target : multiAssign.targets) {
            if (target instanceof Expr.Assign expr) {
                Token name = expr.target.name;
                updateVariable(name, value);
            } 
            else if (target instanceof Expr.Set expr) {
                Object object = evaluate(expr.object);

                if (object == null) {
                    ChocoPy.exitCode = 4;
                    throw new RuntimeError(expr.line,
                            String.format("'<None>' object has no attribute '%s'", expr.name.lexeme),
                            "AttributeError");
                } else if (!(object instanceof ChocoPyInstance)) {
                    throw new RuntimeError(expr.name,
                            String.format("'%s' object has no attribute '%s'", expr.object.inferredType.toString(), expr.name.lexeme),
                            "AttributeError");
                }

                ((ChocoPyInstance)object).set(expr.name, value);
            } 
            else if (target instanceof Expr.ListSet expr) {
                Object idObject = evaluate(expr.id);
                Object listObject = evaluate(expr.listing);

                if (listObject == null || idObject == null) {
                    ChocoPy.exitCode = 4;
                    throw new RuntimeError(expr.line, "'<None>' object is not subscriptable", "TypeError");
                } else if (!(idObject instanceof Integer)) {
                    ChocoPy.exitCode = 4;
                    throw new RuntimeError(expr.line, "list indices must be 'int', not '<None>'", "TypeError");
                }

                List list = (List) listObject;
                Integer id = (Integer) idObject;

                if (list.isEmpty() || id < 0 || id >= list.size()) {
                    ChocoPy.exitCode = 3;
                    throw new RuntimeError(expr.line, "list index out of range", "IndexError");
                } else {
                    return list.set(id, value);
                }
            }
        }
        
        return value;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        Object iterableObject = evaluate(stmt.iterable);
        
        if (iterableObject == null) {
            ChocoPy.exitCode = 4;
            throw new RuntimeError(stmt.line, "'<None>' object is not iterable", "TypeError");
        }
        
        if (iterableObject instanceof List) {
            List list = (List) iterableObject;
            for (Object value : list) {
                updateVariable(stmt.identifier.name, value);
                stmt.body.accept(this);
            }
        } else if (iterableObject instanceof String) {
            String str = (String) iterableObject;
            for (int i = 0; i < str.length(); i++) {
                updateVariable(stmt.identifier.name, String.valueOf(str.charAt(i)));
                stmt.body.accept(this);
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
        this.environment.define(stmt.name.lexeme, Environment.Scope.GLOBAL);
        return null;
    }

    @Override
    public Void visitNonlocalStmt(Stmt.Nonlocal stmt) {
        this.environment.define(stmt.name.lexeme, Environment.Scope.NONLOCAL);
        return null;
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements,
                      Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    private Object lookUpVariable(Token name) {
        Environment environment = this.environment;
        
        while (environment != null) {
            if (environment.values.containsKey(name.lexeme)) {
                Object obj = environment.values.get(name.lexeme);
                if (obj instanceof Environment.Scope) {
                    Environment.Scope scope = (Environment.Scope) obj;
                    if (scope == Environment.Scope.GLOBAL) {
                        return globals.get(name);
                    } else if (scope == Environment.Scope.NONLOCAL) {
                        environment = environment.enclosing;
                    }
                } else {
                    return obj;
                }
            } else {
                environment = environment.enclosing;
            }
        }
        
        return null;
    }
    
    private void updateVariable(Token name, Object value) {
        Environment environment = this.environment;
        while (environment != null) {
            if (environment.values.containsKey(name.lexeme)) {
                Object obj = environment.values.get(name.lexeme);
                if (obj instanceof Environment.Scope) {
                    Environment.Scope scope = (Environment.Scope) obj;
                    if (scope == Environment.Scope.GLOBAL) {
                        globals.values.put(name.lexeme, value);
                        return;
                    } else if (scope == Environment.Scope.NONLOCAL) {
                        environment = environment.enclosing;
                    }
                } else {
                    environment.values.put(name.lexeme, value);
                    return;
                }
            } else {
                environment = environment.enclosing;
            }
        }
    }
}
