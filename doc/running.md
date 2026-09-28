# Running Features

There are three ways to run a feature, depending on your situation.

## clojure.test

`deffeature` defines a `deftest`, so `clojure.test/run-tests`, your editor's test runner or any clojure.test runner picks it up. To run one feature and print its Gherkin report:

```clojure
(require '[scenari.v2.test :refer [run-feature]])
(run-feature #'my-specification)
;; ________________________
;; @checkout
;; Feature : shopping cart
;;   A cart holds items and can be emptied.
;;
;; @edge-case
;; Testing scenario : removing too much
;;   Given a cart with 2 items         (from user/"a cart with {number} items")
;;   When I remove 5 items         (from user/"I remove {number} items")
;;   Step failed
;; java.lang.AssertionError: Assert failed: cannot remove 5 from 2
;;   Then the cart holds 0 items         (from user/"the cart holds {number} items")
;; removing too much FAILED
;;
;; ________________________
```

Output is colorized along the Gherkin syntax: keywords in bold cyan, parameters in yellow, and the sentence itself in green, red or grey depending on whether the step passed, failed or never ran. Tags are cyan; descriptions, doc strings and datatable separators grey. Under Kaocha, coloring honours `--color` / `--no-color`, so a piped or CI run can be kept plain. Without Kaocha the output is always colored.

## As data

`scenari.v2.core/run-feature` runs the feature and returns it as data -- the structure described in [Feature data structure](feature-structure.md) -- with:

- the `:status` of the feature and of each scenario, `:success` or `:fail`
- each step's `:status`: `:pending` when not executed, `:fail` when an assertion fails or an exception is thrown, `:success` otherwise
- each step's `:input-state` (what the previous step returned) and `:output-state`

```clojure
(require '[scenari.v2.core :refer [run-feature]])
(run-feature #'my-specification)
```

Useful at the REPL, for debugging.

## Kaocha

Kaocha is not a dependency of Clornichon: declare it in the alias that runs your tests.

```clojure
;; deps.edn
:test {:extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}}
```

Then declare a suite of type `:kaocha.type/scenari` in `tests.edn`:

```clojure
#kaocha/v1
{:tests [{:id                             :scenario
          :type                           :kaocha.type/scenari
          :kaocha/source-paths            ["src"]
          :kaocha/test-paths              ["test/scenario"]
          :kaocha.type.scenari/glue-paths ["test/scenario/glue"]}]
 :kaocha/plugins [:kaocha.plugin/scenari-tags
                  :kaocha.plugin/scenari-doc
                  :kaocha.plugin/scenari-dry-run
                  :kaocha.plugin/scenari-slowest-steps
                  :kaocha.plugin/scenari-messages]}
```

The namespaces under the `glue-paths` are loaded first, then those under the `test-paths`: a feature finds its glues without requiring them. The `glue-paths` can sit under the `test-paths`, as here, or anywhere else.

Kaocha runs them in a random order by default, the scenarios of a `Rule` together; `--no-randomize` follows the files. The console prints `Rule : <name>` above the scenarios of a rule and `End of rule : <name>` under them, since the scenario that runs next may belong to no rule.

Each feature, each `Rule` and each scenario is a node of the Kaocha tree. A scenario's id is qualified by its feature, `:my.ns.my-feature/scenario-name`, whether it sits in a rule or not; the bare name stays an alias for `--focus`. A rule's id is `:my.ns.my-feature.rule/rule-name`, and `--focus rule-name` runs its scenarios alone.

```clojure
(require '[kaocha.repl :as krepl])
(krepl/run :scenario)
```

The five plugins are optional. `scenari-doc` and `scenari-dry-run` read the test plan once filtered, so list them after `scenari-tags`.

### Filtering by tag

`:kaocha.plugin/scenari-tags` adds `--tags`, which takes a [cucumber tag expression](https://github.com/cucumber/tag-expressions):

```bash
bin/kaocha --tags "@smoke and not @wip"
bin/kaocha --tags "(@api or @ui) and not @slow"
```

The expression is evaluated **per scenario**, on the tags the scenario carries -- including those it inherits from its `Feature`, its `Rule` and its `Examples` table. So `--tags "@smoke and not @wip"` on a feature tagged `@smoke` runs every scenario of that feature except the `@wip` ones.

Kaocha's own `--focus-meta` / `--skip-meta` still work, and combine with `--tags`, but they are limited to OR: a repeated `--focus-meta` matches a testable carrying *any* of the keys, and the focus is dropped for a whole subtree as soon as one node matches -- a feature tagged `@smoke` therefore runs all of its scenarios, tagged or not. What each can express:

| cucumber tag expression | with `--focus-meta` / `--skip-meta` |
|---|---|
| `@smoke` | `--focus-meta :smoke`, but the whole feature if the tag is on the feature |
| `not @wip` | `--skip-meta :wip` |
| `@smoke or @api` | `--focus-meta :smoke --focus-meta :api` |
| `@smoke and not @wip` | `--focus-meta :smoke --skip-meta :wip` |
| `@smoke and @api` | ✗ |
| `(@a or @b) and (@c or @d)` | ✗ |
| `not (@a and @b)` | ✗ |

Two differences worth knowing: `--focus-meta` also reads the var metadata of a `deffeature` (`(deffeature ^:wip my-feature ...)`) while `--tags` only reads Gherkin tags, and `--tags` leaves non-scenari suites alone -- combine it with `--focus :scenario` to run the features only. The expression can also be set in `tests.edn` as `:kaocha.plugin.scenari-tags/expression`, which is how to use it from `kaocha.repl`.

### Checking the glue without running

`:kaocha.plugin/scenari-dry-run` adds `--dry-run`: it lists every selected step that resolves no glue, with the feature and scenario using it, and runs nothing. Add `--unused-glues` to also list the glues no selected step uses.

```bash
bin/kaocha --dry-run --unused-glues
```

### Slowest step definitions

`:kaocha.plugin/scenari-slowest-steps` adds `--slowest-steps N`: after the run, it prints the N step definitions that took the most time, all their calls added up, with their call count and their slowest call. Grouped by glue rather than by step, since a slow glue is spread over every scenario that calls it. The time per scenario is what kaocha's own `:kaocha.plugin/profiling` reports.

```bash
bin/kaocha --slowest-steps 10
```

### Cucumber messages

`:kaocha.plugin/scenari-messages` adds `--cucumber-messages FILE`: after the run, it writes it as a [cucumber-messages](https://github.com/cucumber/messages) NDJSON stream, the format every Cucumber formatter reads -- one envelope per line: the sources, their Gherkin documents and pickles, the step definitions, then one test case per scenario with each step's status (`PASSED`, `FAILED`, `SKIPPED` after a failure, `UNDEFINED` without a glue), duration and captured arguments.

```bash
bin/kaocha --cucumber-messages target/messages.ndjson
```

Only the scenarios kept by the filters are written. For instance, Cucumber's own HTML report, from `npm install @cucumber/html-formatter @cucumber/message-streams`:

```js
// node to-html.mjs target/messages.ndjson report.html
import fs from 'node:fs'
import { pipeline } from 'node:stream'
import { NdjsonToMessageStream } from '@cucumber/message-streams'
import { CucumberHtmlStream } from '@cucumber/html-formatter'
const dist = 'node_modules/@cucumber/html-formatter/dist'
pipeline(fs.createReadStream(process.argv[2]), new NdjsonToMessageStream(),
  new CucumberHtmlStream(`${dist}/main.css`, `${dist}/main.js`),
  fs.createWriteStream(process.argv[3]), err => err && console.error(err))
```

A step failing on an `is` carries no detail in the stream -- just `A clojure.test assertion failed` -- since its report already went to Kaocha's reporter; a step that throws carries its exception.

### HTML documentation

`:kaocha.plugin/scenari-doc` turns the selected scenarios into one HTML document: a table of contents, one section per feature, one anchor per scenario.

```bash
bin/kaocha --tags @smoke --doc-html target/features.html     # runs nothing
bin/kaocha --doc-report target/report.html                   # runs, then annotates each step with its result
```

The document follows the files, not the run: the features by namespace then line, the scenarios in the order of their feature. Kaocha's `randomize` plugin, on by default, does not change it: two runs write the same document.
