# Technical notes — AllcrudGeneratorExtension

Non-obvious internal rationale, not decisions about this project's own
architecture (that's `docs/adr/`).

## No pojoNamingStyle property here (there used to be one)

`allcrud-generator.yml` (see `configFile`) is now the single source of
truth for `pojoNamingStyle`, packages, defaults and resources - keeping a
second, independent `pojoNamingStyle` knob on the extension would have been
the same duplicated-authority problem already fixed for `allcrudIdType` and
`generateServiceLayer`.
