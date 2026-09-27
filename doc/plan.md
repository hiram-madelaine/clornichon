# Clornichon — prochaines étapes (communauté + refonte Electre)

## Contexte
0.1.2 publié (Clojars + cljdoc, docs, ADR). La roadmap (`doc/roadmap.md`) liste les écarts
avec Cucumber. Electre = le « large production codebase » du README (250+ features,
3 400 steps, 440 glues) ; son code n'est pas accessible ici, le classement Electre
s'appuie sur la roadmap et le README (perf = priorité).

## Gestion d'état

États : `TODO` · `EN COURS` · `BLOQUÉ` · `FAIT` · `ABANDONNÉ`

Règles :
- Un seul item `EN COURS` à la fois.
- Passage à `FAIT` seulement quand : `./test.sh` vert, entrée CHANGELOG `[Unreleased]`,
  case cochée dans `doc/roadmap.md` si l'item y figure, commit fait.
- `BLOQUÉ` / `ABANDONNÉ` : une ligne de raison dans la colonne Notes.
- Ce fichier est copié dans le repo (`doc/plan.md`) au démarrage et mis à jour à chaque
  changement d'état, dans le même commit que le travail concerné.

## Tableau de bord

| #  | Item                                              | Lot | Cible      | État | Notes |
|----|---------------------------------------------------|-----|------------|------|-------|
| 1  | Step non résolu → message clair, pas NPE          | 1   | les deux   | FAIT | statut `:fail` gardé ; `:undefined` distinct avec #6 |
| 2  | Retirer les dépendances inutilisées               | 1   | communauté | FAIT | `tools.namespace` gardé : utilisé |
| 3  | CI GitHub Actions                                 | 1   | communauté | TODO |       |
| R1 | Release 0.1.3                                     | 1   | les deux   | TODO | après 1–3 |
| 4  | Durées step/scénario + rapport « slowest steps »  | 2   | Electre    | TODO |       |
| 5  | Hooks reçoivent le scénario + hooks par tag       | 2   | Electre    | TODO |       |
| 6  | Sortie cucumber-messages (NDJSON)                 | 3   | communauté | TODO |       |
| 7  | Types de paramètres custom publics                | 3   | communauté | TODO |       |
| 8  | Namespace `clornichon.*` (alias)                  | 3   | communauté | TODO | décision à prendre |

## Détail des items

### 1. Step non résolu → message clair — FAIT
`run-step` (`src/scenari/v2/core.clj`) levait une NPE (`(apply nil ...)`) sans glue.
Il lève maintenant `Undefined step: <phrase>` + le skeleton (`generate-step-fn`), statut
`:fail` : aucun consommateur (reporter, kaocha, `--doc-html`) n'a eu à changer.
Test : `undefined-step-names-the-step-test` dans `test/scenari/v2/feature_test.clj`.

### 2. Dépendances inutilisées — FAIT
`tools.logging` et `clojure.java-time` retirés de `deps.edn` (le pom est généré depuis la
basis par `script/build.clj`). `org.clojure/tools.namespace` reste : `kaocha/type/scenari.clj`
le requiert, et kaocha tire un fork (`lambdaisland/tools.namespace`), pas celui-ci.

### 3. CI
`.github/workflows/test.yml` : checkout, setup Java + Clojure CLI, `./test.sh`, sur push/PR.
`CI=true` active déjà le profil `:ci` (junit dans `target/junit.xml`).
Vérif : run vert sur la PR.

### 4. Durées + slowest steps
`System/nanoTime` autour de `apply f` dans `run-step`, stocké en `:duration-ns` sur le
step. Plugin kaocha `--slowest-steps N` sur le modèle de
`src/kaocha/plugin/scenari_dry_run.clj`, agrégé par glue.
Vérif : corpus Electre, durée totale avant/après (overhead négligeable), lecture du top-N.

### 5. Hooks
`run-hooks` (`core.clj:252`) appelle `(pre-run-fn)` sans argument : passer le scénario
(nom, tags, statut en post-run). Arité 0 conservée pour la compat. Filtre par tag via
`io.cucumber/tag-expressions` (déjà en dépendance, cf. `scenari_tags.clj`).

### 6. cucumber-messages
Plugin qui émet des `Envelope` NDJSON (le parser en produit déjà) + mapping des
résultats de run. Plus gros chantier : à détailler quand il passe `EN COURS`.

### 7. Types de paramètres custom
Exposer une fonction publique sur le `ParameterTypeRegistry` de `glue.clj` + doc dans
`doc/step-expressions.md`.

### 8. Namespace `clornichon.*`
Décision d'abord (alias non cassant vs deux noms à documenter). Pas de code avant.

## Hors périmètre (YAGNI)
Retry/rerun, stop-on-failure, attachments, niveau `Rule` dans le rapport, déclaration
sans macro : attendre une demande réelle.
