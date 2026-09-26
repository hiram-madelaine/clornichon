<p align="center">
  <img src="clornichon.svg" alt="Clornichon" width="220">
</p>

# Clornichon - Gherkin specifications, executed by Clojure

*Clornichon* = **Clo**jure + **cornichon**, the French gherkin. Write your specifications in plain [Gherkin](https://cucumber.io/docs/gherkin/) (Given/When/Then), bind each step to a Clojure function, run them with `clojure.test` or [Kaocha](https://github.com/lambdaisland/kaocha).

* **Cucumber's own parser**: feature files are read by [`io.cucumber/gherkin`](https://github.com/cucumber/gherkin), the reference implementation -- ~70 languages, `Rule`, `Background`, `Scenario Outline`, tags, datatables and doc strings.
* **Cucumber expressions**: `"I add {int} items to the {string} cart"`, or a plain regex when you need one. A missing step prints the glue skeleton to paste.
* **State threaded through the steps**: each step receives what the previous one returned, like a Ring handler chain -- no world object, no dependency injection.
* **A Kaocha test type**: features show up in the Kaocha tree, with colored Gherkin output, focus/skip by tag, and a `--dry-run` that catches undefined steps before anything runs.

> [!IMPORTANT]
> **Performance is a first-class concern.** Clornichon runs the acceptance suites of a large production codebase: 250+ features, 500+ scenarios and 3,400 steps bound to 440 step definitions. At that scale every millisecond spent per step shows, so the library does its work up front -- the glue of each step is resolved once, at parse time, not on every execution -- and a slower run is treated as a regression.

> [!TIP]
> **Coming from scenari?** The migration is mostly a dependency swap: the namespaces, the macros, the `deffeature` options and the `:kaocha.type/scenari` test type are unchanged. Under the hood the Gherkin parser and the step matching are now Cucumber's own, which makes feature files and step sentences a little stricter. A handful of glues and sentences may need a touch; `--dry-run` finds them without running anything. See [Migrating from scenari](#migrating-from-scenari).

* [Installation](#installation)
* [Migrating from scenari](#migrating-from-scenari)
* [Basic Usage](#basic-usage)
	* [Write Scenarios in plain text]()
	* [Map steps to Clojure code]()
	* [Execute Specification and get a report]()
  * [Define "before" and "after" code]()
* [Documentation](#documentation)
* [Origins](#origins)
* [Rationale](#rationale)
* [ToDoS](#todos)

## Installation

```clojure
;; deps.edn
io.github.hiram-madelaine/clornichon {:mvn/version "0.1.0"}
;; or project.clj
[io.github.hiram-madelaine/clornichon "0.1.0"]

;; then in your ns statement -- the namespaces keep their scenari.* names
(:require [scenari.v2.core :as scenari :refer [defgiven defwhen defthen deffeature]])
```

[![Clojars Project](https://img.shields.io/clojars/v/io.github.hiram-madelaine/clornichon.svg)](https://clojars.org/io.github.hiram-madelaine/clornichon)

## Migrating from scenari

Clornichon forks scenari 2.0.2. On a real project -- 221 feature files, 1,123 step sentences, 401 glues -- the migration touched 9 glues and 2 sentences.

**What you gain**

* Feature files parsed by [`io.cucumber/gherkin`](https://github.com/cucumber/gherkin): `Background`, `Rule`, every `Examples` block of an outline, tags on `Examples`, ~70 languages, doc string content types, line and column in parse errors.
* Step sentences matched as [cucumber expressions](https://github.com/cucumber/cucumber-expressions): `{int}` `{float}` `{word}` `{string}`, optional text, alternation. `{number}` is kept, and now takes a sign and decimals.
* Kaocha: `--tags "@smoke and not @wip"`, `--dry-run`, `--doc-html` / `--doc-report`, focus/skip by tag, a throwing step reported as a failure in junit-xml, colored Gherkin output.
* A step ending on an assertion (`is` returns a boolean) or a side effect (`nil`) keeps the scenario state instead of replacing it -- no more trailing `state` in a `defthen`.

**How to migrate**

1. Swap the dependency: `io.defsquare/scenari` (or the `io.github.hiram-madelaine/scenari` git dep) becomes `io.github.hiram-madelaine/clornichon`. Your namespaces and the `:kaocha.type/scenari` suites of `tests.edn` stay as they are.
2. Add the plugin to `tests.edn` and run `--dry-run`: it lists every step that no longer resolves a glue, with the feature and scenario using it, without running anything.
   ```clojure
   :kaocha/plugins [:kaocha.plugin/scenari-dry-run]
   ```
3. Fix what it reports, then run the suite: what `--dry-run` cannot see shows up as a parse error or a step fn called with the wrong number of arguments. The usual suspects:
   * **Escape `/`, `(` and `)` in a string sentence matcher** (`"the folders {string} \\/ {string} exist"`), or they read as alternation or optional text.
   * **A regex glue passes its capture groups**, not the quoted literals of the sentence: make a grouping-only group non-capturing, `(?:consultation|création)`, and capture what you want as an argument, `\"(.+)\"`.
   * **Start every `.feature` with `Feature:`** (or a tag or comment) -- a bare `Scenario:` is a parse error.
   * **Non-English features need a `# language: fr` header**, and French writes `Scénario:` with no space before the colon.
4. A scenario's kaocha id is now qualified by its feature, `:my.ns.my-feature/scenario-name` instead of `:scenario-name` (the bare name stays an alias for `--focus`). In a junit-xml report the testcases' `classname` changes with it, so a CI that tracks test history sees them as new tests.
5. Only if you read the parsed feature map yourself: `:scenario-name` lost its leading space, and `:feature` is the name alone with the `As a / I want / So that` narrative in `:description`.

The [CHANGELOG](CHANGELOG.md) details every change.

## Basic Usage


### Write Scenarios in plain text
First, write your scenarios in plain text using the [Gherkin grammar]((https://github.com/cucumber/cucumber/wiki/Gherkin)) in a file or String :
You can add a "narrrative" for all your scenarios with the story syntax at the beginning of the story file (As a role I want to do something In order to get value)
```Gherkin
Scenario: create a new product
# this is a comment
When I create a new product with name "iphone 6" and description "awesome phone"
Then I receive a response with an id 56422 and a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL
Then I receive a 200 response

Scenario: get product info
When I invoke a GET request on location URL
Then I receive a 200 response
```

### Declaring a specification 

```clojure
(require 'scenari.v2.core :refer [deffeature])

(deffeature my-specification "./path/to/feature/file") ;; define deftest bound to symbol 'my-specification', put the specification as clojure data-structure in metadata and return the specification
;;=> 
;;{:scenarios [{:id "0ef9b8a9-e035-4ae2-96c4-662c0b8988de",
;;		:pre-run (),
;;		:post-run (),
;;		:scenario-name " create a new product",
;;		:steps [{:sentence-keyword :when,
;;			 :sentence "I create a new product with name \"iphone 6\" and description \"awesome phone\"",
;;			 :raw "When I create a new product with name \"iphone 6\" and description \"awesome phone\"",
;;			 :params [{:type :value, :val "iphone 6"} {:type :value, :val "awesome phone"}],
;;			 :order 0,
;;			 :glue nil}
;;			 ...steps]}
;;	        ...scenarios ],
;; :pre-run ()}
```
### Write glue-code

Then write the code that will get executed for each scenario steps:

```clojure

(require 'scenari.v2.core :refer [defwhen defthen])

(defwhen "I create a new product with name {string} and description {string}"
[_ name desc]
  (println "executing my product creation function with params " name desc)
  (let [id (UUID/next.)]
  	{:id (UUID/next. ) 
  	 :name name 
  	 :desc desc 
  	 :qty (rand-int 50) 
  	 :location-url (str "http://example.com/product/" id)}))


(defthen "I receive a response with an id {string}"
  [_ id]
  (println (str "executing the assertion that the product has been created with the id " id))
  id)
```

### Step expressions

A step's sentence matcher is a [cucumber expression](https://github.com/cucumber/cucumber-expressions): `{int}` `{float}` `{word}` `{string}`, optional text `apple(s)`, alternation `hot/cold`. `{number}` is not a cucumber type — clornichon defines it, so the glues written before still work, and it now accepts a sign and decimals. A glue defined with a `#"..."` literal stays a plain regex whatever it contains, matching the whole sentence, and its capture groups become the arguments — make a group non-capturing (`(?:a|b)`) if it is only there to group. A *string* sentence wrapped in `^...$` or `/.../` is read as a regex too.

A literal `/`, `(` or `)` in a sentence must be escaped, or it reads as alternation or optional text:

```clojure
(defthen "the folders {string} \\/ {string} exist" [state a b] ...)
```

**Tips**: you can get a function snippet generated for you when executing the spec without step function. Think about enclosing with quote 'your data' in step sentence to get them detected by the parser and it'll generate a step function skeleton in the output with the correct sentence matcher group.
Example: 

Executing the specification with the step sentence without any matching function:

```gherkin
When I create a new product with name "iphone 6" and description "awesome phone"
```

will generate in the stdout the following step function skeleton:

```
Missing step for : When I create a new product with name "iphone 6" and description "awesome phone"
(defwhen "I create a new product with name {string} and description {string}"  [state arg0 arg1]  (do "something"))
```

### how to get data from the scenario into your step function

One argument per token of the sentence matcher, left to right, after the state — and each token converts its capture: `{int}` gives a number, `{string}` the text without its quotes, `{word}` a String. The sentence's own literals are not read: what a step receives is what its tokens capture.

A datatable or a doc string is appended after those, as one more argument: a vector of maps for the table, the string for the doc string.


### Execute scenario(s)
 There is three-way to execute scenarios, depending on your situation

#### Tree execution
Declaring your specification using `scenari.v2.core/deffeature` returns the parsed specification as clojure data structure. By using `scenari.v2.core/run-scenario`, the specification as data will be ran

```Clojure
(require 'scenari.v2.core :refer [run-feature run-features])
(run-feature #'my-specification)

;;OR
(require 'scenari.v2.core :refer [run-scenarios])
(run-features #'my-specification)
```

The execution report will be returned, rely on same clojure data-structure returned by `scenari.v2.core/deffeature`. Will set : 
- final `:status` of scenario(s) execution
- step `:status` as `pending` when not executed, `failed` when assertions fail or exception thrown, `success` at last
- step `:input-state` as the value returned by the previous step executed (empty map for the first one)  
- step `:output-state` as the value returned by the current step

This method is useful for debugging.

#### Clojure-test execution
Use clojure-test reporting system by printing execution. 

```clojure
(require 'scenari.v2.test :refer [run-feature])
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

Output is colorized following the gherkin syntax: keywords in bold cyan, `{string}`
and `{number}` parameters in yellow, and the sentence itself in green, red or grey
depending on whether the step passed, failed or never ran. Tags are cyan, free
descriptions, docstrings and datatables grey. Coloring honours kaocha's
`--color` / `--no-color`, so a piped or CI run can be kept plain.
Useful to integrate a feature in a clojure test namespace


#### Kaocha runner
Kaocha is a test runner and handle test phase lifecycle. 

By defining a test type in your kaocha configuration file (`tests.edn` by default) like this  
```clojure
#kaocha/v1
        {:tests [{:id                                :scenario
                  :type                              :kaocha.type/scenari
                  :kaocha/source-paths               ["src"]
                  :kaocha/test-paths                 ["test/scenario"]
                  :kaocha.type.scenari/glue-paths    ["test/scenario/glue"]}]}
```
You are able to launch your scenario using kaocha repl utility function

```clojure
(require 'kaocha.repl :as krepl)
(krepl/run :scenario)

;; Testing scenario : create a new product
;;   When I invoke a GET request on location URL         (from scenari.v2.glue/"I invoke a GET request on location URL")
;;   When I create a new product with name "iphone 6" and description "awesome phone" with properties         (from scenari.v2.glue/"I create a new product with name {string} and description {string} with properties")
;;       | size | weight |
;;       | 6    | 2      |
;;   Then I receive a response with an id 56422         (from scenari.v2.glue/"I receive a response with an id {number}")
;;   Then a location URL         (from scenari.v2.glue/"a location URL")
;; 
;; 
;; 1 tests, 1 assertions, 0 failures.
;; => #:kaocha.result{:count 1, :pass 1, :error 0, :fail 0, :pending 0}
```
Suitable when using kaocha to manage test lifecycle.

#### Filtering by tag

Register the `scenari-tags` plugin to get `--tags`, which takes a
[cucumber tag expression](https://github.com/cucumber/tag-expressions):

```clojure
#kaocha/v1
        {:tests   [...]
         :kaocha/plugins [:kaocha.plugin/scenari-tags]}
```

```bash
./test.sh --tags "@smoke and not @wip"
./test.sh --tags "(@api or @ui) and not @slow"
```

The expression is evaluated **per scenario**, on the gherkin `@annotations` the
scenario carries — including those it inherited from its `Feature`, its `Rule`
and its `Examples` table. So `--tags "@smoke and not @wip"` on a feature tagged
`@smoke` runs every scenario of that feature except the `@wip` ones.

kaocha's own `--focus-meta` / `--skip-meta` still work, and combine with
`--tags`, but they are limited to OR: a repeated `--focus-meta` matches a
testable carrying *any* of the keys, and the focus is dropped for a whole
subtree as soon as one node matches — a feature tagged `@smoke` therefore runs
all of its scenarios, tagged or not. What each can express:

| cucumber tag expression | with `--focus-meta` / `--skip-meta` |
|---|---|
| `@smoke` | `--focus-meta :smoke`, but the whole feature if the tag is on the feature |
| `not @wip` | `--skip-meta :wip` |
| `@smoke or @api` | `--focus-meta :smoke --focus-meta :api` |
| `@smoke and not @wip` | `--focus-meta :smoke --skip-meta :wip` |
| `@smoke and @api` | ✗ |
| `(@a or @b) and (@c or @d)` | ✗ |
| `not (@a and @b)` | ✗ |

Two differences worth knowing: `--focus-meta` also reads the var metadata of a
`deffeature` (`(deffeature ^:wip my-feature ...)`) while `--tags` only reads
gherkin tags, and `--tags` leaves non-scenari suites alone — combine it with
`--focus :scenario` to run the features only. The expression can also be set in
`tests.edn` as `:kaocha.plugin.scenari-tags/expression`, which is how to use it
from `kaocha.repl`.

### Using hooks
By providing an options maps in `scenari.v2.core/deffeature`, you can specify function which execute :
- `:pre-run` before feature execution
- `:post-run` after feature executed
- `:pre-scenario-run` before each scenario execution
- `:post-scenaro-run` after each scenario executed
Example:

```Clojure
(require 'scenari.v2.core :refer [deffeature])

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

### Provide an initial state
For each scenario execution, an initial state can be provided within the options map of `deffeature`.

Example:

```clojure
(deffeature my-specification "./path/to/feature/file"
			{:default-scenario-state {:foo "bar"}})
```
By default, the scenario state is an empty map `{}`.

## Documentation 

### Development Workflow
The [Development Workflow Guide](doc/development-workflow.md) provides a comprehensive overview of the full development cycle when using Clornichon. It covers writing feature files, defining features in code, implementing step definitions, and understanding how execution works.

### Internal Feature Structure
The [Feature Structure Documentation](doc/feature-structure.md) provides a detailed explanation of the internal data structure used to represent features, scenarios, and steps in Clornichon. This is particularly useful when extending or customizing Clornichon.

### Declaring same step (glue-code) but different namespace
Sometimes, you have to declare the same step (using the sentence matcher) but for different context (domain or component level for exemple).

TODO Put an exemple about step proximity resolution

### Macros (deprecated)
There are 3 macros available for given/when/then:

```clojure
;; a sentence for matching a step in the scenarios
;; a params vector: 
;;    the first param is the return of the previous step (nil if first step)
;;    then one param for each sentence group (aka. something in parens (...)) you define.
;; a body function.

(defgiven sentence-matcher params body)
(defwhen sentence-matcher params body)
(defthen sentence-matcher params body)
```

Macros are here for convenience, plain-old function are also available, you have to provide the step execution function as parameters with the function having groups count + 1 parameters (one for the previous step return and one params for each groups in the sentence matcher).

```clojure
(ns mystuff
  (:require [spexec :as spec]))
...
(Given sentence-matcher fn)
(When sentence-matcher fn)
(Then sentence-matcher fn)
```

### Chaining steps 
Steps often produce side effect or retrieve some stuffs (fn, data) to be used in the next ones, you can store your state in your code or in the scenario itself, but I think an easier mechanism is to think of the steps like a chain and pass a data structure from the return of a step to the input of the next one (very similar to [ring handlers](https://github.com/ring-clojure/ring/wiki/Concepts) or chain of responsibility for instance). So, each step function's return is taken as the input for the next step as the first argument (you can name it `_` if you don't need it). A good practice would be to use a map or vector and then destructure it as the first param of the next step, like :

```clojure
(defwhen "my sentence to be matched with {string} and {string}" 
         [[key1-in-previous-result k2] param1 param2] 
         (do-something param1 k2 param2) ...)
```

A step whose last form returns `nil`, `true` or `false` — an assertion, a
`doseq`, a `println` — keeps the state it was given, instead of passing that
value on. So a `defthen` that only asserts needs no trailing `state`:

```clojure
(defthen "my cart should contain {number} item" [state n]
         (is (= n (count (:cart state)))))   ; the next step still gets state
```

### Advanced usage (deprecated)

I use Spexec to test Spexec (yes it eats its own dog food, pretty amazing :-) only a dynamic language like Lisp can do that as easily), only the bootstrap step "Given the step function" is needed:

```gherkin
Scenario: a scenario that test spexec using spexec
Given the step function: (defgiven (re-pattern "^this scenario in a file named (.*)") [_ feature-file-name] [feature-file-name])
Given the step function: (defwhen (re-pattern "^I run the scenarios with '(.+)'") [prev-ret my-data] (conj prev-ret (str "processed" my-data)))
Given the step function: (defthen (re-pattern "^I should get '(.+)' from scenario file '(.*)' returned from the previous when step") [prev-ret expected-data scenario-file] (clojure.test/is (= (last prev-ret) expected-data))(clojure.test/is (= (first prev-ret) scenario-file)))
Given this scenario in a file named resources/spexec.feature
When I run the scenarios with 'mydatavalues'
Then I should get 'processedmydatavalues' from scenario file 'resources/spexec.feature' returned from the previous when step
```

```clojure
(defgiven "the step function: {string}" [_ step-fn]
   (eval (read-string step-fn)))
```

### Run the scenarios with each steps

### Logging

## Origins

Clornichon started as a fork of [scenari](https://github.com/jgrodziski/scenari), written by Jérémie Grodziski at [DefSquare](https://defsquare.io), and has since diverged far enough to go its own way: the Gherkin parser, the step expressions, the Kaocha integration and the reporting have been rewritten. The namespaces still carry the `scenari` name. Thanks to the original authors for the foundations.

## Rationale

*In the words of scenari's original author:*

I'm used to [JBehave](http://jbehave.org/) and I wanted a BDD framework with an [external DSL](http://www.martinfowler.com/bliki/DomainSpecificLanguage.html) following the [gherkin grammar](https://github.com/cucumber/cucumber/wiki/Gherkin) but also with an easy and fast setup and with steps written in [Clojure](http://clojure.org/). The previous BDD attempt I known in Clojure were all with an [internal DSL](http://www.martinfowler.com/bliki/DomainSpecificLanguage.html). I prefer an external one because I think it's easier to share the scenarios with a domain expert. I you prefer an internal DSL BDD Framework, have a look at [Speclj](http://speclj.com/).

Proper compatibility with traditional clojure.test interfaces is needed, for instance we could make the following relations between Clornichon/BDD concepts and clojure.test:
- A scenario execution is like a `deftest`. Particularly, we need to associate steps with Gherkin scenarios defined in one or more feature files. For that we would define this association between steps (`defgiven`, `defwhen` and `defthen`) and scenarios with `(defscenarios "my.feature")`. The execution would then be run with `(run-scenarios)` much like `(run-tests)`. In case of examples table used to feed the scenario with data, each data row would be associated with a new testing context for each steps (the `testing` context description would be the steps sentence with all data placeholder replaced with the actual ones).
- A scenario's step is like a `testing` context inside a `deftest`. 
Also, steps and scenarios association must be isolated within namespace to avoid collisions when the same scenarios are used with different steps (like ones for domain test, others for integration testing, etc.).
The compatibility with clojure.test would also be with its various reports available (`:pass`, `:fail`, etc.) with reports specific to narrative, scenarios and steps. 
Concerning assertion, steps could contains `clojure.test/is` assertions or throws exception that will be handled properly like clojure.test ones. 

Feature files are parsed by [`io.cucumber/gherkin`](https://github.com/cucumber/gherkin), the reference implementation, so clornichon reads what Cucumber reads: the ~70 languages, `Rule`, tags on `Examples`, doc string content types. The *step sentence* — the half gherkin does not cover, the one that binds a sentence to Clojure code — is a [cucumber expression](https://github.com/cucumber/cucumber-expressions), matched and generated by cucumber's own implementation.

Jérémie presented the internals of the original library at the Clojure Paris User Group and the slides are here: ["Anatomy of a BDD Execution Library in Clojure"](https://speakerdeck.com/jgrodziski/anatomy-of-a-bdd-execution-library-in-clojure).

## TODOS

Gaps against Cucumber, roughly by value/effort.

### Step expressions

* [ ] custom parameter types: the `ParameterTypeRegistry` is in `glue.clj`, it needs a public way to add one
* [ ] a datatable API beyond a vector of string maps: lists, transpose, diff, row-to-entity conversion

### Execution

* [x] `--dry-run`, through `:kaocha.plugin/scenari-dry-run`: lists the steps that resolve no glue, and with `--unused-glues` the glues no scenario uses, without running anything
* [ ] an unresolved step should be `:undefined`, not the NPE `run-step` raises by calling `(apply nil ...)`
* [ ] stop-on-failure? as an option for execution
* [ ] rerun only the scenarios that failed, `--retry n` for flaky ones

### Reporting

* [ ] measure step and scenario durations -- nothing is timed today, which also blocks a slowest-steps report
* [ ] cucumber-messages / JSON output, the interchange format the whole ecosystem reads (Allure, Cucumber Reports, CI). The parser already speaks it: a feature is read as `Envelope` messages, only the run needs to emit its own
* [ ] usage report: which glues ran, which are dead
* [ ] attachments (screenshots) from a step or a hook

### Hooks

* [ ] step-level hooks, and suite-level before-all / after-all
* [ ] tag-conditional hooks (`@before "@web and not @slow"`)
* [ ] pass the scenario -- name, tags, status -- to the hook, which currently receives nothing
* [ ] hooks shared across features, instead of one options map per `deffeature`

### Gherkin

* [ ] a `Rule` level in the report and in the kaocha tree; rules are parsed but flattened into the feature

### Glue code

* [ ] give another way to declare steps without macro (proper defn with `:scenari/regex` in meta)

Deliberately out of scope: parallel execution, dependency injection (the chained
scenario state replaces it), IDE integration.

## License

Clornichon is released under the terms of the [MIT License](http://opensource.org/licenses/MIT).

Copyright © 2024 DefSquare defsquare.io

Copyright © 2026 Hiram Madelaine

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
