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

Output is colorized along the Gherkin syntax: keywords in bold cyan, parameters in yellow, and the sentence itself in green, red or grey depending on whether the step passed, failed or never ran. Tags are cyan; descriptions, doc strings and datatable separators grey. Coloring honours Kaocha's `--color` / `--no-color`, so a piped or CI run can be kept plain.

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

Declare a suite of type `:kaocha.type/scenari` in `tests.edn`:

```clojure
#kaocha/v1
{:tests [{:id                             :scenario
          :type                           :kaocha.type/scenari
          :kaocha/source-paths            ["src"]
          :kaocha/test-paths              ["test/scenario"]
          :kaocha.type.scenari/glue-paths ["test/scenario/glue"]}]
 :kaocha/plugins [:kaocha.plugin/scenari-tags
                  :kaocha.plugin/scenari-doc
                  :kaocha.plugin/scenari-dry-run]}
```

Each feature and each scenario is a node of the Kaocha tree. A scenario's id is qualified by its feature, `:my.ns.my-feature/scenario-name`; the bare name stays an alias for `--focus`.

```clojure
(require '[kaocha.repl :as krepl])
(krepl/run :scenario)
```

The three plugins are optional. `scenari-doc` and `scenari-dry-run` read the test plan once filtered, so list them after `scenari-tags`.

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

### HTML documentation

`:kaocha.plugin/scenari-doc` turns the selected scenarios into one HTML document: a table of contents, one section per feature, one anchor per scenario.

```bash
bin/kaocha --tags @smoke --doc-html target/features.html     # runs nothing
bin/kaocha --doc-report target/report.html                   # runs, then annotates each step with its result
```
