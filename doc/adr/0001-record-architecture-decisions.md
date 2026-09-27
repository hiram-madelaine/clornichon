# 1. Record architecture decisions

Date: 2026-09-27

## Status

Accepted

## Context

Clornichon's design choices -- why a fork, why Cucumber's parser, why the glue is resolved early -- were scattered across the README, the CHANGELOG and commit messages. A user wondering why something works the way it does had to dig.

## Decision

We record architecture decisions as [Architecture Decision Records](http://thinkrelevance.com/blog/2011/11/15/documenting-architecture-decisions), as described by Michael Nygard: one numbered file per decision in `doc/adr/`, with Context, Decision and Consequences. Decisions taken before this one are recorded after the fact, dated from when they were made.

## Consequences

A decision that is reversed gets a new ADR that supersedes the old one; the old one stays, marked `Superseded`.
