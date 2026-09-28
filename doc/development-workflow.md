# Clornichon Development Workflow

This document outlines the development workflow when using Clornichon for Behavior-Driven Development (BDD) in Clojure projects. It covers the complete journey from writing scenarios to executing and maintaining them.

## Overview

The Clornichon development workflow follows these main steps:

1. Write scenarios in Gherkin format (`.feature` files)
2. Define feature references using `deffeature`
3. Implement step definitions (glue code) 
4. Execute and validate the scenarios
5. Refine and iterate

Let's explore each step in detail.

## 1. Writing Feature Files

Feature files use the Gherkin syntax and typically have a `.feature` extension. They describe behaviors from a user's perspective.

### Basic Structure

```gherkin
Feature: Shopping Cart
  As a customer
  I want to manage items in my cart
  So that I can purchase what I need

  Scenario: Add item to empty cart
    Given I have an empty shopping cart
    When I add "Clojure Programming" book to the cart
    Then my cart should contain 1 item
    And the item should be "Clojure Programming" book
```

### Key Components

- **Feature**: The overall functionality being described
- **Narrative**: "As a..., I want to..., So that..." pattern explaining the purpose
- **Scenarios**: Specific examples of the feature in action
- **Steps**: Individual actions and assertions (Given/When/Then/And)

### Tips for Writing Good Scenarios

- Focus on business value and user perspective
- Keep scenarios concise and focused on a single behavior
- Use declarative style ("what" rather than "how")
- Maintain consistency in terminology
- Use data tables for multiple examples

### Example with Data Tables

```gherkin
Scenario: Calculate discounts
  Given the following products in catalog:
    | product    | price | category |
    | Keyboard   | 100   | hardware |
    | Mouse      | 50    | hardware |
    | Clojure    | 40    | book     |
  When I apply the "SUMMER10" discount code
  Then the prices should be:
    | product    | discounted_price |
    | Keyboard   | 90               |
    | Mouse      | 45               |
    | Clojure    | 36               |
```

### Example with Scenario Outline

```gherkin
Scenario Outline: Apply tax based on location
  Given a product with price <base_price>
  When shipping to <location>
  Then the final price should be <final_price>

  Examples:
    | base_price | location | final_price |
    | 100        | US       | 108         |
    | 100        | EU       | 120         |
    | 100        | AU       | 110         |
```

### Example with Doc Strings

Doc strings (delimited by triple quotes) are useful for passing multi-line text content:

```gherkin
Scenario: Create a blog post with markdown
  Given I am logged in as an author
  When I create a new blog post with content:
    """
    # Introduction to Clojure

    Clojure is a dynamic, general-purpose programming language.

    ## Key Features
    - Functional programming
    - Immutable data structures
    - Runs on the JVM
    """
  Then the post should be formatted as HTML
  And the title should be "Introduction to Clojure"
```

Doc strings are commonly used for:
- JSON or XML payloads
- Markdown or HTML content
- Multi-line configuration
- Email templates
- Test data fixtures

## 2. Defining Feature References

After writing the feature file, you need to reference it in your Clojure code using `deffeature`.

```clojure
(ns my-project.shopping-cart-test
  (:require [clojure.test :refer :all]
            [scenari.v2.core :as scenari :refer [deffeature]]))

;; Reference to the feature file
(deffeature shopping-cart "resources/features/shopping_cart.feature")
```

This creates a test that can be executed by Clojure's test runner. The `deffeature` macro:

1. Loads and parses the feature file
2. Creates a Clojure test that will execute all scenarios in the feature
3. Associates the feature with the current namespace for step discovery

### Configuration Options

You can customize the feature execution with options:

```clojure
(deffeature shopping-cart "resources/features/shopping_cart.feature"
  {:pre-run [#'setup-database]
   :post-run [#'teardown-database]
   :pre-scenario-run [#'setup-cart]  
   :post-scenario-run [#'cleanup-cart]
   :default-scenario-state {:user-id "test-user"}})
```

## 3. Implementing Step Definitions (Glue Code)

Step definitions (also called "glue code") connect the Gherkin steps with actual Clojure code. Clornichon provides macros for defining these connections.

### Basic Step Definitions

```clojure
(ns my-project.shopping-cart-test
  (:require [clojure.test :refer :all]
            [scenari.v2.core :as scenari :refer [deffeature defgiven defwhen defthen]]))

(defgiven "I have an empty shopping cart" [state]
  (assoc state :cart []))

(defwhen "I add {string} book to the cart" [state book-title]
  (update state :cart conj {:title book-title :type :book}))

(defthen "my cart should contain {number} item" [state item-count]
  (is (= item-count (count (:cart state)))))

(defthen "the item should be {string} book" [state book-title]
  (is (= book-title (-> state :cart first :title))))
```

### Parameter Handling

Clornichon supports various parameter types in step definitions:

A sentence matcher is a [cucumber expression](https://github.com/cucumber/cucumber-expressions), so its tokens are cucumber's own:

- `{string}`: a quoted string — single or double quotes — passed without its quotes
- `{int}`, `{float}`, `{word}`, and the other built-in types
- `{number}`: clornichon's own, kept for the glues written before the others existed; it accepts a sign and decimals
- optional text `apple(s)` and alternation `hot/cold`; a literal `(` or `/` must be escaped (`\/`)
- Table data: Automatically passed as a vector of maps
- Doc strings: Automatically passed as a multi-line string

A glue defined with a `#"..."` literal stays a plain regex whatever it contains — reading it as an expression would turn a `([^"]*)` into optional text and raise on its `\"` — and it matches the whole sentence, as `re-matches` did. Its capture groups become the arguments, so a group that only groups must be made non-capturing (`(?:a|b)`). A *string* sentence wrapped in `^...$` or `/.../` is read as a regex too.

### Working with Tables

```clojure
(defgiven "the following products in catalog:" [state table-data]
  (assoc state :products
    (into {} (map (fn [row] [(:product row) row]) table-data))))
```

### Working with Doc Strings

```clojure
(defwhen "I create a new blog post with content:" [state doc-string]
  ;; doc-string contains the multi-line text from the feature file
  (let [parsed-post (parse-markdown doc-string)]
    (assoc state :post {:content doc-string
                        :title (extract-title parsed-post)
                        :html (markdown->html doc-string)})))
```

### State Passing Between Steps

Each step function receives the state from the previous step, and what it returns is the state of the next one. This allows for data to flow through your scenario.

A step whose last form returns `nil`, `true` or `false` -- an assertion, a `doseq`, a `println` -- keeps the state it was given: a `defthen` that only asserts needs no trailing `state`, as above. See [State and hooks](state-and-hooks.md#chaining-steps).

### Best Practices for Step Definitions

- Keep step functions focused and small
- Use descriptive step names
- Include meaningful assertions
- Don't couple steps too tightly to implementation details
- Store state in a map for flexibility

## 4. Execution Flow

When a feature is executed, Clornichon performs the following steps:

1. **Feature Loading**: Parse the feature file into an AST
2. **Feature Transformation**: Convert the AST into an executable structure
3. **Scenario Execution**: For each scenario:
   - Initialize the scenario state (empty map or provided default)
   - Execute any pre-scenario hooks
   - For each step:
     - Find the matching step definition
     - Execute the step function with the current state and parameters
     - Capture the result and status
     - Pass the result state to the next step
   - Execute any post-scenario hooks
4. **Reporting**: Collect results and generate reports

### Step Matching Process

The step matching process is a key part of Clornichon:

1. Convert the step text from the feature file into a searchable format
2. Look for step definitions that match the pattern
3. If multiple matches are found, use namespace proximity to select the best match
4. Extract parameters from the step text
5. Execute the matching function with state and parameters

## 5. Execution and Validation

### Running Tests

A `deffeature` is a `deftest`: `clojure.test/run-tests`, your editor's test runner or any clojure.test runner picks it up.

```clojure
(clojure.test/run-tests 'my-project.shopping-cart-test)
```

Clornichon integrates with Kaocha for more advanced test execution, once a suite of type `:kaocha.type/scenari` is declared in `tests.edn` -- see [Running features](running.md#kaocha):

```bash
clojure -M:test -m kaocha.runner                  # Run all tests
clojure -M:test -m kaocha.runner --focus my-test  # Run specific test
```

### Test Output

The test output will show each scenario and step execution:

```
________________________
@checkout
Feature : Shopping Cart
  A cart holds items and can be emptied.

Testing scenario : Add item to empty cart
  Given I have an empty shopping cart         (from my-project.glue/"I have an empty shopping cart")
  When I add "Clojure Programming" book to the cart         (from my-project.glue/"I add {string} book to the cart")
  Then my cart should contain 1 item         (from my-project.glue/"my cart should contain {number} item")
  And the item should be "Clojure Programming" book         (from my-project.glue/"the item should be {string} book")
________________________
```

The output is colorized along the gherkin syntax - bold cyan keywords, yellow
`{string}`/`{number}` parameters, cyan tags, grey descriptions, docstrings and
datatables - and the sentence takes the colour of the step's outcome: green when it
passed, red when it failed, grey when it never ran. Pass `--no-color` (or run under
kaocha with colour disabled) for plain output.

### Debugging Tests

When a step fails, Clornichon provides information about the failure:

```
Testing scenario : Add item to empty cart

FAIL in (shopping-cart) (glue.clj:42)
expected: (= 1 (count (:items state)))
  actual: (not (= 1 0))
  Given I have an empty shopping cart         (from my-project.glue/"I have an empty shopping cart")
  When I add "Clojure Programming" book to the cart         (from my-project.glue/"I add {string} book to the cart")
  Then my cart should contain 1 item         (from my-project.glue/"my cart should contain {number} item")
  Step failed
  And the item should be "Clojure Programming" book         (from my-project.glue/"the item should be {string} book")
Add item to empty cart FAILED
```

A scenario is reported once it has run: what its steps print while they run, the
report of a failed `is` included, comes above them. A scenario stops at its first
failed step, so that report is the one of the step marked `Step failed`.

The failing step is printed in red, and every step after it in grey - they are
reported as pending rather than dropped, so the scenario stays readable end to end.

To examine the state passed between steps, run the feature as data: `scenari.v2.core/run-feature` returns each step with its `:input-state` and its `:output-state`. See [Running features](running.md#as-data).

## 6. Advanced Features

### Namespace Resolution

When multiple step definitions match a step, Clornichon uses namespace proximity to choose:

1. Steps in the same namespace as the feature have highest priority
2. Steps in namespaces with more shared segments have higher priority
3. If equal priority, an error is raised to avoid ambiguity

### Custom Parameter Types

Define your own `{token}` with `define-parameter-type!`: a regex and the function that converts its capture. See [Step expressions](step-expressions.md#custom-parameter-types).

### Hooks and Lifecycle Management

Clornichon supports several hook points for setup and teardown:

- Pre-feature hooks: Run once before the entire feature
- Post-feature hooks: Run once after the entire feature
- Pre-scenario hooks: Run before each scenario
- Post-scenario hooks: Run after each scenario

A hook can receive the scenario's name, tags and status, and be restricted to some tags: see [State and hooks](state-and-hooks.md#hooks).

These are declared in the options of each `deffeature`. A hook every feature needs is declared once, as a global hook: a var marked `:scenari/hook`, which also gives `:before-all` and `:after-all` around the whole suite. See [Global hooks](state-and-hooks.md#global-hooks).

## 7. Releasing Clornichon

For the maintainers of the library. `release.sh` tags, builds, publishes to Clojars and pushes. A release takes three steps.

### Commit the "Prepare"

The pom points to the tag, and cljdoc builds its documentation from it: the docs have to be ready in the commit the tag lands on. In one commit, `Prepare 0.2.0`:

- date the `[Unreleased]` section of `CHANGELOG.md` as the version to come, `# [0.2.0] - 2026-09-28`;
- install that version in `README.md` and in `doc/getting-started.md`.

### Dry run

Without the Clojars credentials, the script checks that and stops before it tags:

```bash
./release.sh minor
# Docs are ready for 0.2.0
# ... CLOJARS_USERNAME is not set
```

It says what is missing, and refuses a working tree with uncommitted changes:

```
CHANGELOG.md has no dated section for 0.2.0
README.md does not install 0.2.0
doc/getting-started.md does not install 0.2.0
Nothing tagged: commit the "Prepare 0.2.0" first.
```

### Release

```bash
CLOJARS_USERNAME=... CLOJARS_PASSWORD=<deploy token> ./release.sh major|minor|patch
```

The script bumps the version of the nearest tag, writes it into `src/scenari/meta.clj`, commits and tags. It then builds the jar, deploys it to Clojars, and pushes the commit and the tag only once Clojars accepted the artifact. If the tag is not the one the docs were prepared for, nothing is published: the tag and its commit stay local.

`./build.sh` alone builds the jar of the current version and installs it in the local Maven repository: under `~/.m2` it replaces the artifact of that version downloaded from Clojars.

## Conclusion

The Clornichon development workflow provides a structured approach to Behavior-Driven Development in Clojure. By following the pattern of writing features, defining glue code, and executing tests, you can create living documentation that verifies your application's behavior.

Remember that the true value of BDD comes from the collaborative process—use feature files as a communication tool between developers, testers, and domain experts to ensure a shared understanding of requirements and behaviors.