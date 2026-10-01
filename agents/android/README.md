# Android agent workspace

Start here: read `AGENT.md`, then `tasks/INDEX.md`, then pick the first task whose status is `todo` and whose dependencies are `done`.

```
agents/android/
  AGENT.md            role, scope, rules, definition of done
  tasks/              one file per task, plus INDEX.md (order, status, dependencies)
  context/            stable reference: stack, architecture, constraints, glossary
  checklists/         PR, release and security checklists
  decisions/          local decision notes; promote big ones to docs/adr/
  handoff/            one file per handoff (TEMPLATE.md)
```

Code location (assumption A-1): code lives in the `Project-EQO-Android` repo; this repo holds tasks, routing and orchestration. Model routing: `docs/agents/MODEL-ROUTING.md`.
