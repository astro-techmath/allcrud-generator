# Technical notes — GeneratedControllerAndServiceCompatTest

Non-obvious implementation rationale for this test's own helper code.

## classLoader deliberately not closed — generic type resolution is lazy

Generic superclass/interface resolution (`getGenericSuperclass()`,
`getActualTypeArguments()`) is lazy and happens later in the caller, still
using this `URLClassLoader` as the defining classloader - closing it here
would break that resolution.
