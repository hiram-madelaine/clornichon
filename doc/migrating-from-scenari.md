# Migrating from scenari

Clornichon forks scenari 2.0.2 ([why](adr/0002-fork-scenari.md)). The migration is mostly a dependency swap: the namespaces, the macros, the `deffeature` options and the `:kaocha.type/scenari` test type are unchanged. On a real project -- 221 feature files, 1,123 step sentences, 401 glues -- it touched 9 glues and 2 sentences.

## What you gain

* Feature files parsed by [`io.cucumber/gherkin`](https://github.com/cucumber/gherkin): `Background`, `Rule`, every `Examples` block of an outline, tags on `Examples`, ~70 languages, doc string content types, line and column in parse errors.
* Step sentences matched as [cucumber expressions](https://github.com/cucumber/cucumber-expressions): `{int}` `{float}` `{word}` `{string}`, optional text, alternation. `{number}` is kept, and now takes a sign and decimals.
* Kaocha: `--tags "@smoke and not @wip"`, `--dry-run`, `--doc-html` / `--doc-report`, focus/skip by tag, a throwing step reported as a failure in junit-xml, colored Gherkin output.
* A step ending on an assertion (`is` returns a boolean) or a side effect (`nil`) keeps the scenario state instead of replacing it -- no more trailing `state` in a `defthen`.

## How to migrate

1. Swap the dependency: `io.defsquare/scenari` (or the `io.github.hiram-madelaine/scenari` git dep) becomes `io.github.hiram-madelaine/clornichon`. Your namespaces and the `:kaocha.type/scenari` suites of `tests.edn` stay as they are. scenari brought Kaocha along, Clornichon does not: declare `lambdaisland/kaocha` yourself if your project got it that way.
2. Add the plugin to `tests.edn` and run `--dry-run`: it lists every step that no longer resolves a glue, with the feature and scenario using it, without running anything.
   ```clojure
   :kaocha/plugins [:kaocha.plugin/scenari-dry-run]
   ```
3. Fix what it reports, then run the suite: what `--dry-run` cannot see shows up as a parse error or a step fn called with the wrong number of arguments. The usual suspects:
   * **Escape `/`, `(` and `)` in a string sentence matcher** (`"the folders {string} \\/ {string} exist"`), or they read as alternation or optional text.
   * **A regex glue passes its capture groups**, not the quoted literals of the sentence: make a grouping-only group non-capturing, `(?:consultation|création)`, and capture what you want as an argument, `\"(.+)\"`.
   * **Start every `.feature` with `Feature:`** (or a tag or comment) -- a bare `Scenario:` is a parse error.
   * **Non-English features need a `# language: fr` header**, and French writes `Scénario:` with no space before the colon.
4. A scenario's kaocha id is now qualified by its feature, `:my.ns.my-feature/scenario-name` instead of `:scenario-name` (the bare name stays an alias for `--focus`). In a junit-xml report the testcases' `classname` changes with it, so a CI that tracks test history sees them as new tests.
5. Only if you read the parsed feature map yourself: `:scenario-name` lost its leading space, and `:feature` is the name alone with the `As a / I want / So that` narrative in `:description`.

The [CHANGELOG](../CHANGELOG.md) details every change.
