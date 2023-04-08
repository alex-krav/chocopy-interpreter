package org.chocopy;

import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestUtils {

    public static Stream<Arguments> testFiles(String dir) {
        List<Path> inputData;
        final Path resourcesPath = Paths.get("src",dir.split("/"));
        try (Stream<Path> walk = Files.walk(resourcesPath, 1)) {
            inputData = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".py"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return inputData.stream().map(Arguments::of);
    }
}
