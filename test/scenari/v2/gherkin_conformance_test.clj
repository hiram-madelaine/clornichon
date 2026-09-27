(ns scenari.v2.gherkin-conformance-test
  "Constructions de la spécification Gherkin
  (https://cucumber.io/docs/gherkin/reference), vérifiées sur la *feature map*
  que produit `->feature-ast`.

  Le parsing lui-même est délégué à `io.cucumber/gherkin` : ces tests ne
  revérifient pas son analyseur, ils verrouillent la traduction pickle ->
  feature map, c'est-à-dire ce que scenari retient de chaque construction."
  (:require [clojure.test :refer :all]
            [scenari.v2.core :as v2]))

(defn- feature [source] (v2/->feature-ast source {} *ns*))
(defn- sentences [f] (mapv #(mapv :sentence (:steps %)) (:scenarios f)))
(defn- names [f] (mapv :scenario-name (:scenarios f)))
(defn- keywords* [f] (mapv #(mapv :sentence-keyword (:steps %)) (:scenarios f)))
(defn- params [f] (-> f :scenarios first :steps first :params))

(deftest feature-level-test
  (testing "nom, description libre et tags de la feature"
    (let [f (feature "@a @b
Feature: ma feature
  Une description libre,
  sur plusieurs lignes.

Scenario: s
Given a")]
      (is (= "ma feature" (:feature f)))
      (is (= "Une description libre,\nsur plusieurs lignes." (:description f)))
      (is (= #{"a" "b"} (:annotations f)))))

  (testing "la narrative As a / I want to / So that est de la description libre,
  comme le veut la spec : gherkin ne lui donne pas de structure"
    (is (= "As a x\nI want to y\nSo that z"
           (:description (feature "Feature: f\nAs a x\nI want to y\nSo that z\n\nScenario: s\nGiven a")))))

  (testing "une feature sans scénario n'a rien à exécuter"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no scenario" (feature "Feature: f\n")))))

(deftest scenario-level-test
  (testing "Scenario, Example et Scenario Outline sont synonymes, et portent une
  description libre et des tags"
    (let [f (feature "Feature: f\n@t\nScenario: s\n  sa narration\nGiven a")]
      (is (= ["s"] (names f)))
      (is (= "sa narration" (:description (first (:scenarios f)))))
      (is (= #{"t"} (:annotations (first (:scenarios f))))))
    (is (= ["e"] (names (feature "Feature: f\nExample: e\nWhen x"))))
    (is (= ["s"] (names (feature "Feature: f\nScenario Template: s\nGiven <x>\nScenarios:\n| x |\n| 1 |")))))

  (testing "Given/When/Then, et And/But/* qui gardent le mot-clé de l'auteur :
  le rapport l'affiche, alors que le pickle le résout en Context/Action/Outcome"
    (is (= [[:given :and :when :then :and :and]]
           (keywords* (feature "Feature: f\nScenario: s\nGiven a\nAnd b\nWhen c\nThen d\nBut e\n* g"))))))

(deftest i18n-test
  (testing "les ~70 langues de gherkin, mot-clé Feature compris — c'est l'apport
  direct du parser officiel : `Fonctionnalité:` tombait avant dans la
  description, et le nom de la feature était perdu en silence.
  La langue se déclare, elle ne se devine plus : sans `# language:`, les
  mots-clés sont ceux de l'anglais."
    (let [f (feature "# language: fr\nFonctionnalité: panier\n@t\nScénario: s\nEtant donné que a\nQuand b\nAlors c\nEt d")]
      (is (= "panier" (:feature f)))
      (is (= ["s"] (names f)))
      (is (= [[:given :when :then :and]] (keywords* f)))))

  (testing "l'en-tête # language: choisit la langue"
    (is (= "panier" (:feature (feature "# language: fr\nFonctionnalité: panier\nScénario: s\nQuand a")))))

  (testing "le dialecte officiel est plus riche que ne l'était la grammaire :
  Soit / Sachant que / Lorsque / Donc / Et que en plus des mots-clés connus.
  En revanche il colle le deux-points au mot-clé, `Scénario:` et non
  `Scénario :` — c'est la seule reprise à faire dans un .feature français."
    (is (= [["setup" "a"]]
           (sentences (feature "# language: fr\nFonctionnalité: f\nContexte:\nEtant donné que setup\nExemple: s\nQuand a"))))
    (is (= ["s 1"]
           (names (feature "# language: fr\nFonctionnalité: f\nPlan du scénario: s <x>\nLorsque a\nEt que b\nExemples:\n| x |\n| 1 |"))))
    (is (= [[:given :when :then]]
           (keywords* (feature "# language: fr\nFonctionnalité: f\nExemple: s\nSoit a\nLorsque b\nDonc c"))))))

(deftest background-rule-outline-test
  (testing "Background, Rule et Scenario Outline sont résolus par le compilateur
  de pickles : la feature map n'a que des scénarios plats, chacun portant sa règle"
    (let [f (feature (str "Feature: f\n"
                          "Background:\nGiven feature setup\n"
                          "@slow\nRule: r\n"
                          "  ce que la regle verifie\n"
                          "  Background:\n  Given rule setup\n"
                          "  @fast\n  Scenario Outline: cas <x>\n"
                          "  When <x>\n"
                          "  Examples:\n  | x |\n  | 1 |\n  | 2 |\n"))]
      (is (= ["cas 1" "cas 2"] (names f)))
      (is (= [["feature setup" "rule setup" "1"]
              ["feature setup" "rule setup" "2"]] (sentences f))
          "le background de la feature d'abord, puis celui de la règle")
      (is (= [#{"slow" "fast"} #{"slow"
                                 "fast"}] (mapv :annotations (:scenarios f)))
          "les tags de la règle sont hérités")
      (is (= ["ce que la regle verifie" "ce que la regle verifie"]
             (mapv (comp :description :rule) (:scenarios f)))
          "la description de la règle est un contexte pour chacun de ses scénarios")))

  (testing "un tag posé sur un bloc Examples ne vaut que pour ses lignes"
    (is (= [#{"nominal"} #{"limite"}]
           (mapv :annotations
                 (:scenarios (feature (str "Feature: f\nScenario Outline: s <x>\nGiven <x>\n"
                                           "@nominal\nExamples:\n| x |\n| 1 |\n"
                                           "@limite\nExamples:\n| x |\n| 9 |\n")))))))

  (testing "une table Examples sans ligne fait disparaître le scénario, ce que
  la garde de feature accuserait à tort la reconnaissance des mots-clés"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Examples table has no row, scenario s"
                          (feature "Feature: f\nScenario Outline: s\nGiven <x>\nExamples:\n| x |\n")))))

(deftest data-table-test
  (testing "une data table devient un vecteur de maps, colonnes dans l'ordre"
    (is (= [{:type :table :val [{:nom "iPhone" :prix "500"}]}]
           (params (feature "Feature: f\nScenario: s\nGiven a\n| nom | prix |\n| iPhone | 500 |")))))

  (testing "cases vides, à toute position"
    (is (= [{:a "1" :b "" :c "3"}]
           (:val (first (params (feature "Feature: f\nScenario: s\nGiven a\n| a | b | c |\n| 1 |   | 3 |")))))))

  (testing "cellule échappée : \\| ne scinde pas la ligne, \\n devient un saut"
    (is (= [{:nom "a | b" :regex "x\ny"}]
           (:val (first (params (feature (str "Feature: f\nScenario: s\nGiven a\n"
                                              "| nom | regex |\n| a \\| b | x\\ny |"))))))))

  (testing "blancs en fin de ligne, très courants dans un .feature édité à la main"
    (is (= [{:a "1" :b "2"}]
           (:val (first (params (feature "Feature: f\nScenario: s\nGiven a\n| a | b |   \n| 1 | 2 |"))))))))

(deftest doc-string-test
  (testing "doc string \"\"\" : le parser retire l'indentation commune"
    (is (= [{:type :doc-string :val "ligne 1\n  ligne 2"}]
           (params (feature "Feature: f\nScenario: s\nGiven a\n  \"\"\"\n  ligne 1\n    ligne 2\n  \"\"\"")))))

  (testing "doc string délimitée par des backticks"
    (is (= [{:type :doc-string :val "du `code` inline"}]
           (params (feature "Feature: f\nScenario: s\nGiven a\n  ```\n  du `code` inline\n  ```")))))

  (testing "content type : ```json est transmis au step, il ne l'était pas"
    (is (= [{:type :doc-string :val "{\"a\": 1}" :media-type "json"}]
           (params (feature "Feature: f\nScenario: s\nGiven a\n  \"\"\"json\n  {\"a\": 1}\n  \"\"\"")))))

  (testing "une doc string non fermée est une erreur, pas une feature vide qui passe"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot parse feature"
                          (feature "Feature: f\nScenario: s\nGiven a\n  \"\"\"\njamais fermee")))))

(deftest lexical-test
  (testing "commentaires, y compris dans une table Examples — ils y faisaient
  échouer le parsing"
    (is (= [["a"]] (sentences (feature "# en tête\nFeature: f\nScenario: s\n# entre les steps\nGiven a"))))
    (is (= ["s"] (names (feature "Feature: f\nScenario Outline: s\nGiven <x>\nExamples:\n| x |\n# cmt\n| 1 |")))))

  (testing "fins de ligne Windows"
    (is (= [["a"]] (sentences (feature "Feature: f\r\nScenario: s\r\nGiven a\r\n")))))

  (testing "un scénario sans ligne Feature est une erreur de syntaxe, là où
  l'ancienne grammaire l'acceptait"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot parse feature"
                          (feature "Scenario: s\nGiven a")))))
