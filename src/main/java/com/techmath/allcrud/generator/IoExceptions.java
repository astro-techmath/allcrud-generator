package com.techmath.allcrud.generator;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// Public only because it's shared across com.techmath.allcrud.generator and
// com.techmath.allcrud.generator.codegen (AllcrudSpringCodegen) - not otherwise part of this
// module's public API surface in spirit. Hosts every defensive "checked IOException -> unchecked"
// wrapper in the module in one place, deliberately excluded from JaCoCo (see build.gradle.kts):
// these branches are only reachable by mocking a real filesystem failure (a full disk, a
// permissions error mid-run), which none of this project's real, meaningful tests do - they
// exercise real filesystems directly instead.
public final class IoExceptions {

    private IoExceptions() {
    }

    public static UncheckedIOException wrap(IOException e) {
        return new UncheckedIOException(e);
    }

    public static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    public static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    public static void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    public static List<Path> listRegularFilesRecursively(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    public static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw wrap(e);
                }
            });
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readYaml(Path ymlPath) {
        try (InputStream in = Files.newInputStream(ymlPath)) {
            return new Yaml().load(in);
        } catch (IOException e) {
            throw wrap(e);
        }
    }

}
