package org.chocopy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.chocopy.TestUtils.removeEmptyLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResolverTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
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
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    public static Stream<Arguments> testFilesWithInferredTypes() {
        return TestUtils.testFiles("test/resources/resolver");
    }

    @ParameterizedTest
    @MethodSource("testFilesWithInferredTypes")
    public void inferredTypesTest(Path inputPath) throws IOException {
        test(inputPath);
    }

    @Test
    public void testOneFile() throws IOException {
        Path resourcesPath = Paths.get("src","test/resources/resolver".split("/"));
        Path inputPath = resourcesPath.resolve("bad_class_attr.py");

        test(inputPath);
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

        // Then
        if (errContent.size() > 0) {
            bytes = Files.readAllBytes(Path.of(inputPath + ".errors"));
            String error = new String(bytes, Charset.defaultCharset());
            assertEquals(error, errContent.toString());
            return;
        } else {
            System.out.print(removeEmptyLines(astPrinter.print(statements)));

            bytes = Files.readAllBytes(Paths.get(inputPath + ".ast"));
            String output = new String(bytes, Charset.defaultCharset());
            assertEquals(output, outContent.toString());
        }
        
        // Given
        errContent.reset();
        outContent.reset();
        
        // When
        interpreter = new Interpreter();
        resolver = new Resolver(interpreter);
        resolver.resolveScript(statements);

        // Then
        if (errContent.size() > 0) {
            bytes = Files.readAllBytes(Path.of(inputPath + ".errors"));
            String error = new String(bytes, Charset.defaultCharset());
            assertEquals(error, errContent.toString());
        } else {
            System.out.print(removeEmptyLines(astPrinter.print(statements)));
            
            bytes = Files.readAllBytes(Paths.get(inputPath + ".ast.typed"));
            String output = new String(bytes, Charset.defaultCharset());
            assertEquals(output, outContent.toString());
        }
    }
}
