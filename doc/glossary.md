# Glossary

The words of Gherkin, and what each becomes in Clornichon.

* **Feature**: a `.feature` file, one behaviour of the system described in Gherkin. Declared in Clojure with `deffeature`, which turns it into a `deftest`.
* **Description**: the free text under the `Feature:` line -- typically the `As a / I want / So that` narrative. Kept as `:description`.
* **Rule**: a business rule grouping scenarios inside a feature. Its tags are inherited by its scenarios.
* **Background**: steps run before each scenario of the feature (or of the rule).
* **Scenario**: one concrete example of the behaviour -- a sequence of steps. A `testing` context in clojure.test, a node in the Kaocha tree.
* **Scenario Outline**: a scenario template, run once per row of its `Examples` tables. `<placeholders>` in its steps are replaced by the row's values.
* **Examples**: a table feeding a Scenario Outline. An outline may have several, each with its own tags.
* **Step**: one line of a scenario, starting with `Given`, `When`, `Then`, `And` or `But`.
* **Glue** (step definition): the Clojure function bound to a step sentence, declared with `defgiven`, `defwhen` or `defthen`. Resolved once, when the feature is declared.
* **Step expression**: the sentence matcher of a glue -- a cucumber expression (`"I add {int} items"`) or a regex (`#"^I add (\d+) items$"`). See [Step expressions](step-expressions.md).
* **Parameter type**: a token of a cucumber expression, `{int}` `{float}` `{word}` `{string}` `{number}`, which captures part of the sentence and converts it.
* **State**: the value a step returns, received as first argument by the next step. Starts as `{}` or `:default-scenario-state`. See [State and hooks](state-and-hooks.md).
* **Hook**: a function run before or after a feature (`:pre-run`, `:post-run`) or each of its scenarios (`:pre-scenario-run`, `:post-scenario-run`). Declared with one argument, it receives the name and tags of what it wraps, and a scenario's `:status` after it; `:scenari/tags` on its var restricts it to the matching scenarios. See [State and hooks](state-and-hooks.md#hooks).
* **Tag**: an `@annotation` on a feature, rule, scenario or examples table. Scenarios inherit the tags above them; `--tags` filters on them.
* **Datatable**: a `| table |` under a step, passed to its glue as a vector of maps, one per row.
* **Doc string**: a `"""` or ```` ``` ```` block under a step, passed to its glue as a string.
* **Dry run**: `--dry-run`, which checks that every step resolves a glue without running anything.
