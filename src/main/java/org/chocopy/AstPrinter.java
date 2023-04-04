package org.chocopy;

import java.util.List;

class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {

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
        return parenthesize2("=", expr.name.lexeme, expr.value);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme,
                expr.left, expr.right);
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        return parenthesize2("call", expr.callee, expr.arguments);
    }

    @Override
    public String visitGetExpr(Expr.Get expr) {
        return parenthesize2(".", expr.object, expr.name.lexeme);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        return null;
    }

    @Override
    public String visitListingExpr(Expr.Listing expr) {
        return null;
    }

    @Override
    public String visitIndexExpr(Expr.Index expr) {
        return null;
    }

    @Override
    public String visitListSetExpr(Expr.ListSet expr) {
        return null;
    }

    @Override
    public String visitLenExpr(Expr.Len expr) {
        return null;
    }

    @Override
    public String visitSetExpr(Expr.Set expr) {
        return parenthesize2("=",
                expr.object, expr.name.lexeme, expr.value);
    }

    @Override
    public String visitSuperExpr(Expr.Super expr) {
        return parenthesize2("super", expr.method);
    }

    @Override
    public String visitThisExpr(Expr.This expr) {
        return "this";
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    private String parenthesize(String name, Expr... exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);
        for (Expr expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }

    private String parenthesize2(String name, Object... parts) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);
        transform(builder, parts);
        builder.append(")");

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
        builder.append("(block ");

        for (Stmt statement : stmt.statements) {
            builder.append(statement.accept(this));
        }

        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitClassStmt(Stmt.Class stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append("(class " + stmt.name.lexeme);

        if (stmt.superclass != null) {
            builder.append("( " + stmt.superclass.lexeme + " )");
        }

        for (Stmt member : stmt.members) {
            builder.append(" " + print(member));
        }

        builder.append(")");
        return builder.toString();
    }

    String print(Stmt stmt) {
        return stmt.accept(this);
    }

    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        return parenthesize(";", stmt.expression);
    }

    @Override
    public String visitFunctionStmt(Stmt.Function stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append("(fun " + stmt.name.lexeme + "(");

        for (Stmt.Var param : stmt.params) {
            if (param != stmt.params.get(0)) builder.append(" ");
            builder.append(print(param));
        }

        builder.append(") ");

        for (Stmt body : stmt.body) {
            builder.append(body.accept(this));
        }

        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitIfStmt(Stmt.If stmt) {
        if (stmt.elseBranch == null) {
            return parenthesize2("if", stmt.condition, stmt.thenBranch);
        }

        return parenthesize2("if-else", stmt.condition, stmt.thenBranch,
                stmt.elseBranch);
    }

    @Override
    public String visitPrintStmt(Stmt.Print stmt) {
        return parenthesize("print", stmt.expression);
    }

    @Override
    public String visitInputStmt(Stmt.Input stmt) {
        return null;
    }

    @Override
    public String visitReturnStmt(Stmt.Return stmt) {
        if (stmt.value == null) return "(return)";
        return parenthesize("return", stmt.value);
    }

    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        if (stmt.initializer == null) {
            return parenthesize2("var", stmt.name);
        }

        return parenthesize2("var", stmt.name, "=", stmt.initializer);
    }

    @Override
    public String visitWhileStmt(Stmt.While stmt) {
        return parenthesize2("while", stmt.condition, stmt.body);
    }

    @Override
    public String visitPassStmt(Stmt.Pass stmt) {
        return null;
    }

    @Override
    public String visitGlobalStmt(Stmt.Global stmt) {
        return null;
    }

    @Override
    public String visitNonlocalStmt(Stmt.Nonlocal stmt) {
        return null;
    }
}
