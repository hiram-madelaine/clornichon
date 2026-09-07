# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

# [Unreleased] #

## Changed ##

Un step dont la dernière forme rend `nil` ou un booléen garde l'état qu'il a
reçu, au lieu de le propager. C'est ce que rendent une assertion (`is` rend le
booléen de son prédicat) et un effet de bord (`doseq`, `println`) : un
`defthen` qui oubliait son `state` final remplaçait l'état du scénario par
`true`, sans erreur, et le step suivant recevait ce booléen. Le `state` final
d'un step qui ne fait que vérifier devient inutile.

Un step qui voudrait vraiment `nil` ou `false` comme état ne le peut plus —
aucun n'existe dans le code, ni dans la doc.

La datatable d'un step n'est plus imprimée en gris : les séparateurs `|`
restent gris, les en-têtes de colonne passent en cyan comme les tags, et les
cases en jaune comme les params déjà mis en valeur dans la phrase du step. Le
padding reste calculé sur le texte brut — l'alignement des colonnes ne bouge
pas — et `--no-color` produit la même sortie qu'avant, à l'octet près.

## Added ##

`--dry-run`, à travers le plugin `:kaocha.plugin/scenari-dry-run` : vérifie que
chaque step des scénarios sélectionnés résout bien un step definition, sans
rien exécuter. Le rapport dit où chaque step manquant est utilisé (feature >
scénario), et la sortie est non nulle s'il en reste un — de quoi en faire une
étape de CI.

Le glue étant résolu au parse, tout est déjà dans le test-plan : il suffit de
le parcourir. Sans ça, un step non défini n'explosait qu'à l'exécution, sur un
`(apply nil ...)`, après les steps précédents et leurs effets de bord.

Le dry run compte aussi l'inverse — les step definitions qu'aucun scénario
sélectionné n'utilise — et `--unused-glues` les liste. Purement indicatif : un
filtre réduit la sélection, donc grossit la liste.

`--doc-html target/features.html`, through the new
`:kaocha.plugin/scenari-doc` plugin: la documentation des scénarios en un
document HTML — sommaire cliquable, une ancre par scénario, tags, descriptions,
steps avec leurs datatables et docstrings.

Elle est écrite depuis le test-plan, donc après `--focus`, `--focus-meta` et
`--tags` : ce qui aurait tourné est exactement ce qui est documenté. Rien n'est
exécuté — c'est de la doc statique, pas un rapport de run, et kaocha annonce
donc que tous les tests ont été skippés.

`--doc-report target/report.html` produit le même document, mais après
exécution : chaque feature, scénario et step y porte son statut (pastille,
liseré, sommaire coloré) et un step en échec affiche son message d'erreur.

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

## Changed ##

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

Arguments passed to a step fn are unchanged for now: they still come from the
sentence's literals, not from the expression match.

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

# [1.4.4] - 2019-09-18

Add insta parse regex to handle unicode characters, numerics and punctuation
https://github.com/jgrodziski/scenari/pull/8

# [1.4.0] - 2019-05-17 #

## Added ##

Examples table as input step param
