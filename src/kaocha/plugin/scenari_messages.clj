(ns kaocha.plugin.scenari-messages
  "`--cucumber-messages FILE`: the run as a cucumber-messages NDJSON stream, one
  envelope per line - the format every cucumber tool reads (html-formatter,
  json-formatter, junit-xml-formatter, report services).

  The start of the stream - source, GherkinDocument, pickles - is what the
  gherkin parser emitted when the feature was loaded; the rest is rebuilt from
  the result tree after the run: step definitions, test cases, and one
  started/finished pair per scenario and step, from the timestamps and durations
  `scenari.v2.core` records. Only the scenarios kept by the filters appear."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kaocha.plugin :refer [defplugin]]
            [kaocha.plugin.scenari-doc :as doc]
            [scenari.v2.glue :as glue])
  (:import (io.cucumber.cucumberexpressions CucumberExpression)
           (io.cucumber.messages ProtocolVersion)
           (java.io Writer)
           (java.lang.reflect Method)
           (java.util List Optional)))

;; ------------------------
;;          JSON
;; ------------------------
;; ponytail: écrit à la main plutôt qu'une dépendance - le format n'a besoin que
;; de maps, listes, chaînes, nombres et booléens. Jackson, si un jour il en faut plus.

(defn- write-string [^Writer w ^String s]
  (.write w "\"")
  (doseq [c s]
    (case c
      \" (.write w "\\\"")
      \\ (.write w "\\\\")
      (if (< (int c) 0x20)
        (.write w (format "\\u%04x" (int c)))
        (.write w (int c)))))
  (.write w "\""))

(defn- message-fields
  "Un message java de cucumber - GherkinDocument, Pickle... - par ses getters :
  `getAstNodeIds` devient `astNodeIds`."
  [x]
  (into {}
        (for [^Method m (.getMethods (class x))
              :let  [n (.getName m)]
              :when (and (str/starts-with? n "get") (not= n "getClass") (zero? (.getParameterCount m)))]
          [(str (Character/toLowerCase (.charAt n 3)) (subs n 4)) (.invoke m x (object-array 0))])))

(defn- absent? [v] (or (nil? v) (and (instance? Optional v) (not (.isPresent ^Optional v)))))

(defn write-json [^Writer w x]
  (cond
    (nil? x)                  (.write w "null")
    (string? x)               (write-string w x)
    (keyword? x)              (write-string w (name x))
    (or (number? x) (boolean? x)) (.write w (str x))
    (instance? Optional x)    (write-json w (.orElse ^Optional x nil))
    ;; les enums de cucumber rendent leur valeur de protocole : "Context", "PASSED"
    (instance? Enum x)        (write-string w (str x))
    (map? x)                  (do (.write w "{")
                                  (->> (remove (comp absent? val) x)
                                       (map-indexed (fn [i [k v]]
                                                      (when (pos? i) (.write w ","))
                                                      (write-string w (name k))
                                                      (.write w ":")
                                                      (write-json w v)))
                                       dorun)
                                  (.write w "}"))
    (or (sequential? x) (instance? List x))
    (do (.write w "[")
        (dorun (map-indexed (fn [i v] (when (pos? i) (.write w ",")) (write-json w v)) x))
        (.write w "]"))
    :else                     (write-json w (message-fields x))))

;; ------------------------
;;       ENVELOPES
;; ------------------------

(defn- timestamp [ms] {:seconds (quot ms 1000) :nanos (* 1000000 (rem ms 1000))})

(defn- duration [ns] {:seconds (quot ns 1000000000) :nanos (rem ns 1000000000)})

(defn- glue-id [glue] (str (:ns glue) "/" (:name glue)))

(defn- step-definition [glue]
  {:stepDefinition
   {:id              (glue-id glue)
    :pattern         {:source (str (:step glue))
                      :type   (if (instance? CucumberExpression (or (:expression glue) (glue/step->expression glue)))
                                "CUCUMBER_EXPRESSION"
                                "REGULAR_EXPRESSION")}
    :sourceReference (cond-> {:uri (:file glue)}
                       (:line glue) (assoc :location {:line (:line glue)}))}})

(defn- test-step-id [step] (str "step-" (:id step)))

(defn- test-case [scenario]
  {:testCase
   {:id        (str "case-" (:id scenario))
    :pickleId  (:id scenario)
    :testSteps (for [{:keys [glue sentence] :as step} (:steps scenario)]
                 {:id                     (test-step-id step)
                  :pickleStepId           (:id step)
                  :stepDefinitionIds      (if glue [(glue-id glue)] [])
                  :stepMatchArgumentsLists (if glue
                                             [{:stepMatchArguments (glue/step-match-arguments glue sentence)}]
                                             [])})}})

(defn- step-result [{:keys [status glue exception duration-ns]}]
  (let [status (case status
                 :success "PASSED"
                 :pending "SKIPPED"
                 :fail    (if glue "FAILED" "UNDEFINED"))]
    (cond-> {:status status :duration (duration (or duration-ns 0))}
      exception
      (assoc :message (or (ex-message exception) (str exception))
             :exception {:type (.getName (class exception)) :message (ex-message exception)})
      ;; ponytail: un `is` qui échoue ne lève rien, et son rapport est déjà
      ;; parti au reporter kaocha - le capturer dans run-step si le détail manque
      (and (= "FAILED" status) (not exception))
      (assoc :message "A clojure.test assertion failed"))))

(defn- test-case-events [{:keys [started-at finished-at steps] :as scenario}]
  (let [tcs-id (str "started-" (:id scenario))]
    (concat
     [{:testCaseStarted {:id tcs-id :testCaseId (str "case-" (:id scenario)) :attempt 0
                         :timestamp (timestamp started-at)}}]
     (mapcat (fn [step]
               ;; un step sauté après un échec n'a pas tourné : il est daté de la
               ;; fin du scénario
               (let [start (or (:started-at step) finished-at)]
                 [{:testStepStarted {:testCaseStartedId tcs-id :testStepId (test-step-id step)
                                     :timestamp (timestamp start)}}
                  {:testStepFinished {:testCaseStartedId tcs-id :testStepId (test-step-id step)
                                      :testStepResult (step-result step)
                                      :timestamp (timestamp (+ start (quot (or (:duration-ns step) 0) 1000000)))}}]))
             steps)
     [{:testCaseFinished {:testCaseStartedId tcs-id :willBeRetried false
                          :timestamp (timestamp finished-at)}}])))

(defn envelopes
  "The run's envelopes, in stream order, as data - Java messages from the parser
  and maps for the rest."
  [result started-at finished-at]
  (let [features  (doc/selected-features result)
        scenarios (filter :started-at (mapcat ::doc/scenarios features))
        kept      (set (map :id (mapcat ::doc/scenarios features)))]
    (concat
     [{:meta {:protocolVersion (ProtocolVersion/getVersion)
              :implementation  {:name "clornichon"}
              :runtime         {:name "clojure" :version (clojure-version)}
              :os              {:name (System/getProperty "os.name")}
              :cpu             {:name (System/getProperty "os.arch")}}}]
     (for [feature features
           env     (:kaocha.type.scenari/messages feature)
           :let    [pickle (.orElse (.getPickle env) nil)]
           :when   (or (nil? pickle) (kept (.getId pickle)))]
       env)
     (->> (mapcat :steps scenarios) (keep :glue) (group-by :ref) vals (map (comp step-definition first)))
     [{:testRunStarted {:timestamp (timestamp started-at)}}]
     (map test-case scenarios)
     (mapcat test-case-events scenarios)
     [{:testRunFinished {:success   (every? #(= :success (:status %)) scenarios)
                         :timestamp (timestamp finished-at)}}])))

(defn write! [target envelopes]
  (io/make-parents target)
  (with-open [w (io/writer target)]
    (doseq [env envelopes]
      (write-json w env)
      (.write w "\n"))))

(defplugin kaocha.plugin/scenari-messages
  "Writes the run as a cucumber-messages NDJSON stream."

  (cli-options [opts]
               (conj opts [nil "--cucumber-messages FILE"
                           "After the run, write it to FILE as cucumber-messages NDJSON."]))

  (config [config]
          (if-let [target (get-in config [:kaocha/cli-options :cucumber-messages])]
            (assoc config ::target-file target)
            config))

  (pre-run [test-plan]
           (assoc test-plan ::started-at (System/currentTimeMillis)))

  (post-run [result]
            ;; ::target-file est une clé de config ordinaire : tests.edn peut la poser
            (when-let [target (::target-file result)]
              (write! target (envelopes result (::started-at result) (System/currentTimeMillis)))
              (println "Wrote" target))
            result))
