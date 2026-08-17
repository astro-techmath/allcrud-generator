# Security Policy

## Reporting a Vulnerability

Please report security vulnerabilities using GitHub's
[private vulnerability reporting](https://github.com/astro-techmath/allcrud-generator/security/advisories/new) —
this keeps the report private while a fix is worked out.

If that's not an option, you can also email:

📧 **mathmferreira@gmail.com**

Please include as much detail as possible so the issue can be reproduced
and understood.

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | ✅ Yes     |

## Security Best Practices

allcrud-generator is a build-time code generation tool, not a runtime
dependency — its own attack surface is different from a library like
[allcrud](https://github.com/astro-techmath/allcrud). Even so:

- Review generated code before relying on it in production, the same way
  you would review any other change to your codebase
- Keep the pinned `allcrud` core version (`allcrudCoreVersion` in
  `gradle.properties`) up to date
- Don't commit real credentials into `allcrud-generator.yml` or any other
  generator configuration file

---

Thank you for helping keep allcrud-generator safe and reliable.
