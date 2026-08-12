package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Generates the VO for the example spec using the custom templates (src/main/resources/templates)
 * and confirms, by actually compiling the output and inspecting it via reflection, that it
 * implements AbstractEntityVO<Long> as expected. Compilation succeeding is itself part of the
 * proof: if the generated class declared "implements AbstractEntityVO<Long>" without honoring
 * the interface contract (getId/setId), javac would fail here first.
 *
 * This is the seed of job 1 growing beyond the core base-class shape check
 * (see CoreDependencyCompatTest) into "does the generator's actual output hold up".
 */
class GeneratedProductVoCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");
    private static final String GENERATED_SOURCE_DIR = "build/generated/test-product-vo";
    private static final String COMPILED_CLASSES_DIR = "build/generated/test-product-vo-classes";

    @Test
    void generatedProductVoImplementsAbstractEntityVoOfLong() throws Exception {
        EntityFixtures.copyInto(Path.of(GENERATED_SOURCE_DIR), "Product", "Order");
        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, Path.of(GENERATED_SOURCE_DIR), EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.POJO, GeneratedLayer.CONTROLLER),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        File generatedFile = new File(GENERATED_SOURCE_DIR, "org/openapitools/model/ProductVO.java");
        assertTrue(generatedFile.exists(), "Expected generated file at " + generatedFile.getAbsolutePath());

        Class<?> productVO = compileAndLoad(generatedFile);

        Type matchingInterface = null;
        for (Type genericInterface : productVO.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType
                    && parameterizedType.getRawType().getTypeName().equals("com.techmath.allcrud.entity.AbstractEntityVO")) {
                matchingInterface = parameterizedType;
                break;
            }
        }

        assertNotNull(matchingInterface,
                "ProductVO should implement AbstractEntityVO<?>, found: " + List.of(productVO.getGenericInterfaces()));

        Type idType = ((ParameterizedType) matchingInterface).getActualTypeArguments()[0];
        assertEquals("java.lang.Long", idType.getTypeName(), "AbstractEntityVO type argument should be Long");
    }

    private Class<?> compileAndLoad(File sourceFile) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        File classesOutputDir = new File(COMPILED_CLASSES_DIR);
        classesOutputDir.mkdirs();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), null)) {
            fileManager.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT, Collections.singletonList(classesOutputDir));

            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path")
            );

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile));

            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();

            if (!success) {
                String errors = diagnostics.getDiagnostics().stream()
                        .map(Diagnostic::toString)
                        .collect(Collectors.joining("\n"));
                fail("Failed to compile generated ProductVO.java:\n" + errors);
            }
        }

        URL classesUrl = toUrl(classesOutputDir.toPath());
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesUrl}, getClass().getClassLoader())) {
            return classLoader.loadClass("org.openapitools.model.ProductVO");
        }
    }

    private URL toUrl(Path path) throws MalformedURLException {
        return path.toUri().toURL();
    }

}
