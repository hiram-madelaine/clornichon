(ns kaocha.plugin.scenari-dry-run
  "`--dry-run`: checks that every step of the selected scenarios has a glue,
  without running anything.

  The glue is resolved at parse time (`scenari.v2.core/pickle-step->map` sets
  `:glue nil` when nothing matches), so everything is already in the test-plan:
  walking it is enough. Without this an undefined step only blows up when run,
  on an `(apply nil ...)`, *after* the previous steps and their side effects.

  Non-zero exit when a glue is missing, with the skeleton to paste for each.
  Like `scenari-doc`, list it after `:kaocha.plugin/scenari-tags` to check only
  what would have run - the walk is scenari-doc's, both plugins read the same
  filtered tree. It cannot be combined with `--doc-html` / `--doc-report`."
  (:require [clojure.string :as str]
            [kaocha.output :as output]
            [kaocha.plugin :refer [defplugin]]
            [kaocha.plugin.scenari-doc :as doc]
            [kaocha.testable :as testable]
            [scenari.v2.glue :as glue]))

(defn undefined-steps
  "`[feature scenario step]` for every step without a glue, in tree order."
  [test-plan]
  (for [feature  (doc/selected-features test-plan)
        scenario (::doc/scenarios feature)
        step     (:steps scenario)
        :when    (nil? (:glue step))]
    [(::testable/desc feature) (::testable/desc scenario) step]))

(defn report
  "The missing steps grouped by scenario. The skeleton to paste is already
  printed by `find-glue-by-step-regex` at parse time (`:missing-step` event) -
  what that lacks is *where* the step is used."
  [missing]
  (str/join
   "\n"
   (for [group (partition-by (juxt first second) missing)
         :let  [[feature scenario _] (first group)]
         line  (cons (str "  " feature " > " scenario)
                     (map (fn [[_ _ step]] (str "    " (:raw step))) group))]
     line)))

(defn unused-glues
  "The other way round: the step definitions none of `steps` uses. Indicative
  only - a filter (`--tags`, `--focus`) shrinks the selection, so grows the list,
  and `all-glues` also sees the glues of the loaded test namespaces."
  [steps]
  (let [used (set (keep #(get-in % [:glue :ref]) steps))]
    (->> (glue/all-glues)
         (remove (comp used :ref))
         (sort-by (juxt (comp str :ns) (comp str :name))))))

(defn unused-report [glues]
  (str/join "\n" (for [{:keys [ns name step]} glues]
                   (str "  " ns "/" name "  \"" step "\""))))

(defplugin kaocha.plugin/scenari-dry-run
  "Checks that every step has a glue, without running the scenarios."

  (cli-options [opts]
               (-> opts
                   (conj [nil "--dry-run" "Check that every selected step resolves a step definition, without running anything."])
                   (conj [nil "--unused-glues" "With --dry-run, also list the step definitions no selected step uses."])))

  (config [config]
          (let [{:keys [dry-run unused-glues]} (:kaocha/cli-options config)]
            (cond-> config
              dry-run      (assoc ::enabled? true)
              unused-glues (assoc ::unused? true))))

  (pre-load [config]
            ;; chacun marque les suites skippées : le second lirait un arbre vide
            ;; et le dry run passerait sans avoir rien vérifié
            (when (and (::enabled? config)
                       (or (::doc/target-file config) (::doc/report-file config)))
              (output/error-and-throw {:kaocha/early-exit 248} nil
                                      "--dry-run cannot be combined with --doc-html or --doc-report."))
            config)

  (post-load [test-plan]
             (if (::enabled? test-plan)
               (let [features  (doc/selected-features test-plan)
                     scenarios (mapcat ::doc/scenarios features)
                     steps     (mapcat :steps scenarios)
                     missing   (undefined-steps test-plan)
                     unused    (unused-glues steps)
                     plan      (update test-plan :kaocha.test-plan/tests
                                       (partial mapv #(assoc % ::testable/skip true)))]
                 (println (format "Dry run: %d feature(s), %d scenario(s), %d step(s), %d undefined, %d unused glue(s)."
                                  (count features) (count scenarios) (count steps) (count missing) (count unused)))
                 (when (and (seq unused) (::unused? test-plan))
                   (println)
                   (println "Unused step definitions:")
                   (println (unused-report unused)))
                 (when (seq missing)
                   (println)
                   (println (report missing))
                   (println)
                   ;; le runner sort sur ce code : le dry run échoue en CI
                   (output/error-and-throw {:kaocha/early-exit 1} nil
                                           (count missing) " step(s) without a step definition."))
                 plan)
               test-plan)))
