(ns scenari.v2.core-test
  (:require [clojure.string :as string]
            [clojure.test :as t :refer [is]]
            [scenari.v2.core :as v2]
            [scenari.v2.test :as sc-test]
            [scenari.v2.glue :as glue]
            [kaocha.type.scenari]
            [kaocha.testable :as testable]
            [kaocha.plugin.filter :as kfilter]
            [kaocha.plugin.scenari-tags :as stags]
            [kaocha.plugin.scenari-doc :as sdoc]
            [kaocha.plugin.scenari-dry-run :as sdry]
            [kaocha.plugin.scenari-slowest-steps :as sslow]
            [kaocha.repl :as krepl]
            [testit.core :refer :all])
  (:import [io.cucumber.tagexpressions TagExpressionParser]))

(v2/defgiven #"My duplicated step in other ns and feature ns" [state]
  state)

(t/deftest deffeature-macro-test
  (t/testing "macro definition taking different feature structure"
    (t/is (some? (macroexpand '(v2/deffeature example-feature "test/scenari/v2/example.feature"))))
    (t/is (some? (macroexpand '(v2/deffeature example-feature (slurp "test/scenari/v2/example.feature")))))
    (t/is (some? (macroexpand '(v2/deffeature example-feature (first (vector (slurp "test/scenari/v2/example.feature")))))))
    (t/is (some? (macroexpand '(v2/deffeature (symbol (str "example-feature")) (first (vector (slurp "test/scenari/v2/example.feature")))))))))

(comment
  (remove-ns 'scenari.v2.core-test)
  (meta #'scenari.v2.core-test/my-feature)
  (v2/run-features)
  (v2/run-features #'scenari.v2.core-test/my-feature)
  (sc-test/run-features #'scenari.v2.core-test/my-feature)

  (t/run-tests)

  (krepl/test-plan)
  (krepl/run-all)
  (krepl/run :scenario))

(t/deftest feature-ast-parse-failure-test
  (t/testing "an unparsable feature raises instead of yielding an empty, passing feature"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot parse feature"
                          (v2/->feature-ast "Feature: f\nScenario: s\nGiven a\n  \"\"\"\nune doc string jamais fermee" {} *ns*)))
    (is (= 1 (count (:scenarios (v2/->feature-ast "Feature: f\nScenario: s\nGiven a" {} *ns*))))
        "a valid feature is unaffected"))

  (t/testing "a feature whose keywords are all unrecognized is a parse error, where
  the old grammar swallowed it as free description and blamed the missing scenario"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot parse feature"
                          (v2/->feature-ast "Fonctionnalite: f\nScenario ! s\nSoit a" {} *ns*)))))

(t/deftest table-cell-escapes-test
  (t/testing "a cell is trimmed and unescaped"
    (is (= [{:nom "a | b" :regex "x\ny"}]
           (-> (v2/->feature-ast (str "Feature: f\nScenario: s\nGiven a\n"
                                      "| nom | regex |\n"
                                      "| a \\| b | x\\ny |")
                                 {} *ns*)
               :scenarios first :steps first :params first :val))
        "\\| stays in its cell instead of splitting it, \\n becomes a newline")))

(v2/defgiven "a number {int}" [state n] n)

(t/deftest scenario-outline-test
  (t/testing "a scenario outline yields one scenario per Examples row, placeholders substituted"
    (let [scenarios (:scenarios (v2/->feature-ast
                                 (str "Feature: f\n"
                                      "Scenario Outline: adding <a> and <b>\n"
                                      "Given a number <a>\n"
                                      "When I add <b>\n"
                                      "Then I get <sum>\n"
                                      "Examples:\n"
                                      "| a | b | sum |\n"
                                      "| 1 | 2 | 3   |\n"
                                      "| 5 | 5 | 10  |")
                                 {} *ns*))]
      (is (= 2 (count scenarios)))
      (is (= ["adding 1 and 2" "adding 5 and 5"] (map :scenario-name scenarios))
          "the scenario name is substituted too")
      (is (= [["a number 1" "I add 2" "I get 3"]
              ["a number 5" "I add 5" "I get 10"]]
             (map #(map :sentence (:steps %)) scenarios)))
      (is (= [[{:type :value :val 1}] [] []]
             (map :params (:steps (first scenarios))))
          "la valeur substituée arrive au glue, convertie par son token ; les deux
          autres steps n'ont pas de glue, donc pas de paramètre"))))

(t/deftest several-examples-blocks-test
  (t/testing "every Examples block of an outline is expanded, not just the first"
    (let [scenarios (:scenarios (v2/->feature-ast
                                 (str "Feature: f\n"
                                      "Scenario Outline: s <x>\n"
                                      "Given a number <x>\n"
                                      "Examples: nominal\n"
                                      "| x |\n| 1 |\n| 2 |\n"
                                      "Examples: edge cases\n"
                                      "| x |\n| 9 |\n")
                                 {} *ns*))]
      (is (= ["s 1" "s 2" "s 9"] (map :scenario-name scenarios))))))

(t/deftest examples-substitution-test
  (t/testing "les placeholders sont substitues colonne par colonne, en sequence :
  une valeur qui ressemble elle-meme a un placeholder est resubstituee par la
  colonne suivante. C'est le comportement de l'implementation de reference."
    (let [scenarios (:scenarios (v2/->feature-ast
                                 (str "Feature: f\n"
                                      "Scenario Outline: s\n"
                                      "Given <a> then <b>\n"
                                      "Examples:\n"
                                      "| a | b |\n"
                                      "| <b> | 42 |\n")
                                 {} *ns*))]
      (is (= ["42 then 42"] (map #(:sentence (first (:steps %))) scenarios)))))

  (t/testing "an Examples table with a header but no row names the scenario,
  instead of making it vanish and letting the feature-level guard blame keyword
  recognition"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"Examples table has no row, scenario s"
         (v2/->feature-ast "Feature: f\nScenario Outline: s\nGiven <x>\nExamples:\n| x |\n"
                           {} *ns*)))))

(t/deftest def-glue-test
  (t/testing "the def* macros return their var, like every other Clojure def*:
  they used to return nil, breaking `(doto (defgiven ...) ...)`, the REPL echo
  and any tooling reading the returned var"
    (let [v (v2/defgiven "a step returning its var" [state] state)]
      (is (var? v))
      (is (= "a step returning its var" (:step (meta v)))))))

(t/deftest rule-tags-test
  (t/testing "a Rule's tags are inherited by the scenarios it groups, per the
  gherkin spec: without them `--focus-meta :slow` silently ran zero scenarios"
    (is (= [#{"slow" "fast"} #{"slow"}]
           (map :annotations
                (:scenarios (v2/->feature-ast
                             (str "Feature: f\n"
                                  "@slow\n"
                                  "Rule: r\n"
                                  "@fast\n"
                                  "Example: e1\n"
                                  "When x\n"
                                  "Example: e2\n"
                                  "When y\n")
                             {} *ns*)))))))

(t/deftest background-test
  (t/testing "background steps are spliced at the head of every scenario"
    (let [scenarios (:scenarios (v2/->feature-ast
                                 (str "Feature: f\n"
                                      "Background: a common setup\n"
                                      "Given a logged user\n"
                                      "And a cart\n"
                                      "\n"
                                      "Scenario: s1\n"
                                      "When I buy\n"
                                      "Scenario: s2\n"
                                      "When I leave")
                                 {} *ns*))]
      (is (= [["a logged user" "a cart" "I buy"]
              ["a logged user" "a cart" "I leave"]]
             (map #(map :sentence (:steps %)) scenarios)))
      (is (= [[0 1 2] [0 1 2]] (map #(map :order (:steps %)) scenarios))
          "spliced steps are renumbered, the runner relies on :order"))))

(t/deftest rule-test
  (t/testing "Rule scenarios are lifted into the feature, with the rule's own background"
    (let [scenarios (:scenarios (v2/->feature-ast
                                 (str "Feature: f\n"
                                      "Background:\n"
                                      "Given feature setup\n"
                                      "Rule: r1\n"
                                      "  Background:\n"
                                      "  Given rule setup\n"
                                      "  Example: e1\n"
                                      "  When x\n"
                                      "Rule: r2\n"
                                      "  Example: e2\n"
                                      "  When y\n")
                                 {} *ns*))]
      (is (= [["e1" ["feature setup" "rule setup" "x"]]
              ["e2" ["feature setup" "y"]]]
             (map (juxt :scenario-name #(map :sentence (:steps %))) scenarios))
          "the feature background comes first, then the rule's own"))))

(t/deftest rule-description-test
  (t/testing "a Rule's description is prepended to each of its scenarios"
    (let [scenarios (:scenarios (v2/->feature-ast
                                 (str "Feature: f\n"
                                      "Rule: r\n"
                                      "  ce que la regle verifie\n"
                                      "  Scenario: s1\n"
                                      "  sa propre narration\n"
                                      "  When x\n"
                                      "  Scenario: s2\n"
                                      "  When y\n")
                                 {} *ns*))]
      (is (= ["ce que la regle verifie\nsa propre narration"
              "ce que la regle verifie"]
             (map :description scenarios))
          "the rule's lines come first, the scenario's own follow")))

  (t/testing "a Rule without description leaves the scenario's own untouched"
    (let [scenarios (:scenarios (v2/->feature-ast
                                 (str "Feature: f\nRule: r\n"
                                      "  Scenario: s\n  sa narration\n  When x\n")
                                 {} *ns*))]
      (is (= ["sa narration"] (map :description scenarios)))))

  (t/testing "a Rule description follows its scenarios through outline expansion.
  Placeholders are not substituted there: per the spec they are replaced in the
  steps, their arguments and the scenario name, not in free description text."
    (let [scenarios (:scenarios (v2/->feature-ast
                                 (str "Feature: f\nRule: r\n  cas <x>\n"
                                      "  Scenario Outline: s\n  When y\n"
                                      "  Examples:\n  | x |\n  | 1 |\n  | 2 |\n")
                                 {} *ns*))]
      (is (= ["cas <x>" "cas <x>"] (map :description scenarios))))))

(defn- surviving-scenarios
  "[feature scenario] pairs left after kaocha's filter marked the rest ::skip."
  [suite]
  (for [feature (when-not (::testable/skip suite) (:kaocha.test-plan/tests suite))
        :when (not (::testable/skip feature))
        scenario (:kaocha.test-plan/tests feature)
        :when (not (::testable/skip scenario))]
    [(::testable/desc feature) (::testable/desc scenario)]))

(defn- focus-meta [suite k]
  (surviving-scenarios (kfilter/filter-testable suite {:focus-meta [k]})))

(t/deftest kaocha-tag-filtering-test
  (let [suite (testable/-load {::testable/type                 :kaocha.type/scenari
                               :kaocha/source-paths            ["src"]
                               :kaocha/test-paths              ["test/scenari/v2"]
                               :kaocha.type.scenari/glue-paths ["scenari/v2"]})]
    (t/testing "every feature of the test paths is loaded"
      (is (< 1 (count (surviving-scenarios suite)))))

    (t/testing "--focus-meta on a gherkin tag of a scenario keeps that scenario alone"
      (is (= [["tagged feature" "tagged scenario"]] (focus-meta suite :scenario-annotated))))

    (t/testing "--focus-meta on a gherkin tag of a feature, or on deffeature var meta,
    keeps the whole feature"
      (is (= [["tagged feature" "tagged scenario"]] (focus-meta suite :annotated)))
      (is (= [["tagged feature" "tagged scenario"]] (focus-meta suite :var-tagged))))

    (t/testing "--focus-meta on a feature tag drags in the scenarios that do not
    carry it: the focus is dropped for the whole subtree as soon as a node matches"
      (is (= [["mixed tags" "picked scenario"]
              ["mixed tags" "plain scenario"]]
             (focus-meta suite :shared))))))

(defn- tags
  "[feature scenario] pairs left after --tags EXPR."
  [suite expr]
  (surviving-scenarios (stags/filter-testable (TagExpressionParser/parse expr) suite)))

(t/deftest kaocha-tag-expression-test
  (let [suite (testable/-load {::testable/type                 :kaocha.type/scenari
                               :kaocha/source-paths            ["src"]
                               :kaocha/test-paths              ["test/scenari/v2"]
                               :kaocha.type.scenari/glue-paths ["scenari/v2"]})]
    (t/testing "a scenario tag keeps that scenario alone"
      (is (= [["mixed tags" "picked scenario"]] (tags suite "@picked"))))

    (t/testing "a feature tag reaches every scenario - the pickle compiler already
    made them inherit it"
      (is (= [["mixed tags" "picked scenario"]
              ["mixed tags" "plain scenario"]]
             (tags suite "@shared"))))

    (t/testing "and / not, evaluated per scenario: the case --focus-meta cannot express"
      (is (= [["mixed tags" "plain scenario"]] (tags suite "@shared and not @picked")))
      (is (= [["tagged feature" "tagged scenario"]]
             (tags suite "@annotated and @scenario-annotated"))))

    (t/testing "parentheses"
      (is (= [["tagged feature" "tagged scenario"]]
             (tags suite "(@picked or @scenario-annotated) and not @shared"))))

    (t/testing "an expression matching nothing skips the features and the suite,
    so the reporter does not announce empty groups"
      (is (empty? (tags suite "@nope")))
      (is (::testable/skip (stags/filter-testable (TagExpressionParser/parse "@nope") suite))))

    (t/testing "an invalid expression throws, so the message reaches the user"
      (is (thrown? Exception (TagExpressionParser/parse "@a and"))))))

(t/deftest scenari-doc-test
  (let [suite     (testable/-load {::testable/type                 :kaocha.type/scenari
                                   :kaocha/source-paths            ["src"]
                                   :kaocha/test-paths              ["test/scenari/v2"]
                                   :kaocha.type.scenari/glue-paths ["scenari/v2"]})
        plan      (fn [expr] {:kaocha.test-plan/tests
                              [(stags/filter-testable (TagExpressionParser/parse expr) suite)]})
        features  (sdoc/selected-features (plan "@shared"))
        html      (sdoc/document features)]

    (t/testing "la doc ne retient que les scénarios laissés par les filtres"
      (is (= [["mixed tags" ["picked scenario" "plain scenario"]]]
             (for [f features]
               [(::testable/desc f) (map ::testable/desc (:kaocha.test-plan/tests f))]))))

    (t/testing "chaque scénario a son ancre et son lien dans le sommaire"
      (doseq [scenario (mapcat :kaocha.test-plan/tests features)
              :let [a (#'sdoc/anchor (::testable/id scenario))]]
        (is (string/includes? html (str "id=\"" a "\"")))
        (is (string/includes? html (str "href=\"#" a "\"")))))

    (t/testing "deux ids distincts gardent deux ancres distinctes : aplatir la
    ponctuation confondait la feature `:ns/f-s` et le scénario `s` de `:ns/f`"
      (is (not= (#'sdoc/anchor :ns/f-s) (#'sdoc/anchor :ns.f/s))))

    (t/testing "les steps et leurs blocs sont rendus, le HTML est échappé"
      (is (string/includes? html "<span class=\"kw\">Then</span>"))
      (is (string/includes? (sdoc/document (sdoc/selected-features {:kaocha.test-plan/tests [suite]}))
                            "<th>size</th>")
          "la datatable d'un step devient un vrai tableau")
      (is (string/includes? (sdoc/document
                             [{::testable/id   :ns/f
                               ::testable/desc "a <b> feature"
                               :kaocha.test-plan/tests []}])
                            "a &lt;b&gt; feature")))

    (t/testing "une suite entièrement skippée ne documente rien"
      (is (empty? (sdoc/selected-features (plan "@nope")))))

    (t/testing "--doc-report : le même walk lit l'arbre de résultats, et le
    statut de chaque scénario et de chaque step est rendu"
      (let [result {:kaocha.result/tests
                    [{:kaocha.result/tests
                      [{::testable/type    :kaocha.type/scenari-feature
                        ::testable/id      :ns/f
                        ::testable/desc    "f"
                        :kaocha.result/tests
                        [{::testable/id   :ns.f/s
                          ::testable/desc "s"
                          :status         :fail
                          :steps          [{:sentence-keyword :given :sentence "ça marche" :status :success}
                                           {:sentence-keyword :then :sentence "ça casse" :status :fail
                                            :exception        (ex-info "boom" {})}
                                           {:sentence-keyword :then :sentence "jamais atteint" :status :pending}]}]}]}]}
            html   (sdoc/document (sdoc/selected-features result))]
        (is (string/includes? html "<span class=\"badge fail\">fail</span>"))
        (is (string/includes? html "<li class=\"step pending\">"))
        (is (string/includes? html "<pre class=\"error\">boom</pre>")))))

  (t/testing "--doc-html et --doc-report ensemble sont refusés : --doc-html
  skippe tout, le rapport serait vide"
    (is (thrown? Exception (sdoc/scenari-doc-pre-load-hook
                            {::sdoc/target-file "a.html" ::sdoc/report-file "b.html"})))
    (is (= {::sdoc/report-file "b.html"}
           (sdoc/scenari-doc-pre-load-hook {::sdoc/report-file "b.html"})))))

(t/deftest scenari-dry-run-test
  (let [suite (testable/-load {::testable/type                 :kaocha.type/scenari
                               :kaocha/source-paths            ["src"]
                               :kaocha/test-paths              ["test/scenari/v2"]
                               :kaocha.type.scenari/glue-paths ["scenari/v2"]})]

    (t/testing "tous les steps des features du repo résolvent un glue"
      (is (empty? (sdry/undefined-steps {:kaocha.test-plan/tests [suite]}))))

    (t/testing "un step sans glue est repéré dans le test-plan, avec l'endroit
    où il est utilisé - le parse, lui, n'imprime que la phrase"
      (let [plan    {:kaocha.test-plan/tests
                     [{:kaocha.test-plan/tests
                       [{::testable/type      :kaocha.type/scenari-feature
                         ::testable/id        :ns/f
                         ::testable/desc      "f"
                         :kaocha.test-plan/tests
                         [{::testable/id   :ns.f/s
                           ::testable/desc "s"
                           :steps          [{:raw "Given ça" :glue {:ref identity}}
                                            {:raw "When ça manque"}]}]}]}]}
            missing (sdry/undefined-steps plan)]
        (is (= [["f" "s" "When ça manque"]]
               (map (fn [[f s step]] [f s (:raw step)]) missing)))
        (is (= "  f > s\n    When ça manque" (sdry/report missing)))))

    (t/testing "l'inverse : un glue qu'aucun step sélectionné n'utilise"
      (let [g (first (filter #(= "a number {int}" (:step %)) (glue/all-glues)))]
        (is (some? g))
        (is (some #{g} (sdry/unused-glues [])))
        (is (not (some #{g} (sdry/unused-glues [{:glue {:ref (:ref g)}}])))))))

  (t/testing "--dry-run avec --doc-html est refusé : la doc skippe les suites, et
  le dry run passait sans avoir rien vérifié"
    (is (thrown? Exception (sdry/scenari-dry-run-pre-load-hook
                            {::sdry/enabled? true ::sdoc/target-file "a.html"})))
    (is (thrown? Exception (sdry/scenari-dry-run-pre-load-hook
                            {::sdry/enabled? true ::sdoc/report-file "a.html"})))
    (is (= {::sdry/enabled? true} (sdry/scenari-dry-run-pre-load-hook {::sdry/enabled? true})))))

(t/deftest scenari-slowest-steps-test
  (t/testing "run-step mesure le glue, qu'il réussisse ou lève"
    (is (nat-int? (:duration-ns (v2/run-step {:glue {:ref (fn [s] [s])}} {}))))
    (is (nat-int? (:duration-ns (v2/run-step {:glue {:ref (fn [_] (throw (ex-info "boom" {})))}} {})))))

  (t/testing "les durées s'additionnent par glue, le plus coûteux en tête ; un
  step jamais lancé ne compte pas"
    (let [fast   {:ref :fast :ns 'n :name 'fast :step "fast"}
          slow   {:ref :slow :ns 'n :name 'slow :step "slow"}
          result {:kaocha.result/tests
                  [{:kaocha.result/tests
                    [{::testable/type :kaocha.type/scenari-feature
                      :kaocha.result/tests
                      [{:steps [{:glue fast :duration-ns 1000000}
                                {:glue slow :duration-ns 5000000}
                                {:glue fast :duration-ns 3000000}]}
                       {:steps [{:glue slow :duration-ns 2000000}
                                {:glue fast :status :pending}]}]}]}]}
          glues  (sslow/slowest-glues result 10)]
      (is (= [[:slow 2 7000000 5000000] [:fast 2 4000000 3000000]]
             (map (juxt (comp :ref :glue) :calls :total-ns :max-ns) glues)))
      (is (= 1 (count (sslow/slowest-glues result 1))))
      (is (string/includes? (sslow/report glues) "7.0 ms")))))

(t/deftest alternation-var-name-test
  (t/testing "l'alternance met une barre oblique dans le nom du var, ce qui en
  faisait un symbole qualifié : `defn` refusait le glue au chargement"
    (let [v (v2/defthen "le cafe est chaud/froid" [state] state)]
      (is (var? v))
      (is (= 'le-cafe-est-chaud-froid (:name (meta v))))
      (is (= "le cafe est chaud/froid" (:step (meta v)))
          "seul le nom du var est nettoyé, la phrase garde son alternance"))))
