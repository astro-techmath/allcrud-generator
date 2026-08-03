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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Generates the Controller, Service, Repository and Converter for the example spec
 * (apiController.mustache, service.mustache, repository.mustache, converter.mustache) and
 * confirms, by compiling the output for real, that they extend
 * CrudController<Product, ProductVO, Long>, CrudService<Product, Long>,
 * EntityRepository<Product, Long> and implement Converter<Product, ProductVO, Long>
 * respectively - the last one throwing UnsupportedOperationException from both methods,
 * as designed (see converter.mustache).
 *
 * The entity name, POJO class name and ID type are resolved by AllcrudSpringCodegen from
 * the actual OpenAPI model (see that class) - not hardcoded in the templates. What's still
 * hand-written and provided as a fixture here (src/test/resources/fixtures) is the Product
 * entity itself - standing in for what a consuming project would provide, since entity
 * generation is out of scope. That fixture is explicitly marked as non-generated in its
 * own file header.
 *
 * Grows job 1 (see CoreDependencyCompatTest, GeneratedProductVoCompatTest) to cover the
 * Controller/Service/Repository/Converter inheritance shape too.
 */
class GeneratedControllerAndServiceCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");
    private static final String GENERATED_SOURCE_DIR = "build/generated/test-controller-service";
    private static final String COMPILED_CLASSES_DIR = "build/generated/test-controller-service-classes";

    @Test
    void generatedControllerExtendsCrudController() throws Exception {
        List<Class<?>> classes = generateCompileAndLoad();
        Class<?> controller = classes.stream()
                .filter(c -> c.getSimpleName().endsWith("Controller"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Generated controller class not found"));

        assertGenericSuperclass(controller, "com.techmath.allcrud.controller.CrudController",
                "org.openapitools.entity.Product", "org.openapitools.model.ProductVO", "java.lang.Long");
    }

    // Proves @RequestMapping is a plain literal ("/product"), not the old Spring property
    // placeholder ("${openapi.product.base-path:}") that silently resolved to an empty path
    // at runtime whenever nobody configured that property - the bug that motivated removing
    // it entirely. Reflection avoids needing spring-web's RequestMapping class on this
    // module's own test compile classpath (allcrud core declares it "runtime" scope only,
    // same reasoning as elsewhere in this test suite for entity/converter type names).
    @Test
    void generatedControllerRequestMappingIsALiteralPath() throws Exception {
        List<Class<?>> classes = generateCompileAndLoad();
        Class<?> controller = classes.stream()
                .filter(c -> c.getSimpleName().endsWith("Controller"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Generated controller class not found"));

        Class<? extends java.lang.annotation.Annotation> requestMappingClass =
                Class.forName("org.springframework.web.bind.annotation.RequestMapping")
                        .asSubclass(java.lang.annotation.Annotation.class);
        Object annotation = controller.getAnnotation(requestMappingClass);
        assertTrue(annotation != null, "Expected @RequestMapping on " + controller.getName());

        String[] paths = (String[]) requestMappingClass.getMethod("value").invoke(annotation);
        assertEquals(List.of("/product"), List.of(paths));
    }

    @Test
    void generatedServiceExtendsCrudService() throws Exception {
        List<Class<?>> classes = generateCompileAndLoad();
        Class<?> service = classes.stream()
                .filter(c -> c.getSimpleName().endsWith("Service"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Generated service class not found"));

        assertGenericSuperclass(service, "com.techmath.allcrud.service.CrudService",
                "org.openapitools.entity.Product", "java.lang.Long");
    }

    @Test
    void generatedRepositoryExtendsEntityRepository() throws Exception {
        List<Class<?>> classes = generateCompileAndLoad();
        Class<?> repository = classes.stream()
                .filter(c -> c.getSimpleName().endsWith("Repository"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Generated repository class not found"));

        Type[] genericInterfaces = repository.getGenericInterfaces();
        assertTrue(genericInterfaces.length == 1 && genericInterfaces[0] instanceof ParameterizedType,
                repository.getName() + " should have exactly one parameterized interface, found: " + List.of(genericInterfaces));

        ParameterizedType parameterizedType = (ParameterizedType) genericInterfaces[0];
        assertEquals("com.techmath.allcrud.repository.EntityRepository", parameterizedType.getRawType().getTypeName());
        assertEquals(List.of("org.openapitools.entity.Product", "java.lang.Long"),
                List.of(parameterizedType.getActualTypeArguments()[0].getTypeName(),
                        parameterizedType.getActualTypeArguments()[1].getTypeName()));
    }

    @Test
    void generatedConverterMethodsThrowUnsupportedOperationException() throws Exception {
        List<Class<?>> classes = generateCompileAndLoad();
        Class<?> converterClass = classes.stream()
                .filter(c -> c.getSimpleName().endsWith("Converter"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Generated converter class not found"));

        Object converter = converterClass.getDeclaredConstructor().newInstance();

        Method convertToVO = findMethod(converterClass, "convertToVO");
        UnsupportedOperationException toVoException = assertThrows(UnsupportedOperationException.class,
                () -> invoke(converter, convertToVO, (Object) null));
        assertTrue(toVoException.getMessage().contains("ProductConverter"),
                "Expected message to name the converter class, was: " + toVoException.getMessage());

        Method convertToEntity = findMethod(converterClass, "convertToEntity");
        UnsupportedOperationException toEntityException = assertThrows(UnsupportedOperationException.class,
                () -> invoke(converter, convertToEntity, (Object) null));
        assertTrue(toEntityException.getMessage().contains("ProductConverter"),
                "Expected message to name the converter class, was: " + toEntityException.getMessage());
    }

    private Method findMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError(type.getName() + " should declare a method named " + name);
    }

    private void invoke(Object target, Method method, Object arg) throws Throwable {
        try {
            method.invoke(target, arg);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void assertGenericSuperclass(Class<?> generatedClass, String expectedRawType, String... expectedTypeArgs) {
        Type genericSuperclass = generatedClass.getGenericSuperclass();
        assertTrue(genericSuperclass instanceof ParameterizedType,
                generatedClass.getName() + " should have a parameterized superclass, found: " + genericSuperclass);

        ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
        assertEquals(expectedRawType, parameterizedType.getRawType().getTypeName());

        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        String[] actualTypeNames = new String[actualTypeArguments.length];
        for (int i = 0; i < actualTypeArguments.length; i++) {
            actualTypeNames[i] = actualTypeArguments[i].getTypeName();
        }

        assertEquals(List.of(expectedTypeArgs), List.of(actualTypeNames));
    }

    private List<Class<?>> generateCompileAndLoad() throws Exception {
        EntityFixtures.copyInto(Path.of(GENERATED_SOURCE_DIR), "Product", "Order");

        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, Path.of(GENERATED_SOURCE_DIR), EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.values()),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api",
                        GeneratedLayer.SERVICE, "org.openapitools.api",
                        GeneratedLayer.REPOSITORY, "org.openapitools.api",
                        GeneratedLayer.CONVERTER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        File modelFile = new File(GENERATED_SOURCE_DIR, "org/openapitools/model/ProductVO.java");
        File controllerFile = new File(GENERATED_SOURCE_DIR, "org/openapitools/api/ProductController.java");
        File serviceFile = new File(GENERATED_SOURCE_DIR, "org/openapitools/api/ProductService.java");
        File repositoryFile = new File(GENERATED_SOURCE_DIR, "org/openapitools/api/ProductRepository.java");
        File converterFile = new File(GENERATED_SOURCE_DIR, "org/openapitools/api/ProductConverter.java");

        for (File generated : List.of(modelFile, controllerFile, serviceFile, repositoryFile, converterFile)) {
            assertTrue(generated.exists(), "Expected generated file at " + generated.getAbsolutePath());
        }

        File productFixture = new File(GENERATED_SOURCE_DIR, "org/openapitools/entity/Product.java");

        List<File> sourceFiles = List.of(
                modelFile, controllerFile, serviceFile, repositoryFile, converterFile,
                productFixture
        );

        return compileAndLoad(sourceFiles,
                "org.openapitools.api.ProductController",
                "org.openapitools.api.ProductService",
                "org.openapitools.api.ProductRepository",
                "org.openapitools.api.ProductConverter");
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
                fail("Failed to compile generated Controller/Service:\n" + errors);
            }
        }

        // Deliberately not closed: generic superclass/interface resolution (getGenericSuperclass(),
        // getActualTypeArguments()) is lazy and happens later in the caller, still using this
        // loader as the defining classloader - closing it here would break that resolution.
        URL classesUrl = classesOutputDir.toURI().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[]{classesUrl}, getClass().getClassLoader());
        List<Class<?>> loaded = new java.util.ArrayList<>();
        for (String className : classNamesToLoad) {
            loaded.add(classLoader.loadClass(className));
        }
        return loaded;
    }

}
