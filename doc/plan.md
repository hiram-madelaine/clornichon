# Clornichon — lot 5 : ce que l'audit a trouvé

## Contexte
0.1.10 publié. Les lots 1 à 4 sont livrés ; leur détail reste dans l'historique
(`git show a702a50:doc/plan.md` pour les lots 1 à 3, `git show 2564ae0:doc/plan.md` pour
le lot 4). Quatre items des lots précédents restent ouverts : ils gardent leur ligne au
tableau de bord.

Ce lot traite l'audit du 2026-09-27 sur les évolutions 0.1.1 → 0.1.10 : chaque constat a
été reproduit par une exécution réelle (projet jetable sous Kaocha, scripts), pas
seulement lu dans le code. Le cas nominal est sain — 80 tests, 268 assertions, CI verte,
niveau `Rule`, cucumber-messages et `define-parameter-type!` vérifiés. Ce qui pèche, c'est
ce qui se passe quand un hook lève, et quelques promesses de la doc que le code ne tient
pas.

Référence perf (Electre, `diffusion/backend`, suite `:scenario`, 392 tests) : ~91 s,
bruit d'environnement ±6 %. Mesures synthétiques de l'audit : `run-hooks` coûte 0,27 µs
par scénario sans hook déclaré, un balayage `global-hooks` 1 à 3 ms, une fois par run.

## Gestion d'état

États : `TODO` · `EN COURS` · `BLOQUÉ` · `FAIT` · `ABANDONNÉ`

Règles :
- Un seul item `EN COURS` à la fois.
- Passage à `FAIT` seulement quand : `./test.sh` vert, entrée CHANGELOG `[Unreleased]`,
  case cochée dans `doc/roadmap.md` si l'item y figure, commit fait.
- `BLOQUÉ` / `ABANDONNÉ` : une ligne de raison dans la colonne Notes.
- Ce fichier est mis à jour à chaque changement d'état, dans le même commit que le
  travail concerné. Six PR pour le lot, regroupées comme ci-dessous : une par item
  aurait fait dix PR, dont plusieurs de quelques lignes.
- Les numéros de ligne cités datent de `2564ae0` : se fier au nom de la fonction s'ils
  ont bougé.

## Tableau de bord

| #  | Item                                                        | Lot | Gravité   | État | Notes |
|----|-------------------------------------------------------------|-----|-----------|------|-------|
| E1 | Electre passe de scenari `2396ada` à clornichon             | 2   | —         | EN COURS | suivi par un agent dédié au repo Electre (MR, CI, bumps) |
| 8  | Namespace `clornichon.*` (alias)                            | 3   | —         | TODO | décision à prendre ; reco : garder `scenari.*`, l'expliquer dans le README |
| 9  | Hooks globaux + before-all / after-all                      | 4   | —         | EN COURS | livré en 0.1.10 ; reste la mesure Electre (synthétique : coût nul) |
| 11 | Niveau `Rule` dans le rapport et l'arbre kaocha             | 4   | —         | EN COURS | livré en 0.1.9 ; reste la mesure Electre (coût attendu nul) |
| 12 | Un hook qui lève fait échouer son scénario, pas le run      | 5   | critique  | FAIT | PR 1 ; NDJSON : pas d'enveloppe `hook`, voir le détail |
| 13 | `release.sh` prépare la doc avant de taguer                 | 5   | important | FAIT | PR 2 ; garde-fou, le « Prepare » reste à la main |
| 14 | `-load` charge aussi les `test-paths`                       | 5   | important | FAIT | PR 2 |
| R9 | Release 0.1.11 (12 à 23, tout le lot 5)                     | 5   | —         | FAIT | publiée sur Clojars, tag `v0.1.11` |
| 15 | Ordre du fichier dans la doc HTML, fin de `Rule` en console | 5   | important | FAIT | PR 4 |
| 16 | Retirer `deffeature` sur un répertoire, et commons-io       | 5   | modéré    | FAIT | PR 5 |
| 17 | Type hints sur le chemin de matching                        | 5   | modéré    | FAIT | PR 5 ; banc : 1 156 ms → 94 ms ; mesure Electre à l'agent dédié |
| 18 | Hooks globaux ignorés sans rien dire                        | 5   | modéré    | FAIT | PR 6 ; un hook privé tourne |
| 19 | Code mort, features d'exemple hors du jar                   | 5   | ménage    | FAIT | PR 6 ; 4 features supprimées, 2 sous `test/features/` |
| 20 | `doc/development-workflow.md` à jour                        | 5   | mineur    | FAIT | PR 6 |
| 21 | `--fail-fast` fait tomber le run au premier step en échec   | 5   | important | FAIT | PR 3 ; traité avant R9, part donc en 0.1.11 |
| 22 | Un `is` qui échoue dans un hook laisse le run vert          | 5   | modéré    | FAIT | PR 6 ; trouvé en traitant 21 ; dans `call-hook`, `:error` compris |
| 23 | Un `is` dont la forme lève dans un step laisse le run vert  | 5   | modéré    | FAIT | PR 7 ; trouvé en traitant 22 |
| R10 | Release 0.1.12 (15 à 20, 22, 23)                           | 5   | —         | ABANDONNÉ | tout était fusionné avant R9 : parti en 0.1.11 |
| 24 | Deux phrases pour un même nom de var : `defglue` lève       | 6   | important | FAIT | branche `fix/glue-name-collision` ; Electre : aucune collision, chargement identique |
| 25 | Kaocha n'est plus une dépendance                            | 6   | important | FAIT | branche `feat/kaocha-optional`, partie de celle de 24 ; rupture notée au CHANGELOG |
| 26 | Une seule boucle d'exécution                                | 6   | modéré    | FAIT | branche `refactor/single-execution-loop`, partie de celle de 25 ; scénarios d'Electre non joués |
| 27 | Page « Known limits »                                       | 6   | mineur    | FAIT | branche `docs/known-limits`, partie de celle de 26 ; doc seule |
| R11 | Release 0.2.0 (24 à 27, tout le lot 6)                     | 6   | —         | FAIT | publiée sur Clojars, tag `v0.2.0` ; build cljdoc en échec, voir 28 |
| 28 | cljdoc ne charge pas les `kaocha.*` sans Kaocha au pom       | 6   | important | FAIT | branche `fix/cljdoc-kaocha-optional` ; Kaocha optionnel au pom |
| R12 | Release 0.2.1 (28)                                          | 6   | —         | À FAIRE | cljdoc de 0.2.0 ne se rattrape pas, sauf exception dans `cljdoc-analyzer` |

Ordre proposé : 12 (le seul critique), 13 (pour que R9 ne refasse pas l'erreur de
0.1.10), 14, 21, R9 ; puis 15 à 20 et 22 dans l'ordre, R10. Les items 16 à 22 sont
indépendants les uns des autres et peuvent se prendre dans n'importe quel ordre.

PR du lot :

| PR | Items      | Release | Pourquoi ensemble |
|----|------------|---------|-------------------|
| 1  | 12         | 0.1.11  | critique, seul |
| 2  | 13, 14     | 0.1.11  | petits, même release |
| 3  | 21         | 0.1.11  | seul ; traité avant R9 |
| 4  | 15         | 0.1.12  | ordre d'affichage, relecture à part |
| 5  | 16, 17     | 0.1.12  | changements de code modérés, indépendants |
| 6  | 18, 19, 20, 22 | 0.1.12 | avertissement, ménage, doc ; 22 va avec 18, deux hooks qui se taisent |
| 7  | 23         | 0.1.12  | trouvé en traitant 22, seul |

Les sept PR étaient fusionnées avant R9 : tout part en 0.1.11, voir R9. La colonne
« Release » garde ce qui était prévu.

Un item passe à `FAIT` dans le commit de sa PR ; une PR de plusieurs items porte un
commit par item.

À décider en ouvrant le lot : 9 et 11 sont livrés et n'attendent qu'une mesure faite
ailleurs — les passer en `BLOQUÉ` (raison : mesure Electre, agent dédié) pour libérer
le seul créneau `EN COURS`.

## Reproduire

Le projet jetable de l'audit n'est pas versionné. Pour le refaire, hors du repo :

```clojure
;; deps.edn
{:paths ["src"]
 :deps {io.github.hiram-madelaine/clornichon {:local/root "<chemin du repo>"}}
 :aliases {:test {:extra-paths ["test"]
                  :extra-deps {lambdaisland/kaocha-junit-xml {:mvn/version "1.17.101"}}}}}
```

- `tests.edn` : celui de `doc/running.md`, plus `:kaocha.plugin/profiling` et
  `:kaocha.plugin/junit-xml` (`target-file "target/junit.xml"`).
- `test/features/cart.feature` : deux scénarios libres, puis une `Rule` avec un
  `Background`, un scénario tagué et un `Scenario Outline` de deux lignes.
- `test/scenario/glue/cart.clj` : les trois glues du README.
- `test/scenario/cart_test.clj` : le `deffeature`, avec un `:post-scenario-run` local.
- `test/scenario/glue/hooks.clj` : un hook global par clé, qui lève selon une variable
  d'environnement :

```clojure
(def mode (System/getenv "HOOK_MODE"))
(defn- boom! [where ctx]
  (when (and (= mode where)
             (or (nil? (:scenario-name ctx)) (= "second one" (:scenario-name ctx))))
    (throw (ex-info (str "boom in " where) {}))))
(defn ^{:scenari/hook :before-scenario} g-before [ctx] (println "HOOK before" ctx) (boom! "before-scenario" ctx))
```

```bash
HOOK_MODE=before-scenario clojure -M:test -m kaocha.runner \
  --no-color --no-capture-output --no-randomize
```

`--no-capture-output` : sans lui Kaocha avale la sortie des hooks d'un run vert.

## Détail des items

### 12. Un hook qui lève fait échouer son scénario, pas le run — critique

Constat, sous Kaocha :
- un `:before-scenario` (ou `:pre-scenario-run`) qui lève sur le scénario 2 sur 5 : les
  3 suivants ne tournent pas, code de sortie 1, ni résumé, ni `junit.xml`, ni NDJSON ;
- un `:after-all` qui lève : les 5 scénarios passent, leurs résultats sont perdus ;
- un `:post-scenario-run` local qui lève : les `:after-scenario` globaux ne tournent
  pas — le nettoyage est sauté ;
- un pre-hook et un post-hook qui lèvent tous les deux : seule l'exception du post-hook
  remonte, la cause est perdue.

Le comportement vient de scenari 2.0.2, mais les hooks globaux l'aggravent : un
`clean-db!` qui échoue une fois emporte toute la suite, et la CI n'a rien à montrer.

Où :
- `src/kaocha/type/scenari.clj:177`, `-run :kaocha.type/scenari-scenario` : appelle
  `sc/run-scenario` sans rien attraper ; idem `:152` (feature) et `:144` (suite) ;
- `src/scenari/v2/core.clj:341`, `around` : `(finally (run! ... post-run))` s'arrête au
  premier post-hook qui lève, et son exception remplace celle du `try`.

Comportement visé, celui de Cucumber :
- hook de scénario qui lève : le scénario est `:fail`, ses steps restent `:pending`,
  les after-hooks tournent, le scénario suivant est lancé ;
- hook de feature qui lève : les scénarios de la feature sont en échec, la feature
  suivante est lancée ;
- `:before-all` qui lève : inchangé, le run s'arrête (c'est documenté, et c'est ce que
  fait un `use-fixtures :once`) ;
- `:after-all` qui lève : l'erreur est rapportée et le code de sortie non nul, mais les
  résultats du run sont gardés — résumé, `junit.xml`, NDJSON écrits ;
- tous les after-hooks tournent, quoi que fassent les autres ; la première exception
  remonte, les suivantes en `addSuppressed`.

Correctif :
- `around` : un `try` par post-hook, les exceptions collectées, la première relancée
  (celle du `try` principal d'abord, si elle existe) ;
- `-run :kaocha.type/scenari-scenario` : `try` autour de `sc/run-scenario`, un
  `t/do-report {:type :fail ...}` comme pour un step qui lève (`scenari.clj:190`), et le
  testable rendu avec `:kaocha.result/fail 1` ;
- même chose un cran au-dessus pour la feature ; pour la suite, n'attraper que ce qui
  vient des `:after-all`.

Tests (`test/scenari/v2/global_hooks_test.clj`) : un before-scenario qui lève laisse
tourner le scénario suivant ; un after-hook qui lève n'empêche pas le suivant ; deux
exceptions, la première remonte et porte la seconde ; un after-all qui lève garde les
résultats. Vérifier à la main sur le projet jetable que `junit.xml` est écrit.
Doc : `doc/state-and-hooks.md`, la phrase « The `:post-*` hooks run even when... » à
compléter. CHANGELOG : `Fixed`.

Fait, et ce qui diffère du correctif prévu :
- `around` rend `[r e]` au lieu de lever ; `try-hooks` et `try-suite` l'exposent,
  `run-hooks` et `run-suite` gardent leur contrat (ils lèvent). Ce que `f` lève n'est pas
  l'échec d'un hook et remonte tel quel.
- L'échec d'un hook de scénario est attrapé dans `core/run-scenario`, pas dans le `-run`
  kaocha : les trois runners en profitent. Le scénario porte l'exception sous
  `:exception`.
- `:after-feature` et `:after-all` : kaocha tire le total d'un groupe de ses enfants,
  l'échec du groupe lui-même ne compte pas. L'échec est porté par une feuille
  `after-feature` ou `after-all` (type `:kaocha.type/scenari-hook`) ajoutée aux
  résultats — d'où 6 tests au résumé pour 5 scénarios. `scenari-doc/kept` l'écarte des
  rapports.
- Runner `clojure.test` : un hook de scénario qui lève est rapporté (`:hook-failed`) et
  le scénario suivant tourne ; un hook de feature qui lève reste l'erreur du `deftest`.
- Vérifié sur le projet jetable, un hook par clé : résumé, `junit.xml` et NDJSON écrits,
  code de sortie non nul ; `--fail-fast` s'arrête proprement sur un hook qui lève.

Reste, hors de cet item : le NDJSON n'a pas d'enveloppe `hook`. Un scénario qu'un hook
fait échouer y a tous ses steps `SKIPPED` ou `PASSED`, et l'échec n'est porté que par
`testRunFinished.success: false`. À ajouter si un consommateur du flux en a besoin.

### 13. `release.sh` prépare la doc avant de taguer — important

Constat : le tag `v0.1.10` pointe sur `114ca76`, où le README installe `0.1.9` et le
CHANGELOG n'a pas de section `[0.1.10]` — le commit « Prepare 0.1.10 » est venu après.
Le pom publié porte `<tag>v0.1.10</tag>`, et cljdoc construit sa doc depuis ce tag. De
0.1.3 à 0.1.9 l'ordre était le bon (« Prepare » puis « Bump »).

```bash
git show v0.1.10:README.md | grep mvn/version      # 0.1.9
git show v0.1.10:CHANGELOG.md | grep -m2 '^# \['   # [Unreleased], [0.1.9]
```

Correctif, au choix — le premier est le plus court :
- `release.sh` refuse de taguer si `CHANGELOG.md` n'a pas de section datée pour la
  version à venir ou si le README ne la mentionne pas ;
- ou `release.sh` fait lui-même le « Prepare » (dater `[Unreleased]`, remplacer la
  version dans `README.md` et `doc/getting-started.md`) et le commite avant metav.

Rien à rattraper pour 0.1.10 : un tag publié ne se déplace pas. À vérifier au passage
sur cljdoc, l'audit ne l'a pas fait.
Tests : aucun test automatisé ; un essai à blanc du garde-fou sur une branche.

Fait, par le garde-fou : `release.sh` calcule la version à venir depuis le tag le plus
proche et refuse de taguer sans section datée au CHANGELOG, sans cette version dans
`README.md` et `doc/getting-started.md`, ou avec des changements non commités. Il
vérifie avant de demander les identifiants : le lancer sans eux fait l'essai à blanc.
Après metav, il compare le tag à la version vérifiée et s'arrête avant de publier s'ils
diffèrent. Pas d'entrée CHANGELOG : rien ne change pour qui utilise la lib.

Essayé dans un clone jetable, dépôt distant factice, `build.sh` et `deploy.sh`
remplacés : sans argument, rien de préparé, préparé sans commit, préparé sans
identifiants, `minor` non préparé — code de sortie 1 et aucun tag à chaque fois ; puis
de bout en bout, metav tague bien `v0.1.11`.

cljdoc, vérifié le 2026-09-27 : seules 0.1.2 et 0.1.3 y sont construites, rien de 0.1.4
à 0.1.10. Ne pas demander le build de 0.1.10, son tag porte le README de 0.1.9 ;
demander celui de 0.1.11 après R9.

### 14. `-load` charge aussi les `test-paths` — important

Constat : avec le `tests.edn` de `doc/running.md:51` (`test-paths ["test/scenario"]`,
`glue-paths ["test/scenario/glue"]`), Kaocha sort sur
`No namespace: scenario.cart-test found`. C'est le premier fichier qu'un nouveau venu
recopie ; l'exemple vient du README de scenari.

Où : `src/kaocha/type/scenari.clj:119`, `-load` ne requiert que les `glue-paths`, puis
`find-features-meta-in-dir` (`:22`) appelle `ns-publics` sur les namespaces des
`test-paths`, pas encore chargés. Le `tests.edn` du repo passe parce que ses
`glue-paths` couvrent ses `test-paths`.

Correctif : `(require-all-ns (concat (::glue-paths testable) (:kaocha/test-paths testable)))`,
les glues d'abord — un glue doit être chargé avant le `deffeature` qui s'en sert.
Tests : un `testable/load` dont les `glue-paths` ne couvrent pas les `test-paths`.
CHANGELOG : `Fixed`.

Fait. Le test charge `test/fixtures/lonely`, qu'aucune suite ne charge ; il échoue aussi
si l'ordre est inversé, la feature ne requérant pas son glue. Vérifié sur le projet
jetable avec le `tests.edn` de la doc.

### R9. Release 0.1.11
À décider : `master` porte déjà 15, et portera 16 et 17 une fois la PR 5 fusionnée. Une
release faite maintenant les embarque, quoi que dise la colonne « Release » du tableau
des PR. Le plus simple est une seule release avec tout ce qui est fusionné, et une
0.1.12 pour le reste.

Après 12, 13, 14 et 21. S'arrêter à la publication Clojars et à la mise à jour de ce plan :
le bump côté Electre revient à l'agent dédié.

Dans l'ordre : commit « Prepare 0.1.11 » sur `master`, `./release.sh patch` sans
identifiants pour l'essai à blanc, puis avec. Ensuite demander le build cljdoc de 0.1.11
et vérifier que son README installe 0.1.11.

Décidé comme recommandé : une seule release. `master` portait les items 12 à 23 quand
R9 a été prise, et `release.sh` publie `HEAD`. R10 n'a plus rien à publier.
Préparée le 2026-09-27 : « Prepare 0.1.11 » sur `master`, non poussé — `release.sh` le
pousse avec le tag, une fois l'artefact accepté par Clojars. Essai à blanc :
`Docs are ready for 0.1.11`, puis arrêt sur `CLOJARS_USERNAME is not set`.

Publiée le 2026-09-27. Vérifié après coup :
- le tag `v0.1.11` est sur GitHub, sur le commit qui suit le « Prepare » ;
- le jar de Clojars ne contient que `scenari/`, `kaocha/` et `META-INF/`, et son pom
  pointe sur `v0.1.11` ;
- cljdoc, build 115048 : 13 namespaces, sans erreur ; son README installe 0.1.11.

### 15. Ordre du fichier dans la doc HTML, fin de `Rule` en console — important

Constat : le plugin `randomize` de Kaocha, actif par défaut, mélange le test-plan avant
que les plugins scenari le lisent.
- `--doc-html` et `--doc-report` : features et scénarios sortent dans l'ordre de la
  graine. La documentation statique change à chaque run.
- Console : rien ne marque la fin d'une `Rule`. Un scénario libre tiré après elle
  s'imprime sous son titre et se lit comme en faisant partie — 4 graines sur 6 essayées.

Où : `src/kaocha/plugin/scenari_doc.clj:44`, `selected-features` ;
`src/kaocha/type/scenari.clj:170`, `-run :kaocha.type/scenari-rule`.

Correctif :
- poser sur chaque scénario son rang dans la feature (`map-indexed` dans `-load`), et
  trier dans le rendu HTML : features par id, scénarios par rang. `selected-features`
  sert aussi à dry-run, slowest-steps et messages, qui se moquent de l'ordre : trier là
  ne casse rien ;
- émettre `{:type :end-rule}` à la fin de `-run :kaocha.type/scenari-rule`, et un
  `defmethod t/report :end-rule` dans `scenari.v2.test` qui ferme la règle d'une ligne.

Tests : le document est le même pour deux graines ; le rapport d'une règle suivie d'un
scénario libre. CHANGELOG : `Fixed`.

Fait, et ce qui diffère du correctif prévu :
- le tri est dans `scenari-doc/document`, pas dans `selected-features`. Le plan disait
  que les autres lecteurs se moquent de l'ordre : c'est faux pour le flux
  cucumber-messages, qui doit rester dans l'ordre où les choses se sont passées ;
- les features sont triées par namespace puis par ligne, pas par id : dans un même
  namespace c'est l'ordre du fichier, comme pour les hooks globaux ;
- le runner `clojure.test` émet `:end-rule` lui aussi.

Vérifié sur le projet jetable, six graines : un seul document `--doc-html`, un seul
`--doc-report` ; en console la règle est fermée avant le scénario libre qui la suit.

### 16. Retirer `deffeature` sur un répertoire, et commons-io — modéré

Constat : `doc/getting-started.md:61` annonce qu'un `deffeature` accepte « a directory
of features ». L'appel lève une `ClassCastException` (`File` passé à `io/resource`), et
n'a jamais marché : dans scenari 2.0.2 le `doseq` rendait `nil`.

Où : `src/scenari/v2/core.clj:76`, `read-source :dir` ; `:49`, `get-feature-files`.

Correctif, par suppression :
- supprimer `read-source :dir` et la mention dans la doc ;
- `get-feature-files` ne sert plus qu'à `test/scenari/v2/corpus_test.clj` : l'y
  déplacer, réécrit avec `file-seq` ;
- retirer `commons-io` de `deps.edn`. La 2.6 est visée, de mémoire, par
  CVE-2021-29425 et CVE-2024-47554 — à confirmer avec un scanner, mais la retirer règle
  la question sans avoir à la trancher.

CHANGELOG : `Changed`, avec la même note que pour `tools.logging` en 0.1.3 — un projet
qui utilisait commons-io à travers Clornichon doit le déclarer.

Fait, et ce qui diffère du correctif prévu :
- `read-source :dir` n'est pas supprimé mais lève une erreur qui dit quoi faire : sans
  lui un répertoire tombait sur `slurp`, `FileNotFoundException (Is a directory)` ;
- trouvé en route : un `java.io.File` levait la même `ClassCastException`, pour un
  fichier comme pour un répertoire — `io/resource` ne prend qu'une chaîne. Corrigé dans
  `file-from-fs-or-classpath`, CHANGELOG `Fixed` ;
- les deux CVE sont confirmées par la base OSV : CVE-2021-29425, corrigée en 2.7, et
  CVE-2024-47554, corrigée en 2.14.0.

Vérifié : commons-io n'est plus dans `clojure -Stree` ni dans le pom du jar construit ;
le test de corpus trouve les mêmes fichiers avant et après.

### 17. Type hints sur le chemin de matching — modéré

Constat : `*warn-on-reflection*` sort 92 avertissements dans `core.clj` et 15 dans
`glue.clj`. Un seul est sur un chemin chaud : `src/scenari/v2/glue.clj:136`, `match`,
appelé pour chaque couple (step, glue) au chargement. Banc synthétique, 3 400 steps
contre 440 glues : 765 ms tel que livré, 105 ms avec un hint.

Correctif : `^Expression` sur l'expression dans `match` (et l'import). Les autres
avertissements sont payés une fois par step ou par feature : ne les traiter que si une
mesure le justifie.
Tests : ceux de `glue_test.clj` suffisent. Mesurer avant et après, sur le banc et sur
Electre si l'agent dédié peut. CHANGELOG : `Changed`.

Fait. Le hint est sur un local : posé sur la forme `(or ...)`, le compilateur l'ignore,
l'avertissement reste et la mesure ne bouge pas. `glue.clj` passe de 15 à 13
avertissements, les deux de `match`.

Banc synthétique, médiane de 5 passes après 3 de chauffe, même machine :

| Mesure                                      | Avant    | Après  |
|---------------------------------------------|----------|--------|
| 3 400 steps contre 440 glues                | 1 156 ms | 94 ms  |
| `->feature-ast`, 500 scénarios de 7 steps   | 1 248 ms | 125 ms |

L'audit donnait 765 ms avant : autre méthode, une seule passe. Reste la mesure sur
Electre, qui revient à l'agent dédié ; le gain y porte sur le chargement, pas sur les
~91 s du run.

### 18. Hooks globaux ignorés sans rien dire — modéré

Constat : deux hooks qui ne tournent jamais, sans message — ce que `global-hooks` évite
déjà pour une clé mal orthographiée.
- `^{:scenari/hook :before-all :scenari/tags "@db"}` : le contexte de suite n'a pas de
  tags, l'expression est toujours fausse (`core.clj:373`, `run-suite`).
- un hook `defn-` : le balayage passe par `ns-publics` (`core.clj:316`).

Correctif :
- `global-hooks` lève, en nommant le hook, si `:scenari/tags` est posé sur un
  `:before-all` ou un `:after-all` ;
- `ns-interns` à la place de `ns-publics`. À décider : faire tourner un hook privé, ou
  lever. Reco : le faire tourner — un hook n'a pas à être public.

Tests : les deux cas, dans `global_hooks_test.clj`. Doc : `doc/state-and-hooks.md`.
CHANGELOG : `Fixed`.

Fait : un hook privé tourne, comme recommandé. Les deux tests échouent sans le
correctif.

### 19. Code mort, features d'exemple hors du jar — ménage

À supprimer :
- `src/scenari/utils.clj:5-74` et `:94-110` : `contextual-eval`, `local-context`,
  `readr`, `break-with-repl`, `get-whole-in`, `get-in-tree`, `digits-only?`,
  `number-value-of` — aucun appelant, environ 85 lignes de l'époque instaparse ;
- `src/kaocha/type/scenari.clj` : `-run :kaocha.type/scenari-step` (`:200`, jamais
  atteint, arguments inversés) et son `s/def` ; le premier `s/def :kaocha.type/scenari`
  (`:15`, redéfini en `:209`) ; les quatre `derive!` (`:218-222`) sur des clés jamais
  émises ; les `require` en double (`v2`/`sc`, `string`/`str`) ;
- `test.sh` : `-A:test -m` émet un avertissement de dépréciation, `-M:test` le règle.

À déplacer : le jar embarque six features d'exemple à la racine du classpath
(`atm.feature`, `calculator.feature`, `product-catalog.feature`, `remember-me.feature`,
`scenari.feature`, `utilisateurs.story`), parce que `resources/` est dans `:paths` et
copié par `script/build.clj`. Un `(deffeature x "calculator.feature")` côté utilisateur
peut résoudre celle de la bibliothèque. Les passer sous `test/`, et `grep` leurs usages
dans `test/` et `doc/` avant de les bouger.

Vu en traitant 16 : quatre des six ne se parsent plus (`product-catalog`, `remember-me`,
`scenari`, `utilisateurs.story`), elles sont dans la grammaire d'avant gherkin. Celles-là
sont à supprimer plutôt qu'à déplacer, si rien ne les lit.

Vérifier : `unzip -l target/clornichon-*.jar` ne liste plus que `scenari/`, `kaocha/`
et `META-INF/`. CHANGELOG : `Changed` pour le contenu du jar, rien pour le code mort.

Fait. Rien ne lisait les six features, ni dans `test/` ni dans `doc/` :
- les quatre qui ne se parsent plus sont supprimées ;
- `atm.feature` et `calculator.feature` passent sous `test/features/`, et
  `corpus_test.clj` vérifie qu'elles se parsent — c'est ce qui a manqué aux quatre
  autres ;
- `resources/` n'existe plus : retiré des `:paths` de `deps.edn` et de
  `script/build.clj`.

Vérifié : le jar ne contient plus que `scenari/`, `kaocha/` et `META-INF/`. Il a été
construit sans l'étape d'installation : `./build.sh` installe aussi le jar dans
`~/.m2`, sous la version courante, et y remplace donc l'artefact de Clojars.
`./test.sh` n'émet plus l'avertissement de `-A`. Le `DEPRECATED: Libs must be
qualified ... enlive` qui reste vient de `~/.clojure/deps.edn`, pas du repo.
Electre n'appelle rien de `scenari.utils`.

Revu après la fusion de la PR 6 : le code mort de `scenari.utils` a son entrée
`Changed`, dans une PR à part. Les huit vars retirées étaient publiques, un projet
tiers pouvait les appeler.

### 20. `doc/development-workflow.md` à jour — mineur

- « must return the (possibly modified) state » contredit la règle nil/booléen de 0.1.1 ;
  les exemples gardent un `state` final devenu inutile ;
- `clojure -M:test` ne lance pas les tests ;
- les hooks globaux n'y figurent pas : renvoyer à `doc/state-and-hooks.md#global-hooks` ;
- la procédure de release n'est décrite nulle part ailleurs que dans l'en-tête de
  `release.sh` : « Prepare », essai à blanc, release.

Fait. La release a sa section, « Releasing Clornichon » : les deux sorties de l'essai
à blanc y sont celles du script, rejoué sans identifiants, docs prêtes puis non.
Corrigé en plus : « The state passed between steps can be examined in the test
output » — rien n'affiche l'état ; la phrase renvoie à `run-feature` comme donnée.
Pas d'entrée au CHANGELOG : la doc seule change.

### 21. `--fail-fast` fait tomber le run au premier step en échec — important

Constat, trouvé en traitant l'item 12, reproduit sur 0.1.10 : avec `--fail-fast`, un
step qui échoue sur un `is` sort sur `Execution error (ExceptionInfo)
{:kaocha/fail-fast true ...}`, sans résumé ni NDJSON. C'est l'option qu'on passe en CI
pour gagner du temps.

```bash
clojure -M:test -m kaocha.runner --fail-fast    # avec un scénario rouge
```

Où : le reporter `kaocha.report/fail-fast` lève depuis le `is` du step ; `run-step`
l'attrape comme l'exception du step ; `-run :kaocha.type/scenari-scenario` la rapporte
alors en `:fail` « Step threw », et le reporter lève une seconde fois, hors de tout
`catch`. `kaocha.type.var` s'en sort en avalant cette exception : `run-testables`
s'arrête de lui-même sur un résultat en échec.

Correctif : dans le `-run` du scénario, ne pas rapporter un step dont l'exception porte
`:kaocha/fail-fast`, et passer `:kaocha.result/exception` sur le `:fail` d'un step qui
lève, comme `report-hook-failure` le fait déjà pour un hook.
Tests : `feature_test.clj`, un scénario rouge sous `testable/*fail-fast?*` lié à `true`
rend un résultat au lieu de lever. CHANGELOG : `Fixed`.

Fait, et ce qui diffère du correctif prévu : le marqueur est écarté dans
`core/run-step`, pas dans le `-run` kaocha. Le step est `:fail` sans `:exception`, comme
pour un `is` qui échoue sans `--fail-fast` : la console, `--doc-report` et le NDJSON
n'affichent plus le marqueur. Le `:fail` d'un step qui lève porte
`:kaocha.result/exception`.
Vérifié sur le projet jetable : `--fail-fast` s'arrête au premier scénario rouge, sur un
`is` comme sur une exception, avec résumé, `junit.xml` et NDJSON ; sans l'option, rien
ne change.

### 22. Un `is` qui échoue dans un hook laisse le run vert — modéré

Constat, trouvé en traitant l'item 21 : un `:before-scenario` qui fait
`(is (= 1 2))`, sans `--fail-fast`. Kaocha affiche `FAIL in ...`, mais le résumé dit
`0 failures`, le code de sortie est 0 et `junit.xml` porte `failures="0"`. Les steps
tournent quand même. Sous `--fail-fast` le marqueur de kaocha fait lever le hook, et le
scénario échoue : ce cas-là est juste.

Où : `-run :kaocha.type/scenari-scenario` tire ses compteurs du `:status` des steps ;
`core/around` ne connaît d'un hook que ce qu'il lève.

Correctif : dans `around`, lier `*report-counters*` autour de chaque hook, comme
`run-step` le fait autour d'un glue, et faire d'un `:fail` compté l'échec du hook — une
`ex-info` qui nomme le hook, notée comme ce qu'il aurait levé.
Tests : `global_hooks_test.clj`, un hook qui échoue sur un `is` fait échouer son
scénario dans les trois runners. CHANGELOG : `Fixed`.

Fait, et ce qui diffère du correctif prévu : le compteur est lié dans `call-hook`, pas
dans `around` — c'est là que passent tous les hooks, ceux de la suite compris, et un
seul endroit au lieu des deux appels de `around`. Le compteur `:error` est lu avec
`:fail` : un `is` dont la forme lève est attrapé par clojure.test, qui le compte en
`:error`.
Vérifié sur le projet jetable, avec un `(is (= 1 2))` sur le deuxième scénario de
trois :

| Hook                | Avant                       | Après                        |
|---------------------|-----------------------------|------------------------------|
| `:before-scenario`  | `0 failures`, code 0        | `1 failures`, code 1, `junit.xml` `failures="1"`, steps `:pending` |
| `:after-scenario`   | `0 failures`, code 0        | `1 failures`, code 1         |
| sous `--fail-fast`  | arrêt au scénario, code 1   | inchangé                     |

Kaocha affiche deux `FAIL in` pour le scénario : l'assertion, puis `Hook threw`.

### 23. Un `is` dont la forme lève dans un step laisse le run vert — modéré

Constat, trouvé en traitant l'item 22, reproduit sur le projet jetable : un step qui
fait `(is (= 1 (throw (ex-info "raised in is" {}))))`. clojure.test attrape
l'exception et la rapporte en `:error`. Kaocha affiche `ERROR in ...`, mais le résumé
dit `0 failures`, le code de sortie est 0 et `junit.xml` porte `errors="0"`.

Où : `core/run-step` ne lit que `:fail` dans `*report-counters*`.

Correctif : lire `:error` avec `:fail`, comme `call-hook` depuis l'item 22.
Tests : `feature_test.clj`. CHANGELOG : `Fixed`.

Fait. `run-step` et `call-hook` lisent le compteur par la même fonction,
`assertion-failed?` : la règle est écrite une fois. Le step est `:fail` sans
`:exception`, comme pour un `is` faux : il n'a rien levé.
Vérifié sur le projet jetable, le `is` qui lève dans le dernier step du troisième
scénario :

| Run                 | Avant                                   | Après                                    |
|---------------------|-----------------------------------------|------------------------------------------|
| sans option         | `0 failures`, code 0, `failures="0"`    | `1 failures`, code 1, `failures="1"`     |
| sous `--fail-fast`  | `1 failures`, code 1                    | inchangé                                 |

Sous `--fail-fast` le cas était déjà juste : le marqueur de kaocha fait lever le step.

Kaocha compte un échec, pas une erreur : le résumé dit `1 failures` et `junit.xml`
porte `failures="1" errors="0"`, avec un élément `<error>` dans le cas de test. C'est
le compte d'un step qui lève.

### R10. Release 0.1.12
Après 15 à 20, 22 et 23. Peut se scinder si un item traîne.

Abandonnée : ses items sont partis en 0.1.11, voir R9.

### 24. Deux phrases pour un même nom de var : `defglue` lève — important

Lot 6, ouvert le 2026-09-28 après la comparaison avec kaocha-cucumber et Burpless. Seul
cet item est décidé : les autres propositions du lot n'ont pas encore de ligne ici.

Constat, reproduit par un script : dans un même namespace, `(defgiven "I have a-b" ...)`
puis `(defgiven "I have a b" ...)` ne laissent qu'un glue, celui de la seconde phrase.
La première n'a plus de définition, ses steps sortent en `Missing step`, et rien ne le
dit au chargement.

Où : `src/scenari/v2/core.clj`, `re->symbol` tire le nom du var de la phrase — les
espaces et les `/` deviennent `-`, `\"(.*)\"` devient `param` — et `defglue` fait un
`defn` de ce nom.

Correctif : `check-glue-name!`, appelé par `defglue` avant son `defn`, lève si le nom
est déjà celui d'un glue du namespace dont la phrase diffère, en citant les deux. Les
phrases sont comparées par leur texte : une regex et une chaîne de même texte sont la
même phrase. La même phrase repasse, c'est un rechargement. La vérification est faite à
l'exécution et non à l'expansion de la macro : elle voit aussi deux glues écrits dans
une même forme.

Limite : au REPL, une phrase reformulée en une autre qui fait le même nom lève aussi, le
var de la première étant toujours là. Le message donne le `ns-unmap` à faire.

Tests : `glue_test.clj`, `glue-name-collision-test`. Doc : `doc/step-expressions.md`,
« Two sentences, one name ». CHANGELOG : `Fixed`.

Fait. Le test échoue sans le correctif, sur deux de ses quatre assertions. Un fichier de
glues qui porte les deux phrases ne se charge plus, et l'erreur donne la ligne de la
seconde :

```
Syntax error compiling at (w3/glue.clj:6:1).
glue w3.glue/I-have-a-b : "I have a b" and "I have a-b" make the same var name, the
second definition would replace the first. Reword one; if the first is no longer in
the source, (ns-unmap 'w3.glue 'I-have-a-b)
```

Vérifié sur Electre le 2026-09-28, checkout `refonte` à `584b0800e4`, sans rien y
modifier :
- balayage du texte des sources, le nom calculé par le vrai `re->symbol` : 36 fichiers,
  486 définitions en comptant celles que `#_` commente, aucun nom partagé par deux
  phrases ;
- chargement des namespaces de `test/scenario` comme le fait `-load`, sans Kaocha ni
  hooks, avec la 0.1.11 puis avec la branche :

| Module              | Namespaces | Glues | Features | Scénarios | Steps | Sans glue | 0.1.11 et branche |
|---------------------|------------|-------|----------|-----------|-------|-----------|-------------------|
| `diffusion/backend` | 72         | 406   | 190      | 397       | 2 832 | 0         | identiques        |
| `bo/backend`        | 8          | 6     | 0        | 0         | 0     | 0         | identiques        |

- `bo/account-domain` ne se charge ni avec l'une ni avec l'autre : `test-utils` n'est pas
  sur son classpath de test. Ses trois fichiers de glues sont dans le balayage ;
- corpus : les 222 features de `diffusion/domain/resources/scenarios` se parsent.

Les scénarios d'Electre n'ont pas été joués : l'item ne touche que la définition d'un
glue, pas son exécution.

### 25. Kaocha n'est plus une dépendance — important

Constat, reproduit : un projet qui ne lance ses features qu'avec `clojure.test`
télécharge Kaocha et ce qu'il tire, 32 jars au lieu de 12. Et le lanceur `clojure.test`
charge deux namespaces de Kaocha, `kaocha.output` et `kaocha.jit`, pour une seule
question : faut-il colorer le rapport.

Où : `deps.edn`, `lambdaisland/kaocha` dans `:deps` ; `src/scenari/utils.clj`, le
`require` de `kaocha.output`, que `scenari.v2.test` charge à son tour.

Correctif :
- `scenari.utils` cherche `kaocha.output/*colored-output*` par `resolve` au lieu de le
  requérir. Kaocha chargé, son `--color` / `--no-color` est suivi comme avant ; sans lui
  le rapport est coloré ;
- Kaocha passe de `:deps` à l'alias `:test` du repo, à la même version. Le pom est écrit
  depuis `deps.edn` : il ne le porte plus. Le type de test et les plugins restent dans
  le jar.

Rupture : un projet qui tenait Kaocha de Clornichon doit le déclarer. CHANGELOG :
`Changed`, avec la note de `commons-io` et de `tools.logging`. Doc : `README.md`,
`doc/running.md`, `doc/migrating-from-scenari.md` — scenari 2.0.2 déclarait Kaocha lui
aussi.

Tests : `report_test.clj`, `no-kaocha-in-the-library-namespaces-test` lit la forme `ns`
de chaque fichier de `src/scenari`. Les tests de couleur existants lient la var de
Kaocha et passent sans changement.

Fait. `./test.sh` : 98 tests, 348 assertions. Vérifié sur deux projets jetables :

| Projet                         | Résultat |
|--------------------------------|----------|
| sans Kaocha, `clojure.test`    | la feature passe, rapport coloré, aucun namespace ni jar de Kaocha |
| sans Kaocha, `-m kaocha.runner`| `Could not locate kaocha/runner__init.class, ...` |
| Kaocha 1.91.1392 déclaré       | la feature passe ; 8 séquences d'échappement avec `--color`, 0 avec `--no-color` |

Le pom écrit dans un répertoire jetable porte 5 dépendances : clojure, gherkin,
cucumber-expressions, tag-expressions, tools.namespace.

Vérifié sur Electre le 2026-09-28, sans rien y modifier. Les quatre modules qui
dépendent de Clornichon déclarent Kaocha eux-mêmes, et leur classpath de test est le
même avec la 0.1.11 et avec la branche, au jar de Clornichon près :

| Module               | Kaocha déclaré | Jars, 0.1.11 | Jars, branche |
|----------------------|----------------|--------------|---------------|
| `bo/backend`         | 1.91.1392      | 851          | 850           |
| `bo/account-domain`  | 0.0-367        | 102          | 101           |
| `diffusion/backend`  | 1.91.1392      | 839          | 838           |
| `diffusion/import`   | 1.0.700        | 769          | 768           |

Le chargement des namespaces de scénarios de `diffusion/backend` et de `bo/backend`
donne les mêmes comptes qu'à l'item 24.

Laissé de côté : sans Kaocha, rien n'éteint les couleurs du lanceur `clojure.test`. Lire
`NO_COLOR` dans `scenari.utils` le jour où quelqu'un redirige un tel run vers un
fichier.

### 26. Une seule boucle d'exécution — modéré

Constat, lu dans le code : deux boucles jouent les steps d'un scénario.
`core/run-steps` sert le type Kaocha et le runner de données ;
`scenari.v2.test/run-feature` a la sienne, avec son propre `try-hooks`. Les items 12, 21
et 23 ont tenu parce que leur correctif a été placé plus bas, dans `run-step` et
`run-scenario` : rien ne l'imposait. Aucun écart de comportement trouvé entre les deux.

Où : `src/scenari/v2/test.clj`, `run-feature` ; `src/scenari/v2/core.clj`, `run-steps`
et `run-scenarios`.

Correctif :
- le runner `clojure.test` appelle `core/run-scenario` et rapporte depuis le résultat,
  comme le `-run` du type Kaocha. `run-step` et `try-hooks` ne sont plus appelés que
  par le cœur ;
- `run-steps` et `run-scenarios` jouent puis remplacent en un passage, signatures
  inchangées. Le point était au YAGNI du lot 5 ; il devient nécessaire, le runner
  `clojure.test` héritant sinon des 870 ms pour 5 000 steps.

Ce qui change pour qui lit la console du runner `clojure.test` : le scénario est
rapporté une fois joué. Ce que les steps impriment en tournant, le `FAIL in` d'un `is`
compris, passe au-dessus des steps du scénario au lieu de s'intercaler. Un scénario
s'arrêtant à son premier step en échec, ce rapport est celui du step marqué
`Step failed`. La suite des événements émis, elle, est la même : le test
`one-loop-for-every-runner-test` passe aussi sur le code d'avant.

```
Testing scenario : adding items             Testing scenario : adding items
  Given a cart with 2 items
  When I add 3 items                        FAIL in (shopping-cart) (cart_test.clj:7)
                                            expected: (= n (:cart state))
FAIL in (shopping-cart) (cart_test.clj:7)     actual: (not (= 6 5))
expected: (= n (:cart state))                 Given a cart with 2 items
  actual: (not (= 6 5))                       When I add 3 items
  Then the cart holds 6 items                 Then the cart holds 6 items
  Step failed                                 Step failed
  Then the cart is not empty                  Then the cart is not empty
adding items FAILED                         adding items FAILED
             avant                                       après
```

S'il faut retrouver l'ancien affichage : un rappel passé à `run-scenario`, appelé à
chaque step joué. Pas fait, personne ne l'a demandé.

Tests : `feature_test.clj`, `one-loop-for-every-runner-test` et `many-scenarios-test` ;
le second lève une `StackOverflowError` sur le code d'avant. CHANGELOG : `Changed` et
`Fixed`. Doc : `doc/development-workflow.md`, l'exemple d'un step en échec.

Fait. `./test.sh` : 100 tests, 352 assertions. Banc dans la JVM, par les points d'entrée
`run-features`, médiane de 7 passes après 3 de chauffe :

| Mesure                                  | Runner        | Avant                | Après  |
|-----------------------------------------|---------------|----------------------|--------|
| 500 scénarios de 7 steps                | données       | 24,5 ms              | 16,2 ms |
| 500 scénarios de 7 steps                | `clojure.test`| 38,5 ms              | 38,3 ms |
| 1 scénario de 5 000 steps               | données       | 868,9 ms             | 9,9 ms |
| 1 scénario de 5 000 steps               | `clojure.test`| 27,1 ms              | 31,4 ms |
| 20 000 scénarios de 3 steps             | données       | `StackOverflowError` | 205 ms |
| 20 000 scénarios de 3 steps             | `clojure.test`| 850,7 ms             | 792,9 ms |

Banc Kaocha de la comparaison du 2026-09-28, 500 scénarios, JVM comprise, médiane de 5
runs sur un instantané des sources de chaque commit : `master` 2,96 s, item 24 2,95 s,
item 25 2,97 s, item 26 2,97 s. Pas d'écart mesurable.

Electre : les namespaces de scénarios se chargent comme avant. Les scénarios n'ont pas
été joués, et c'est leur exécution que cet item touche : `bo/backend` passe par le
runner `clojure.test`, `diffusion/backend` par le type Kaocha. À lancer avant la
release.

### 27. Page « Known limits » — mineur

Ce que la comparaison du 2026-09-28 a relevé et qui ne vaut pas un correctif : le dire,
avec quoi faire à la place. `doc/known-limits.md`, liée depuis le README,
`doc/getting-started.md` et `doc/cljdoc.edn`.

Reproduit par un script avant d'être écrit :

| Limite                                   | Constat |
|------------------------------------------|---------|
| état `nil`, `true` ou `false`            | le step suivant reçoit l'état d'avant ; sous une clé, `{:found? false}` passe |
| type de paramètre, registre unique       | `{price}` redéfini sans `EUR` : « team a pays 12 EUR » ne trouve plus son glue |
| même phrase deux fois dans un namespace  | un glue, celui de la seconde définition |
| glue supprimé, au REPL                   | toujours trouvé après rechargement ; après `ns-unmap` seul aussi, le cache n'étant refait qu'au chargement d'un namespace ou à la définition d'un glue |
| pas de relance d'un scénario en échec    | `--focus` sur l'id imprimé par `FAIL in` ne rejoue que lui, par id complet comme par nom |
| couleurs sans Kaocha                     | le filtre `perl` de la page : 8 séquences d'échappement avant, 0 après |

Corrigé par rapport au constat du plan proposé : un `remove-ns` seul ne laisse pas le
cache périmé, le nombre de namespaces change. C'est `ns-unmap` qui le laisse, et un
`remove-ns` suivi du chargement d'un autre namespace dans le même intervalle.

Sans reproduction, ce sont des absences : pas de hook autour d'un step, pas d'exécution
parallèle, les namespaces nommés `scenari`.

Fait. Pas d'entrée au CHANGELOG, la doc seule change. Les ancres et les fichiers que la
page cite existent, `doc/cljdoc.edn` se lit.

### R11. Release 0.2.0
Les items 24 à 27, tout le lot 6. Une seule PR pour le lot, la 17 : `docs/known-limits`
vers `master`, un commit par item.

Version : `minor`, décidé le 2026-09-28. Kaocha retiré des dépendances est une rupture,
notée au CHANGELOG : un projet qui le recevait par Clornichon doit le déclarer. La
release avait d'abord été préparée en 0.1.12.

Préparée le 2026-09-28 : « Prepare 0.2.0 » sur la branche locale `release/0.2.0`,
partie de la tête de la PR 17, non poussée. `master` n'est pas touché, la PR n'étant pas
fusionnée : le « Prepare » y sera repris par `git cherry-pick` une fois qu'elle l'est.

Vérifié sur la branche :
- `./test.sh` : 100 tests, 352 assertions, 0 échec ;
- le jar, construit dans un répertoire jetable sans l'étape `install` : 13 sources sous
  `scenari/` et `kaocha/`, et `META-INF/` ; son pom porte 5 dépendances, sans Kaocha ;
- ce jar dans un projet jetable sans Kaocha, sous `clojure.test` : un scénario passe, un
  scénario en échec est compté, aucun namespace `kaocha.*` n'est chargé, 13 jars au
  classpath, le sien compris ; deux phrases qui font le même nom lèvent ;
- essai à blanc, `./release.sh minor` : `Docs are ready for 0.2.0`, puis arrêt sur
  `CLOJARS_USERNAME is not set`. Rien n'est tagué.

Reste, dans l'ordre :
1. jouer les scénarios d'Electre avec la branche, ce que l'item 26 demande avant la
   release. Non fait ici : le lanceur purge un schéma de base et des collections Solr.
   Le Clornichon local se substitue par un alias, sans rien modifier dans Electre :

   ```bash
   O='{:aliases {:branch {:override-deps {io.github.hiram-madelaine/clornichon {:local/root "/Users/hmadelaine/devel/clones/clornichon"}}}}}'
   # diffusion/backend, type Kaocha
   DATA_ISOLATION_ID=local_test LOG_FORMAT=TEXT clojure -Sdeps "$O" -M:dev:test:branch -m kaocha.runner --focus :scenario
   # bo/backend, runner clojure.test
   DATA_ISOLATION_ID=local_test LOG_FORMAT=TXT clojure -Sdeps "$O" -M:test:branch -m kaocha.runner --focus :scenario
   ```

   Vérifié sans lancer de test : avec cet alias, le classpath des deux modules porte
   `clornichon/src` et Kaocha 1.91.1392 ;
2. fusionner la PR 17 ;
3. sur `master` à jour, reprendre le « Prepare », puis `./release.sh minor` avec les
   identifiants Clojars ;
4. demander le build cljdoc de 0.2.0, vérifier que son README installe 0.2.0, passer
   R11 à `FAIT`.

Publiée le 2026-09-28. Vérifié après coup :
- le tag `v0.2.0` est sur GitHub, sur le commit qui suit le « Prepare » ;
- le jar de Clojars ne contient que `scenari/`, `kaocha/` et `META-INF/`, et son pom
  pointe sur `v0.2.0`, avec 5 dépendances, sans Kaocha ;
- cljdoc, build 115238 : échec, `Could not locate kaocha/output` en chargeant
  `kaocha.plugin.scenari-doc`. Voir 28.

### 28. cljdoc ne charge pas les `kaocha.*` sans Kaocha au pom — important
Constat : cljdoc charge chaque namespace du jar avec les dépendances du pom. Depuis 25,
Kaocha n'y est plus, et les namespaces `kaocha.*` du jar ne se chargent pas : pas de doc
d'API pour 0.2.0.

Fait : `script/build.clj` ajoute Kaocha au pom avec `:optional true`. cljdoc met les
dépendances optionnelles et `provided` au classpath de son analyse
(`cljdoc-analyzer`, `deps.clj`, `provided-deps`) ; tools.deps les écarte des dépendances
transitives (`extensions/maven.clj`), Maven aussi. tools.build sait écrire `optional`,
pas `scope`.

Vérifié : le pom porte `<optional>true</optional>` sur Kaocha ; un projet jetable qui
dépend de ce pom, servi par un dépôt local, a le même classpath qu'avec 0.2.0 publiée,
sans Kaocha. Le build cljdoc ne se vérifie qu'une fois la version publiée.

Pour 0.2.0 déjà publiée : seule une exception dans `cljdoc-analyzer` rattraperait le
build. Pas demandée, 0.2.1 la remplace.

## Vérifié par l'audit, rien à faire

- Niveau `Rule` : `--focus` par alias et par id complet, ids de scénarios inchangés,
  `--tags`, règle sans nom, deux règles du même nom, `classname` junit stables.
- cucumber-messages : 52 enveloppes JSON valides, `pickleId` et `stepDefinitionIds`
  cohérents, protocole 27.2.0.
- Hooks : ordre en oignon entre globaux et locaux, contexte et `:status` conformes à
  la doc.
- `define-parameter-type!` : redéfinition, conflit avec un type natif, registre intact
  après une définition refusée.

## Hors périmètre (YAGNI)

Relevé par l'audit, laissé tel quel :
- entre hooks globaux, les `after` tournent dans l'ordre des `before`, pas en ordre
  inverse comme Cucumber : c'est documenté, c'est un choix ;
- `table/cells` rend `nil` sur un vecteur vide fait à la main, et le rapport plante
  (`scenari/v2/test.clj:57`) : le parser ne produit jamais ce cas.

Des lots précédents : retry/rerun, stop-on-failure (voir d'abord si le `--fail-fast` de
kaocha suffit), attachments, hooks par step, déclaration de glue sans macro, statut
`:undefined` distinct, `diff` de datatable : attendre une demande réelle. Exécution
parallèle : branche `feat/parallel-feature-execution` en attente de décision.
