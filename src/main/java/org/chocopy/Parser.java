package org.chocopy;

import java.util.ArrayList;
import java.util.List;

import static org.chocopy.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Expr expression() {
        return ternary();
    }

    private Expr equality() {
        Expr expr = term();

        while (match(BANG_EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, IS)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
            expr.line = operator.line;
        }

        return expr;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean checkTwo(TokenType first, TokenType second) {
        if (check(first)) {
            return checkNext(second);
        }

        return false;
    }

    private boolean check(TokenType... types) {
        if (isAtEnd()) return false;

        for (TokenType type : types) {
            if (peek().type == type) {
                return true;
            }
        }

        return false;
    }

    private boolean checkNext(TokenType type) {
        if (isAtEnd()) return false;
        return peekNext().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekNext() {
        if (!isAtEnd()) return tokens.get(current + 1);
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
            expr.line = operator.line;
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(DOUBLE_SLASH, STAR, PERCENT)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
            expr.line = operator.line;
        }

        return expr;
    }

    private Expr unary() {
        if (match(MINUS)) {
            Token operator = previous();
            Expr right = unary();
            Expr.Unary unary = new Expr.Unary(operator, right);
            unary.line = operator.line;
            return unary;
        }

        return call();
    }
    
    private Expr literal() {
        if (match(NONE)) {
            Expr.Literal literal = new Expr.Literal(null);
            literal.line = previous().line;
            return literal;
        }
        if (match(TRUE)) {
            Expr.Literal literal = new Expr.Literal(true);
            literal.line = previous().line;
            return literal;
        }
        if (match(FALSE)) {
            Expr.Literal literal = new Expr.Literal(false);
            literal.line = previous().line;
            return literal;
        }

        if (match(NUMBER, STRING, IDSTRING)) {
            Expr.Literal literal = new Expr.Literal(previous().literal);
            literal.line = previous().line;
            return literal;
        }

        throw error(peek(), "invalid literal", "ValueError");
    }

    private Expr primary() {
        if (match(NONE)) {
            Expr.Literal literal = new Expr.Literal(null);
            literal.line = previous().line;
            return literal;
        }
        if (match(TRUE)) {
            Expr.Literal literal = new Expr.Literal(true);
            literal.line = previous().line;
            return literal;
        }
        if (match(FALSE)) {
            Expr.Literal literal = new Expr.Literal(false);
            literal.line = previous().line;
            return literal;
        }

        if (match(NUMBER, STRING, IDSTRING)) {
            Expr.Literal literal = new Expr.Literal(previous().literal);
            literal.line = previous().line;
            return literal;
        }

        if (match(SELF)) {
            Expr.Self self = new Expr.Self(previous());
            self.line = previous().line;
            return self;
        }

        if (match(ID)) {
            Expr.Variable variable = new Expr.Variable(previous());
            variable.line = previous().line;
            return variable;
        }
        
        if (match(OBJECT_TYPE, INT_TYPE, STR_TYPE, BOOL_TYPE)) {
            Expr.Variable variable = new Expr.Variable(previous());
            variable.line = previous().line;
            return variable;
        }

        if (match(LEN_NATIVE_FUN)) return lenFunc();
        if (match(INPUT_NATIVE_FUN)) return inputFunc();
        if (match(PRINT_NATIVE_FUN)) return printFunc();

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "expect ')' after expression");
            return new Expr.Grouping(expr);
        }
        
        if (match(LEFT_BRACKET)) {
            List<Expr> elements = new ArrayList<>();
            if (!check(RIGHT_BRACKET)) {
                do {
                    if (elements.size() >= 255) {
                        error(peek(), "can't have more than 255 elements", "ValueError");
                    }

                    elements.add(expression());
                } while (match(COMMA));
            }
            consume(RIGHT_BRACKET, "expect ']' after list definition");
            Expr.Listing listing = new Expr.Listing(elements);
            listing.line = previous().line;
            return listing;
        }

        throw error(peek(), "expected expression", "SyntaxError");
    }

    private Token consume(TokenType type, String message) {
        return consume(type, message, "SyntaxError");
    }

    private Token consume(TokenType type, String message, String errorType) {
        if (check(type)) return advance();

        throw error(peek(), message, errorType);
    }

    private ParseError error(Token token, String message, String type) {
        ChocoPy.error(token, message, type);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == NEWLINE) return;

            switch (peek().type) {
                case CLASS:
                case DEF:
                case FOR:
                case IF:
                case WHILE:
                case PASS:
                case RETURN:
                case GLOBAL:
                case NONLOCAL:
                    return;
            }

            advance();
        }
    }

    private Stmt statement() {
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();

        Stmt stmt = simpleStatement();
        if (!isAtEnd() && !checkTwo(DEDENT, EOF) && !checkTwo(DEDENT, DEDENT)) {
            consume(NEWLINE, "expect 'newline' after simple statement");
        }
        return stmt;
    }
    
    private Stmt simpleStatement() {
        if (match(PASS)) return new Stmt.Pass(previous());
        if (match(RETURN)) return returnStatement();
        
        Stmt assignment = assignment();
        if (assignment != null)
            return assignment;

        return expressionStatement();
    }

    private Expr printFunc() {
        consume(LEFT_PAREN, "expect '(' before argument");
        int line = previous().line;
        Expr value = expression();
        consume(RIGHT_PAREN, "expect ')' after argument");
        Expr.Print print = new Expr.Print(value);
        print.line = line;
        return print;
    }

    private Expr inputFunc() {
        consume(LEFT_PAREN, "expect '(' for function call");
        consume(RIGHT_PAREN, "expect ')' for function call");
        Expr.Input input = new Expr.Input(new Token(INPUT_NATIVE_FUN, "", null, previous().line));
        input.line = previous().line;
        return input;
    }

    private Expr lenFunc() {
        consume(LEFT_PAREN, "expect '(' before argument");
        Expr value = expression();
        consume(RIGHT_PAREN, "expect ')' after argument");
        Expr.Len len = new Expr.Len(value);
        len.line = previous().line;
        return len;
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        return new Stmt.Expression(expr);
    }
    
    private Stmt assignment() {
        List<Expr> targets = new ArrayList<>();

        int tokenBeforeAssignment = current;
        Expr target = target();
        if (target != null) {
            if (match(EQUAL)) {
                Token equals = previous();
                targets.add(target);
                
                int tokenBeforeRightValue = current;
                target = target();
                while (target != null) {
                    if (match(EQUAL)) {
                        targets.add(target);
                    } else {
                        break;
                    }
                    tokenBeforeRightValue = current;
                    target = target();
                }
                
                current = tokenBeforeRightValue;
                Expr value = expression();
                
                Expr multiAssign = new Expr.MultiAssign(targets, value);
                multiAssign.line = equals.line;
                return new Stmt.Expression(multiAssign);
            } else {
                current = tokenBeforeAssignment;
            }
        }
        
        return null;
    }

    private Expr target() {
        Expr expr = selfOrId();
        if (expr == null)
            return expr;

        while (true) {
            if (match(LEFT_PAREN)) {
                int line = previous().line;
                expr = finishCall(expr);
                expr.line = line;
            } else if (match(DOT)) {
                Token name = consume(ID,
                        "expect property name after '.'");
                expr = new Expr.Get(expr, name);
                expr.line = name.line;
            } else if (match(LEFT_BRACKET)) {
                Expr index = expression();
                consume(RIGHT_BRACKET, "expect ']' after list index");
                expr = new Expr.Index(expr, index);
                expr.line = previous().line;
            } else {
                break;
            }
        }

        return expr;
    }
    
    private Expr selfOrId() {
        Expr expr = null;

        if (match(SELF)) {
            expr = new Expr.Self(previous());
            expr.line = previous().line;
        } else if (match(ID)) {
            expr = new Expr.Variable(previous());
            expr.line = previous().line;
        }
        
        return expr;
    }

    private Stmt declaration() {
        try {
            if (checkTwo(ID, COLON)) return varDefinition("global variable");
            if (match(DEF)) return function("function");
            if (match(CLASS)) return classDefinition();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDefinition() {
        Token name = consume(ID, "expect class name");
        consume(LEFT_PAREN, "expect '(' after " + name.lexeme + " class name");
        Token superclass;

        if (check(ID, OBJECT_TYPE)) {
            superclass = consume(peek().type, "expect superclass name");
        } else {
            throw error(peek(), "expect superclass name", "SyntaxError");
        }
        
        consume(RIGHT_PAREN, "expect ')' after superclass name of " + name.lexeme + "class");

        consume(COLON, "expect ':' after " + name.lexeme + " declaration");
        consume(NEWLINE, "expect 'newline' after " + name.lexeme + " declaration");
        consume(INDENT, "expect 'indent' before '" + name.lexeme + "' class body", "IndentationError");

        List<Stmt> members = new ArrayList<>();
        while (!check(DEDENT) && !isAtEnd()) {
            members.add(classMember(name));
        }

        consume(DEDENT, "expect 'dedent' after " + name.lexeme + " body", "IndentationError");

        Stmt.Class klass = new Stmt.Class(name, superclass, members);
        klass.line = name.line;
        return klass;
    }
    
    private Stmt classMember(Token className) {
        if (match(PASS)) {
            if (!isAtEnd() && !checkTwo(DEDENT, EOF) && !checkTwo(DEDENT, DEDENT)) {
                consume(NEWLINE, "expect 'newline' after pass statement");
            }
            return new Stmt.Pass(new Token(PASS, "pass", null, previous().line));
        }
        if (check(ID)) return varDefinition("class field");
        if (match(DEF)) return function("method");

        throw error(peek(), "unexpected token in " + className + " class body", "SyntaxError");
    }

    private Stmt varDefinition(String kind) {
        Stmt.Var var = typedVarDeclaration(kind);

        consume(EQUAL, "expect '=' after " + kind + " declaration");
        var.setInitializer(literal());

        if (!isAtEnd() && !checkTwo(DEDENT, EOF) && !checkTwo(DEDENT, DEDENT)) {
            consume(NEWLINE, "expect 'newline' after " + kind + " definition");
        }
        return var;
    }
    
    private Stmt.Var typedVarDeclaration(String kind) {
        if (check(ID, SELF)) {
            Token name = consume(peek().type, "expect " + kind + " name");
            consume(COLON, "expect ':' after " + kind + " name");
            ValueType type = varType(kind);
            Stmt.Var var = new Stmt.Var(name, type, null);
            var.line = name.line;
            return var;
        }

        throw error(peek(), "unexpected token for " + kind + " id", "SyntaxError");
    }
    
    private ValueType varType(String kind) {
        if (check(ID, BOOL_TYPE, STR_TYPE, INT_TYPE, OBJECT_TYPE, IDSTRING)) {
            Token token = consume(peek().type, "expect " + kind + " type name");
            String type = token.lexeme.replaceAll("^\"|\"$", "");
            return switch (type) {
                case "str" -> new StrType();
                case "int" -> new IntType();
                case "bool" -> new BoolType();
                case "object" -> new ObjectType();
                default -> new ClassValueType(type);
            };
        } else if (match(LEFT_BRACKET)) {
            ValueType elementType = varType(kind);
            consume(RIGHT_BRACKET, "expect ']' after " + kind + " type name");
            return new ListValueType(elementType);
        }

        throw error(peek(), "unexpected token for " + kind + " type", "SyntaxError");
    }
    
    private Expr ternary() {
        Expr onTrue = or();
        
        if (match(IF)) {
            int line = previous().line;
            Expr condition = or();
            consume(ELSE, "expected 'else' after condition expression");
            Expr onFalse = ternary();
            onTrue = new Expr.Ternary(onTrue, condition, onFalse);
            onTrue.line = line;
        }

        return onTrue;
    }

    private Stmt.Block block() {
        consume(NEWLINE, "expect 'newline' before block");
        consume(INDENT, "expect 'indent' before block", "IndentationError");
        int line = previous().line;

        List<Stmt> statements = new ArrayList<>();
        while (!check(DEDENT) && !isAtEnd()) {
            statements.add(statement());
        }

        consume(DEDENT, "expect 'dedent' after block", "IndentationError");
        Stmt.Block block = new Stmt.Block(statements);
        block.line = line;
        return block;
    }

    private List<Stmt> functionBody(String kind) {
        List<Stmt> statements = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            statements.add(functionStatement());
        }

        consume(DEDENT, "expect 'dedent' after " + kind + " body", "IndentationError");
        return statements;
    }
    
    private Stmt functionStatement() {
        try {
            if (match(GLOBAL)) return globalDeclaration();
            if (match(NONLOCAL)) return nonlocalDeclaration();
            if (checkTwo(ID, COLON)) return varDefinition("function local variable");
            if (match(DEF)) return function("inner function");

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt globalDeclaration() {
        Token name = consume(ID, "expect variable name");
        Stmt.Global global = new Stmt.Global(name);
        global.line = name.line;
        if (!isAtEnd() && !checkTwo(DEDENT, EOF) && !checkTwo(DEDENT, DEDENT)) {
            consume(NEWLINE, "expect 'newline' after global variable declaration");
        }
        return global;
    }

    private Stmt nonlocalDeclaration() {
        Token name = consume(ID, "expect variable name");
        Stmt.Nonlocal nonlocal = new Stmt.Nonlocal(name);
        nonlocal.line = name.line;
        if (!isAtEnd() && !checkTwo(DEDENT, EOF) && !checkTwo(DEDENT, DEDENT)) {
            consume(NEWLINE, "expect 'newline' after nonlocal variable declaration");
        }
        return nonlocal;
    }

    private Stmt ifStatement() {
        Expr condition = expression();
        consume(COLON, "expect ':' after if condition");
        int line = previous().line;
        Stmt thenBranch = block();
        Stmt elseBranch = null;

        if (match(ELIF)) {
            elseBranch = ifStatement();
        }
        
        if (match(ELSE)) {
            consume(COLON, "expect ':' after else keyword");
            elseBranch = block();
        }

        Stmt.If stmtIf = new Stmt.If(condition, thenBranch, elseBranch);
        stmtIf.line = line;
        return stmtIf;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
            expr.line = operator.line;
        }

        return expr;
    }

    private Expr and() {
        Expr expr = not();

        while (match(AND)) {
            Token operator = previous();
            Expr right = not();
            expr = new Expr.Logical(expr, operator, right);
            expr.line = operator.line;
        }

        return expr;
    }

    private Expr not() {
        Expr expr;

        if (match(NOT)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Unary(operator, right);
            expr.line = operator.line;
        } else {
            expr = equality();
        }

        return expr;
    }

    private Stmt whileStatement() {
        Expr condition = expression();
        consume(COLON, "expect ':' after condition");
        int line = previous().line;
        Stmt.Block body = block();

        Stmt.While stmtWhile = new Stmt.While(condition, body);
        stmtWhile.line = line;
        return stmtWhile;
    }

    private Stmt forStatement() {
        Token id = consume(ID, "expect element name");
        consume(IN, "expect 'in' after " + id.lexeme + " id");
        Expr iterable = expression();
        consume(COLON, "expect ':' after iterable");
        Stmt.Block body = block();
        
        Stmt.For stmtFor = new Stmt.For(new Expr.Variable(id), iterable, body);
        stmtFor.line = id.line;
        return stmtFor;
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                int line = previous().line;
                expr = finishCall(expr);
                expr.line = line;
            } else if (match(DOT)) {
                Token name = consume(ID,
                        "expect property name after '.'");
                expr = new Expr.Get(expr, name);
                expr.line = name.line;
            } else if (match(LEFT_BRACKET)) {
                Expr index = expression();
                consume(RIGHT_BRACKET, "expect ']' after list index");
                expr = new Expr.Index(expr, index);
                expr.line = previous().line;
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "can't have more than 255 arguments", "ValueError");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN,
                "expect ')' after arguments");

        return new Expr.Call(callee, paren, arguments);
    }

    private Stmt.Function function(String kind) {
        Token name;
        if (check(ID, INPUT_NATIVE_FUN, LEN_NATIVE_FUN, PRINT_NATIVE_FUN)) {
            name = consume(peek().type, "expect " + kind + " name");
        } else {
            throw error(peek(), "expect " + kind + " name", "SyntaxError");
        }
        
        consume(LEFT_PAREN, "expect '(' after " + kind + " name");
        List<Stmt.Var> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "can't have more than 255 parameters", "ValueError");
                }

                parameters.add(
                        typedVarDeclaration("function parameter"));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "expect ')' after parameters");

        ValueType returnType = new NoneType();
        if (!check(COLON)) {
            consume(ARROW, "expect '->' before " + kind + " return type");    
            returnType = varType("return");
        }
        
        consume(COLON, "expect ':' after " + kind + " definition");
        consume(NEWLINE, "expect 'newline' after " + kind + " definition");
        consume(INDENT, String.format("expect 'indent' before '%s' %s body", name.lexeme, kind), "IndentationError");
        
        List<Stmt> body = functionBody(kind);
        Stmt.Function function = new Stmt.Function(name, parameters, returnType, body);
        function.line = name.line;
        return function;
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(NEWLINE)) {
            value = expression();
        }

        Stmt.Return stmtReturn = new Stmt.Return(keyword, value);
        stmtReturn.line = keyword.line;
        return stmtReturn;
    }
}