# 3. Parse feature files with Cucumber's Gherkin parser

Date: 2026-08-29

## Status

Accepted

## Context

scenari parsed feature files with its own instaparse grammar. Every Gherkin construct it did not know -- `Rule`, `Background`, tags on `Examples`, several `Examples` blocks, ~70 languages, doc string content types -- had to be added by hand, and a file Cucumber accepts could be rejected or misread.

## Decision

Parse feature files with [`io.cucumber/gherkin`](https://github.com/cucumber/gherkin), the reference implementation, and build the feature map from its pickles.

## Consequences

* Clornichon reads what Cucumber reads, and follows the language as Cucumber evolves it.
* Parse errors come with line and column.
* Stricter: a file must start with `Feature:` (or a tag or comment), a non-English file needs a `# language:` header.
* A Java dependency, and the parser's messages (`Envelope`) are the base of a future cucumber-messages report.
