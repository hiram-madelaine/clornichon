(ns scenari.v2.feature-test
  (:require [clojure.string :as string]
            [clojure.test :as t :refer [deftest testing is]]
            [scenari.v2.core :as v2]
            [scenari.v2.test :as sc-test]
            [kaocha.type.scenari]
            [scenari.v2.some-glue-ns]
            [kaocha.repl :as krepl]
            [kaocha.testable :as testable]
            [testit.core :refer :all]))

(def side-effect-atom (atom 0))
(def scenario-side-effect-atom (atom 0))

(v2/defwhen #"I foo" [state]
  (let [scenario-side-effect @scenario-side-effect-atom
        side-effect-atom @side-effect-atom]
    (fact 1 => scenario-side-effect)
    (fact 1 => side-effect-atom)
    state))

(v2/defgiven "a doc string"  [state doc-string] (is (= "This is markdown" doc-string)) state)

(defn init-side-effect [] (reset! side-effect-atom 1))
(defn pre-scenario-run-side-effect [] (reset! scenario-side-effect-atom 1))
(defn post-scenario-run-side-effect [] (reset! scenario-side-effect-atom 1))

(v2/deffeature my-feature "test/scenari/v2/example.feature"
  {:pre-run           [#'init-side-effect]
   :pre-scenario-run  [#'pre-scenario-run-side-effect]
   :post-scenario-run [#'post-scenario-run-side-effect]
   :post-run          [#'init-side-effect]})

(v2/defthen "My initial state contains foo"  [state] (is (= state {:foo 1})) state)

(v2/deffeature short-feature
  "Feature: feature description
  Scenario: Scenario description
      Then My initial state contains foo"
  {:default-scenario-state {:foo 1}})

;; used by kaocha filtering: `--focus-meta var-tagged` / `--focus-meta annotated`
(v2/deffeature ^:var-tagged tagged-feature
  "@annotated
Feature: tagged feature
  @scenario-annotated
  Scenario: tagged scenario
      Then My initial state contains foo"
  {:default-scenario-state {:foo 1}})

;; used by `--tags`: a feature tag shared by two scenarios, only one of which
;; carries its own tag. `--focus-meta` cannot tell them apart - it drops the
;; focus for the whole subtree as soon as the feature matches.
(v2/deffeature mixed-tags-feature
  "@shared
Feature: mixed tags
  @picked
  Scenario: picked scenario
      Then My initial state contains foo
  Scenario: plain scenario
      Then My initial state contains foo"
  {:default-scenario-state {:foo 1}})

(defn- loaded-features
  "Every feature testable kaocha builds, as ./test.sh would load them."
  []
  (:kaocha.test-plan/tests
   (testable/load {:kaocha.testable/type           :kaocha.type/scenari
                   :kaocha.testable/id             :scenario
                   :kaocha/source-paths            ["src"]
                   :kaocha/test-paths              ["test/scenari/v2"]
                   :kaocha.type.scenari/glue-paths ["test/scenari/v2"]})))

(defn- loaded-feature
  "The feature testable kaocha builds for `id`, as ./test.sh would load it."
  [id]
  (first (filter #(= id (:kaocha.testable/id %)) (loaded-features))))

(v2/deffeature outline-feature
  "Feature: one scenario per Examples row
  Scenario Outline: constant name
      Then My initial state contains foo
  Examples:
  | x |
  | 1 |
  | 2 |"
  {:default-scenario-state {:foo 1}})

(deftest outline-scenario-ids-test
  (testing "each Examples row must stay addressable: kaocha keys a leaf by its
  id, and a scenario name with no <placeholder> gives every row the same one"
    (is (= [:scenari.v2.feature-test.outline-feature/constant-name
            :scenari.v2.feature-test.outline-feature/constant-name-2]
           (map :kaocha.testable/id
                (:kaocha.test-plan/tests (loaded-feature ::outline-feature))))))
  (testing "the bare-name alias is not numbered: a scenario name names the whole
  Examples table, so --focus constant-name runs both rows"
    (is (= [[:constant-name] [:constant-name]]
           (map :kaocha.testable/aliases
                (:kaocha.test-plan/tests (loaded-feature ::outline-feature))))))
  (testing "a scenario id is unique across features: kaocha attaches the run's
  events to a testable by id equality, so a same-named scenario in another
  feature would show its failures too"
    (let [ids (map :kaocha.testable/id
                   (mapcat :kaocha.test-plan/tests (loaded-features)))]
      (is (apply distinct? ids)))))

(def post-run-atom (atom 0))
(defn post-run-side-effect [] (swap! post-run-atom inc))

(v2/deffeature post-run-feature
  "Feature: teardown at the feature level
  Scenario: Scenario description
      Then My initial state contains foo"
  {:default-scenario-state {:foo 1}
   :post-run               [#'post-run-side-effect]})

(deftest post-run-test
  (testing ":post-run runs once the feature is over, in all three runners"
    (reset! post-run-atom 0)
    (v2/run-features #'scenari.v2.feature-test/post-run-feature)
    (is (= 1 @post-run-atom) "core/run-features must be eager, not a lazy map")
    (sc-test/run-features #'scenari.v2.feature-test/post-run-feature)
    (is (= 2 @post-run-atom))
    ;; the kaocha suite type is the runner ./test.sh uses, and it used to load
    ;; the feature without its :post-run at all
    (let [feature (loaded-feature ::post-run-feature)]
      (is (seq (:kaocha.type.scenari/post-run feature))
          "-load must carry the feature's :post-run onto the testable")
      (testable/-run feature {})
      (is (= 3 @post-run-atom)))))

(v2/defwhen "the step blows up" [_] (throw (ex-info "boom" {})))

(deftest throwing-step-reports-a-kaocha-failure-test
  (testing "a step that throws must emit an event kaocha reads as a failure:
  junit-xml only renders :kaocha/fail-type events, so the scenario used to come
  out green in CI, and fail-summary never mentioned it"
    (let [events   (atom [])
          scenario (-> (v2/->feature-ast "Feature: f\n  Scenario: s\n    When the step blows up" {} *ns*)
                       :scenarios
                       first
                       (merge {:kaocha.testable/type :kaocha.type/scenari-scenario
                               :kaocha.testable/id   ::throwing-scenario}))]
      (binding [t/report (fn [m] (swap! events conj m))]
        (testable/-run scenario {}))
      (is (= 1 (count (filter #(and (= :fail (:type %)) (instance? Throwable (:actual %)))
                              @events)))))))

(deftest undefined-step-names-the-step-test
  (testing "a step without glue fails with its sentence and a skeleton, not the
  NPE `(apply nil ...)` used to raise"
    (let [step (binding [t/report (constantly nil)] ; le :missing-step du parsing
                 (-> (v2/->feature-ast "Feature: f\n  Scenario: s\n    When nobody wrote this step" {} *ns*)
                     :scenarios first v2/run-scenario :steps first))
          msg  (ex-message (:exception step))]
      (is (= :fail (:status step)))
      (is (string/includes? msg "Undefined step: When nobody wrote this step"))
      (is (string/includes? msg "(defwhen \"nobody wrote this step\"")))))

(v2/defthen "the assertion is the last form" [state] (is (= {:foo 1} state)))
(v2/defthen "the side effect is the last form" [state] (run! identity [1 2]))

(deftest state-survives-a-non-state-return-test
  (testing "un step qui finit par une assertion ou un effet de bord rend
  true/false/nil : l'etat d'entree est garde, sinon le step suivant recoit ce
  booleen et l'oubli du `state` final ne se voit qu'au step d'apres"
    (let [steps (-> (v2/->feature-ast
                     (str "Feature: f\n  Scenario: s\n"
                          "    Then the assertion is the last form\n"
                          "    Then the side effect is the last form\n"
                          "    Then My initial state contains foo")
                     {:default-scenario-state {:foo 1}} *ns*)
                    :scenarios first v2/run-scenario :steps)]
      (is (= [{:foo 1} {:foo 1} {:foo 1}] (map :output-state steps)))
      (is (= [:success :success :success] (map :status steps))))))

(deftest run-hooks-test
  (testing "the teardown runs even when the body throws - a report, an ambiguous
  glue or a pre-run hook can throw past run-step's catch, and that is the very
  case :post-run exists for"
    (let [journal (atom [])
          hook    (fn [k] {:ref #(swap! journal conj k)})]
      (is (thrown? Exception
                   (v2/run-hooks {:pre-run [(hook :pre)] :post-run [(hook :post)]}
                                 #(throw (ex-info "boom" {})))))
      (is (= [:pre :post] @journal))
      (reset! journal [])
      (is (thrown? Exception
                   (v2/run-hooks {:pre-run  [{:ref #(throw (ex-info "boom" {}))}]
                                  :post-run [(hook :post)]}
                                 (constantly nil))))
      (is (= [:post] @journal) "a throwing pre-run must not skip the teardown"))))

(def hook-journal (atom []))
(defn hook-with-ctx [ctx] (swap! hook-journal conj ctx))
(defn hook-without-ctx [] (swap! hook-journal conj :no-ctx))
(defn hook-with-both-arities
  ([] (swap! hook-journal conj :zero-arity))
  ([perimetres] (swap! hook-journal conj perimetres)))
(defn ^{:scenari/tags "@db and not @slow"} db-hook [] (swap! hook-journal conj :db))

(def hooks-source
  (str "@f\nFeature: hooked\n"
       "  @db\n  Scenario: green\n    Then My initial state contains foo\n"
       "  @db @slow\n  Scenario: red\n    When the step blows up\n"))

(defn- run-hooked [options]
  (reset! hook-journal [])
  (run! v2/run-scenario (:scenarios (v2/->feature-ast hooks-source options *ns*)))
  @hook-journal)

(deftest hooks-context-test
  (testing "a hook with one argument receives the scenario's name and tags, and
  its status after it; a hook without argument is called as before"
    (is (= [{:scenario-name "green" :annotations #{"f" "db"}}
            {:scenario-name "green" :annotations #{"f" "db"} :status :success}
            :no-ctx
            {:scenario-name "red" :annotations #{"f" "db" "slow"}}
            {:scenario-name "red" :annotations #{"f" "db" "slow"} :status :fail}
            :no-ctx]
           (run-hooked {:default-scenario-state {:foo 1}
                        :pre-scenario-run       [#'hook-with-ctx]
                        :post-scenario-run      [#'hook-with-ctx #'hook-without-ctx]}))))

  (testing "a hook that also has a zero arity keeps being called without
  argument: its one-argument arity expects something else than the context"
    (is (= [:zero-arity :zero-arity]
           (run-hooked {:default-scenario-state {:foo 1}
                        :post-scenario-run      [#'hook-with-both-arities]}))))

  (testing "a hook tagged :scenari/tags only runs where the scenario's tags match"
    (is (= [:db] (run-hooked {:default-scenario-state {:foo 1}
                              :post-scenario-run      [#'db-hook]}))))

  (testing "an invalid tag expression names the hook, at load time"
    (let [bad (with-meta (fn []) {:scenari/tags "@db and"})]
      (is (thrown-with-msg? Exception #"hook"
                            (v2/->feature-ast hooks-source {:post-scenario-run [bad]} *ns*)))))

  (testing "a feature hook receives the feature's name and tags, in kaocha too"
    (reset! hook-journal [])
    (v2/run-hooks {:feature "hooked" :annotations #{"f"} :pre-run [{:ref hook-with-ctx :arglists '([ctx])}]}
                  (constantly nil))
    (is (= [{:feature "hooked" :annotations #{"f"}}] @hook-journal))))

(deftest scenari-runner-test
  (testing "Using scenari runner"
    (testing "execute success feature"
      (let [[feature-result] (v2/run-features #'scenari.v2.feature-test/short-feature)]
        (fact "return an execution tree with status :success"
              feature-result =in=> {:feature   "feature description",
                                    :scenarios [{:pre-run       [],
                                                 :post-run      [],
                                                 :default-state {:foo 1},
                                                 :scenario-name "Scenario description",
                                                 :steps         [{:sentence-keyword :then,
                                                                  :input-state      {:foo 1},
                                                                  :raw              "Then My initial state contains foo",
                                                                  :sentence         "My initial state contains foo",
                                                                  :params           [],
                                                                  :output-state     {:foo 1},
                                                                  :status           :success,
                                                                  :order            0}],
                                                 :status        :success}],
                                    :pre-run   [],
                                    :status    :success})))))

(comment
  (remove-ns 'scenari.v2.feature-test)
  (meta #'scenari.v2.feature-test/my-feature)
  (v2/run-features)
  (v2/run-features #'scenari.v2.feature-test/my-feature)
  (sc-test/run-features #'scenari.v2.feature-test/my-feature)
  (krepl/test-plan)
  (krepl/run-all)
  (krepl/run :scenario))