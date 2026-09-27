# 6. Thread the scenario state through the steps

Date: 2014-08-08, amended 2026-08-30

## Status

Accepted

## Context

Steps need to share data: the product a `When` creates is the one a `Then` checks. Cucumber implementations use a *world* object, often filled by dependency injection.

## Decision

Each step receives, as first argument, what the previous step returned -- like a chain of Ring handlers. The first step receives `{}`, or the feature's `:default-scenario-state`.

Amended 2026-08-30: a step returning `nil` or a boolean keeps the state it was given. That is what an assertion (`is`) or a side effect (`doseq`, `println`) returns, and a `defthen` that forgot its trailing `state` silently replaced the state with `true`.

## Consequences

* No world object, no dependency injection, no mutable state: a step is a plain function of its input.
* A step cannot set the state to `nil` or `false`.
