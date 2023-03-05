package org.chocopy;

import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestUtils {

    public static Stream<Arguments> testFiles(String dir, String resExt) {
        List<Path> inputData;
        final Path resourcesPath = Paths.get("src",dir.split(","));
        try (Stream<Path> walk = Files.walk(resourcesPath)) {
            inputData = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".py"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Arguments[] arguments = new Arguments[inputData.size()];
        for (int i = 0; i < inputData.size(); i++) {
            Path inputFilePath = inputData.get(i).toAbsolutePath();
            Path resFilePath = Paths.get(inputFilePath + resExt);
//            String[] segments = inputFilePath.toString().split("/");
//            String testName = segments[segments.length-2] + "/" + segments[segments.length-1];
            arguments[i] = Arguments.of(/*testName,*/ inputFilePath, resFilePath);
        }

        return Arrays.stream(arguments);
    }
}
