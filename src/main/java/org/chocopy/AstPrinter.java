package org.chocopy;

import java.util.List;

class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {

    private final String TAB = "  ";
    private int level = 0;

    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
                new Expr.Unary(
                        new Token(TokenType.MINUS, "-", null, 1),
                        new Expr.Literal(123)),
                new Token(TokenType.STAR, "*", null, 1),
                new Expr.Grouping(
                        new Expr.Literal(45.67)));

        System.out.println(new AstPrinter().print(expression));
    }

    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Assign");
        lineSeparate(builder, "target", expr.target.accept(this));
        lineSeparate(builder, "value", expr.value.accept(this));
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Binary");
        lineInferredType(builder, expr);
        lineSeparate(builder, "left", expr.left.accept(this));
        lineQuotes(builder, "operator", expr.operator.lexeme);
        lineSeparate(builder, "right", expr.right.accept(this));
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Call");
        lineInferredType(builder, expr);
        lineSeparate(builder, "callee", expr.callee.accept(this));
        if (expr.arguments.isEmpty()) {
            line(builder, "arguments", "null");
        } else {
            builder.append(tabs()); builder.append("arguments:\n");
            level += 1;
            for (Expr argument : expr.arguments) {
                builder.append(tabs()); builder.append("- argument:\n");
                level += 1;
                builder.append(argument.accept(this)); builder.append("\n");
                level -= 1;
            }
            level -= 1;
        }
        level -= 1;
        
        return builder.toString();
    }

    @Override
    public String visitGetExpr(Expr.Get expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Get");
        lineInferredType(builder, expr);
        lineSeparate(builder, "object", expr.object.accept(this));
        line(builder, "name", expr.name.lexeme);
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Grouping");
        lineSeparate(builder, "expr", expr.expression.accept(this));
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        StringBuilder builder = new StringBuilder();
        
        level += 1;
        line(builder, "class", "Expr.Literal");
        lineInferredType(builder, expr);
        String value;
        if (expr.value == null) {
            value = "null";
        } else if (expr.value instanceof String) {
            value = "\"" + expr.value + "\"";
        } else {
            value = expr.value.toString();
        }
        line(builder, "value", value);
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Logical");
        lineInferredType(builder, expr);
        lineSeparate(builder, "left", expr.left.accept(this));
        lineQuotes(builder, "operator", expr.operator.lexeme);
        lineSeparate(builder, "right", expr.right.accept(this));
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Ternary");
        lineInferredType(builder, expr);
        lineSeparate(builder, "onTrue", expr.onTrue.accept((this)));
        lineSeparate(builder, "condition", expr.condition.accept((this)));
        lineSeparate(builder, "onFalse", expr.onFalse.accept((this)));
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitListingExpr(Expr.Listing expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Listing");
        lineInferredType(builder, expr);
        if (expr.elements.isEmpty()) {
            line(builder, "elements", "null");
        } else {
            builder.append(tabs()); builder.append("elements:\n");
            level += 1;
            for(int i = 0; i < expr.elements.size(); i++) {
                Expr element = expr.elements.get(i);
                builder.append(tabs()); builder.append("- element:\n");
                level += 1;
                builder.append(element.accept(this));
                level -= 1;
                builder.append("\n");
            }
            level -= 1;
        }
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitIndexExpr(Expr.Index expr) {
        StringBuilder builder = new StringBuilder();
        
        level += 1;
        line(builder, "class", "Expr.Index");
        lineInferredType(builder, expr);
        lineSeparate(builder, "list", expr.listing.accept(this));
        lineSeparate(builder, "index", expr.id.accept(this));
        level -= 1;
        
        return builder.toString();
    }

    @Override
    public String visitListSetExpr(Expr.ListSet expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.ListSet");
        lineInferredType(builder, expr);
        lineSeparate(builder, "list", expr.listing.accept(this));
        lineSeparate(builder, "index", expr.id.accept(this));
        lineSeparate(builder, "value", expr.value.accept(this));
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitLenExpr(Expr.Len expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Len");
        lineSeparate(builder, "expr", expr.expression.accept(this));
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitInputExpr(Expr.Input expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Input");
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitSetExpr(Expr.Set expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Set");
        lineSeparate(builder, "object", expr.object.accept(this));
        line(builder, "name", expr.name.lexeme);
        lineSeparate(builder, "value", expr.value.accept(this));
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitSelfExpr(Expr.Self expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "name", "self");
        lineInferredType(builder, expr);
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Unary");
        lineInferredType(builder, expr);
        lineQuotes(builder, "operator", expr.operator.lexeme);
        lineSeparate(builder, "operand", expr.right.accept(this)); 
        level -= 1;
        
        return builder.toString();
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        line(builder, "class", "Expr.Variable");
        lineInferredType(builder, expr);
        line(builder, "name", expr.name.lexeme);
        level -= 1;
        
        return builder.toString();
    }

    private void transform(StringBuilder builder, Object... parts) {
        for (Object part : parts) {
            builder.append(" ");
            if (part instanceof Expr) {
                builder.append(((Expr)part).accept(this));
//> Statements and State omit
            } else if (part instanceof Stmt) {
                builder.append(((Stmt) part).accept(this));
//< Statements and State omit
            } else if (part instanceof Token) {
                builder.append(((Token) part).lexeme);
            } else if (part instanceof List) {
                transform(builder, ((List) part).toArray());
            } else {
                builder.append(part);
            }
        }
    }

    @Override
    public String visitBlockStmt(Stmt.Block stmt) {
        StringBuilder builder = new StringBuilder();

        level += 1;
        for (Stmt statement : stmt.statements) {
            builder.append(print(statement));
//            builder.append(tabs()); builder.append(statement.accept(this)); builder.append("\n");
        }
        level -= 1;

        return builder.toString();
    }

    @Override
    public String visitClassStmt(Stmt.Class stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.Class");
        line(builder, "name", stmt.name.lexeme);
        line(builder, "superClass", stmt.superclass.lexeme);
        
        if (stmt.members.isEmpty()) {
            line(builder, "members", "null");
        } else {
            builder.append(tabs()); builder.append("members:\n");
            for (Stmt member : stmt.members) {
                level += 1;
                lineSeparate(builder, "- member", member.accept(this));
                level -= 1;
            }
        }
        level -= 2;
        
        return builder.toString();
    }
    
    String print(List<Stmt> statements) {
        level = 0;
        StringBuilder output = new StringBuilder();
        for (Stmt stmt : statements) {
            output.append(print(stmt));
        }
        return output.toString();
    }

    String print(Stmt stmt) {
        StringBuilder builder = new StringBuilder();
        
        if (level == 0) {
            builder.append(tabs()); builder.append("statements:\n");
            level += 1;
        }

//        level += 2;
        if (stmt instanceof Stmt.Block) {
            for (Stmt statement : ((Stmt.Block) stmt).statements) {
                builder.append(print(statement));
            }
        } else {
            lineSeparate(builder, "- statement", stmt.accept(this));
        }

        return builder.toString();
    }

    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.Expression");
        lineSeparate(builder, "expr", stmt.expression.accept(this));
        level -= 2;

        return builder.toString();
    }

    @Override
    public String visitFunctionStmt(Stmt.Function stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.Function");
        line(builder, "name", stmt.name.lexeme);
        
        if (stmt.params.isEmpty()) {
            line(builder, "params", "null");
        } else {
            builder.append(tabs()); builder.append("params:\n");
            level += 1;
            for (Stmt.Var param : stmt.params) {
                lineSeparate(builder, "- param", param.accept(this));
            }
            level -= 1;
        }

        if (stmt.returnType == null) {
            line(builder, "returnType", "<None>");
        } else {
            line(builder, "returnType", stmt.returnType.toString());
        }

        builder.append(tabs()); builder.append("body:\n");
        level += 1;
        for (Stmt body : stmt.body) {
            builder.append(print(body));
        }
        level -= 1;
        level -= 2;

        return builder.toString();
    }

    @Override
    public String visitIfStmt(Stmt.If stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.If");
        lineSeparate(builder, "condition", stmt.condition.accept(this));
        lineSeparate(builder, "thenBranch", stmt.thenBranch.accept(this));
        if (stmt.elseBranch == null) {
            line(builder, "elseBranch", "null");
        } else if (stmt.elseBranch instanceof Stmt.Block) {
            lineSeparate(builder, "elseBranch", stmt.elseBranch.accept(this));
        } else {
            builder.append(tabs()); builder.append("elseBranch:\n");
            level += 1;
            builder.append(print(stmt.elseBranch));
            level -= 1;
        }
        level -= 2;

        return builder.toString();
    }

    @Override
    public String visitPrintStmt(Stmt.Print stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder,"class", "Stmt.Print");
        lineSeparate(builder, "expr", stmt.expression.accept(this));
        level -= 2;

        return builder.toString();
    }

    @Override
    public String visitReturnStmt(Stmt.Return stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.Return");
        if (stmt.value == null) {
            line(builder, "value", "null");
        } else {
            lineSeparate(builder, "value", stmt.value.accept(this));
        }
        level -= 2;
        
        return builder.toString();
    }

    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        StringBuilder builder = new StringBuilder();
        
        level += 2;
        line(builder, "class", "Stmt.Var");
        line(builder, "name", stmt.name.lexeme);
        line(builder, "type", stmt.type.toString());
        if (stmt.initializer == null) {
            line(builder, "initializer", "null");
        } else {
            lineSeparate(builder, "initializer", stmt.initializer.accept(this));
        }
        level -= 2;
        
        return builder.toString();
    }

    @Override
    public String visitWhileStmt(Stmt.While stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.While");
        lineSeparate(builder, "condition", stmt.condition.accept(this));
        lineSeparate(builder, "body", stmt.body.accept(this));
        level -= 2;

        return builder.toString();
    }

    @Override
    public String visitForStmt(Stmt.For stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.For");
        lineSeparate(builder, "identifier", stmt.identifier.accept(this));
        lineSeparate(builder, "iterable", stmt.iterable.accept(this));
        lineSeparate(builder, "body", stmt.body.accept(this));
        level -= 2;

        return builder.toString();
    }

    @Override
    public String visitPassStmt(Stmt.Pass stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.Pass");
        level -= 2;

        return builder.toString();
    }

    @Override
    public String visitGlobalStmt(Stmt.Global stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.Global");
        line(builder, "name", stmt.name.lexeme);
        level -= 2;

        return builder.toString();
    }

    @Override
    public String visitNonlocalStmt(Stmt.Nonlocal stmt) {
        StringBuilder builder = new StringBuilder();

        level += 2;
        line(builder, "class", "Stmt.Nonlocal");
        line(builder, "name", stmt.name.lexeme);
        level -= 2;

        return builder.toString();
    }
    
    private String tabs() {
        return TAB.repeat(level);
    }
    
    private void lineInferredType(StringBuilder builder, Expr expr) {
        if (expr.inferredType != null) {
            line(builder, "inferredType", expr.inferredType.toString());
        }
    }
    
    private void line(StringBuilder builder, String key, String value) {
        _line(builder, key, ": ", value, false);
    }
    
    private void lineQuotes(StringBuilder builder, String key, String value) {
        _line(builder, key, ": ", value, true);
    }
    
    private void lineSeparate(StringBuilder builder, String key, String value) {
        _line(builder, key, ":\n", value, false);
    }
    
    private void _line(StringBuilder builder, String key, String sep, String value, boolean quotes) {
        builder.append(tabs());
        builder.append(key);
        builder.append(sep);
        if (quotes) {
            builder.append("\"").append(value).append("\"");
        } else {
            builder.append(value);
        }
        builder.append("\n");
    }
}
