# Clornichon Feature Data Structure Documentation

This document describes the internal data structure of a Clornichon feature after parsing from Gherkin text. Understanding this structure is helpful when extending or customizing Clornichon.

## Top-Level Structure

A feature is represented as a map with the following keys:

```clojure
{:scenarios [...]       ; Vector of scenario maps
 :feature "..."         ; The Feature title
 :description "..."     ; Optional free text between the Feature line and the first keyword
 :annotations #{...}    ; Optional annotations (tags)
 :pre-run [...]         ; Hook functions to execute before feature
 :status :success/:fail ; Status after execution
}
```

## Narrative

A narrative (`As a … I want to … So that …`) has no structure of its own: per
the gherkin spec it is free text under the Feature line, so it lands in
`:description`.

## Annotations

Annotations (tags) are stored as a set of strings:

```clojure
{:annotations #{"smoke" "regression" "api"}}
```

## Scenarios

Each scenario is represented as a map within the `:scenarios` vector:

```clojure
{:id "uuid-string"           ; Unique identifier
 :scenario-name "Name"       ; The scenario title
 :annotations #{...}         ; Optional annotations (tags) of the scenario
 :steps [...]                ; Vector of step maps
 :pre-run [...]              ; Functions to run before scenario
 :post-run [...]             ; Functions to run after scenario
 :default-state {}           ; Initial state for the scenario
 :status :success/:fail/:pending ; Execution status
}
```

## Steps Structure

Each step within a scenario is represented as a map:

```clojure
{:sentence-keyword :given/:when/:then/:and  ; Step type
 :sentence "Step text"                      ; The actual step text
 :raw "Given Step text"                     ; Full text with keyword
 :order 0                                   ; Position in scenario
 :glue {...}                                ; Matched step definition
 :params [...]                              ; The glue's captures, then the datatable/docstring block
 :status :success/:fail/:pending            ; Execution status
 :input-state {}                            ; State before execution
 :output-state {}                           ; State after execution
 :exception {...}                           ; If step failed
}
```

## Parameters

Parameters extracted from steps come in three types:

```clojure
;; Value parameters (extracted from step text)
{:type :value, :val "some string"}
{:type :value, :val 42}

;; Table parameters
{:type :table,
 :val [{:header1 "value1", :header2 "value2"},
       {:header1 "value3", :header2 "value4"}]}

;; Doc string parameters (multi-line text blocks)
{:type :doc-string,
 :val "This is a multi-line\ntext block that can contain\nany content including markdown"}

;; A doc string opened with a content type (```json) carries it
{:type :doc-string, :val "{\"a\": 1}", :media-type "json"}
```

## Glue Metadata

The `:glue` key contains information about the matched implementation function:

```clojure
{:step "I do something {string}"  ; Pattern to match
 :ns user.namespace               ; Function namespace
 :name function-name              ; Function name
 :ref #'user.namespace/function   ; Reference to actual function
 :warning "Warning message"       ; Optional warning
}
```

## Example Execution Flow

1. Feature is parsed from text by `io.cucumber/gherkin`, into one *pickle* per runnable scenario
2. Steps are matched to implementation functions via `find-glue-by-step-regex`
3. During execution, each step receives the previous step's output state
4. Parameters from the step text are extracted and passed to the implementation
5. Function results and status are captured in the step's `:output-state` and `:status`
6. Scenario status is derived from all contained steps' statuses
7. Feature status is derived from all scenarios' statuses

## Common Transformations

- From Gherkin text → GherkinDocument + pickles, via `io.cucumber/gherkin`
- From pickles → executable feature via `->feature-ast`
- Feature execution via `run-feature`
- Step execution via `run-step`

This data structure provides a flexible representation that preserves all information from the original Gherkin text while supporting execution, reporting, and integration with test frameworks.

## Constructs resolved at parse time

`Background`, `Rule` and `Scenario Outline` have no representation in this
structure. Gherkin's pickle compiler resolves them away: a pickle is one
runnable scenario, and the feature map is a thin translation of it.

- **Background** — its steps are spliced at the head of every scenario's `:steps`
  (the feature's background first, then the enclosing rule's, if any).
- **Rule** — only groups scenarios, so its scenarios are lifted into the
  feature's `:scenarios`, inheriting its tags and its description. The rule name
  is not kept.
- **Scenario Outline** — becomes one scenario per `Examples` row, with the
  `<placeholders>` substituted throughout the scenario, its name included.
