## Summary

@coderabbitai summary

## Type of change

- [ ] `feat` — new feature
- [ ] `fix` — bug fix
- [ ] `docs` — documentation only
- [ ] `refactor` — no functional change
- [ ] `test` — test-only change
- [ ] `chore` — tooling/build/dependency change

## Testing

<!-- How was this verified? For any change with runtime implications, "it compiles" is not sufficient - see AGENTS.md. -->

- [ ] Real compilation and/or real Spring context validation done for any
  runtime-affecting change (not reflection/mocks alone)
- [ ] Existing tests pass
- [ ] New tests added where applicable
- [ ] If this affects generated code, validated against an external smoke
  test project (not just internal repo tests) with real Docker where
  applicable

## Checklist

- [ ] Comments are in English
- [ ] No dead code left behind from a superseded decision
- [ ] If this touches a Mustache template, it has a traceability comment
  referencing the original openapi-generator template
- [ ] Generate-once-never-overwrite policy respected for scaffolding layers
  (Repository/Converter/Service/Controller/exceptionHandler/unitTest/
  integrationTest)