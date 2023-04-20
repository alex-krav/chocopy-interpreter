package org.chocopy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ChocoPy {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    static Integer exitCode;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: chocopy [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate an error in the exit code.
        if (hadError) System.exit(exitCode != null ? exitCode : 65);
        if (hadRuntimeError) System.exit(exitCode != null ? exitCode : 70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        // For now, just print the tokens.
//        for (Token token : tokens) {
//            System.out.println(token);
//        }

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return;

//        System.out.println(new AstPrinter().print(expression));

        Resolver resolver = new Resolver();
        resolver.resolveScript(statements);

        // Stop if there was a resolution error.
        if (hadError) return;

        interpreter.interpret(statements);
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    private static void report(int line, String where, String message) {
        System.err.printf("[line %d] Error%s: %s%n", line, where, message);
        hadError = true;
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    static void error(int line, String text, String message) {
        report(line, " at '" + text + "'", message);
    }
    
    static void binopError(Expr.Binary expr) {
        ChocoPy.error(expr.operator, String.format("Cannot use operator %s on types %s and %s", 
                expr.operator.lexeme, expr.left.inferredType, expr.right.inferredType));
    }
    
    static void binopError(Expr.Logical expr) {
        ChocoPy.error(expr.operator, String.format("Cannot use operator %s on types %s and %s", 
                expr.operator, expr.left.inferredType, expr.right.inferredType));
    }

    static void runtimeError(RuntimeError error) {
        System.err.printf("[line %d] %s%n", error.line, error.getMessage());
        hadRuntimeError = true;
    }
}