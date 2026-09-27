# 2. Fork scenari

Date: 2026-09-26

## Status

Accepted

## Context

[scenari](https://github.com/jgrodziski/scenari), by Jérémie Grodziski at DefSquare, runs Gherkin features with Clojure glue. Used on a large production codebase -- 250+ features, 500+ scenarios, 3,400 steps -- it showed its limits: a hand-written Gherkin grammar that missed parts of the language (`Rule`, several `Examples`, languages other than English), a hand-rolled step matching, no tag expressions, no way to check the glue without running.

Fixing those meant rewriting the parser, the step matching, the Kaocha integration and the reporting.

## Decision

Fork scenari 2.0.2 and publish it as Clornichon, `io.github.hiram-madelaine/clornichon` on Clojars.

Keep the public surface of scenari: the `scenari.*` namespaces, `defgiven` / `defwhen` / `defthen` / `deffeature` and their options, the `:kaocha.type/scenari` test type.

## Consequences

* Migrating from scenari is a dependency swap plus a handful of glues to touch -- see [Migrating from scenari](../migrating-from-scenari.md).
* The namespaces carry a name that is no longer the project's. Renaming them would break every user for no functional gain.
