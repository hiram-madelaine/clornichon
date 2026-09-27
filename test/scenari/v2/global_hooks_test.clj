(ns scenari.v2.global-hooks-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [kaocha.testable :as testable]
            [kaocha.type.scenari]
            [scenari.v2.core :as v2]
            [scenari.v2.test :as sc-test]
            [scenari.v2.feature-test]))

(def calls (atom []))

(defn- record [x] (fn [& [ctx]] (swap! calls conj (if ctx [x ctx] x))))

(defn- with-global-hooks
  "Runs f with `hooks` - [name metadata fn] - interned in a namespace of their
  own, removed afterwards: a global hook would otherwise run for every feature of
  the test suite."
  [hooks f]
  (let [ns (create-ns (gensym "scenari.v2.global-hooks-tmp"))]
    (try (doseq [[sym m hook] hooks]
           (intern ns (with-meta sym m) hook))
         (reset! calls [])
         (f)
         (finally (remove-ns (ns-name ns))))))

(defn local-before-feature [] (swap! calls conj :local-before-feature))
(defn local-after-feature [] (swap! calls conj :local-after-feature))
(defn local-before [] (swap! calls conj :local-before))
(defn local-after [] (swap! calls conj :local-after))

(v2/deffeature global-hooks-feature
  "@global
Feature: global hooks
  Scenario: s
      Then My initial state contains foo"
  {:default-scenario-state {:foo 1}
   :pre-run                [#'local-before-feature]
   :post-run               [#'local-after-feature]
   :pre-scenario-run       [#'local-before]
   :post-scenario-run      [#'local-after]})

(def ^:private scenario-and-feature-hooks
  [['g-before-feature {:scenari/hook :before-feature} (record :g-before-feature)]
   ['g-after-feature {:scenari/hook :after-feature} (record :g-after-feature)]
   ['g-before-scenario {:scenari/hook :before-scenario} (record :g-before-scenario)]
   ['g-after-scenario {:scenari/hook :after-scenario :arglists '([ctx])} (record :g-after-scenario)]])

(def ^:private onion
  [:g-before-feature :local-before-feature
   :g-before-scenario :local-before
   :local-after [:g-after-scenario {:scenario-name "s" :annotations #{"global"} :status :success}]
   :local-after-feature :g-after-feature])

(deftest global-hooks-order-test
  (testing "a global hook runs around every feature or scenario, in onion order
  with the hooks of the deffeature: in before them, out after them"
    (with-global-hooks scenario-and-feature-hooks
      #(do (v2/run-features #'global-hooks-feature)
           (is (= onion @calls))))))

(deftest global-hooks-in-every-runner-test
  (testing "the clojure.test runner runs them too"
    (with-global-hooks scenario-and-feature-hooks
      #(do (binding [t/*test-out* (java.io.StringWriter.)]
             (sc-test/run-features #'global-hooks-feature))
           (is (= onion @calls)))))
  (testing "and so does kaocha"
    (with-global-hooks scenario-and-feature-hooks
      #(let [feature (->> (testable/load {:kaocha.testable/type           :kaocha.type/scenari
                                          :kaocha.testable/id             :scenario
                                          :kaocha/source-paths            ["src"]
                                          :kaocha/test-paths              ["test/scenari/v2"]
                                          :kaocha.type.scenari/glue-paths ["test/scenari/v2"]})
                          :kaocha.test-plan/tests
                          (filter (comp #{::global-hooks-feature} :kaocha.testable/id))
                          first)]
         (binding [t/*test-out* (java.io.StringWriter.)]
           (testable/-run feature {}))
         (is (= onion @calls))))))

(deftest global-hooks-tags-test
  (testing "`:scenari/tags` restricts a global hook as it does a local one"
    (with-global-hooks [['tagged {:scenari/hook :before-scenario :scenari/tags "@global"} (record :tagged)]
                        ['other {:scenari/hook :before-scenario :scenari/tags "@elsewhere"} (record :other)]]
      #(do (v2/run-features #'global-hooks-feature)
           (is (= [:local-before-feature :tagged :local-before :local-after :local-after-feature]
                  @calls))))))

(deftest global-hooks-order-between-them-test
  (testing "global hooks of one level run in the order of their lines"
    (with-global-hooks [['second {:scenari/hook :before-scenario :line 20} (record :second)]
                        ['first {:scenari/hook :before-scenario :line 10} (record :first)]]
      #(do (v2/run-features #'global-hooks-feature)
           (is (= [:first :second] (filter #{:first :second} @calls)))))))

(deftest unknown-hook-key-test
  (testing "a misspelled :scenari/hook throws, naming the hook, instead of never running"
    (with-global-hooks [['typo {:scenari/hook :before-scenarios} (record :typo)]]
      #(is (thrown-with-msg? clojure.lang.ExceptionInfo #"typo.*:before-scenarios is not one of"
                             (v2/global-hooks))))))

(deftest suite-hooks-test
  (testing "before-all and after-all run once around the suite"
    (with-global-hooks [['start {:scenari/hook :before-all} (record :before-all)]
                        ['stop {:scenari/hook :after-all} (record :after-all)]]
      #(do (is (= :ran (v2/run-suite (fn [] (swap! calls conj :suite) :ran))))
           (is (= [:before-all :suite :after-all] @calls)))))
  (testing "a before-all that throws runs no scenario, and after-all still runs"
    (with-global-hooks [['start {:scenari/hook :before-all} (fn [] (throw (ex-info "no database" {})))]
                        ['stop {:scenari/hook :after-all} (record :after-all)]]
      #(do (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no database"
                                 (v2/run-suite (fn [] (swap! calls conj :suite)))))
           (is (= [:after-all] @calls))))))

(deftest global-hooks-looked-up-once-per-run-test
  (testing "a Kaocha run looks the global hooks up once, not per feature or
  scenario: a lookup walks every loaded var, which a large suite would pay per
  scenario. Fails if the binding stops reaching them - a thread pool without
  bound-fn, a runner path around run-suite."
    (let [suite    (testable/load {:kaocha.testable/type           :kaocha.type/scenari
                                   :kaocha.testable/id             :scenario
                                   :kaocha/source-paths            ["src"]
                                   :kaocha/test-paths              ["test/scenari/v2"]
                                   :kaocha.type.scenari/glue-paths ["test/scenari/v2"]})
          ;; two features, five scenarios, one of them in rules
          suite    (update suite :kaocha.test-plan/tests
                           (partial filterv (comp #{::global-hooks-feature
                                                    :scenari.v2.feature-test/rules-feature}
                                                  :kaocha.testable/id)))
          lookups  (atom 0)
          original v2/global-hooks]
      (with-redefs [v2/global-hooks (fn [] (swap! lookups inc) (original))]
        (binding [t/*test-out* (java.io.StringWriter.)]
          (testable/-run suite {})))
      (is (= 1 @lookups)))))
