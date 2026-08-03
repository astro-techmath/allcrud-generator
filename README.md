# Allcrud Generator
![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/spring_boot-%236DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/gradle-%2302303A.svg?style=for-the-badge&logo=gradle&logoColor=white)

---

## 📃 About

**Allcrud Generator** is a contract-first code generator for [Allcrud](https://github.com/astro-techmath/allcrud). It reads an OpenAPI spec plus an `allcrud-generator.yml` config file and generates the Controller/Service/Repository/Converter/POJO stack for each resource - already wired to extend/implement Allcrud's own base classes (`CrudController<T, VO, ID>`, `CrudService<T, ID>`, `EntityRepository<T, ID>`, `Converter<T, VO, ID>`).

Most generated files are scaffolding meant to be hand-edited afterward (a `Converter` starts out throwing `UnsupportedOperationException` on purpose, waiting for you to fill in the mapping) and are **never overwritten** on a later run once they exist. The OpenAPI spec's shape and `allcrud-generator.yml` decide what gets (re)generated - the Entity itself is always hand-written and out of this generator's scope.

Lives in its own repo/artifact so it never becomes a transitive dependency of projects that only consume `allcrud` at runtime.

---

## ✨ Features

- **Contract-first**: an OpenAPI spec is the single source of truth for what gets generated - not annotations sprinkled across hand-written classes.
- **7 artifact types per run**: POJO, Repository, Converter, Service, Controller, a project-wide `exceptionHandler`, and optional `unitTest`/`integrationTest` classes.
- **Scaffolding, preserved by default**: Repository/Converter/Service/Controller are never overwritten once they exist, so hand-written business logic on top of them survives every regeneration.
- **POJO overwrite is the one configurable exception**, per resource, via `onRegenerate: preserve` (default) or `overwrite`.
- **Package placement is configurable per layer, globally**, with a per-resource override escape hatch on all 7 layers when one resource needs to live somewhere different.
- **`@RequestMapping` base path is configurable** globally (a prefix) or per resource (a final, absolute override).
- **Automatic resource inference** (`x-allcrud-auto-resource`) is available as an opt-in alternative to marking every path by hand - see the [yml reference](#-allcrud-generatoryml-reference) below.
- **Fails fast, loudly**, instead of emitting broken Java: a resource with no `id` property is rejected at generation time with a message naming the resource, not a mysterious `javac` error downstream.

---

## ⚠️ Prerequisites / Project Setup

Before applying the plugin, your consumer project needs:

- **The Allcrud core dependency, declared by you.** The plugin generates code that extends/implements Allcrud's classes, but it does **not** add the Allcrud dependency to your build automatically. You must declare it yourself:

  ```kotlin
  dependencies {
      implementation("io.github.astro-techmath:allcrud:<version>")
  }
  ```

  If you generate `unitTest` and/or `integrationTest` (see below), you also need Allcrud's test-fixtures variant, which is where `CrudServiceTests`/`CrudControllerIntegrationTests` (the base classes the generated tests extend) live:

  ```kotlin
  dependencies {
      testImplementation(testFixtures("io.github.astro-techmath:allcrud:<version>"))
  }
  ```

  Skipping either of these is a real, easy mistake to make - the generator has no way to detect it at generation time, and the failure only shows up later as a compile error (`cannot find symbol: class CrudController`) or a missing test base class.

- **Docker, if you generate `integrationTest`.** The generated `*ControllerIT` classes extend `CrudControllerIntegrationTests`, which spins up a real PostgreSQL container via Testcontainers. If Docker isn't running when those tests execute, the build fails with a Testcontainers connection error that gives no hint the actual problem is "Docker isn't running." `unitTest` has no such requirement (it mocks the repository).

---

## 📦 Quick Start

1. Apply the plugin in your `build.gradle.kts`:

   ```kotlin
   plugins {
       java
       id("io.github.astro-techmath.allcrud-generator") version "<version>"
   }

   dependencies {
       implementation("io.github.astro-techmath:allcrud:<version>")
       testImplementation(testFixtures("io.github.astro-techmath:allcrud:<version>"))
   }

   allcrudGenerator {
       specFile.set(file("src/main/resources/api-spec.yaml"))
   }
   ```

2. Write your OpenAPI spec. `x-allcrud-auto-resource: true` at the document root turns on automatic resource inference - a path pair matching the `CrudController` shape (collection + item) becomes a resource with no per-path marker needed. A path that matches the shape can still opt out with `x-allcrud-resource: false` (an internal/admin-only path, for example). `x-allcrud-resource: true` on its own, without the global flag, is the 100%-explicit alternative - it works today with no extra config. See the vendor extensions table below for both:

   ```yaml
   openapi: 3.0.3
   info:
     title: Product API
     version: 1.0.0
   x-allcrud-auto-resource: true
   paths:
     /products:
       get:
         operationId: findAllProducts
         responses:
           '200':
             description: List
             content:
               application/json:
                 schema:
                   type: array
                   items:
                     $ref: '#/components/schemas/Product'
     /products/{id}:
       get:
         operationId: findProductById
         parameters:
           - name: id
             in: path
             required: true
             schema: { type: integer, format: int64 }
         responses:
           '200':
             description: Found
             content:
               application/json:
                 schema:
                   $ref: '#/components/schemas/Product'
   components:
     schemas:
       Product:
         type: object
         properties:
           id: { type: integer, format: int64, readOnly: true }
           name: { type: string }
   ```

   ### 📎 OpenAPI spec vendor extensions

   | Extension | Description | Possible values | Default | Required |
   |---|---|---|---|---|
   | `x-allcrud-auto-resource` | Document root. Turns on automatic resource inference for the whole spec. | `true`, `false` | `false` | No |
   | `x-allcrud-resource` | Per path. Marks (or, with inference on, un-marks) a path as an allcrud resource. Explicit value always wins over inference. | `true`, `false` | Absent (falls back to inference's result, or to "not a resource" with inference off) | No |
   | `x-allcrud-id-type` | Per path. Manual override for the resolved ID type, for the rare case where the `id` property's schema type isn't a reliable signal on its own. | Any Java type name, e.g. `Long`, `java.util.UUID` | Absent (resolved from the `id` property's schema) | No |

3. Write `allcrud-generator.yml` at your project root (see the full reference below):

   ```yaml
   pojoNamingStyle: VO
   generation:
     pojo:
       package: com.acme.dto
     repository:
       package: com.acme.persistence
     converter:
       package: com.acme.persistence
     service:
       package: com.acme.service
     controller:
       package: com.acme.web
   ```

4. Hand-write the `Product` entity (Entity generation is out of scope - see [Known Limitations](#-known-limitations-v1)):

   ```java
   package com.acme.catalog;

   import com.techmath.allcrud.entity.AbstractEntity;
   import jakarta.persistence.Entity;
   import jakarta.persistence.Id;

   @Entity
   public class Product implements AbstractEntity<Long> {
       @Id
       private Long id;
       private String name;
       // getters/setters, equals/hashCode/toString
   }
   ```

5. Run the generator:

   ```
   ./gradlew generateAllcrud
   ```

   `compileJava`/`compileTestJava` depend on it automatically, so a plain `./gradlew build` also works once the plugin is applied.

6. This is what actually comes out the other end - a real, compiling `ProductController.java`, already a Spring bean, already returning JSON:

   ```java
   package com.acme.web;

   import com.techmath.allcrud.controller.CrudController;
   import com.techmath.allcrud.converter.Converter;
   import com.techmath.allcrud.service.CrudService;
   import com.acme.dto.ProductVO;
   import com.acme.catalog.Product;
   import com.acme.service.ProductService;
   import com.acme.persistence.ProductConverter;

   import org.springframework.web.bind.annotation.RestController;
   import org.springframework.web.bind.annotation.RequestMapping;

   // Generated by allcrud-generator - customize the implementation below.
   @RestController
   @RequestMapping("/product")
   public class ProductController extends CrudController<Product, ProductVO, Long> {

       private final ProductService service;
       private final ProductConverter converter;

       public ProductController(ProductService service, ProductConverter converter) {
           this.service = service;
           this.converter = converter;
       }

       @Override
       protected CrudService<Product, Long> getService() {
           return service;
       }

       @Override
       protected Converter<Product, ProductVO, Long> getConverter() {
           return converter;
       }

   }
   ```

   Service and Converter follow the same shape: an annotated Spring bean (`@Service`, `@Component`), constructor injection, ready to customize. Repository needs no annotation - Spring Data JPA auto-detects it.

---

## 🧩 Generated Artifacts

| Artifact | Scope | Overwrite policy |
|---|---|---|
| POJO (VO or DTO) | Per-resource | Configurable via `onRegenerate` (`preserve`, default, or `overwrite`) |
| Repository | Per-resource | Always preserved once it exists |
| Converter | Per-resource | Always preserved once it exists |
| Service | Per-resource | Always preserved once it exists |
| Controller | Per-resource | Always preserved once it exists |
| `exceptionHandler` (`GlobalExceptionHandler` `@ControllerAdvice` stub) | **Global** - one per project, not per-resource | Always preserved once it exists |
| `unitTest` (`CrudServiceTests` subclass) | Per-resource | Always preserved once it exists |
| `integrationTest` (`CrudControllerIntegrationTests` subclass) | Per-resource | Always preserved once it exists |

Notes:

- Only the POJO's overwrite behavior is configurable - everything else is scaffolding meant to carry hand-written logic (`@Query` methods, business rules, custom endpoints), so a blind overwrite would silently destroy it. "Preserve" for POJO is also the default: overwrite is strictly opt-in, never assumed.
- `unitTest`/`integrationTest` always land under your test source root (`src/test/java` by default), never under the production source root - this isn't configurable.
- `exceptionHandler` is generated once, independent of the OpenAPI spec's content - it's the one artifact that isn't tied to a resource at all.

---

## ⚙️ allcrud-generator.yml Reference

**Top-level configuration:**

| Property | Description | Possible values | Default | Required |
|---|---|---|---|---|
| `pojoNamingStyle` | Global naming style for the generated POJO class (`ProductVO` vs `ProductDTO`). No per-resource override. | `VO`, `DTO` | - | **Yes** |
| `routing.basePathPrefix` | Prefix prepended to every resource's `@RequestMapping` path, unless overridden by `resources.<name>.basePath`. | Any string, e.g. `/v1` | `""` (no prefix) | No |
| `exceptionHandler.generate` | Whether to generate the project-wide `GlobalExceptionHandler` stub. | `true`, `false` | `true` | No |
| `exceptionHandler.package` | Target package for the generated exception handler. | Any Java package | `generation.controller.package`'s value | No (required only if `generation.controller.package` is ever left unset too) |
| `exceptionHandler.className` | Class name for the generated exception handler. | Any valid Java class name | `GlobalExceptionHandler` | No |

**Per-layer defaults (`generation.<layer>.*`)** - one row per property, applying uniformly to all 7 layers (`pojo`, `repository`, `converter`, `service`, `controller`, `unitTest`, `integrationTest`):

| Property | Description | Possible values | Default | Required |
|---|---|---|---|---|
| `generation.<layer>.enabled` | Whether this layer is generated globally. | `true`, `false` | `true` for the 5 production layers; **`false`** for `unitTest`/`integrationTest` | No |
| `generation.<layer>.package` | Global target package for this layer. | Any Java package | - | **Yes, if `enabled` is true** - and only for the 5 production layers, see note below |
| `generation.<layer>.onRegenerate` | Global overwrite policy for this layer. | `preserve`, `overwrite` | `preserve` | No - and only ever valid on `pojo`, see note below |

Variations the table above doesn't show per-layer:
- `enabled` defaults to `true` for `pojo`/`repository`/`converter`/`service`/`controller`, and to `false` for `unitTest`/`integrationTest` - opting into either test layer is always explicit.
- `package` only exists as a **global** key for the 5 production layers. `unitTest`/`integrationTest` never accept `generation.unitTest.package`/`generation.integrationTest.package` at all - their package is always computed dynamically, per resource, from that resource's own resolved `service`/`controller` package (see `resources.<name>.<layer>.package` below for the one place to override it).
- `onRegenerate` is exclusive to `pojo` - no other layer accepts this key, globally or per resource.

**Per-resource overrides (`resources.<name>.*`):**

| Property | Description | Possible values | Default | Required |
|---|---|---|---|---|
| `resources.<name>.<layer>.enabled` | Per-resource override of any of the 7 layers' `enabled` flag. | `true`, `false` | Inherits `generation.<layer>.enabled` | No |
| `resources.<name>.<layer>.package` | Per-resource override of any of the 7 layers' target package. The only place `unitTest`/`integrationTest` ever accept a `package` at all - there's no global `generation.unitTest.package`/`generation.integrationTest.package` key (see the note above). | Any Java package | For the 5 production layers: inherits `generation.<layer>.package`. For `unitTest`/`integrationTest`: this resource's own resolved `service`/`controller` package | No |
| `resources.<name>.pojo.onRegenerate` | Per-resource overwrite policy override for the POJO layer. | `preserve`, `overwrite` | Inherits `generation.pojo.onRegenerate` | No |
| `resources.<name>.basePath` | Final, absolute `@RequestMapping` path for this resource - not concatenated with `routing.basePathPrefix`. | Any string, e.g. `/custom/orders` | Computed from `routing.basePathPrefix` + the resource name | No |

```yaml
pojoNamingStyle: VO

routing:
  basePathPrefix: /v1

exceptionHandler:
  generate: true
  package: com.acme.web
  className: GlobalExceptionHandler

generation:
  pojo:
    enabled: true
    package: com.acme.dto
    onRegenerate: preserve
  repository:
    enabled: true
    package: com.acme.persistence
  converter:
    enabled: true
    package: com.acme.persistence
  service:
    enabled: true
    package: com.acme.service
  controller:
    enabled: true
    package: com.acme.web
  unitTest:
    enabled: false   # no "package" key here - see "unitTest/integrationTest never
                       # take a global package" below
  integrationTest:
    enabled: false

resources:
  # Optional - only exceptions to the global "generation" block above.
  Order:
    converter:
      enabled: false
    service:
      enabled: false
    controller:
      enabled: false   # CONTROLLER requires SERVICE+CONVERTER+POJO - can't disable
                         # one without the other
    basePath: /custom/orders
  Product:
    pojo:
      package: com.acme.catalog.dto
      onRegenerate: overwrite
    repository:
      package: com.acme.catalog.persistence
    unitTest:
      enabled: true
      package: com.acme.catalog.test.unit
```

Key rules:

- **Every one of the 7 layers toggles independently via its own `enabled` flag** - global in `generation.<layer>.enabled`, overridable per resource in `resources.<name>.<layer>.enabled`. A resource absent from `resources:` inherits every global default as-is.
- **The 5 production layers default to `enabled: true`; `unitTest`/`integrationTest` default to `enabled: false`.** Opting into either test layer is always explicit, per project or per resource.
- **Layer dependencies are validated eagerly**, at config-load time, against the fully resolved `enabled` set - both globally and for every resource's own resolved set. Missing a dependency fails immediately with a clear message, instead of surfacing as a confusing compile error later:

  | Layer | Depends on |
  |---|---|
  | `pojo` | — |
  | `repository` | — |
  | `converter` | `pojo` |
  | `service` | `repository` |
  | `controller` | `service`, `converter`, `pojo` |
  | `unitTest` | `service` |
  | `integrationTest` | `controller` |

- **A production layer's `package` is required exactly when that layer is enabled** - globally if `generation.<layer>.enabled` is true, or per resource if that resource enables it without a global package configured.
- **`unitTest`/`integrationTest` never take a global `package`.** Their package is always computed dynamically, per resource, from that resource's own resolved `service`/`controller` package - the only place to override it is `resources.<name>.unitTest.package`/`resources.<name>.integrationTest.package`.

### 📍 `x-allcrud-auto-resource` — lives in your OpenAPI spec, NOT in `allcrud-generator.yml`

> **This is a document-root vendor extension in your OpenAPI spec file** (`api-spec.yaml`, alongside `openapi:`/`info:`/`paths:`) - it is never a key in `allcrud-generator.yml`. It's documented here, next to the rest of the config reference, purely so it's easy to find in one place. See the [Quick Start](#-quick-start)'s spec example and [vendor extensions table](#-openapi-spec-vendor-extensions) for the full walk-through and detection rules.

`x-allcrud-resource` (explicit or inferred) is the actual gate on generation: only a confirmed resource gets a Repository/Converter/Service/Controller. The POJO is the one exception - it's generated from the OpenAPI schema directly, independent of whether any path references it as a CRUD resource.

---

## 🔌 Gradle Plugin Extension Reference

Plugin ID: `io.github.astro-techmath.allcrud-generator`. Configuration block: `allcrudGenerator { ... }`.

| Property | Required | Default |
|---|---|---|
| `specFile` | **Yes** - no default, task fails with a clear message if unset | - |
| `configFile` | No | `<project root>/allcrud-generator.yml` |
| `outputDir` | No | `src/main/java` |
| `testOutputDir` | No | `src/test/java` |

`outputDir`/`testOutputDir` are the Java source roots the generator writes to directly (not an intermediate `build/generated/...` scratch directory) - both source sets are wired automatically, so `compileJava`/`compileTestJava` pick up the generated files with no extra configuration.

---

## 🧭 Design Decisions

The reasoning behind some of the choices above, for when the "what" isn't enough to predict the "why":

- ✅ **`generation.<layer>.package` is global by default, with a per-resource override as the escape hatch.** A project with hundreds of resources declaring the same 5 package paths hundreds of times over would be pure noise - the global default covers the common case, and `resources.<name>.<layer>.package` exists for the resources that genuinely need to live somewhere else.
- ✅ **Each of the 7 layers toggles independently via its own `enabled` flag, at both the global and per-resource level, instead of a single list a resource replaces wholesale.** A per-resource layer list that either fully replaced or fully inherited the global set couldn't express "disable just this one layer for this one resource" without repeating every other layer's name back verbatim - independent per-layer flags can.
- ✅ **`exceptionHandler` is the only artifact that defaults to `generate: true`** (every other artifact defaults to not generating until asked). It corrects a functional gap the generated Controller already implies: without a `GlobalExceptionHandler` registered, Spring's default exception handling returns the wrong HTTP status for exceptions `AbstractGlobalExceptionHandler` already maps (`EntityNotFoundException` → 500 instead of 404, for example) - a status code mismatch that exists regardless of whether you asked for exception handling.
- ✅ **The Entity is never generated.** Its persistence mapping, inheritance, and auditing concerns belong to the consumer's domain model - the generator's job stops at the boundary the OpenAPI contract actually describes.
- ✅ **`x-allcrud-auto-resource` defaults to `false`.** The generator has been marking resources by explicit `x-allcrud-resource: true` since before inference existed - defaulting inference to on would silently change what an existing, unmodified spec generates. Turning it on is always an explicit choice.

---

## 🧪 Testing Support

`unitTest` and `integrationTest` extend Allcrud's own `CrudServiceTests<T, ID>`/`CrudControllerIntegrationTests<T, VO, ID>` (see [Prerequisites](#️-prerequisites--project-setup) for the `testFixtures` dependency and Docker requirement these need). Both generated classes come with the full CRUD test suite already implemented by their Allcrud base class - add `@Test` methods on top for anything specific to your resource.

| | `unitTest` (`ProductServiceTest`) | `integrationTest` (`ProductControllerIT`) |
|---|---|---|
| Extends | `CrudServiceTests<T, ID>` | `CrudControllerIntegrationTests<T, VO, ID>` |
| Needs Docker? | No | Yes - spins up a real PostgreSQL container via Testcontainers |
| What it exercises | The Service layer, with the Repository mocked (Mockito `@Mock`/`@InjectMocks`) | The full stack: Controller → Service → Repository → real database, over real HTTP (RestAssuredMockMvc) |
| Extra dependency needed | `testFixtures("io.github.astro-techmath:allcrud:<version>")` | Same, plus a JDBC driver on `testRuntimeOnly` (e.g. `org.postgresql:postgresql`) |
| Boots a Spring context? | No | Yes (`@SpringBootTest`, `@AutoConfigureMockMvc`) |

---

## ⚠️ Known Limitations (V1)

- **The Entity is never generated.** It's always hand-written by you, before running the generator. `AllcrudSpringCodegen` finds each Entity's package by scanning your source tree for a file literally named `<EntityName>.java`, so it must exist (and compile) first.

- **Composite IDs (an `id` property that's an object/`$ref`, not a scalar) aren't supported - generation breaks.** Wiring this up properly would need a new generated artifact tying into Allcrud's `AbstractCompositeIdConverter`. Deliberate V1 scope decision - don't mark a composite-key resource's paths `x-allcrud-resource: true` yet.

- **Removing a resource from the spec doesn't remove its generated code.** The generator only ever adds files - a direct consequence of "generate once, never silently overwrite." Drop a resource from your spec, clean up its generated files by hand.

- **A resource with no `id` property fails generation immediately**, naming the resource and explaining why. Working as designed: `CrudController`/`CrudService`/`EntityRepository` all require an ID type, so this fails loud at generation time instead of a confusing `javac` error later.

---

## 🔗 Core Version Compatibility

This generator targets a pinned version of the `allcrud` core, declared in `gradle.properties` (`allcrudCoreGroup` / `allcrudCoreArtifact` / `allcrudCoreVersion`) - never a version range.

Compat check has two parts:

1. **Automated, every push**: a build-time test resolves the pinned core version and verifies its base classes (`AbstractEntityVO`, `CrudController`, `CrudService`) exist and expose the expected shape.
2. **Manual/on-demand**: `./gradlew checkCoreUpdates` checks Maven Central for a newer `allcrud` core version than the one pinned, as a reminder to go look - it doesn't validate compatibility by itself, that's job 1's role once `allcrudCoreVersion` is bumped.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
Feel free to use, modify, and distribute it with attribution.

## 💬 Contact

For questions, suggestions or feedback, open an issue or contact **mathmferreira@gmail.com**.

---
