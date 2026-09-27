# Clornichon — lot 4 : ce qu'un utilisateur de Cucumber cherche en premier

## Contexte
0.1.8 publié. Les lots 1 à 3 (message de step non résolu, CI, durées, hooks avec
contexte, cucumber-messages, types de paramètres custom, releases 0.1.3 → 0.1.8) sont
faits ; leur détail reste dans l'historique (`git show a702a50:doc/plan.md`).

Ce lot reprend trois cases de `doc/roadmap.md`, celles qui manquent le plus à quelqu'un
qui vient de Cucumber : des hooks qui ne se redéclarent pas dans chaque `deffeature`,
une API de datatable, et le niveau `Rule` dans le rapport.

Référence perf (Electre, `diffusion/backend`, suite `:scenario`, 392 tests) : ~91 s,
bruit d'environnement ±6 %. Les items 9 et 11 touchent le chemin d'exécution : mesurer
avant de passer à `FAIT`.

## Gestion d'état

États : `TODO` · `EN COURS` · `BLOQUÉ` · `FAIT` · `ABANDONNÉ`

Règles :
- Un seul item `EN COURS` à la fois.
- Passage à `FAIT` seulement quand : `./test.sh` vert, entrée CHANGELOG `[Unreleased]`,
  case cochée dans `doc/roadmap.md` si l'item y figure, commit fait.
- `BLOQUÉ` / `ABANDONNÉ` : une ligne de raison dans la colonne Notes.
- Ce fichier est mis à jour à chaque changement d'état, dans le même commit que le
  travail concerné. Une PR par item, comme les lots précédents.

## Tableau de bord

| #  | Item                                              | Lot | Cible      | État | Notes |
|----|---------------------------------------------------|-----|------------|------|-------|
| E1 | Electre passe de scenari `2396ada` à clornichon | 2 | Electre  | EN COURS | suivi par un agent dédié au repo Electre (MR, CI, bumps) |
| 8  | Namespace `clornichon.*` (alias)                  | 3   | communauté | TODO | décision à prendre ; reco : garder `scenari.*`, l'expliquer dans le README |
| 9  | Hooks globaux + before-all / after-all            | 4   | les deux   | TODO | |
| 10 | API de datatable                                  | 4   | communauté | TODO | |
| 11 | Niveau `Rule` dans le rapport et l'arbre kaocha   | 4   | communauté | TODO | |
| R7 | Release 0.1.9 (lot 4)                             | 4   | les deux   | TODO | une release par item si l'un d'eux traîne |

Ordre proposé : 11 (le plus petit, rien ne change dans l'API), 10, puis 9 (le seul qui
touche Electre).

## Détail des items

### E1. Migration d'Electre — EN COURS
`diffusion/backend`, `diffusion/import`, `bo/backend`, `bo/account-domain` : la dep git
`io.github.hiram-madelaine/scenari` (SHA `2396ada`) devient
`io.github.hiram-madelaine/clornichon {:mvn/version ...}`. Suivi et bumps faits par
l'agent dédié au repo Electre, pas ici.

### 8. Namespace `clornichon.*`
Décision d'abord. Les API publiques sont surtout des macros (`defgiven`, `deffeature`...) :
un alias demande de les redéclarer une à une. Reco : garder `scenari.*` (c'est ce qui
rend la migration depuis scenari triviale), une section « Pourquoi `scenari.*` ? » dans
le README, et passer l'item en `ABANDONNÉ` faute de demande réelle.

### 9. Hooks globaux + before-all / after-all
Aujourd'hui chaque `deffeature` porte ses hooks dans son map d'options : 250 features =
250 fois le même `{:pre-scenario-run [#'clean-db!]}`.

Proposition : un hook se déclare comme un glue, par une métadonnée sur la var, dans les
namespaces de glue, et vaut pour toutes les features :

```clojure
(defn ^{:scenari/hook :before-scenario :scenari/tags "@db"} clean-db! [] ...)
(defn ^{:scenari/hook :before-all} start-system! [] ...)
```

- Clés : `:before-all` `:after-all` (suite), `:before-feature` `:after-feature`,
  `:before-scenario` `:after-scenario`. `:scenari/tags` et la règle d'arité (0 prioritaire,
  1 = contexte) déjà en place s'appliquent tels quels : `->hook` et `call-hook` servent.
- Découverte : même balayage que les glues (`glue.clj`), mis en cache de la même façon.
- Ordre en oignon : hooks globaux avant ceux du `deffeature` à l'entrée, après eux à la
  sortie. Entre hooks globaux : ordre de chargement des ns, puis des vars (à documenter).
- before-all / after-all : tournent dans `testable/-run :kaocha.type/scenari`, autour de
  `run-testables` ; after-all dans un `finally`. Un before-all qui lève fait échouer la
  suite sans lancer de scénario.
- Runner `clojure.test` seul : pas de notion de suite, before-all / after-all n'y
  tournent pas (le dire dans la doc, renvoyer à `use-fixtures :once`). Les hooks de
  feature et de scénario, eux, y tournent.
- Les hooks du map d'options de `deffeature` restent, rien ne casse pour Electre.

Tests : ordre en oignon, tag, after-all malgré un échec, un before-all qui lève.
Doc : `doc/state-and-hooks.md`. Mesure Electre : la découverte ne doit rien coûter par
scénario (résolue une fois, comme les glues).

### 10. API de datatable
Aujourd'hui une table arrive en vecteur de maps (clés = en-têtes en keyword, cellules en
chaînes). Ça casse deux formes courantes : la table verticale `| nom | valeur |` et la
liste à une colonne, dont la première ligne est prise pour un en-tête.

Proposition, sans changer ce que reçoit un step existant :
- les cellules brutes (en-tête compris) posées en métadonnée du vecteur par
  `argument->params` ;
- un ns `scenari.v2.table` : `cells` (vecteur de vecteurs), `as-list` (une colonne ou une
  ligne → vecteur), `as-map` (table à deux colonnes → map, clés en keyword), `transpose`
  (en-têtes en première colonne → même vecteur de maps qu'aujourd'hui) ;
- `diff` en dernier, seulement si le reste est livré : compare une table attendue à une
  collection de maps et échoue en montrant les lignes en trop ou manquantes.

Non fait (YAGNI) : conversion ligne → entité typée — c'est un `map` + `update` côté glue.
Tests : chaque fonction, et le squelette généré d'un step manquant inchangé.
Doc : section datatable de `doc/step-expressions.md`.

### 11. Niveau `Rule`
Le parser garde les `Rule` dans le GherkinDocument, mais les pickles les aplatissent :
seule la description de la règle remonte (`ast-nodes`, préfixée à celle du scénario).

Proposition :
- `ast-nodes` pose aussi `:rule` (nom, tags, description) sur chaque scénario d'une règle ;
- rapport console (`scenari.v2.test` et le reporter kaocha) : une ligne `Rule : <nom>`
  quand la règle change, scénarios indentés dessous ; la description de la règle n'est
  plus préfixée à chaque scénario mais imprimée une fois sous la règle ;
- arbre kaocha : un groupe `:kaocha.type/scenari-rule` entre feature et scénario. Les ids
  des scénarios ne changent pas, pour que les `--focus` existants marchent encore ; la
  règle gagne un id et un alias (`--focus <nom-de-règle>`).
- cucumber-messages : rien à faire, le flux porte déjà les règles.

Tests : feature avec et sans règle, deux règles, outline dans une règle, `--focus` sur
une règle. Doc : `doc/feature-structure.md` (la section qui dit que Rule est aplatie).

## Hors périmètre (YAGNI)
Retry/rerun, stop-on-failure (voir d'abord si le `--fail-fast` de kaocha suffit),
attachments, hooks par step, déclaration de glue sans macro, statut `:undefined`
distinct : attendre une demande réelle. Exécution parallèle : branche
`feat/parallel-feature-execution` en attente de décision, hors de ce lot.
