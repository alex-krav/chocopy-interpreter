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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScannerTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final Scanner scanner = new Scanner("");

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

    public static Stream<Arguments> tabsAndSpaces() {
        return Stream.of(
                Arguments.of(0, 0, 0),
                Arguments.of(0, 1, 1),
                Arguments.of(0, 10, 10),
                Arguments.of(1, 0, 8),
                Arguments.of(1, 1, 8),
                Arguments.of(1, 2, 8),
                Arguments.of(1, 3, 8),
                Arguments.of(1, 4, 8),
                Arguments.of(1, 5, 8),
                Arguments.of(1, 6, 8),
                Arguments.of(1, 7, 8),
                Arguments.of(1, 8, 16),
                Arguments.of(1, 9, 16),
                Arguments.of(1, 15, 16),
                Arguments.of(1, 16, 24),
                Arguments.of(2, 0, 16),
                Arguments.of(2, 1, 16),
                Arguments.of(2, 7, 16),
                Arguments.of(2, 8, 24)
        );
    }

    @ParameterizedTest
    @MethodSource("tabsAndSpaces")
    public void replaceTabsTest(int tabs, int spaces, int expectedSpaces) {
        // Given
        scanner.tabs = tabs;
        scanner.spaces = spaces;

        // When
        scanner.replaceTabs();

        // Then
        assertEquals(expectedSpaces, scanner.spaces);
    }

    public static Stream<Arguments> sourceStrings() {
        return Stream.of(
                Arguments.of("", 1, new Token(TokenType.EOF, "", null, 1)),
                Arguments.of("\"hello\"", 2, new Token(TokenType.STRING, "\"hello\"", "hello", 1)),
                Arguments.of("\"Hello\\\t\\\"user\\\"\\\n\\\\bye!\"", 2, new Token(TokenType.STRING, "\"Hello\\\t\\\"user\\\"\\\n\\\\bye!\"", "Hello\t\"user\"\n\\bye!", 2))
        );
    }

    @ParameterizedTest
    @MethodSource("sourceStrings")
    public void scanStringTest(String source, int tokensNumber, Token firstToken) {
        // Given
        scanner.source = source;

        // When
        List<Token> tokens = scanner.scanTokens();

        // Then
        assertEquals(tokensNumber, tokens.size());
        assertEquals(firstToken, tokens.get(0));
    }

    public static Stream<Arguments> testFilesWithTokens() {
        return TestUtils.testFiles("test/resources/parsing");
    }

    @ParameterizedTest
    @MethodSource("testFilesWithTokens")
    public void scanTokensTest(Path inputPath) throws IOException {
        test(inputPath);
    }

    @Test
    public void testOneFile() throws IOException {
        Path resourcesPath = Paths.get("src","test/resources/parsing".split("/"));
        Path inputPath = resourcesPath.resolve("indentation_first_line.py");

        test(inputPath);
    }

    private void test(Path inputPath) throws IOException {
        // Given
        byte[] bytes = Files.readAllBytes(inputPath);
        String input = new String(bytes, Charset.defaultCharset());

        // When
        Scanner scanner = new Scanner(input);
        List<Token> tokens = scanner.scanTokens();
        for (Token token : tokens) {
            System.out.println(token);
        }

        // Then
        if (errContent.size() > 0) {
            bytes = Files.readAllBytes(Path.of(inputPath + ".errors"));
            String error = new String(bytes, Charset.defaultCharset());
            assertEquals(error, errContent.toString());
        } else {
            bytes = Files.readAllBytes(Paths.get(inputPath + ".tokens"));
            String output = new String(bytes, Charset.defaultCharset());
            assertEquals(output, outContent.toString());
        }
    }
}
