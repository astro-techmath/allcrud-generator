# Contributing to Allcrud Generator

Thank you for considering contributing to **Allcrud Generator**! Your help is very appreciated. This guide outlines how you can contribute effectively.

---

## 🛠 How to Contribute

There are several ways to get involved:

- 🐞 **Report bugs** by opening issues
- 🌟 **Suggest new features or improvements**
- 💻 **Submit pull requests** for fixes or enhancements
- 🧪 **Improve or add new tests**

---

## 📦 Project Setup

Make sure you have:

- Java 21
- Gradle (or a compatible IDE like IntelliJ or Eclipse)
- Docker - required to run the integration tests (`*ControllerIT` compat tests spin up a real PostgreSQL container via Testcontainers)

Two Gradle modules make up this repo:

- `:` (root) - the generation engine (`AllcrudGenerator`, `AllcrudSpringCodegen`, the Mustache templates, `AllcrudGeneratorYamlConfig`)
- `:allcrud-generator-gradle-plugin` - the Gradle plugin that wires the engine into a consumer's build

If you're changing the engine and want to verify the change against the Gradle plugin (or a real consumer project), publish it to your local Maven repository first:

```
./gradlew publishToMavenLocal
```
