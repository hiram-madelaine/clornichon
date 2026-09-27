# Roadmap

Gaps against Cucumber, roughly by value/effort.

## Step expressions

* [ ] custom parameter types: the `ParameterTypeRegistry` is in `glue.clj`, it needs a public way to add one
* [ ] a datatable API beyond a vector of string maps: lists, transpose, diff, row-to-entity conversion

## Execution

* [x] `--dry-run`, through `:kaocha.plugin/scenari-dry-run`: lists the steps that resolve no glue, and with `--unused-glues` the glues no scenario uses, without running anything
* [ ] an unresolved step should be `:undefined`, not the NPE `run-step` raises by calling `(apply nil ...)`
* [ ] stop-on-failure? as an option for execution
* [ ] rerun only the scenarios that failed, `--retry n` for flaky ones

## Reporting

* [ ] measure step and scenario durations -- nothing is timed today, which also blocks a slowest-steps report
* [ ] cucumber-messages / JSON output, the interchange format the whole ecosystem reads (Allure, Cucumber Reports, CI). The parser already speaks it: a feature is read as `Envelope` messages, only the run needs to emit its own
* [ ] usage report: which glues ran, which are dead
* [ ] attachments (screenshots) from a step or a hook

## Hooks

* [ ] step-level hooks, and suite-level before-all / after-all
* [ ] tag-conditional hooks (`@before "@web and not @slow"`)
* [ ] pass the scenario -- name, tags, status -- to the hook, which currently receives nothing
* [ ] hooks shared across features, instead of one options map per `deffeature`

## Gherkin

* [ ] a `Rule` level in the report and in the kaocha tree; rules are parsed but flattened into the feature

## Glue code

* [ ] give another way to declare steps without macro (proper defn with `:scenari/regex` in meta)

Deliberately out of scope: parallel execution, dependency injection (the chained scenario state replaces it), IDE integration.
