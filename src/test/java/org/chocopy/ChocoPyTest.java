package org.chocopy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChocoPyTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

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

    public static Stream<Arguments> testFilesForInterpreter() {
        return TestUtils.testFiles("test,resources,interpreter");
    }

    @Disabled
    @ParameterizedTest
    @MethodSource("testFilesForInterpreter")
    public void testInterpreter(Path inputPath, Path outputPath) throws IOException {
        ChocoPy.runFile(inputPath.toString());

        byte[] bytes = Files.readAllBytes(outputPath);
        String output = new String(bytes, Charset.defaultCharset());

        assertEquals(output, outContent.toString());
    }
}
