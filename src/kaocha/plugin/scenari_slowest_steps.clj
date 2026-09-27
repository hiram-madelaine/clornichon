(ns kaocha.plugin.scenari-slowest-steps
  "`--slowest-steps N`: after the run, the N step definitions that took the most
  time, all their calls added up.

  Grouped by glue rather than by step: a suite of 3 000 steps runs 400 glues, and
  a glue that costs 50 ms is spread over every scenario that calls it. The time
  per scenario is kaocha's own profiling plugin's job.

  Each step carries its `:duration-ns`, measured by `scenari.v2.core/run-step`
  around the glue call. The walk is scenari-doc's: it reads the result tree."
  (:require [clojure.string :as str]
            [kaocha.plugin :refer [defplugin]]
            [kaocha.plugin.scenari-doc :as doc]))

(defn slowest-glues
  "`{:glue :calls :total-ns :max-ns}` per glue, the most expensive first. Steps
  without a glue or a duration (never run) are left out."
  [result n]
  (->> (for [feature  (doc/selected-features result)
             scenario (::doc/scenarios feature)
             step     (:steps scenario)
             :when    (and (:glue step) (:duration-ns step))]
         step)
       (group-by #(get-in % [:glue :ref]))
       (map (fn [[_ steps]]
              (let [ds (map :duration-ns steps)]
                {:glue     (:glue (first steps))
                 :calls    (count steps)
                 :total-ns (reduce + ds)
                 :max-ns   (reduce max ds)})))
       (sort-by :total-ns >)
       (take n)))

(defn- ms [ns] (format "%.1f ms" (/ ns 1e6)))

(defn report [glues]
  (str/join "\n" (for [{{:keys [ns name step]} :glue :keys [calls total-ns max-ns]} glues]
                   (format "  %10s  %5d call(s)  max %10s  %s/%s  \"%s\""
                           (ms total-ns) calls (ms max-ns) ns name step))))

(defplugin kaocha.plugin/scenari-slowest-steps
  "Prints the step definitions that took the most time."

  (cli-options [opts]
               (conj opts [nil "--slowest-steps N" "After the run, print the N step definitions that took the most time."
                           :parse-fn #(Integer/parseInt %)]))

  (config [config]
          (if-let [n (get-in config [:kaocha/cli-options :slowest-steps])]
            (assoc config ::count n)
            config))

  (post-run [result]
            ;; ::count est une clé de config ordinaire : tests.edn peut la poser
            (when-let [n (::count result)]
              (when-let [glues (seq (slowest-glues result n))]
                (println)
                (println (str "Top " (count glues) " slowest step definitions (total time over all calls):"))
                (println (report glues))))
            result))
