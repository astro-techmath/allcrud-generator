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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * product-example.yaml now describes TWO resources (Product, Order) generated in a
 * SINGLE DefaultGenerator#generate() call - everything tested so far only ever exercised
 * one resource per run. AllcrudSpringCodegen carries instance state built up while
 * processing (entityNameByApiName in toApiFilename, the per-tag resolution in
 * postProcessOperationsWithModels) that was never proven not to leak between resources
 * processed in the same run (e.g. Product's ID type or entity name bleeding into Order's
 * output, or vice versa).
 *
 * Product uses Long for its ID, Order deliberately uses Integer (see the Order fixture) -
 * if any state leaked between resources, the ID type is exactly where it would show up.
 *
 * All four generated artifacts (Controller/Service/Repository/Converter) for BOTH
 * resources are compiled together, in one javac invocation, alongside both entity
 * fixtures - proving they coexist without name/package collisions too.
 */
class MultiResourceCompatTest {

    private static final String GENERATED_SOURCE_DIR = "build/generated/test-multi-resource";
    private static final String COMPILED_CLASSES_DIR = "build/generated/test-multi-resource-classes";

    @Test
    void productArtifactsAreCorrectWhenGeneratedAlongsideOrder() throws Exception {
        Map<String, Class<?>> classes = generateCompileAndLoad();

        assertGenericSuperclass(classes.get("ProductController"), "com.techmath.allcrud.controller.CrudController",
                "org.openapitools.entity.Product", "org.openapitools.model.ProductVO", "java.lang.Long");
        assertGenericSuperclass(classes.get("ProductService"), "com.techmath.allcrud.service.CrudService",
                "org.openapitools.entity.Product", "java.lang.Long");
        assertGenericInterface(classes.get("ProductRepository"), "com.techmath.allcrud.repository.EntityRepository",
                "org.openapitools.entity.Product", "java.lang.Long");
        assertConverterThrows(classes.get("ProductConverter"), "ProductConverter");
    }

    @Test
    void orderArtifactsAreCorrectWhenGeneratedAlongsideProduct() throws Exception {
        Map<String, Class<?>> classes = generateCompileAndLoad();

        assertGenericSuperclass(classes.get("OrderController"), "com.techmath.allcrud.controller.CrudController",
                "org.openapitools.entity.Order", "org.openapitools.model.OrderVO", "java.lang.Integer");
        assertGenericSuperclass(classes.get("OrderService"), "com.techmath.allcrud.service.CrudService",
                "org.openapitools.entity.Order", "java.lang.Integer");
        assertGenericInterface(classes.get("OrderRepository"), "com.techmath.allcrud.repository.EntityRepository",
                "org.openapitools.entity.Order", "java.lang.Integer");
        assertConverterThrows(classes.get("OrderConverter"), "OrderConverter");
    }

    private void assertConverterThrows(Class<?> converterClass, String expectedNameInMessage) throws Exception {
        Object converter = converterClass.getDeclaredConstructor().newInstance();

        Method convertToVO = findMethod(converterClass, "convertToVO");
        UnsupportedOperationException toVoException = assertThrows(UnsupportedOperationException.class,
                () -> invoke(converter, convertToVO));
        assertTrue(toVoException.getMessage().contains(expectedNameInMessage),
                "Expected message to name " + expectedNameInMessage + ", was: " + toVoException.getMessage());

        Method convertToEntity = findMethod(converterClass, "convertToEntity");
        UnsupportedOperationException toEntityException = assertThrows(UnsupportedOperationException.class,
                () -> invoke(converter, convertToEntity));
        assertTrue(toEntityException.getMessage().contains(expectedNameInMessage),
                "Expected message to name " + expectedNameInMessage + ", was: " + toEntityException.getMessage());
    }

    private Method findMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError(type.getName() + " should declare a method named " + name);
    }

    private void invoke(Object target, Method method) throws Throwable {
        try {
            method.invoke(target, (Object) null);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void assertGenericSuperclass(Class<?> generatedClass, String expectedRawType, String... expectedTypeArgs) {
        Type genericSuperclass = generatedClass.getGenericSuperclass();
        assertTrue(genericSuperclass instanceof ParameterizedType,
                generatedClass.getName() + " should have a parameterized superclass, found: " + genericSuperclass);
        assertMatches((ParameterizedType) genericSuperclass, expectedRawType, expectedTypeArgs);
    }

    private void assertGenericInterface(Class<?> generatedClass, String expectedRawType, String... expectedTypeArgs) {
        Type[] genericInterfaces = generatedClass.getGenericInterfaces();
        assertTrue(genericInterfaces.length == 1 && genericInterfaces[0] instanceof ParameterizedType,
                generatedClass.getName() + " should have exactly one parameterized interface, found: " + List.of(genericInterfaces));
        assertMatches((ParameterizedType) genericInterfaces[0], expectedRawType, expectedTypeArgs);
    }

    private void assertMatches(ParameterizedType parameterizedType, String expectedRawType, String... expectedTypeArgs) {
        assertEquals(expectedRawType, parameterizedType.getRawType().getTypeName());

        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        String[] actualTypeNames = new String[actualTypeArguments.length];
        for (int i = 0; i < actualTypeArguments.length; i++) {
            actualTypeNames[i] = actualTypeArguments[i].getTypeName();
        }
        assertEquals(List.of(expectedTypeArgs), List.of(actualTypeNames));
    }

    private Map<String, Class<?>> generateCompileAndLoad() throws Exception {
        // Single generate() call for both resources - the whole point of this test.
        ProductExampleGeneration.generate(GENERATED_SOURCE_DIR, true);

        List<String> generatedSimpleNames = List.of(
                "ProductController", "ProductService", "ProductRepository", "ProductConverter",
                "OrderController", "OrderService", "OrderRepository", "OrderConverter"
        );

        List<File> sourceFiles = new java.util.ArrayList<>();
        sourceFiles.add(new File(GENERATED_SOURCE_DIR, "src/main/java/org/openapitools/model/ProductVO.java"));
        sourceFiles.add(new File(GENERATED_SOURCE_DIR, "src/main/java/org/openapitools/model/OrderVO.java"));
        for (String simpleName : generatedSimpleNames) {
            sourceFiles.add(new File(GENERATED_SOURCE_DIR, "src/main/java/org/openapitools/api/" + simpleName + ".java"));
        }
        for (File generated : sourceFiles) {
            assertTrue(generated.exists(), "Expected generated file at " + generated.getAbsolutePath());
        }

        sourceFiles.add(fixtureFile("fixtures/Product.java"));
        sourceFiles.add(fixtureFile("fixtures/Order.java"));

        List<String> classNamesToLoad = generatedSimpleNames.stream()
                .map(simpleName -> "org.openapitools.api." + simpleName)
                .collect(Collectors.toList());

        List<Class<?>> loaded = compileAndLoad(sourceFiles, classNamesToLoad.toArray(new String[0]));

        Map<String, Class<?>> bySimpleName = new java.util.HashMap<>();
        for (Class<?> loadedClass : loaded) {
            bySimpleName.put(loadedClass.getSimpleName(), loadedClass);
        }
        return bySimpleName;
    }

    private File fixtureFile(String classpathResource) throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource(classpathResource);
        assertTrue(resource != null, "Expected fixture on test classpath: " + classpathResource);
        return new File(resource.toURI());
    }

    private List<Class<?>> compileAndLoad(List<File> sourceFiles, String... classNamesToLoad) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        File classesOutputDir = new File(COMPILED_CLASSES_DIR);
        classesOutputDir.mkdirs();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(classesOutputDir));

            List<String> options = List.of("-classpath", System.getProperty("java.class.path"));
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);

            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();

            if (!success) {
                String errors = diagnostics.getDiagnostics().stream()
                        .map(Diagnostic::toString)
                        .collect(Collectors.joining("\n"));
                fail("Failed to compile generated multi-resource output:\n" + errors);
            }
        }

        // Deliberately not closed: generic superclass/interface resolution is lazy and
        // happens later in the caller, still using this loader as the defining classloader.
        URL classesUrl = classesOutputDir.toURI().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[]{classesUrl}, getClass().getClassLoader());
        List<Class<?>> loaded = new java.util.ArrayList<>();
        for (String className : classNamesToLoad) {
            loaded.add(classLoader.loadClass(className));
        }
        return loaded;
    }

}
