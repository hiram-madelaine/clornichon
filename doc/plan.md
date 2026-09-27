# Clornichon — prochaines étapes (communauté + refonte Electre)

## Contexte
0.1.3 publié (Clojars + cljdoc, docs, ADR, CI). La roadmap (`doc/roadmap.md`) liste les écarts
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
| 3  | CI GitHub Actions                                 | 1   | communauté | FAIT | 1er run vert sur la PR #1 |
| R1 | Release 0.1.3                                     | 1   | les deux   | FAIT | publiée sur Clojars, tag `v0.1.3` |
| 4  | Durées step/scénario + rapport « slowest steps »  | 2   | Electre    | FAIT | 0 surcoût mesurable sur Electre ; 1 glue = ~28 % de la suite |
| R2 | Release 0.1.4 (`--slowest-steps`)                 | 2   | les deux   | FAIT | publiée sur Clojars, tag `v0.1.4` |
| E1 | Electre passe de scenari `2396ada` à clornichon 0.1.5 | 2 | Electre  | EN COURS | branche `chore/migration-clornichon-0.1.4` poussée (e8072f8ac2, 0.1.5) ; MR + CI à faire |
| 5  | Hooks reçoivent le scénario + hooks par tag       | 2   | Electre    | FAIT | arité 0 prioritaire (compat Electre) ; 392/392 verts |
| R3 | Release 0.1.5 (hooks)                             | 2   | les deux   | FAIT | publiée sur Clojars, tag `v0.1.5` |
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

### 3. CI — FAIT
`.github/workflows/test.yml` : Java 17 (temurin), Clojure CLI, cache `~/.m2` + `~/.gitlibs`,
`./test.sh` sur push `master` et PR. `CI=true` active le profil `:ci` (vérifié en local :
exit 0, `target/junit.xml` à 0 échec). `.gitignore` ignorait `.*` : exception `!/.github/`.
Pas d'entrée CHANGELOG (rien ne change pour les utilisateurs de la lib).
Premier run vert sur la PR #1 (21 s).

### 4. Durées + slowest steps — FAIT
`run-step` pose `:duration-ns` (succès comme échec) ; plugin
`:kaocha.plugin/scenari-slowest-steps`, `--slowest-steps N`, agrégé par glue (total,
appels, max). Le temps par scénario reste celui de `:kaocha.plugin/profiling`.
Test : `scenari-slowest-steps-test` dans `test/scenari/v2/core_test.clj`.

Mesure sur Electre (`diffusion/backend`, suite `:scenario`, 392 tests verts partout),
temps de suite selon kaocha, Clornichon substitué par `:override-deps` :

| Run                               | Suite  |
|-----------------------------------|--------|
| scenari `2396ada` (gitlib)        | 90,7 s · 93,0 s |
| `2396ada` en `local/root`         | 90,8 s |
| `HEAD`                            | 91,5 s |
| `HEAD` + `--slowest-steps 20`     | 95,1 s · 98,5 s · 86,8 s |

Bruit d'environnement de ±6 % (Postgres/Solr), aucun écart attribuable au code.

À exploiter côté Electre (top du rapport) :
- `l'{word} {string} de l'organisation {string}` (`glue.utilisateur`) : 26,1 s sur
  372 appels (~70 ms chacun), ~28 % de la suite à lui seul.
- création de panier avec sélection de notices : 5,0 s / 120 appels.
- `l'{word} {string} du groupe ... rattaché à l'organisation ...` : 4,3 s / 61 appels.

Prérequis local découvert : le schéma Postgres doit être migré
(`clojure -M:db-migrator` dans `bo/backend`), `test.sh` ne le fait qu'en CI.

### E1. Migration d'Electre vers 0.1.5 — EN COURS
`diffusion/backend`, `diffusion/import`, `bo/backend`, `bo/account-domain` : la dep git
`io.github.hiram-madelaine/scenari` (SHA `2396ada`, sur une branche non mergée du fork)
devient `io.github.hiram-madelaine/clornichon {:mvn/version "0.1.4"}`.
`:kaocha.plugin/scenari-slowest-steps` ajouté au `tests.edn` de `diffusion/backend`.
Vérifié : classpaths identiques hormis la lib (même `tools.logging`, même `java-time`) ;
`diffusion/backend` `:scenario` 392/392 verts, 92,3 s ; `bo/account-domain` 41 tests verts.
Constat : les scénarios de `bo/account-domain` (`test/scenario/`) ne tournent jamais — ns
mal nommés (`scenario_org` au lieu de `scenario.scenario-org`) et `test-utils` introuvable ;
préexistant, hors migration.

Passage à 0.1.5 (hooks) : `diffusion/backend` `:scenario` 392/392 (96,1 s, dans le bruit),
`bo/account-domain` 41 tests verts.

### 5. Hooks — FAIT
Un hook qui n'a que l'arité 1 reçoit `{:scenario-name :annotations}` (+ `:status` en
`:post-scenario-run`) ou `{:feature :annotations}` pour une feature ; lu dans les
`:arglists` de la var. Métadonnée `^{:scenari/tags "@db and not @slow"}` sur la var :
le hook ne tourne que si les tags matchent, expression parsée au chargement.
Doc : `doc/state-and-hooks.md`. Tests : `hooks-context-test` (`feature_test.clj`).

Piège trouvé sur Electre : `add-perimetres-contractuels` a `[]` et `[perimetres]` ; la
première version lui passait le contexte et le run plantait (hugsql). Règle retenue :
l'arité 0 l'emporte, tout hook existant en a une. Electre `:scenario` : 392/392, 90,8 s.
Non fait (YAGNI) : `:status` au niveau feature, hooks par step, before-all/after-all.

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
