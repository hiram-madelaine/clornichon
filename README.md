<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="doc/img/clornichon-lockup-dark.svg">
    <img src="doc/img/clornichon-lockup.svg" alt="Clornichon" width="480">
  </picture>
</p>

# Clornichon - BDD for Clojure: Gherkin specifications, executed by Clojure

[![Clojars Project](https://img.shields.io/clojars/v/io.github.hiram-madelaine/clornichon.svg)](https://clojars.org/io.github.hiram-madelaine/clornichon)
[![cljdoc](https://cljdoc.org/badge/io.github.hiram-madelaine/clornichon)](https://cljdoc.org/d/io.github.hiram-madelaine/clornichon)

*Clornichon* = **Clo**jure + **cornichon**, the French gherkin. A Behavior-Driven Development (BDD) library for Clojure: write your specifications in plain [Gherkin](https://cucumber.io/docs/gherkin/) (Given/When/Then), bind each step to a Clojure function, run them with `clojure.test` or [Kaocha](https://github.com/lambdaisland/kaocha).

* **Cucumber's own parser**: feature files are read by [`io.cucumber/gherkin`](https://github.com/cucumber/gherkin), the reference implementation -- ~70 languages, `Rule`, `Background`, `Scenario Outline`, tags, datatables and doc strings.
* **Cucumber expressions**: `"I add {int} items to the {string} cart"`, or a plain regex when you need one. A missing step prints the glue skeleton to paste.
* **State threaded through the steps**: each step receives what the previous one returned, like a Ring handler chain -- no world object, no dependency injection.
* **A Kaocha test type**: features show up in the Kaocha tree, with colored Gherkin output, focus/skip by tag, and a `--dry-run` that catches undefined steps before anything runs.

> [!IMPORTANT]
> **Performance is a first-class concern.** Clornichon runs the acceptance suites of a large production codebase: 250+ features, 500+ scenarios and 3,400 steps bound to 440 step definitions. At that scale every millisecond spent per step shows, so the library does its work up front -- the glue of each step is resolved once, at parse time, not on every execution -- and a slower run is treated as a regression.

> [!TIP]
> **Coming from scenari?** The migration is mostly a dependency swap: the namespaces, the macros, the `deffeature` options and the `:kaocha.type/scenari` test type are unchanged. Under the hood the Gherkin parser and the step matching are now Cucumber's own, which makes feature files and step sentences a little stricter. A handful of glues and sentences may need a touch; `--dry-run` finds them without running anything. See [Migrating from scenari](doc/migrating-from-scenari.md).

## Installation

```clojure
;; deps.edn
io.github.hiram-madelaine/clornichon {:mvn/version "0.2.0"}
;; or project.clj
[io.github.hiram-madelaine/clornichon "0.2.0"]
```

That is all `clojure.test` needs. To run the features under Kaocha, declare Kaocha too: Clornichon does not bring it.

## In a nutshell

```gherkin
# test/features/cart.feature
Feature: shopping cart

  Scenario: adding items
    Given a cart with 2 items
    When I add 3 items
    Then the cart holds 5 items
```

```clojure
(ns cart-test
  (:require [clojure.test :refer [is]]
            [scenari.v2.core :refer [defgiven defwhen defthen deffeature]]))

(defgiven "a cart with {int} items" [_ n] {:cart n})
(defwhen "I add {int} items" [state n] (update state :cart + n))
(defthen "the cart holds {int} items" [state n] (is (= n (:cart state))))

(deffeature shopping-cart "test/features/cart.feature")   ; a deftest
```

Run it like any test: `clojure.test/run-tests`, your editor, or [Kaocha](doc/running.md#kaocha).

## Documentation

Also on [cljdoc](https://cljdoc.org/d/io.github.hiram-madelaine/clornichon).

* [Getting started](doc/getting-started.md): from an empty project to a first green scenario
* [Step expressions](doc/step-expressions.md): cucumber expressions, regexes, datatables, doc strings, missing steps
* [State and hooks](doc/state-and-hooks.md): chaining steps, initial state, before/after hooks
* [Running features](doc/running.md): clojure.test, Kaocha, `--tags`, `--dry-run`, `--doc-html`
* [Known limits](doc/known-limits.md): what it does not do, and what to do about it
* [Migrating from scenari](doc/migrating-from-scenari.md)
* [Development workflow](doc/development-workflow.md)
* [Glossary](doc/glossary.md)
* [Architecture decisions](doc/adr/0001-record-architecture-decisions.md): why a fork, Cucumber's parser, glue resolved at parse time...
* [Feature data structure](doc/feature-structure.md)
* [Roadmap](doc/roadmap.md) and [CHANGELOG](CHANGELOG.md)

## Origins

Clornichon started as a fork of [scenari](https://github.com/jgrodziski/scenari), written by Jérémie Grodziski at [DefSquare](https://defsquare.io), and has since diverged far enough to go its own way: the Gherkin parser, the step expressions, the Kaocha integration and the reporting have been rewritten. The namespaces still carry the `scenari` name. Thanks to the original authors for the foundations.

## Rationale

*In the words of scenari's original author:*

I'm used to [JBehave](http://jbehave.org/) and I wanted a BDD framework with an [external DSL](http://www.martinfowler.com/bliki/DomainSpecificLanguage.html) following the [gherkin grammar](https://github.com/cucumber/cucumber/wiki/Gherkin) but also with an easy and fast setup and with steps written in [Clojure](http://clojure.org/). The previous BDD attempt I known in Clojure were all with an [internal DSL](http://www.martinfowler.com/bliki/DomainSpecificLanguage.html). I prefer an external one because I think it's easier to share the scenarios with a domain expert. I you prefer an internal DSL BDD Framework, have a look at [Speclj](http://speclj.com/).

Jérémie presented the internals of the original library at the Clojure Paris User Group and the slides are here: ["Anatomy of a BDD Execution Library in Clojure"](https://speakerdeck.com/jgrodziski/anatomy-of-a-bdd-execution-library-in-clojure).

## License

Clornichon is released under the terms of the [MIT License](http://opensource.org/licenses/MIT).

Copyright © 2024 DefSquare defsquare.io

Copyright © 2026 Hiram Madelaine

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
