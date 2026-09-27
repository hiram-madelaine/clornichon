# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

# [Unreleased] #

## Added ##

A `Rule` has its own level: the report prints `Rule : <name>`, its tags and its
description once, above its scenarios, in the console and in `--doc-html`; the
Kaocha tree gets a group per rule, which `--focus <rule-name>` selects. Scenario
ids are unchanged, so an existing `--focus` still works.

## Changed ##

A scenario's `:description` is its own only. The rule's description, which was
prepended to it, now sits under `:rule` with the rule's name and tags.

# [0.1.8] - 2026-09-27 #

## Changed ##

The pom carries a description and the project URL, so Clojars and cljdoc
present Clornichon as a BDD library for Clojure.

# [0.1.7] - 2026-09-27 #

## Added ##

`:kaocha.plugin/scenari-messages` adds `--cucumber-messages FILE`: the run
written as a cucumber-messages NDJSON stream, which Cucumber's formatters (HTML,
JUnit XML, JSON) read. Scenarios and steps now carry the gherkin pickle ids as
`:id`, and `:started-at` timestamps; a feature keeps the parser's envelopes
under `:messages`. `->feature-ast` takes an optional `uri`, which `deffeature`
sets to the feature file.

# [0.1.6] - 2026-09-27 #

## Added ##

`scenari.v2.core/define-parameter-type!` defines a custom token for the
sentence matchers: `(define-parameter-type! "isbn" #"\d{13}" parse-isbn)`, then
`(defgiven "the book {isbn}" ...)` receives the converted value. The transform
gets one argument per capture group of the regex, or the whole match.

# [0.1.5] - 2026-09-27 #

## Added ##

A hook declared with one argument receives what it wraps: `:scenario-name`
and `:annotations` for a scenario hook, plus `:status` (`:success` or `:fail`)
after the scenario; `:feature` and `:annotations` for a feature hook. A hook
that has a zero arity is called without argument as before, even when it also
has a one-argument arity.

A hook whose var carries `:scenari/tags` metadata, a cucumber tag expression,
only runs for the scenarios whose tags match it. The expression is parsed when
the feature is loaded, and an invalid one fails there, naming the hook.

# [0.1.4] - 2026-09-27 #

## Added ##

Every step run carries its `:duration-ns`, the time its glue took. The new
kaocha plugin `:kaocha.plugin/scenari-slowest-steps` adds `--slowest-steps N`,
which prints after the run the N step definitions that took the most time, all
their calls added up.

# [0.1.3] - 2026-09-27 #

## Changed ##

A step whose sentence matches no glue fails with `Undefined step: <sentence>`
and the skeleton to paste, instead of the `NullPointerException` that
`(apply nil ...)` raised. Its status stays `:fail`.

`org.clojure/tools.logging` and `clojure.java-time` are no longer dependencies:
nothing in the library required them. A project that used them through
Clornichon must now declare them itself.

# [0.1.2] - 2026-09-27 #

## Changed ##

The documentation moves from the README to `doc/`, published on
[cljdoc](https://cljdoc.org/d/io.github.hiram-madelaine/clornichon): getting
started, step expressions, state and hooks, running features, migrating from
scenari, a glossary and architecture decision records. No change to the library.

# [0.1.1] - 2026-09-27 #

First release of Clornichon, forked from scenari 2.0.2: every change since
scenari 2.0.2 is below. 0.1.0 was rejected by Clojars for lacking a licence and
was never published.

## Changed ##

The project becomes Clornichon, published as `io.github.hiram-madelaine/clornichon`
from 0.1.1 on (instead of `io.defsquare/scenari`). The namespaces keep their
`scenari.*` names and the kaocha type stays `:kaocha.type/scenari`: only the
dependency coordinates change.

A step whose last form returns `nil` or a boolean keeps the state it was given,
instead of passing that value on. That is what an assertion returns (`is`
returns the boolean of its predicate) and what a side effect returns (`doseq`,
`println`): a `defthen` that forgot its trailing `state` replaced the scenario
state with `true`, without any error, and the next step received that boolean.
The trailing `state` of a step that only checks is no longer needed.

A step that really wants `nil` or `false` as its state no longer can — none
exists in the code, nor in the docs.

A step's datatable is no longer printed all grey: the `|` separators stay
grey, the column headers turn cyan like the tags, and the cells yellow like the
params already highlighted in the step's sentence. The padding is still
computed on the raw text — the column alignment does not move — and
`--no-color` gives the same output as before, byte for byte.

A step's arguments are the captures of its cucumber expression, converted by
their token: `{int}` gives a number, `{string}` the text without its quotes.
Nothing reads the sentence's literals any more — `find-sentence-params`, the
`sentence` and `step` instaparse grammars and `scenari.v2.parser` are gone, and
with them the instaparse dependency. Skeletons for a missing step come from
cucumber's `CucumberExpressionGenerator`, which also escapes what would read as
expression syntax.

Gained: an argument list that follows the matcher instead of the sentence
(`{float}` and `{word}` are usable, `12.5` is one argument and not two), single
quoted `'strings'`, and a sentence the old grammar could not split - `a < b`,
`{a: 1}` - no longer raises.

Breaking:

- A step whose sentence matches no glue has no value params, only its datatable
  or doc string. Nothing ran for such a step before either.
- The arguments of a regex glue are its capture groups, where they used to be
  the quoted literals found in the sentence. A group that only groups -
  `(consultation|création)` - now passes an argument, and a matcher written
  `\"(.+)\"` passes one where `\".+\"` no longer does. On the 401 glues of a real
  project, 9 were concerned.
- Generated skeletons suggest cucumber's types, `{int}` and `{double}`, where
  they used to suggest `{number}`. Both still match.
- `{string}` also matches a single-quoted `'value'`. Replayed on a corpus of 221
  real feature files: 5 sentences out of 1123 gained an argument that way, none
  lost one.

Step sentences are matched with `io.cucumber/cucumber-expressions`, the
reference implementation, instead of the two hand-rolled token substitutions.

Gained: `{int}` `{float}` `{word}` and the other built-in types, optional text
`apple(s)`, alternation `hot/cold`, and an undefined token now raises an error
naming the guilty glue instead of a `PatternSyntaxException`. `{number}` is kept
as a custom parameter type — the glues already written still match, and it now
accepts a sign and decimals. A glue defined with a `#"..."` literal is still a
plain regex whatever it contains, and still matches the whole sentence; a string
sentence wrapped in `^...$` or `/.../` is read as a regex too. The `/` of an
alternation is stripped from the generated var name, which `defn` would reject
as a qualified symbol.

Breaking:

- A literal `/`, `(` or `)` in a sentence matcher must be escaped (`\/`), or it
  reads as alternation or optional text. Replayed on a 221-file corpus: 2
  sentences out of 1123 were concerned.

Feature files are now parsed by `io.cucumber/gherkin`, the reference
implementation, instead of the hand-written instaparse grammar. `->feature-ast`
builds the feature map from gherkin *pickles*, which already resolve `Background`
splicing, `Rule` flattening, tag inheritance and `Scenario Outline` expansion.

Gained: the ~70 gherkin languages and the `# language:` header, tags on an
`Examples` block, comments inside an `Examples` table, doc string content types
(``` ```json ``` reaches the step as `:media-type`), and line/column in parse
errors.

Breaking:

- A `.feature` must start with a `Feature:` line (or a tag, comment or
  `# language:` header). A bare `Scenario:` is now a parse error.
- Non-English keywords require the `# language:` header; the language is no
  longer guessed from the keywords themselves.
- French features must write `Scénario:`, not `Scénario :` — the official
  dialect puts no space before the colon. In exchange the dialect is richer:
  `Soit`, `Sachant que`, `Lorsque`, `Donc`, `Et que` all work.
- `:scenario-name` no longer carries the leading space the old grammar left in.
- `:feature` is the feature name; an `As a / I want to / So that` narrative is
  free description text, per the spec, and lands in `:description`.
- `<placeholders>` are substituted in steps, their arguments and the scenario
  name, not in free description text.

## Added ##

`--dry-run`, through the `:kaocha.plugin/scenari-dry-run` plugin: checks that
every step of the selected scenarios resolves a step definition, without running
anything. The report says where each missing step is used (feature >
scenario), and the exit code is non-zero if one is left — enough to make it a CI
step.

The glue being resolved at parse time, everything is already in the test-plan:
walking it is enough. Without this, an undefined step only blew up when run, on
an `(apply nil ...)`, after the previous steps and their side effects.

The dry run also counts the other way round — the step definitions no selected
scenario uses — and `--unused-glues` lists them. Indicative only: a filter
shrinks the selection, so grows the list.

`--doc-html target/features.html`, through the new `:kaocha.plugin/scenari-doc`
plugin: the documentation of the scenarios as one HTML document — a clickable
table of contents, one anchor per scenario, tags, descriptions, steps with their
datatables and doc strings.

It is written from the test-plan, so after `--focus`, `--focus-meta` and
`--tags`: what would have run is exactly what gets documented. Nothing is run —
this is static documentation, not a run report, so kaocha announces that every
test was skipped.

`--doc-report target/report.html` writes the same document, but after the run:
every feature, scenario and step carries its status (badge, border, coloured
table of contents) and a failed step shows its error message.

`--dry-run`, `--doc-html` and `--doc-report` exclude each other: the first two
run nothing, so the one read second would see an empty selection. Combining
them exits with an error.

`--tags "@smoke and not @wip"`, the cucumber tag expression syntax, through the
new `:kaocha.plugin/scenari-tags` kaocha plugin. The expression is parsed by
`io.cucumber/tag-expressions` — cucumber's own — and evaluated per scenario, on
the gherkin tags it carries, inherited `Feature` / `Rule` / `Examples` tags
included.

kaocha's `--focus-meta` / `--skip-meta` could only express an OR of tags, and
dropped the focus for a whole subtree as soon as one node matched: a feature
tagged `@smoke` ran all of its scenarios, tagged or not. `@a and @b`,
parentheses and `not (...)` were out of reach. Both mechanisms still work and
combine; `--tags` only skips scenari testables, so a clojure.test suite in the
same run is left alone.

## Fixed ##

A step that throws now produces a `<failure>` in the junit-xml report and shows
up in kaocha's end-of-run summary. Only a step failing on an `is` did before —
one that threw left its testcase green in CI.

A scenario's kaocha id is qualified by its feature
(`:my.ns.my-feature/scenario-name` instead of `:scenario-name`). Kaocha matches
a run's events to a testable by id equality, so two features with a same-named
scenario used to show each other's failures; junit's `classname` was empty on
every testcase too. `--focus <scenario-name>` still works, the bare name is kept
as an alias.

# [1.4.4] - 2019-09-18

Add insta parse regex to handle unicode characters, numerics and punctuation
https://github.com/jgrodziski/scenari/pull/8

# [1.4.0] - 2019-05-17 #

## Added ##

Examples table as input step param
