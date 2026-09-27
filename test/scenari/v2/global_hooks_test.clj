(ns scenari.v2.global-hooks-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [kaocha.result]
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

;; ------------------------
;;    A HOOK THAT THROWS
;; ------------------------

(v2/deffeature two-scenarios-feature
  "Feature: two scenarios
  Scenario: first
      Then My initial state contains foo
  Scenario: second
      Then My initial state contains foo"
  {:default-scenario-state {:foo 1}})

(defn- boom
  "A hook that records k, then throws - only where `throws?` holds for its
  context, if given."
  ([k] (boom k (constantly true)))
  ([k throws?]
   (fn [& [ctx]]
     (swap! calls conj k)
     (when (throws? ctx)
       (throw (ex-info (str "boom " (name k)) {}))))))

(def ^:private first? #(= "first" (:scenario-name %)))

(defn- hook [sym k m f]
  [sym (merge {:scenari/hook k :arglists '([ctx])} m) f])

(defn- first-scenario []
  (-> #'two-scenarios-feature meta :scenari/feature-ast :scenarios first))

(deftest hook-that-throws-test
  (testing "a before-scenario that throws fails its scenario, whose steps stay
  pending; the after hooks run, and so does the next scenario"
    (with-global-hooks [(hook 'before :before-scenario {} (boom :before first?))
                        (hook 'after :after-scenario {} (record :after))]
      #(let [[{:keys [status scenarios]}] (v2/run-features #'two-scenarios-feature)]
         (is (= :fail status))
         (is (= [:fail :success] (map :status scenarios)))
         (is (= [[:pending] [:success]] (map (fn [s] (map :status (:steps s))) scenarios)))
         (is (= ["boom before" nil] (map (comp ex-message :exception) scenarios)))
         (is (= [:before [:after :fail] :before [:after :success]]
                (map (fn [x] (if (vector? x) [(first x) (:status (second x))] x)) @calls))))))

  (testing "an after hook that throws fails a scenario whose steps passed, and
  does not skip the after hooks that follow"
    (with-global-hooks [(hook 'after-1 :after-scenario {:line 1} (boom :after-1))
                        (hook 'after-2 :after-scenario {:line 2} (boom :after-2 (constantly false)))]
      #(let [scenario (v2/run-scenario (first-scenario))]
         (is (= :fail (:status scenario)))
         (is (= [:success] (map :status (:steps scenario))))
         (is (= "boom after-1" (ex-message (:exception scenario))))
         (is (= [:after-1 :after-2] @calls)))))

  (testing "the first exception comes up, and carries the others: the cause is
  not lost to what the teardown throws after it"
    (with-global-hooks [(hook 'before :before-scenario {} (boom :before))
                        (hook 'after :after-scenario {} (boom :after))]
      #(let [e (:exception (v2/run-scenario (first-scenario)))]
         (is (= "boom before" (ex-message e)))
         (is (= ["boom after"] (map ex-message (.getSuppressed ^Throwable e)))))))

  (testing "the same exception twice - a failed delay throws its one instance
  again - is not a self-suppression"
    (let [e (ex-info "same" {})]
      (with-global-hooks [(hook 'before :before-scenario {} (fn [_] (throw e)))
                          (hook 'after :after-scenario {} (fn [_] (throw e)))]
        #(is (identical? e (:exception (v2/run-scenario (first-scenario))))))))

  (testing "a before-feature that throws fails the scenarios of its feature,
  without running them, and the next feature runs"
    (with-global-hooks [(hook 'before :before-feature {} (boom :before (fn [ctx] (= "two scenarios" (:feature ctx)))))
                        (hook 'before-scenario :before-scenario {} (fn [_] (swap! calls conj :scenario)))]
      #(let [[failed passed] (v2/run-features #'two-scenarios-feature #'global-hooks-feature)]
         (is (= [:fail :success] (map :status [failed passed])))
         (is (= [:fail :fail] (map :status (:scenarios failed))))
         (is (= "boom before" (ex-message (:exception failed))))
         ;; one before-feature per feature, and the one scenario that ran
         (is (= [:before :before :scenario] (filter #{:before :scenario} @calls))))))

  (testing "run-hooks still throws what a hook threw, for who calls it"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"boom after"
                          (v2/run-hooks {:post-run [{:ref (boom :after)}]} (constantly :ran))))))

(deftest hook-that-throws-in-clojure-test-runner-test
  (testing "the clojure.test runner reports the scenario failed, its steps
  pending, and runs the next one"
    (with-global-hooks [(hook 'before :before-scenario {} (boom :before first?))]
      #(let [events (atom [])]
         (binding [t/report (fn [m] (swap! events conj m))]
           (sc-test/run-features #'two-scenarios-feature))
         (is (= [:hook-failed :scenario-failed :scenario-succeed]
                (filter #{:hook-failed :scenario-failed :scenario-succeed} (map :type @events))))
         (is (= [:pending :success]
                (map (comp :status :step) (filter (comp #{:begin-step} :type) @events))))))))

(defn- run-kaocha
  "The scenari suite narrowed to these features, run by kaocha without a word:
  [result events]."
  [& feature-ids]
  (let [suite  (-> (testable/load {:kaocha.testable/type           :kaocha.type/scenari
                                   :kaocha.testable/id             :scenario
                                   :kaocha/source-paths            ["src"]
                                   :kaocha/test-paths              ["test/scenari/v2"]
                                   :kaocha.type.scenari/glue-paths ["test/scenari/v2"]})
                   (update :kaocha.test-plan/tests
                           (partial filterv (comp (set feature-ids) :kaocha.testable/id))))
        events (atom [])]
    (binding [t/report (fn [m] (swap! events conj m))]
      [(testable/-run suite {}) @events])))

(defn- failures
  "What kaocha counts: the leaves of the result, each with its failures."
  [result]
  (->> (tree-seq :kaocha.result/tests :kaocha.result/tests result)
       (remove :kaocha.result/tests)
       (map (juxt (comp name :kaocha.testable/id) :kaocha.result/fail))))

(defn- hook-failures [events]
  (->> events
       (filter #(and (= :fail (:type %)) (:kaocha.result/exception %)))
       (map (comp ex-message :actual))))

(deftest hook-that-throws-in-kaocha-test
  (testing "a before-scenario that throws is one failed scenario, not the end
  of the run: the next scenario runs, and the failure is one kaocha reads"
    (with-global-hooks [(hook 'before :before-scenario {} (boom :before first?))]
      #(let [[result events] (run-kaocha ::two-scenarios-feature)]
         (is (= [["first" 1] ["second" 0]] (failures result)))
         (is (= ["boom before"] (hook-failures events))))))

  (testing "a before-feature that throws fails each scenario of its feature,
  and the next feature runs"
    (with-global-hooks [(hook 'before :before-feature {} (boom :before (fn [ctx] (= "two scenarios" (:feature ctx)))))]
      #(let [[result events] (run-kaocha ::two-scenarios-feature ::global-hooks-feature)]
         (is (= #{["first" 1] ["second" 1] ["s" 0]} (set (failures result))))
         (is (= ["boom before" "boom before"] (hook-failures events))))))

  (testing "an after-feature that throws fails the run and keeps the results of
  the scenarios, which passed"
    (with-global-hooks [(hook 'after :after-feature {} (boom :after))]
      #(let [[result events] (run-kaocha ::two-scenarios-feature)]
         (is (= [["first" 0] ["second" 0] ["after-feature" 1]] (failures result)))
         (is (= ["boom after"] (hook-failures events))))))

  (testing "an after-all that throws fails the run and keeps its results"
    (with-global-hooks [(hook 'stop :after-all {} (boom :after-all))]
      #(let [[result events] (run-kaocha ::two-scenarios-feature)]
         (is (= [["first" 0] ["second" 0] ["after-all" 1]] (failures result)))
         (is (kaocha.result/failed? result))
         (is (= ["boom after-all"] (hook-failures events))))))

  (testing "a before-all that throws still stops the run"
    (with-global-hooks [(hook 'start :before-all {} (boom :before-all))]
      #(is (thrown-with-msg? clojure.lang.ExceptionInfo #"boom before-all"
                             (run-kaocha ::two-scenarios-feature))))))
