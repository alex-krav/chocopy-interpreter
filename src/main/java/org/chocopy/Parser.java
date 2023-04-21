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
        return assignment();
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

        throw error(peek(), "Expected literal for var definition.");
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

        if (match(IDENTIFIER)) {
            Expr.Variable variable = new Expr.Variable(previous());
            variable.line = previous().line;
            return variable;
        }
        
        if (match(OBJECT_TYPE, INT_TYPE, STR_TYPE, BOOL_TYPE)) {
            Expr.Variable variable = new Expr.Variable(previous());
            variable.line = previous().line;
            return variable;
        }

        if (match(LEN_NATIVE_FUN)) return lenExpression();
        if (match(INPUT_NATIVE_FUN)) return inputExpression();
        if (match(PRINT_NATIVE_FUN)) return printExpression();

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
            Expr.Listing listing = new Expr.Listing(elements);
            listing.line = previous().line;
            return listing;
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
        if (!isAtEnd() && !checkTwo(DEDENT, EOF)) {
            consume(NEWLINE, "Expect 'newline' after simple statement.");
        }
        return stmt;
    }
    
    private Stmt simpleStatement() {
        if (match(PASS)) return new Stmt.Pass(previous());
        if (match(RETURN)) return returnStatement();

        return expressionStatement();
    }

    private Expr printExpression() {
        consume(LEFT_PAREN, "Expect '(' before argument.");
        int line = previous().line;
        Expr value = expression();
        consume(RIGHT_PAREN, "Expect ')' after argument.");
        Expr.Print print = new Expr.Print(value);
        print.line = line;
        return print;
    }

    private Expr inputExpression() {
        consume(LEFT_PAREN, "Expect '(' for function call.");
        consume(RIGHT_PAREN, "Expect ')' for function call.");
        Expr.Input input = new Expr.Input(new Token(INPUT_NATIVE_FUN, "", null, previous().line));
        input.line = previous().line;
        return input;
    }

    private Expr lenExpression() {
        consume(LEFT_PAREN, "Expect '(' before argument.");
        Expr value = expression();
        consume(RIGHT_PAREN, "Expect ')' after argument.");
        Expr.Len len = new Expr.Len(value);
        len.line = previous().line;
        return len;
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
        consume(LEFT_PAREN, "Expect '(' after " + name.lexeme + " class name.");
        Token superclass;

        if (check(IDENTIFIER, OBJECT_TYPE)) {
            superclass = consume(peek().type, "Expect superclass name.");
        } else {
            throw error(peek(), "Expect superclass name.");
        }
        
        consume(RIGHT_PAREN, "Expect ')' after superclass name of " + name.lexeme + "class.");

        consume(COLON, "Expect ':' after " + name.lexeme + " declaration.");
        consume(NEWLINE, "Expect 'newline' after " + name.lexeme + " declaration.");
        consume(INDENT, "Expect 'indent' before " + name.lexeme + " body.");

        List<Stmt> members = new ArrayList<>();
        while (!check(DEDENT) && !isAtEnd()) {
            members.add(classMember(name));
        }

        consume(DEDENT, "Expect 'dedent' after " + name.lexeme + " body.");

        //todo check: members must be: 1 pass OR 1+ (var_def OR func_def)
        Stmt.Class klass = new Stmt.Class(name, superclass, members);
        klass.line = name.line;
        return klass;
    }
    
    private Stmt classMember(Token className) {
        if (match(PASS)) {
            if (!isAtEnd()) consume(NEWLINE, "Expect 'newline' after pass statement.");
            return new Stmt.Pass(new Token(PASS, "pass", null, previous().line));
        }
        if (check(IDENTIFIER)) return varDefinition("class field");
        if (match(DEF)) return function("method");

        throw error(peek(), "Unexpected token in " + className + " class body.");
    }

    private Stmt varDefinition(String kind) {
        Stmt.Var var = typedVarDeclaration(kind);

        consume(EQUAL, "Expect '=' after " + kind + " declaration.");
        var.setInitializer(literal());

        if (!isAtEnd()) consume(NEWLINE, "Expect 'newline' after " + kind + " definition.");
        return var;
    }
    
    private Stmt.Var typedVarDeclaration(String kind) {
        if (check(IDENTIFIER, SELF)) {
            Token name = consume(peek().type, "Expect " + kind + " name.");
            consume(COLON, "Expect ':' after " + kind + " name.");
            ValueType type = varType(kind);
            Stmt.Var var = new Stmt.Var(name, type, null);
            var.line = name.line;
            return var;
        }

        throw error(peek(), "Unexpected token for " + kind + " identifier.");
    }
    
    private ValueType varType(String kind) {
        if (check(IDENTIFIER, BOOL_TYPE, STR_TYPE, INT_TYPE, OBJECT_TYPE, IDSTRING)) {
            Token token = consume(peek().type, "Expect " + kind + " type name.");
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
            consume(RIGHT_BRACKET, "Expect ']' after " + kind + " type name.");
            return new ListValueType(elementType);
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
                Expr.Assign assign = new Expr.Assign(new Expr.Variable(name), value);
                assign.line = equals.line;
                return assign;
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get) expr;
                Expr.Set set = new Expr.Set(get.object, get.name, value);
                set.line = equals.line;
                return set;
            } else if (expr instanceof Expr.Index) {
                Expr.Index index = (Expr.Index) expr;
                Expr.ListSet listSet = new Expr.ListSet(index.listing, index.id, value);
                listSet.line = equals.line;
                return listSet;
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }
    
    private Expr ternary() {
        Expr onTrue = or();
        
        if (match(IF)) {
            int line = previous().line;
            Expr condition = or();
            consume(ELSE, "Expected 'else' after condition expression.");
            Expr onFalse = ternary();
            onTrue = new Expr.Ternary(onTrue, condition, onFalse);
            onTrue.line = line;
        }

        return onTrue;
    }

    private Stmt.Block block() {
        consume(NEWLINE, "Expect 'newline' before block.");
        consume(INDENT, "Expect 'indent' before block.");
        int line = previous().line;

        List<Stmt> statements = new ArrayList<>();
        while (!check(DEDENT) && !isAtEnd()) {
            statements.add(statement());
        }

        consume(DEDENT, "Expect 'dedent' after block.");
        Stmt.Block block = new Stmt.Block(statements);
        block.line = line;
        return block;
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
        Stmt.Global global = new Stmt.Global(name);
        global.line = name.line;
        consume(NEWLINE, "Expect 'newline' after global variable declaration.");
        return global;
    }

    private Stmt nonlocalDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");
        Stmt.Nonlocal nonlocal = new Stmt.Nonlocal(name);
        nonlocal.line = name.line;
        consume(NEWLINE, "Expect 'newline' after nonlocal variable declaration.");
        return nonlocal;
    }

    private Stmt ifStatement() {
        Expr condition = expression();
        consume(COLON, "Expect ':' after if condition.");
        int line = previous().line;
        Stmt thenBranch = block();
        Stmt elseBranch = null;

        if (match(ELIF)) {
            elseBranch = ifStatement();
        }
        
        if (match(ELSE)) {
            consume(COLON, "Expect ':' after else keyword.");
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
        consume(COLON, "Expect ':' after condition.");
        int line = previous().line;
        Stmt.Block body = block();

        Stmt.While stmtWhile = new Stmt.While(condition, body);
        stmtWhile.line = line;
        return stmtWhile;
    }

    private Stmt forStatement() {
        Token identifier = consume(IDENTIFIER, "Expect element name.");
        consume(IN, "Expect 'in' after " + identifier.lexeme + " identifier.");
        Expr iterable = expression();
        consume(COLON, "Expect ':' after iterable.");
        Stmt.Block body = block();
        
        Stmt.For stmtFor = new Stmt.For(new Expr.Variable(identifier), iterable, body);
        stmtFor.line = identifier.line;
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
                Token name = consume(IDENTIFIER,
                        "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
                expr.line = name.line;
            } else if (match(LEFT_BRACKET)) {
                Expr index = expression();
                consume(RIGHT_BRACKET, "Expect ']' after list index.");
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
        Token name;
        if (check(IDENTIFIER, INPUT_NATIVE_FUN, LEN_NATIVE_FUN, PRINT_NATIVE_FUN)) {
            name = consume(peek().type, "Expect " + kind + " name.");
        } else {
            throw error(peek(), "Expect " + kind + " name.");
        }
        
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

        ValueType returnType = new NoneType();
        if (!check(COLON)) {
            consume(ARROW, "Expect '->' before " + kind + " return type.");    
            returnType = varType("return");
        }
        
        consume(COLON, "Expect ':' after " + kind + " definition.");
        consume(NEWLINE, "Expect 'newline' after " + kind + " definition.");
        consume(INDENT, "Expect 'indent' before " + kind + " body.");
        
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