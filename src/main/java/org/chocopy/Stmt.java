package org.chocopy;

import java.util.List;

abstract class Stmt {
    protected ValueType inferredType;
    protected int line;
    protected boolean isReturn;
    protected int resolverStage;
    
    interface Visitor<R> {
        R visitBlockStmt(Block stmt);
        R visitClassStmt(Class stmt);
        R visitExpressionStmt(Expression stmt);
        R visitFunctionStmt(Function stmt);
        R visitIfStmt(If stmt);
        R visitReturnStmt(Return stmt);
        R visitVarStmt(Var stmt);
        R visitWhileStmt(While stmt);
        R visitForStmt(For stmt);
        R visitPassStmt(Pass stmt);
        R visitGlobalStmt(Global stmt);
        R visitNonlocalStmt(Nonlocal stmt);
    }

    static class Block extends Stmt {
        Block(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }

        final List<Stmt> statements;
    }

    static class Class extends Stmt {
        Class(Token name, Token superclass, List<Stmt> members) {
            this.name = name;
            this.superclass = superclass;
            this.members = members;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitClassStmt(this);
        }

        final Token name;
        final Token superclass;
        final List<Stmt> members;
    }

    static class Expression extends Stmt {
        Expression(Expr expression) {
            this.expression = expression;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }

        final Expr expression;
    }

    static class Function extends Stmt {
        Function(Token name, List<Stmt.Var> params, ValueType returnType, List<Stmt> body) {
            this.name = name;
            this.params = params;
            this.returnType = returnType;
            this.body = body;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionStmt(this);
        }

        final Token name;
        final List<Stmt.Var> params;
        final ValueType returnType;
        final List<Stmt> body;
        FuncType signature;
    }

    static class If extends Stmt  {
        If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }

        final Expr condition;
        final Stmt thenBranch;
        final Stmt elseBranch;
    }

    static class Return extends Stmt {
        Return(Token keyword, Expr value) {
            this.keyword = keyword;
            this.value = value;
            isReturn = true;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitReturnStmt(this);
        }

        final Token keyword;
        final Expr value;
        ValueType expectedType;
    }

    static class Var extends Stmt {
        Var(Token name, ValueType type, Expr initializer) {
            this.name = name;
            this.type = type;
            this.initializer = initializer;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStmt(this);
        }

        final Token name;
        final ValueType type;
        Expr initializer;

        public void setInitializer(Expr initializer) {
            this.initializer = initializer;
        }
    }

    static class While extends Stmt {
        While(Expr condition, Stmt body) {
            this.condition = condition;
            this.body = body;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStmt(this);
        }

        final Expr condition;
        final Stmt body;
    }

    static class For extends Stmt {
        For(Expr.Variable id, Expr iterable, Stmt body) {
            this.id = id;
            this.iterable = iterable;
            this.body = body;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitForStmt(this);
        }

        final Expr.Variable id;
        final Expr iterable;
        final Stmt body;
    }

    static class Pass extends Stmt {
        Pass(Token keyword) {
            this.keyword = keyword;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitPassStmt(this);
        }

        final Token keyword;
    }

    static class Global extends Stmt {
        Global(Token name) {
            this.name = name;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitGlobalStmt(this);
        }

        final Token name;
    }

    static class Nonlocal extends Stmt {
        Nonlocal(Token name) {
            this.name = name;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitNonlocalStmt(this);
        }

        final Token name;
    }

    abstract <R> R accept(Visitor<R> visitor);
}
