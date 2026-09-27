(ns kaocha.plugin.scenari-tags
  "`--tags \"@a and not @b\"`: filtering on a Cucumber tag expression.

  kaocha can only express an OR (`--focus-meta`/`--skip-meta` hand their list to
  a `some`), and its focus is dropped for the whole subtree as soon as a node
  matches - a tagged feature therefore runs all of its scenarios. Here the
  expression is evaluated scenario by scenario, as Cucumber evaluates it pickle
  by pickle.

  Only scenari suites are concerned: `--tags` leaves a clojure.test suite
  running. To run the features only, combine it with `--focus`."
  (:require [kaocha.output :as output]
            [kaocha.plugin :refer [defplugin]]
            [kaocha.testable :as testable])
  (:import [io.cucumber.tagexpressions Expression TagExpressionParser]))

(defn- scenario? [t] (= :kaocha.type/scenari-scenario (::testable/type t)))

(defn- scenari? [t]
  (contains? #{:kaocha.type/scenari-feature :kaocha.type/scenari-rule :kaocha.type/scenari-scenario}
             (::testable/type t)))

(defn- matches? [^Expression expr testable]
  ;; les tags sont stockés sans le @ (scenari.v2.core/tag-names), l'expression
  ;; le veut - `evaluate ["smoke"]` sur `@smoke` rend false
  (.evaluate expr (mapv #(str "@" %) (:annotations testable))))

(defn filter-testable
  "Marks `::testable/skip` the scenarios whose tags do not satisfy `expr`, then
  every scenari node left with skipped children only - otherwise the reporter
  announces an empty feature."
  [expr testable]
  (if-let [tests (:kaocha.test-plan/tests testable)]
    (let [tests (mapv #(filter-testable expr %) tests)]
      (cond-> (assoc testable :kaocha.test-plan/tests tests)
        (and (seq tests) (every? scenari? tests) (every? ::testable/skip tests))
        (assoc ::testable/skip true)))
    (cond-> testable
      (and (scenario? testable) (not (matches? expr testable)))
      (assoc ::testable/skip true))))

(defplugin kaocha.plugin/scenari-tags
  "Filters the scenari scenarios on a cucumber tag expression."

  (cli-options [opts]
               (conj opts
                     [nil "--tags EXPR" (str "Only run scenari scenarios whose gherkin tags match this "
                                             "cucumber tag expression, e.g. \"@smoke and not @wip\".")]))

  (config [config]
          (cond-> config
            (:tags (:kaocha/cli-options config))
            (assoc ::expression (:tags (:kaocha/cli-options config)))))

  (post-load [test-plan]
             ;; ::expression est une clé de config ordinaire : `:kaocha.plugin.scenari-tags/expression`
             ;; dans tests.edn marche aussi, ce qui la rend utilisable depuis kaocha.repl
             (if-let [s (::expression test-plan)]
               (let [expr (try (TagExpressionParser/parse s)
                               (catch Exception e
                                 (output/error-and-throw {:kaocha/early-exit 248} nil (.getMessage e))))
                     plan (update test-plan :kaocha.test-plan/tests
                                  (partial mapv #(filter-testable expr %)))]
                 (when-not (some #(and (scenario? %) (not (::testable/skip %)))
                                 (testable/test-seq plan))
                   (output/warn "--tags " s " did not match any scenario."))
                 plan)
               test-plan)))
