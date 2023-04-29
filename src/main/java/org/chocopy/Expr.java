package org.chocopy;

import java.util.List;

abstract class Expr {
    protected ValueType inferredType;
    protected int line;
    protected boolean callable;
    protected int targetCounter;
    
    interface Visitor<R> {
        R visitAssignExpr(Assign expr);
        R visitBinaryExpr(Binary expr);
        R visitCallExpr(Call expr);
        R visitGetExpr(Get expr);
        R visitGroupingExpr(Grouping expr);
        R visitLiteralExpr(Literal expr);
        R visitLogicalExpr(Logical expr);
        R visitTernaryExpr(Ternary expr);
        R visitListingExpr(Listing expr);
        R visitIndexExpr(Index expr);
        R visitListSetExpr(ListSet expr);
        R visitLenExpr(Len expr);
        R visitInputExpr(Input expr);
        R visitSetExpr(Set expr);
        R visitSelfExpr(Self expr);
        R visitUnaryExpr(Unary expr);
        R visitVariableExpr(Variable expr);
        R visitPrintExpr(Print expr);
    }

    static class Assign extends Expr {
        Assign(Variable target, Expr value) {
            this.target = target;
            this.value = value;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitAssignExpr(this);
        }

        final Variable target;
        final Expr value;
    }

    static class Binary extends Expr {
        Binary(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinaryExpr(this);
        }

        final Expr left;
        final Token operator;
        final Expr right;
    }

    static class Call extends Expr {
        Call(Expr callee, Token paren, List<Expr> arguments) {
            this.callee = callee;
            this.paren = paren;
            this.arguments = arguments;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitCallExpr(this);
        }

        final Expr callee;
        final Token paren;
        final List<Expr> arguments;
    }

    static class Get extends Expr {
        Get(Expr object, Token name) {
            this.object = object;
            this.name = name;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitGetExpr(this);
        }

        final Expr object;
        final Token name;
    }

    static class Grouping extends Expr {
        Grouping(Expr expression) {
            this.expression = expression;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitGroupingExpr(this);
        }

        final Expr expression;
    }

    static class Literal extends Expr {
        Literal(Object value) {
            this.value = value;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteralExpr(this);
        }

        final Object value;
    }

    static class Logical extends Expr {
        Logical(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitLogicalExpr(this);
        }

        final Expr left;
        final Token operator;
        final Expr right;
    }

    static class Ternary extends Expr {
        Ternary(Expr onTrue, Expr condition, Expr onFalse) {
            this.onTrue = onTrue;
            this.condition = condition;
            this.onFalse = onFalse;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitTernaryExpr(this);
        }

        final Expr onTrue;
        final Expr condition;
        final Expr onFalse;
    }

    static class Listing extends Expr {
        Listing(List<Expr> elements) {
            this.elements = elements;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitListingExpr(this);
        }

        final List<Expr> elements;
    }

    static class Index extends Expr {
        Index(Expr listing, Expr id) {
            this.listing = listing;
            this.id = id;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitIndexExpr(this);
        }

        final Expr listing;
        final Expr id;
    }

    static class ListSet extends Expr {
        ListSet(Expr listing, Expr id, Expr value) {
            this.listing = listing;
            this.id = id;
            this.value = value;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitListSetExpr(this);
        }

        final Expr listing;
        final Expr id;
        final Expr value;
    }

    static class Len extends Expr {
        Len(Expr expression) {
            this.expression = expression;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitLenExpr(this);
        }

        final Expr expression;
    }

    static class Input extends Expr {
        Input(Token keyword) {
            this.keyword = keyword;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitInputExpr(this);
        }

        final Token keyword;
    }

    static class Set extends Expr {
        Set(Expr object, Token name, Expr value) {
            this.object = object;
            this.name = name;
            this.value = value;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitSetExpr(this);
        }

        final Expr object;
        final Token name;
        final Expr value;
    }

    static class Self extends Expr {
        Self(Token keyword) {
            this.keyword = keyword;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitSelfExpr(this);
        }

        final Token keyword;
    }

    static class Unary extends Expr {
        Unary(Token operator, Expr right) {
            this.operator = operator;
            this.right = right;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnaryExpr(this);
        }

        final Token operator;
        final Expr right;
    }

    static class Variable extends Expr {
        Variable(Token name) {
            this.name = name;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitVariableExpr(this);
        }

        final Token name;
    }

    static class Print extends Expr {
        Print(Expr expression) {
            this.expression = expression;
        }

        @Override
        <R> R accept(Visitor<R> visitor) {
            return visitor.visitPrintExpr(this);
        }

        final Expr expression;
    }

    abstract <R> R accept(Visitor<R> visitor);
}
