package org.chocopy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.chocopy.TestUtils.removeEmptyLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InterpreterTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private Scanner scanner;
    private Parser parser;
    private Resolver resolver;
    private Interpreter interpreter;
    private final AstPrinter astPrinter = new AstPrinter();

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    public static Stream<Arguments> testFilesForInterpreter() {
        return TestUtils.testFiles("test/resources/interpreter/sample");
    }

    public static Stream<Arguments> testFilesForBenchmark() {
        return TestUtils.testFiles("test/resources/interpreter/benchmark");
    }

    @ParameterizedTest
    @MethodSource("testFilesForInterpreter")
    public void testInterpreter(Path inputPath) throws IOException {
        test(inputPath);
    }

    @ParameterizedTest
    @MethodSource("testFilesForBenchmark")
    public void testBenchmark(Path inputPath) throws IOException {
        test(inputPath);
    }

    @Test
    public void testOneFile() throws IOException {
        Path resourcesPath = Paths.get("src","test/resources/interpreter/sample".split("/"));
        Path inputPath = resourcesPath.resolve("call.py");

        test(inputPath);
    }

    @Test
    public void testInput() throws IOException {
        Path resourcesPath = Paths.get("src","test/resources/interpreter".split("/"));
        Path inputPath = resourcesPath.resolve("input.py");

        InputStream stdin = System.in;
        try {
            System.setIn(new ByteArrayInputStream(Files.readAllBytes(
                    Paths.get("src/test/resources/interpreter/input.py.in"))));
            test(inputPath);
        } finally {
            System.setIn(stdin);
        }
    }

    private void test(Path inputPath) throws IOException {
        // Given
        byte[] bytes = Files.readAllBytes(inputPath);
        String input = new String(bytes, Charset.defaultCharset());

        // When
        scanner = new Scanner(input);
        List<Token> tokens = scanner.scanTokens();
        parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        if (ChocoPy.errors.size() > 0) {
            ChocoPy.errors.forEach(System.err::println);
            bytes = Files.readAllBytes(Path.of(inputPath + ".ast.typed.s.result"));
            String error = new String(bytes, Charset.defaultCharset());
            assertEquals(error, outContent.toString());
            return;
        }
        
        interpreter = new Interpreter();
        resolver = new Resolver();
        resolver.resolveScript(statements);
        
        // Then
        if (ChocoPy.errors.size() > 0) {
            ChocoPy.errors.forEach(System.err::println);
            
            bytes = Files.readAllBytes(Path.of(inputPath + ".ast.typed.s.result"));
            String error = new String(bytes, Charset.defaultCharset());
            assertEquals(error, outContent.toString());
            return;
        } else {
            System.out.print(removeEmptyLines(astPrinter.print(statements)));

            bytes = Files.readAllBytes(Paths.get(inputPath + ".ast.typed"));
            String output = new String(bytes, Charset.defaultCharset());
            assertEquals(output, outContent.toString());
        }

        // Given
        outContent.reset();

        // When
        interpreter.interpret(statements);

        // Then
        if (ChocoPy.errors.size() > 0 || ChocoPy.runtimeErrors.size() > 0) {
            ChocoPy.errors.forEach(System.err::println);
            ChocoPy.runtimeErrors.forEach(System.err::println);
        }
        
        bytes = Files.readAllBytes(Paths.get(inputPath + ".ast.typed.s.result"));
        String output = new String(bytes, Charset.defaultCharset());
        assertEquals(output, outContent.toString());
    }
}
