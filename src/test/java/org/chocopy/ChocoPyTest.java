package org.chocopy;

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ChocoPyTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private String testCaseName;
    private Path inputPath;
    private Path outputPath;

    @Before
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @After
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    public ChocoPyTest(String testCaseName, Path inputPath, Path outputPath) {
        this.testCaseName = testCaseName;
        this.inputPath = inputPath;
        this.outputPath = outputPath;
    }

    @Parameterized.Parameters( name = "{0}" )
    public static Collection testFilesBoolean() {
        List<Path> inputData;
        final Path resourcesPath = Paths.get("src","test","resources");
        try (Stream<Path> walk = Files.walk(resourcesPath)) {
            inputData = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".py"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Object[][] params = new Object[inputData.size()][];
        for (int i = 0; i < inputData.size(); i++) {
            Path inputFilePath = inputData.get(i).toAbsolutePath();
            Path resFilePath = Paths.get(inputFilePath + ".res");
            String[] segments = inputFilePath.toString().split("/");
            String testName = segments[segments.length-2] + "/" + segments[segments.length-1];
            Object[] param = new Object[]{
                    testName, inputFilePath, resFilePath
            };
            params[i] = param;
        }

        return Arrays.asList(params);
    }

    @Test
    public void test() throws IOException {
        ChocoPy.runFile(inputPath.toString());

        byte[] bytes = Files.readAllBytes(outputPath);
        String output = new String(bytes, Charset.defaultCharset());

        assertEquals(output, outContent.toString());
    }
}
