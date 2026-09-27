# 5. Resolve each step's glue at parse time

Date: 2026-08-27

## Status

Accepted

## Context

Clornichon runs suites of thousands of steps bound to hundreds of glues. Looking up the glue of a step on every execution -- scanning every public var of the runtime, then matching each glue against the sentence -- cost time on every step of every run, and loading a feature re-scanned the runtime once per step.

## Decision

Resolve the glue of each step once, when `deffeature` parses the feature, and store it in the step (`:glue`). Compile each glue's expression once, and memoize the list of glues.

Performance is a first-class concern: a slower run is treated as a regression.

## Consequences

* Running a step is a function call, with no lookup.
* The glues must be loaded before the `deffeature` that uses them. The step keeps the glue's var: a glue body redefined at the REPL is picked up as is, but a new glue or a changed sentence needs the `deffeature` re-evaluated.
* An unresolved step is known before anything runs, which is what makes `--dry-run` possible.
