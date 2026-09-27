# 4. Match step sentences with cucumber expressions

Date: 2026-08-29

## Status

Accepted

## Context

A glue's sentence matcher was turned into a regex by two hand-rolled substitutions (`{string}`, `{number}`), and a step's arguments were the quoted literals found in the sentence, not what the matcher captured. `{float}` or `{word}` were impossible, `12.5` read as two arguments, and some sentences (`a < b`) could not be split at all.

## Decision

Match step sentences with [`io.cucumber/cucumber-expressions`](https://github.com/cucumber/cucumber-expressions), the reference implementation. A step's arguments are the captures of the expression, converted by their token. Keep `{number}` as an extra parameter type so existing glues still match. A `#"..."` glue stays a plain regex.

Generate the skeleton of a missing step with cucumber's `CucumberExpressionGenerator`.

## Consequences

* All of cucumber's built-in types, optional text and alternation.
* `/`, `(` and `)` must be escaped in a string matcher.
* A regex glue passes its capture groups: a grouping-only group must be made non-capturing. On a real project, 9 glues out of 401 were concerned.
* The instaparse dependency is gone.
