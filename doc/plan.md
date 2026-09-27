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
  travail concerné. Une PR par item, comme les lots précédents.
- Les numéros de ligne cités datent de `2564ae0` : se fier au nom de la fonction s'ils
  ont bougé.

## Tableau de bord

| #  | Item                                                        | Lot | Gravité   | État | Notes |
|----|-------------------------------------------------------------|-----|-----------|------|-------|
| E1 | Electre passe de scenari `2396ada` à clornichon             | 2   | —         | EN COURS | suivi par un agent dédié au repo Electre (MR, CI, bumps) |
| 8  | Namespace `clornichon.*` (alias)                            | 3   | —         | TODO | décision à prendre ; reco : garder `scenari.*`, l'expliquer dans le README |
| 9  | Hooks globaux + before-all / after-all                      | 4   | —         | EN COURS | livré en 0.1.10 ; reste la mesure Electre (synthétique : coût nul) |
| 11 | Niveau `Rule` dans le rapport et l'arbre kaocha             | 4   | —         | EN COURS | livré en 0.1.9 ; reste la mesure Electre (coût attendu nul) |
| 12 | Un hook qui lève fait échouer son scénario, pas le run      | 5   | critique  | FAIT | branche `hook-failures` ; NDJSON : pas d'enveloppe `hook`, voir le détail |
| 13 | `release.sh` prépare la doc avant de taguer                 | 5   | important | TODO | à faire avant R9 |
| 14 | `-load` charge aussi les `test-paths`                       | 5   | important | TODO | l'exemple `tests.edn` de la doc ne charge pas |
| R9 | Release 0.1.11 (12, 13, 14)                                 | 5   | —         | TODO | |
| 15 | Ordre du fichier dans la doc HTML, fin de `Rule` en console | 5   | important | TODO | |
| 16 | Retirer `deffeature` sur un répertoire, et commons-io       | 5   | modéré    | TODO | |
| 17 | Type hints sur le chemin de matching                        | 5   | modéré    | TODO | 765 ms → 105 ms au chargement, banc synthétique |
| 18 | Hooks globaux ignorés sans rien dire                        | 5   | modéré    | TODO | |
| 19 | Code mort, features d'exemple hors du jar                   | 5   | ménage    | TODO | |
| 20 | `doc/development-workflow.md` à jour                        | 5   | mineur    | TODO | |
| 21 | `--fail-fast` fait tomber le run au premier step en échec   | 5   | important | TODO | trouvé en traitant 12 |
| R10 | Release 0.1.12 (15 à 21)                                   | 5   | —         | TODO | |

Ordre proposé : 12 (le seul critique), 13 (pour que R9 ne refasse pas l'erreur de
0.1.10), 14, R9 ; puis 15 à 21 dans l'ordre, R10. Les items 16 à 21 sont indépendants
les uns des autres et peuvent se prendre dans n'importe quel ordre.

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

### R9. Release 0.1.11
Après 12, 13 et 14. S'arrêter à la publication Clojars et à la mise à jour de ce plan :
le bump côté Electre revient à l'agent dédié.

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

Vérifier : `unzip -l target/clornichon-*.jar` ne liste plus que `scenari/`, `kaocha/`
et `META-INF/`. CHANGELOG : `Changed` pour le contenu du jar, rien pour le code mort.

### 20. `doc/development-workflow.md` à jour — mineur

- « must return the (possibly modified) state » contredit la règle nil/booléen de 0.1.1 ;
  les exemples gardent un `state` final devenu inutile ;
- `clojure -M:test` ne lance pas les tests ;
- les hooks globaux n'y figurent pas : renvoyer à `doc/state-and-hooks.md#global-hooks`.

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

### R10. Release 0.1.12
Après 15 à 20. Peut se scinder si un item traîne.

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
- `run-scenarios` et `run-steps` empilent des `map` paresseux : `StackOverflowError` à
  20 000 scénarios dans une feature, 890 ms pour 5 000 steps. Tailles irréalistes ;
  passer à `mapv` le jour où on y touche ;
- `table/cells` rend `nil` sur un vecteur vide fait à la main, et le rapport plante
  (`scenari/v2/test.clj:57`) : le parser ne produit jamais ce cas.

Des lots précédents : retry/rerun, stop-on-failure (voir d'abord si le `--fail-fast` de
kaocha suffit), attachments, hooks par step, déclaration de glue sans macro, statut
`:undefined` distinct, `diff` de datatable : attendre une demande réelle. Exécution
parallèle : branche `feat/parallel-feature-execution` en attente de décision.
