package org.chocopy;

import java.util.ArrayList;
import java.util.List;

import static org.chocopy.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private int forLoopsCounter = 0;

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
        return assignment();
    }

    private Expr equality() {
        Expr expr = term();

        while (match(BANG_EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, IS)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
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
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(DOUBLE_SLASH, STAR, PERCENT)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }
    
    private Expr literal(boolean expected) {
        if (match(NONE)) return new Expr.Literal(null);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(FALSE)) return new Expr.Literal(false);

        if (match(NUMBER, STRING)) { // todo: IDSTRING?
            return new Expr.Literal(previous().literal);
        }
        
        if (expected) {
            throw error(peek(), "Expect literal.");
        } else {
            throw new RuntimeError(peek(), "Non-literal token. Continue parsing.");
        }
    }

    private Expr primary() {
        try {
            return literal(false);
        } catch(RuntimeError ignored) {
            // keep parsing
        }

        if (match(SUPER)) {
            Token keyword = previous();
            consume(DOT, "Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER,
                    "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }

        if (match(SELF)) return new Expr.Self(previous());

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEN_NATIVE_FUN)) return lenExpression();
        if (match(INPUT_NATIVE_FUN)) return inputExpression();

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        
        if (match(LEFT_BRACKET)) {
            List<Expr> elements = new ArrayList<>();
            if (!check(RIGHT_BRACKET)) {
                do {
                    if (elements.size() >= 255) {
                        error(peek(), "Can't have more than 255 elements.");
                    }

                    elements.add(expression());
                } while (match(COMMA));
            }
            consume(RIGHT_BRACKET, "Expect ']' after list definition.");
            return new Expr.Listing(elements);
        }

        throw error(peek(), "Expect expression.");
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        ChocoPy.error(token, message);
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
                case PRINT_NATIVE_FUN:
                case RETURN:
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
        if (!isAtEnd()) consume(NEWLINE, "Expect 'newline' after simple statement.");
        return stmt;
    }
    
    private Stmt simpleStatement() {
        if (match(PRINT_NATIVE_FUN)) return printStatement();
        
        if (match(PASS)) return new Stmt.Pass(previous());
        if (match(RETURN)) return returnStatement();

        return expressionStatement();
    }

    private Stmt printStatement() {
        consume(LEFT_PAREN, "Expect '(' before argument.");
        Expr value = expression();
        consume(RIGHT_PAREN, "Expect ')' after argument.");
        return new Stmt.Print(value);
    }

    private Expr inputExpression() { //todo: impl as anonymous function?
        consume(LEFT_PAREN, "Expect '(' for function call.");
        consume(RIGHT_PAREN, "Expect ')' for function call.");
        return new Expr.Input(new Token(INPUT_NATIVE_FUN, "", null, previous().line));
    }

    private Expr lenExpression() {
        consume(LEFT_PAREN, "Expect '(' before argument.");
        Expr value = expression();
        consume(RIGHT_PAREN, "Expect ')' after argument.");
        return new Expr.Len(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        return new Stmt.Expression(expr);
    }

    private Stmt declaration() {
        try {
            if (checkTwo(IDENTIFIER, COLON)) return varDefinition("global variable");
            if (match(DEF)) return function("function");
            if (match(CLASS)) return classDefinition();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDefinition() {
        Token name = consume(IDENTIFIER, "Expect class name.");
        consume(LEFT_PAREN, "Expect '(' after " + name + " class name.");
        Token superclass = consume(peek().type, "Expect superclass name of " + name + "class.");
        consume(RIGHT_PAREN, "Expect ')' after superclass name of " + name + "class.");

        consume(COLON, "Expect ':' after " + name + " declaration.");
        consume(NEWLINE, "Expect 'newline' after " + name + " declaration.");
        consume(INDENT, "Expect 'indent' after before " + name + " body.");

        List<Stmt> members = new ArrayList<>();
        while (!check(DEDENT) && !isAtEnd()) {
            members.add(classMember(name));
        }

        consume(DEDENT, "Expect 'dedent' after class body.");

        //todo check: members must be: 1 pass OR 1+ (var_def OR func_def)
        return new Stmt.Class(name, superclass, members);
    }
    
    private Stmt classMember(Token className) {
        if (match(PASS)) {
            consume(NEWLINE, "Expect 'newline' after pass statement.");
            return new Stmt.Pass(new Token(PASS, "pass", null, previous().line));
        }
        if (check(IDENTIFIER)) return varDefinition("class field");
        if (match(DEF)) return function("method");

        throw error(peek(), "Unexpected token in " + className + " class body.");
    }

    private Stmt varDefinition(String kind) {
        Stmt.Var var = typedVarDeclaration(kind);

        consume(EQUAL, "Expect '=' after " + kind + " declaration.");
        var.setInitializer(literal(true));

        consume(NEWLINE, "Expect 'newline' after " + kind + " definition.");
        return var;
    }
    
    private Stmt.Var typedVarDeclaration(String kind) {
        if (check(IDENTIFIER, SELF)) {
            Token name = consume(peek().type, "Expect " + kind + " name.");
            consume(COLON, "Expect ':' after " + kind + " name.");
            Token type = varType(kind);
            return new Stmt.Var(name, type, null);
        }

        throw error(peek(), "Unexpected token for " + kind + " identifier.");
    }
    
    private Token varType(String kind) {
        if (check(IDENTIFIER, BOOL_TYPE, STR_TYPE, INT_TYPE, OBJECT_TYPE)) {
            return consume(peek().type, "Expect " + kind + " type name.");
        } else if (check(STRING)) {
            Token name = consume(peek().type, "Expect " + kind + " type name.");
            return new Token(IDSTRING, name.lexeme, name.literal, name.line);
        } else if (match(LEFT_BRACKET)) {
            if (check(IDENTIFIER, BOOL_TYPE, STR_TYPE, INT_TYPE, OBJECT_TYPE)) {
                Token name = consume(peek().type, "Expect " + kind + " type name.");
                consume(RIGHT_BRACKET, "Expect ']' after " + kind + " type name.");
                return new Token(LIST_TYPE, name.lexeme, name.literal, name.line);
            }
        }

        throw error(peek(), "Unexpected token for " + kind + " type.");
    }

    private Expr assignment() {
        Expr expr = ternary();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get) expr;
                return new Expr.Set(get.object, get.name, value);
            } else if (expr instanceof Expr.Index) {
                Expr.Index index = (Expr.Index) expr;
                return new Expr.ListSet(index.listing, index.id, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }
    
    private Expr ternary() {
        Expr onTrue = or();
        
        if (match(IF)) {
            Expr condition = or();
            consume(ELSE, "Expected 'else' after condition expression.");
            Expr onFalse = ternary();
            onTrue = new Expr.Ternary(onTrue, condition, onFalse);
        }

        return onTrue;
    }

    private Stmt.Block block() {
        consume(NEWLINE, "Expect 'newline' before block.");
        consume(INDENT, "Expect 'indent' before block.");

        List<Stmt> statements = new ArrayList<>();
        while (!check(DEDENT) && !isAtEnd()) {
            statements.add(statement());
        }

        consume(DEDENT, "Expect 'dedent' after block.");
        return new Stmt.Block(statements);
    }

    private List<Stmt> functionBody(String kind) {
        List<Stmt> statements = new ArrayList<>();

        while (!check(DEDENT) && !isAtEnd()) {
            statements.add(functionStatement());
        }

        consume(DEDENT, "Expect 'dedent' after " + kind + " body.");
        return statements;
    }
    
    private Stmt functionStatement() {
        try {
            if (match(GLOBAL)) return globalDeclaration();
            if (match(NONLOCAL)) return nonlocalDeclaration();
            if (checkTwo(IDENTIFIER, COLON)) return varDefinition("function local variable");
            if (match(DEF)) return function("inner function");

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt globalDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        consume(NEWLINE, "Expect 'newline' after global variable declaration.");
        return new Stmt.Global(name);
    }

    private Stmt nonlocalDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        consume(NEWLINE, "Expect 'newline' after nonlocal variable declaration.");
        return new Stmt.Nonlocal(name);
    }

    private Stmt ifStatement() {
        Expr condition = expression();
        consume(COLON, "Expect ':' after if condition.");
        Stmt thenBranch = block();
        Stmt elseBranch = null;

        if (match(ELIF)) {
            elseBranch = ifStatement();
        }
        
        if (match(ELSE)) {
            consume(COLON, "Expect ':' after else keyword.");
            elseBranch = block();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = not();

        while (match(AND)) {
            Token operator = previous();
            Expr right = not();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr not() {
        Expr expr;

        if (match(NOT)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Unary(operator, right);
        } else {
            expr = equality();
        }

        return expr;
    }

    private Stmt whileStatement() {
        Expr condition = expression();
        consume(COLON, "Expect ':' after condition.");
        Stmt.Block body = block();

        return new Stmt.While(condition, body);
    }

    // syntactic desugaring of FOR stmt into WHILE stmt
    // todo: after implementing interpreter update function to block (local scope vars)?
    private Stmt forStatement() {
        Token element = consume(IDENTIFIER, "Expect element name.");
        consume(IN, "Expect 'in' after " + element + " identifier.");
        Expr iterable = expression();
        consume(COLON, "Expect ':' after iterable.");
        Stmt.Block body = block();

        Token i = new Token(IDENTIFIER, "i", null,-1);
        Stmt init_i = new Stmt.Var(
                                        i, 
                                        new Token(INT_TYPE, "int", null, -1), 
                                        new Expr.Literal(0));
        Stmt init_elem = new Stmt.Var(
                                        element, 
                                        new Token(OBJECT_TYPE, "object", null, -1), 
                                        new Expr.Literal(null));
        Expr condition = new Expr.Binary(
                                        new Expr.Variable(i),
                                        new Token(LESS, "<", null,-1),
                                        new Expr.Len(iterable));
        Expr assignNextElem = new Expr.Assign(
                                        element,
                                        new Expr.Index(iterable, new Expr.Variable(i)));
        Expr increment = new Expr.Assign(
                                        i,
                                        new Expr.Binary(
                                            new Expr.Variable(i),
                                            new Token(PLUS, "+", null,-1),
                                            new Expr.Literal(1)
                                        ));
        List<Stmt> statements = body.statements;
        statements.add(0, new Stmt.Expression(assignNextElem));
        statements.add(new Stmt.Expression(increment));
        Stmt.While whileLoop = new Stmt.While(condition, new Stmt.Block(statements));
        
        Stmt.Function fun = new Stmt.Function(
                new Token(IDENTIFIER, "__forLoop"+forLoopsCounter, null, -1),
                new ArrayList<>(),
                null,
                List.of(init_i, init_elem, whileLoop)
        );
        Expr.Call call = new Expr.Call(
                new Expr.Variable(
                        new Token(IDENTIFIER, "__forLoop"+forLoopsCounter, null, -1)
                ),
                new Token(RIGHT_PAREN, ")", null, -1),
                new ArrayList<>()
        );
        forLoopsCounter++;
        
        return new Stmt.Block(List.of(fun, new Stmt.Expression(call)));
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER,
                        "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else if (match(LEFT_BRACKET)) {
                Expr index = expression();
                consume(RIGHT_BRACKET, "Expect ']' after list index.");
                expr = new Expr.Index(expr, index);
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
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN,
                "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Stmt.Var> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }

                parameters.add(
                        typedVarDeclaration("function parameter"));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        Token returnType = null;
        if (!check(COLON)) {
            consume(ARROW, "Expect '->' before " + kind + " return type.");    
            returnType = varType("return");
        }
        
        consume(COLON, "Expect ':' after " + kind + " definition.");
        consume(NEWLINE, "Expect 'newline' after " + kind + " definition.");
        consume(INDENT, "Expect 'indent' before " + kind + " body.");
        
        List<Stmt> body = functionBody(kind);
        return new Stmt.Function(name, parameters, returnType, body);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(NEWLINE)) {
            value = expression();
        }

        return new Stmt.Return(keyword, value);
    }
}