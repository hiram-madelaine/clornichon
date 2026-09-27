# Roadmap

Gaps against Cucumber, roughly by value/effort.

## Step expressions

* [x] custom parameter types: `define-parameter-type!`
* [ ] a datatable API beyond a vector of string maps: lists, transpose, diff, row-to-entity conversion

## Execution

* [x] `--dry-run`, through `:kaocha.plugin/scenari-dry-run`: lists the steps that resolve no glue, and with `--unused-glues` the glues no scenario uses, without running anything
* [x] an unresolved step fails with its sentence and a skeleton, not the NPE `run-step` raised by calling `(apply nil ...)`
* [ ] a distinct `:undefined` status in the result; cucumber-messages reads it from the missing glue meanwhile
* [ ] stop-on-failure? as an option for execution
* [ ] rerun only the scenarios that failed, `--retry n` for flaky ones

## Reporting

* [x] measure step durations (`:duration-ns`) and report the slowest glues with `--slowest-steps N`; scenario durations come from kaocha's profiling plugin
* [x] cucumber-messages NDJSON output, the interchange format the whole ecosystem reads, with `--cucumber-messages FILE` through `:kaocha.plugin/scenari-messages`
* [ ] usage report: which glues ran, which are dead
* [ ] attachments (screenshots) from a step or a hook

## Hooks

* [ ] step-level hooks, and suite-level before-all / after-all
* [x] tag-conditional hooks, through the `:scenari/tags` metadata of the hook
* [x] pass the scenario -- name, tags, status -- to a hook declared with one argument
* [ ] hooks shared across features, instead of one options map per `deffeature`

## Gherkin

* [x] a `Rule` level in the report and in the kaocha tree: `Rule : <name>` above its scenarios, a group node `--focus` can select

## Glue code

* [ ] give another way to declare steps without macro (proper defn with `:scenari/regex` in meta)

Deliberately out of scope: parallel execution, dependency injection (the chained scenario state replaces it), IDE integration.
