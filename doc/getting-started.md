# Getting Started

From an empty project to a first green scenario.

## 1. Add the dependency

```clojure
;; deps.edn
io.github.hiram-madelaine/clornichon {:mvn/version "0.1.4"}
;; or project.clj
[io.github.hiram-madelaine/clornichon "0.1.4"]
```

The namespaces keep their `scenari.*` names:

```clojure
(:require [scenari.v2.core :refer [defgiven defwhen defthen deffeature]])
```

## 2. Write a feature file

A feature is plain [Gherkin](https://cucumber.io/docs/gherkin/). Every `.feature` starts with `Feature:` (or a tag or a comment); a bare `Scenario:` is a parse error.

```gherkin
# test/features/cart.feature
Feature: shopping cart

  Scenario: adding items
    Given a cart with 2 items
    When I add 3 items
    Then the cart holds 5 items
```

## 3. Write the glue

Each step sentence is bound to a Clojure function by a [cucumber expression](step-expressions.md). The first argument is the scenario [state](state-and-hooks.md) -- what the previous step returned -- then one argument per token of the expression.

```clojure
(ns cart-test
  (:require [clojure.test :refer [is]]
            [scenari.v2.core :refer [defgiven defwhen defthen deffeature]]))

(defgiven "a cart with {int} items" [_ n]
  {:cart n})

(defwhen "I add {int} items" [state n]
  (update state :cart + n))

(defthen "the cart holds {int} items" [state n]
  (is (= n (:cart state))))
```

A `defthen` ending on an assertion keeps the state it received: no trailing `state` needed.

## 4. Declare the feature

```clojure
(deffeature shopping-cart "test/features/cart.feature")
```

`deffeature` defines a `deftest` named `shopping-cart`. It takes a path on the filesystem or on the classpath, a directory of features, or the Gherkin text itself. Each step's glue is resolved right there, so the glues must be loaded before the `deffeature` -- in the same namespace above it, or in a namespace it requires.

## 5. Run it

```clojure
(clojure.test/run-tests 'cart-test)
;; ________________________
;; Feature : shopping cart
;;
;; Testing scenario : adding items
;;   Given a cart with 2 items         (from cart-test/"a cart with {int} items")
;;   When I add 3 items         (from cart-test/"I add {int} items")
;;   Then the cart holds 5 items         (from cart-test/"the cart holds {int} items")
;; adding items succeed !
```

A step without glue prints the skeleton to paste:

```
Missing step for : When I remove 1 item
(defwhen "I remove {int} item"  [state arg0]  (do "something"))
```

## Next

* [Running features](running.md): clojure.test, Kaocha, tags, `--dry-run`, HTML documentation
* [Step expressions](step-expressions.md): tokens, regexes, datatables and doc strings
* [State and hooks](state-and-hooks.md): initial state, chaining, before/after
* [Development workflow](development-workflow.md): the full cycle, in depth
