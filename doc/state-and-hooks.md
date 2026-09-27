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
