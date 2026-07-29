package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Type;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves the allcrudPojoNamingStyle switch (AllcrudSpringCodegen#processOpts) actually
 * works for both values, by generating and compiling the POJO for real in each mode -
 * not just checking the template text renders without error.
 *
 * "VO" (default, unset) should produce ProductVO implementing AbstractEntityVO<Long>.
 * "DTO" should produce ProductDTO implementing AbstractEntityDTO<Long> - a DIFFERENT
 * interface, not just "still assignable to AbstractEntityVO because AbstractEntityDTO
 * extends it" (that inheritance is real, confirmed in the core, but checking the
 * directly-declared interface is what actually proves the switch changed something,
 * rather than silently falling back to VO).
 */
class PojoNamingStyleSwitchCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");
    private static final String COMPILED_CLASSES_DIR_PREFIX = "build/generated/test-pojo-naming-style-classes-";

    @Test
    void voStyleProducesProductVoImplementingAbstractEntityVo() throws Exception {
        assertPojoNamingStyle(PojoNamingStyle.VO, "ProductVO", "com.techmath.allcrud.entity.AbstractEntityVO");
    }

    @Test
    void dtoStyleProducesProductDtoImplementingAbstractEntityDto() throws Exception {
        assertPojoNamingStyle(PojoNamingStyle.DTO, "ProductDTO", "com.techmath.allcrud.entity.AbstractEntityDTO");
    }

    private void assertPojoNamingStyle(PojoNamingStyle namingStyle, String expectedClassSimpleName, String expectedInterface) throws Exception {
        String outputDir = "build/generated/test-pojo-naming-style-" + namingStyle.name().toLowerCase(Locale.ROOT);
        EntityFixtures.copyInto(Path.of(outputDir), "Product", "Order");
        AllcrudGenerator.generate(new GenerationRequest(SPEC, Path.of(outputDir), namingStyle,
                Set.of(GeneratedLayer.POJO, GeneratedLayer.CONTROLLER),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        File generatedFile = new File(outputDir, "org/openapitools/model/" + expectedClassSimpleName + ".java");
        assertTrue(generatedFile.exists(), "Expected generated file at " + generatedFile.getAbsolutePath());

        Class<?> pojoClass = compileAndLoad(generatedFile, "org.openapitools.model." + expectedClassSimpleName, namingStyle);

        List<String> declaredInterfaceNames = List.of(pojoClass.getInterfaces()).stream()
                .map(Class::getName)
                .collect(Collectors.toList());
        assertEquals(List.of(expectedInterface), declaredInterfaceNames);

        Type genericInterface = pojoClass.getGenericInterfaces()[0];
        assertTrue(genericInterface instanceof java.lang.reflect.ParameterizedType,
                "Expected a parameterized interface, found: " + genericInterface);
        Type idTypeArgument = ((java.lang.reflect.ParameterizedType) genericInterface).getActualTypeArguments()[0];
        assertEquals("java.lang.Long", idTypeArgument.getTypeName());
    }

    private Class<?> compileAndLoad(File sourceFile, String className, PojoNamingStyle namingStyle) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        File classesOutputDir = new File(COMPILED_CLASSES_DIR_PREFIX + namingStyle.name().toLowerCase(Locale.ROOT));
        classesOutputDir.mkdirs();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(classesOutputDir));

            List<String> options = List.of("-classpath", System.getProperty("java.class.path"));
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile));

            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();

            if (!success) {
                String errors = diagnostics.getDiagnostics().stream()
                        .map(Diagnostic::toString)
                        .collect(Collectors.joining("\n"));
                fail("Failed to compile generated " + className + ":\n" + errors);
            }
        }

        // Deliberately not closed: generic interface resolution (getGenericInterfaces())
        // is lazy and happens after this method returns, still using this loader.
        URL classesUrl = classesOutputDir.toURI().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[]{classesUrl}, getClass().getClassLoader());
        return classLoader.loadClass(className);
    }

}
