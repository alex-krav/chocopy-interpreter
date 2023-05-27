package org.chocopy;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ChocoPy {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    static Integer exitCode;
    static List<String> errors = new ArrayList<>();
    static List<String> runtimeErrors = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        if (args.length == 1) {
            runFile(args[0]);
        } else {
            System.out.println("Usage: java -jar chocopy.jar [script]");
            System.exit(64);
        }
    }

    static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate an error in the exit code.
        if (hadError) {
            errors.forEach(System.err::println);
            System.exit(exitCode != null ? exitCode : 65);
        }
        if (hadRuntimeError) {
            runtimeErrors.forEach(System.err::println);
            System.exit(exitCode != null ? exitCode : 70);
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return;

        Resolver resolver = new Resolver();
        resolver.resolveScript(statements);

        // Stop if there was a resolution error.
        if (hadError) return;

        interpreter.interpret(statements);
    }

    static void error(int line, String message, String errorType) {
        report(line, message, errorType);
    }

    private static void report(int line, String message, String errorType) {
        int errorsSize = errors.size();
        String errMsg = String.format("[line %d] %s: %s", line, errorType, message);
        
        if (errorsSize == 0 || !errors.get(errorsSize - 1).equals(errMsg)) {
            errors.add(errMsg);
        }
        hadError = true;
    }

    static void error(Token token, String message, String errorType) {
        report(token.line, message, errorType);
    }
    
    static void binopError(Expr.Binary expr, String errorType) {
        ChocoPy.error(expr.operator, String.format("unsupported operand type(s) for %s: '%s' and '%s'", 
                expr.operator.lexeme, expr.left.inferredType, expr.right.inferredType), errorType);
    }
    
    static void binopError(Expr.Logical expr, String errorType) {
        ChocoPy.error(expr.operator, String.format("unsupported operand type(s) for %s: '%s' and '%s'", 
                expr.operator.lexeme, expr.left.inferredType, expr.right.inferredType), errorType);
    }

    static void runtimeError(RuntimeError error) {
        int runtimeErrorsSize = runtimeErrors.size();
        String errMsg;
        if (error.line > 0) {
            errMsg = String.format("[line %d] %s: %s", error.line, error.errorType, error.getMessage());
        } else {
            errMsg = String.format("%s: %s", error.errorType, error.getMessage());
        }
        
        if (runtimeErrorsSize == 0 || !runtimeErrors.get(runtimeErrorsSize - 1).equals(errMsg)) {
            runtimeErrors.add(errMsg);
        }
        hadRuntimeError = true;
    }
}