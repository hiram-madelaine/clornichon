(ns kaocha.type.scenari
  (:require [clojure.string :as string]
            [clojure.test :as t]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.tools.namespace.find :as ns-find]
            [kaocha.testable :as testable]
            [kaocha.hierarchy :as hierarchy]
            [kaocha.repl :as krepl]
            [scenari.v2.core :as v2]
            [scenari.v2.core :as sc]
            [scenari.v2.test]))

(s/def :kaocha.type/scenari (s/keys :req [:kaocha/source-paths
                                          :kaocha/test-paths]))

(defn path->file "Looking path from resource or a file in file system" [path]
  (or (io/file (io/resource path))
      (io/file path)))

(defn find-features-meta-in-dir [path]
  (->> path
       path->file
       ns-find/find-namespaces-in-dir
       (map #(ns-publics (symbol %)))
       (mapcat #(map meta (vals %)))
       (filter #(:scenari/raw-feature %))))

(defn ->id [s]
  (-> s
      str/trim
      (str/replace #"/" ".")
      (str/replace #"_" "-")
      (str/replace #" " "-")))

(defn- name->id [s]
  (-> s
      str/trim
      (str/replace #" " "-")))

(defn scenario->id [scenario]
  (name->id (:scenario-name scenario)))

(defn scenario->testable [feature-id scenario]
  (let [id (scenario->id scenario)]
    (merge scenario
           {::testable/type    :kaocha.type/scenari-scenario
            ;; qualified by its feature: kaocha attaches the run's events to a
            ;; testable by id equality, so two features with a same-named
            ;; scenario would otherwise share each other's failures - and junit
            ;; reports `classname` as the id's namespace
            ::testable/id      (keyword (str (namespace feature-id) "." (name feature-id)) id)
            ;; keeps `--focus <scenario-name>` working. `with-unique-ids` numbers
            ;; a repeated id but leaves this alias bare, so every row of an
            ;; Examples table answers to the scenario's name - see its docstring.
            ::testable/aliases [(keyword id)]
            ;; gherkin @annotations of the scenario, so `--focus-meta`/`--skip-meta`
            ;; also work one level below the feature
            ::testable/meta    (zipmap (map keyword (:annotations scenario)) (repeat true))
            ::testable/desc    (or (:scenario-name scenario) "")})))

(defn- with-unique-ids
  "Kaocha addresses a leaf by its id, and every row of an Examples table gives a
  scenario with the same name as soon as that name carries no <placeholder> -
  the common case. Number the repeats, so --focus can reach a single row and the
  report does not show N indistinguishable leaves.

  Only the id is numbered, not the bare-name alias `scenario->testable` sets:
  `--focus constant-name` runs *every* row of that Examples table, while
  `--focus :the.ns.the-feature/constant-name-2` runs the second one alone. Which
  is the useful pair - a scenario name names the whole table. Careful with
  repeated --focus though, they are OR-ed into one list, and a testable that
  matches any of them drops the filter for its whole subtree
  (kaocha.plugin.filter/filter-testable): `--focus :scenario --focus
  constant-name` matches the suite id and runs all of it, not one scenario."
  [testables]
  (second (reduce (fn [[seen acc] testable]
                    (let [id (::testable/id testable)
                          n  (inc (get seen id 0))]
                      [(assoc seen id n)
                       (conj acc (cond-> testable
                                   (> n 1) (assoc ::testable/id
                                                  (keyword (namespace id) (str (name id) "-" n)))))]))
                  [{} []]
                  testables)))

(defn- rule->testable
  "A `Rule` as a group between its feature and its scenarios. The scenarios keep
  the ids they had without it, so an existing `--focus` still reaches them; the
  rule answers to `--focus <rule-name>` through its alias."
  [feature-id rule scenarios]
  (let [id (name->id (:name rule))]
    {::testable/type         :kaocha.type/scenari-rule
     ::testable/id           (keyword (str (namespace feature-id) "." (name feature-id) ".rule") id)
     ::testable/aliases      [(keyword id)]
     ::testable/meta         (zipmap (map keyword (:annotations rule)) (repeat true))
     ::testable/desc         (:name rule)
     :kaocha.test-plan/tests (vec scenarios)
     :rule                   rule}))

(defn- group-rules
  "The scenarios of a feature, those of a `Rule` gathered under it. Gherkin puts
  the rules after the feature's own scenarios, and pickles come in file order."
  [feature-id scenarios]
  (with-unique-ids
    (mapcat (fn [group]
              (if-let [rule (:rule (first group))]
                [(rule->testable feature-id rule group)]
                group))
            (partition-by (comp :id :rule) scenarios))))

(defn- require-all-ns [paths]
  (->> paths
       (map path->file)
       (mapcat ns-find/find-namespaces-in-dir)
       (apply require)))

(defmethod testable/-load :kaocha.type/scenari [testable]
  (require-all-ns (::glue-paths testable))
  (let [tests (for [test-path (:kaocha/test-paths testable)
                    {{:keys [feature scenarios pre-run post-run annotations description messages]} :scenari/feature-ast
                     :as                                             feature-meta} (find-features-meta-in-dir test-path)
                    :let [feature-id (keyword (str (:ns feature-meta)) (str (:name feature-meta)))]]
                {::testable/type         :kaocha.type/scenari-feature
                 ::testable/id           feature-id
                 ;; allows `--focus <ns>` to select every feature of a namespace
                 ::testable/aliases     [(keyword (str (:ns feature-meta)))]
                 ;; var metadata (deffeature ^:tag ...) + gherkin @annotations,
                 ;; so `--focus-meta`/`--skip-meta` work on features
                 ::testable/meta         (merge (dissoc feature-meta :scenari/raw-feature :scenari/feature-ast :test)
                                                (zipmap (map keyword annotations) (repeat true)))
                 ::testable/desc         feature
                 ;; numbered before grouping: a scenario id stays what it was
                 ;; before rules had a level of their own
                 :kaocha.test-plan/tests (group-rules feature-id (with-unique-ids (map #(scenario->testable feature-id %) scenarios)))
                 ::annotations           annotations
                 ::description           description
                 ::messages              messages
                 ::pre-run               pre-run
                 ::post-run              post-run})]
    (assoc testable :kaocha.test-plan/tests tests)))

(defmethod testable/-run :kaocha.type/scenari [testable test-plan]
  (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)
        testable (-> testable
                     (dissoc :kaocha.test-plan/tests)
                     (assoc :kaocha.result/tests results))]
    testable))

(defmethod testable/-run :kaocha.type/scenari-feature [testable test-plan]
  (t/do-report {:type        :begin-feature
                :feature     (:kaocha.testable/desc testable)
                :annotations (::annotations testable)
                :description (::description testable)})
  (sc/run-hooks
   {:pre-run     (::pre-run testable)
    :post-run    (::post-run testable)
    :feature     (:kaocha.testable/desc testable)
    :annotations (::annotations testable)}
   (fn []
     (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)
           testable (-> testable
                        (dissoc :kaocha.test-plan/tests)
                        (assoc :kaocha.result/tests results))]
       (t/do-report {:type :end-feature})
       testable))))

(defmethod testable/-run :kaocha.type/scenari-rule [testable test-plan]
  (t/do-report {:type :begin-rule :rule (:rule testable)})
  (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)]
    (-> testable
        (dissoc :kaocha.test-plan/tests)
        (assoc :kaocha.result/tests results))))

(defmethod testable/-run :kaocha.type/scenari-scenario [testable test-plan]
  (t/do-report {:type :begin-scenario :scenario testable})
  (let [testable (sc/run-scenario testable)]
    (doseq [step (:steps testable)]
      ;; every step is reported, :pending ones included, so the steps skipped
      ;; after a failure still show up
      (t/do-report {:type :begin-step :step step})
      (when (= :fail (:status step))
        (t/do-report {:type :step-failed :exception (:exception step)})
        ;; a step failing on an `is` already emitted its own :fail; a step that
        ;; throws emitted nothing kaocha knows how to read - junit-xml only
        ;; renders :kaocha/fail-type events, so the scenario stayed green in CI,
        ;; and the end-of-run fail-summary never mentioned it
        (when-let [e (:exception step)]
          (t/do-report {:type     :fail
                        :message  (str "Step threw: " (:raw step))
                        :expected "the step to return"
                        :actual   e}))))
    (-> testable
        (merge {:kaocha.result/count 1
                :kaocha.result/pass  (if (= (:status testable) :success) 1 0)
                :kaocha.result/fail  (if (= (:status testable) :fail) 1 0)}))))

(defmethod testable/-run :kaocha.type/scenari-step [testable test-plan]
  (let [results [(v2/run-step {} testable)]
        testable (-> testable
                     (dissoc :kaocha.test-plan/tests)
                     (assoc :kaocha.result/pass results))]
    testable))

(s/def ::glue-paths (s/coll-of string?))

(s/def :kaocha.type/scenari (s/keys :req [:kaocha/source-paths
                                          :kaocha/test-paths
                                          ::glue-paths]))

(s/def :kaocha.type/scenari-feature any?)
(s/def :kaocha.type/scenari-rule any?)
(s/def :kaocha.type/scenari-scenario any?)
(s/def :kaocha.type/scenari-step any?)

(hierarchy/derive! ::begin-feature :kaocha/begin-group)
(hierarchy/derive! ::end-feature :kaocha/end-group)

(hierarchy/derive! ::begin-scenario :kaocha/begin-test)
(hierarchy/derive! ::end-scenario :kaocha/end-test)

(hierarchy/derive! :kaocha.type/scenari :kaocha.testable.type/suite)
(hierarchy/derive! :kaocha.type/scenari-feature :kaocha.testable.type/group)
(hierarchy/derive! :kaocha.type/scenari-rule :kaocha.testable.type/group)
(hierarchy/derive! :kaocha.type/scenari-scenario :kaocha.testable.type/leaf)

(comment
  (in-ns 'kaocha.type.scenari)
  (krepl/run :scenario)
  (krepl/run :unit)

  (krepl/run {:config-file "tests.edn"})

  (krepl/test-plan)

  (krepl/test-plan {:tests [{:id                           :scenario
                             :type                         :kaocha.type/scenari
                             :kaocha/source-paths          ["src"]
                             :kaocha/test-paths            ["test/scenari/v2"]
                             :scenari.v2.kaocha/glue-paths ["test/scenari/v2"]}]}))
