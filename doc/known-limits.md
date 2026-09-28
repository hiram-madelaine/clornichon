# Known Limits

What Clornichon does not do, or does in a way that can surprise, and what to do about it.

## A step cannot hand `nil`, `true` or `false` over as the state

A step whose last form returns `nil`, `true` or `false` keeps the state it was given: that is what lets a `defthen` end on its assertion. The price is that none of the three can be the state itself.

```clojure
(defwhen "the search finds nothing" [state]
  false)                        ; the next step receives state, not false

(defwhen "the search finds nothing" [state]
  (assoc state :found? false))  ; the next step receives {... :found? false}
```

Keep the state a map, and put the value under a key. See [Chaining steps](state-and-hooks.md#chaining-steps) and [ADR 6](adr/0006-thread-state-through-steps.md).

## A parameter type is defined for the whole process

`define-parameter-type!` has one registry, not one per namespace or per suite. Defining `{price}` a second time replaces the first for every glue, wherever it is written:

```clojure
(define-parameter-type! "price" #"(\d+) (EUR|USD)" ...)   ; in team-a.glue
(define-parameter-type! "price" #"(\d+) (USD)" ...)       ; in team-b.glue, loaded after

;; "team a pays 12 EUR" no longer matches the glue "team a pays {price}"
```

Give each type one name in the project, and define them all in one namespace the glues require.

## The same sentence twice in one namespace

The second definition replaces the first, without a word: nothing tells it from a reload. Two *different* sentences that make the same var name throw, see [Two sentences, one name](step-expressions.md#two-sentences-one-name).

## At the REPL, a deleted glue stays defined

Reloading a file evaluates the forms that are left in it: nothing removes the var of a glue that was deleted, or whose sentence changed. Its old sentence still matches.

```clojure
(ns-unmap 'my.glue 'the-old-sentence)
(scenari.v2.glue/invalidate-glues-cache!)
```

The second line is needed after an `ns-unmap` alone: the glues are looked up once, and looked up again when a namespace is loaded or a glue is defined. Removing the whole namespace removes its glues with it. A fresh JVM -- the command line, the CI -- never meets this.

## No hook around a step

Hooks wrap the suite, a feature or a scenario, see [State and hooks](state-and-hooks.md#hooks). What every step needs goes in a function its glues call.

## No retry of a failed scenario

Nothing runs a failed scenario again, and nothing lists the failed ones to run them alone. Under Kaocha, a failure prints the id of its scenario, which `--focus` takes:

```
FAIL in cart-test.shopping-cart/adding-too-many (cart_test.clj:7)
```

```bash
bin/kaocha --focus :cart-test.shopping-cart/adding-too-many
```

## Scenarios run one after the other

No parallel run, of features or of scenarios.

## Without Kaocha, the report is always colored

Under Kaocha `--no-color` turns the colors off. The `clojure.test` runner alone has no such setting. To keep a piped run plain:

```bash
clojure -M:test -e "..." | perl -pe 's/\e\[[0-9;]*m//g'
```

## The namespaces are named `scenari`

`scenari.v2.core`, `:scenari/hook`, `:kaocha.type/scenari`: the names of the library Clornichon forks, kept so that a project migrating has nothing to rename. See [Migrating from scenari](migrating-from-scenari.md).
