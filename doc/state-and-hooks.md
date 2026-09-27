# State and Hooks

## Chaining steps

Each step function receives, as first argument, what the previous step returned -- like a [Ring handler](https://github.com/ring-clojure/ring/wiki/Concepts) chain. No world object, no dependency injection. A map is the usual choice, destructured by the next step:

```clojure
(defwhen "my sentence to be matched with {string} and {string}"
         [{:keys [k1 k2]} param1 param2]
         (do-something param1 k2 param2) ...)
```

A step whose last form returns `nil`, `true` or `false` -- an assertion, a `doseq`, a `println` -- keeps the state it was given, instead of passing that value on. So a `defthen` that only asserts needs no trailing `state`:

```clojure
(defthen "my cart should contain {number} item" [state n]
         (is (= n (count (:cart state)))))   ; the next step still gets state
```

## Initial state

By default, the first step of a scenario receives an empty map `{}`. Give another one in the options of `deffeature`:

```clojure
(deffeature my-specification "./path/to/feature/file"
            {:default-scenario-state {:foo "bar"}})
```

## Hooks

The same options map takes the functions to run:

- `:pre-run` before the feature
- `:post-run` after the feature
- `:pre-scenario-run` before each scenario
- `:post-scenario-run` after each scenario

```clojure
(defn before-all [] (prn "init feature components"))
(defn before-each [] (prn "init scenario components"))
(defn after-each [] (prn "clean scenario side effects"))
(defn clean [] (prn "reset and shut down components"))

(deffeature my-specification "./path/to/feature/file"
            {:pre-run           [#'before-all]
             :pre-scenario-run  [#'before-each]
             :post-scenario-run [#'after-each]
             :post-run          [#'clean]})
```

The `:post-*` hooks run even when a step or a `:pre-*` hook throws.

### When a hook throws

A hook that throws fails what it wraps, and the run goes on:

| The hook that throws                   | What fails                                                                       |
|----------------------------------------|----------------------------------------------------------------------------------|
| `:pre-scenario-run`                    | its scenario; the steps do not run and stay `:pending`                           |
| `:post-scenario-run`                   | its scenario, even if every step passed                                          |
| `:pre-run`                             | every scenario of the feature, without running                                   |
| `:post-run`                            | the run; the scenarios keep their results (Kaocha adds a failed `after-feature` test to the feature) |

A hook whose assertion fails -- an `is` that is false, or whose form raises -- counts as a hook that throws, with an exception that names it: `hook #'my.ns/clean-db! : an assertion failed`. The assertion itself is reported as usual, above it.

The next scenario, or the next feature, runs as usual. Every `:post-*` hook runs, whatever the ones before it threw. When several hooks throw, the first exception is the one reported, and it carries the others as suppressed exceptions -- the cause is not hidden by what the teardown throws after it.

`scenari.v2.core/run-feature` returns the exception under `:exception`, on the scenario or on the feature. Under the `clojure.test` runner a `:pre-run` or `:post-run` that throws is an error of the feature's `deftest`.

### What a hook receives

A hook declared with a single argument receives the name and the tags of what it wraps -- the tags without their `@`, those a scenario inherits from its `Feature`, `Rule` and `Examples` included. A hook without argument is called without one, as before:

```clojure
(defn log-scenario [{:keys [scenario-name annotations status]}]
  (println scenario-name annotations status))
```

| Hook                 | Argument                                                  |
|----------------------|-----------------------------------------------------------|
| `:pre-scenario-run`  | `{:scenario-name "..." :annotations #{"db"}}`             |
| `:post-scenario-run` | the same, plus `:status` -- `:success` or `:fail`         |
| `:pre-run`, `:post-run` | `{:feature "..." :annotations #{...}}`, no `:status`   |

A `:post-scenario-run` hook can so keep what a failed scenario left behind, to look at it, instead of cleaning it up.

The arity is read from the var's `:arglists`: pass hooks as vars (`#'log-scenario`), as above. A bare function has no `:arglists` and is called without argument. A hook with both a zero and a one-argument arity is called without argument: its one-argument arity was written for something else than this map.

### Hooks restricted by tag

Put a [cucumber tag expression](https://github.com/cucumber/tag-expressions) in the `:scenari/tags` metadata of the hook: it only runs where the tags match.

```clojure
(defn ^{:scenari/tags "@db and not @readonly"} clean-db! []
  (truncate-tables!))

(deffeature my-specification "./path/to/feature/file"
            {:post-scenario-run [#'clean-db!]})   ; skipped for the other scenarios
```

The expression is parsed when the feature is loaded: an invalid one fails there, naming the hook.

## Global hooks

A hook every feature needs -- clean the database before each scenario, start the system once -- does not have to be repeated in each `deffeature`. Mark the var with `:scenari/hook`, in any loaded namespace, and it runs for every feature:

```clojure
(defn ^{:scenari/hook :before-all} start-system! [] (start!))
(defn ^{:scenari/hook :after-all} stop-system! [] (stop!))

(defn ^{:scenari/hook :before-scenario :scenari/tags "@db"} clean-db! []
  (truncate-tables!))
```

| `:scenari/hook`                        | Runs                                    |
|----------------------------------------|-----------------------------------------|
| `:before-all`, `:after-all`            | once around the whole suite, Kaocha only |
| `:before-feature`, `:after-feature`    | around each feature                     |
| `:before-scenario`, `:after-scenario`  | around each scenario                    |

They follow the rules of the hooks above: a one-argument hook receives the name and the tags of what it wraps (a `:before-all` / `:after-all` gets an empty map), `:scenari/tags` restricts where it runs, and the `after` ones run even when a step or a `before` hook throws -- one that throws fails what it wraps, as [above](#when-a-hook-throws). The var can be private: a `defn-` is a hook like another.

A hook that could never run throws when the hooks are looked up, naming the hook, instead of being skipped in silence: a misspelled `:scenari/hook` value, or `:scenari/tags` on a `:before-all` / `:after-all` -- the suite has no tags, the expression would never match.

**Order.** Global hooks wrap those of the `deffeature`, like an onion: a global `:before-scenario` runs before the feature's `:pre-scenario-run`, a global `:after-scenario` after its `:post-scenario-run`. Between global hooks of one kind, the order is that of their namespaces' names, then of their lines.

**When they are looked up.** Once at the start of a run, among the loaded namespaces -- not when the features are parsed, so a namespace of hooks that no feature requires is still found, as long as it is loaded (a namespace under the Kaocha `glue-paths` is). Under the `clojure.test` runner alone, each feature is its own `deftest` and looks them up again.

**Suite hooks and Kaocha.** `:before-all` and `:after-all` run around the `:kaocha.type/scenari` suite, and not with `--dry-run` or `--doc-html`, which run nothing. A `:before-all` that throws stops the run, like a failing `(use-fixtures :once ...)`: no scenario runs, the exception is printed and the exit code is non-zero, and the `:after-all` hooks still run. An `:after-all` that throws fails the run and keeps its results: the summary, `junit.xml` and the reports are written, with a failed `after-all` test next to the features. The `clojure.test` runner has no suite: use `(use-fixtures :once ...)` there.
