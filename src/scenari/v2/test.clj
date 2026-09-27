(ns scenari.v2.test
  (:require [clojure.stacktrace :as stacktrace]
            [clojure.string :as string]
            [clojure.test :as t]
            [scenari.v2.step :refer [generate-step-fn]]
            [scenari.v2.core :refer [run-step run-hooks try-hooks with-global-hooks]]
            [scenari.v2.table :as table]
            [scenari.utils :as utils]))

(def ^:dynamic *feature-succeed* nil)

;; ------------------------
;;      GHERKIN RENDERING
;; ------------------------

(def ^:private step-colors {:fail :red :pending :grey})

(defn- scenario-label
  "The grammar's scenario_sentence keeps the space after the colon."
  [scenario]
  (string/trim (str (:scenario-name scenario))))

(defn- tags-str [annotations]
  (when (seq annotations)
    (utils/color-str :cyan (string/join " " (map #(str "@" %) (sort annotations))))))

(defn- description-str [description indent]
  (when-not (string/blank? description)
    (->> (string/split-lines description)
         (map #(utils/color-str :grey (str indent %)))
         (string/join "\n"))))

;; ponytail: display-only heuristic. Les captures réelles sont dans (:params
;; step) - les prendre de là si l'affichage et le glue divergent. Les guillemets
;; simples, que {string} accepte aussi, sont volontairement hors du lot : une
;; phrase française est pleine d'apostrophes.
(def ^:private param-re #"\"[^\"]*\"|\b\d+\b")

(defn- highlight-params
  "Colorize a step sentence in `base`, with its {string}/{number} tokens picked out
  in yellow. Each token restores `base` after itself, since the nested reset would
  otherwise drop the colour for the rest of the sentence."
  [base sentence]
  (utils/color-str base
                   (string/replace sentence param-re
                                   #(str (utils/color-str :yellow %) (utils/ansi-code base)))))

(defn- table-lines
  "Render a datatable param back to its `| col | col |` gherkin form: pipes grey,
  first row cyan like the tags, cells yellow like the params in the sentence.
  Rendered from the cells as written, so a one-row table - a key/value pair -
  prints too."
  [t]
  (let [[header & rows :as cells] (table/cells t)
        ;; max 1 : une colonne dont l'en-tete et toutes les cases sont vides donne une
        ;; largeur 0, et Formatter refuse le drapeau de justification sans largeur (%-0s)
        widths (apply map (fn [& col] (apply max 1 (map count col))) cells)
        pipe   (utils/color-str :grey "|")
        ;; la largeur est calculee sur le texte brut : on colore la case *apres*
        ;; l'avoir padee, sinon les codes ANSI comptent dans le %-Ns
        line   (fn [color vals]
                 (str pipe " "
                      (string/join (str " " pipe " ")
                                   (map #(utils/color-str color (format (str "%-" %1 "s") %2)) widths vals))
                      " " pipe))]
    (cons (line :cyan header)
          (map #(line :yellow %) rows))))

(defn- param-lines
  "The block params of a step - docstring and datatable - as the lines to print
  under its sentence. Value params are already visible in the sentence itself."
  [params]
  (mapcat (fn [{:keys [type val]}]
            (case type
              :doc-string (map #(utils/color-str :grey %)
                               (concat ["\"\"\""] (string/split-lines val) ["\"\"\""]))
              :table (table-lines val)
              nil))
          params))

;; ------------------------
;;         REPORTING
;; ------------------------

(defmethod t/report :begin-feature [{:keys [feature annotations description]}]
  (t/with-test-out
    (t/inc-report-counter :executed-features)
    (println)
    (println (utils/color-str :grey "________________________"))
    (when-let [tags (tags-str annotations)]
      (println tags))
    (println (utils/color-str [:bold :white] "Feature : " feature))
    (when-let [desc (description-str description "  ")]
      (println desc))
    (println)))

(defmethod t/report :feature-succeed [_] (t/inc-report-counter :feature-succeed))

(defmethod t/report :end-feature [{:keys [succeed?]}]
  (if succeed? (t/inc-report-counter :feature-succeed) (t/inc-report-counter :feature-failed))
  (t/with-test-out
    (println (utils/color-str :grey "________________________"))
    (println)))

(defmethod t/report :begin-rule [{{:keys [name annotations description]} :rule}]
  (t/with-test-out
    (when-let [tags (tags-str annotations)]
      (println tags))
    (println (utils/color-str [:bold :white] "Rule : " name))
    (when-let [desc (description-str description "  ")]
      (println desc))
    (println)))

(defmethod t/report :end-rule [{{:keys [name]} :rule}]
  (t/with-test-out
    (println (utils/color-str :grey "End of rule : " name))
    (println)))

(defmethod t/report :begin-scenario [{:keys [scenario]}]
  (t/with-test-out
    (t/inc-report-counter :test)
    (t/inc-report-counter :executed-scenarios)
    (when-let [tags (tags-str (:annotations scenario))]
      (println tags))
    (println (str (utils/color-str :grey "Testing scenario : ")
                  (utils/color-str [:bold :white] (scenario-label scenario))))
    (when-let [desc (description-str (:description scenario) "  ")]
      (println desc))))

(defmethod t/report :begin-step [{:keys [step]}]
  (t/with-test-out
    (let [{:keys [sentence-keyword sentence params status]
           {glue-warning :warning glue-pattern :step glue-ns :ns} :glue} step
          base (get step-colors status :green)]
      (when (some? glue-warning)
        (println (utils/color-str :yellow glue-warning)))
      (println (str "  " (utils/color-str [:bold :cyan] (string/capitalize (name sentence-keyword)))
                    " " (highlight-params base sentence)
                    (when glue-ns
                      (str "         " (utils/color-str :grey "(from " glue-ns "/\"" glue-pattern "\")")))))
      ;; les lignes arrivent deja colorees : le grey global ecraserait la table
      (doseq [line (param-lines params)]
        (println (str "      " line))))))

(defmethod t/report :step-succeed [_] (t/with-test-out ""))

(defmethod t/report :step-failed [{:keys [exception]}]
  (t/with-test-out
    (println (utils/color-str :red "  Step failed"))
    (some->> exception stacktrace/print-stack-trace)))

(defmethod t/report :hook-failed [{:keys [exception]}]
  (t/with-test-out
    (println (utils/color-str :red "  Hook failed"))
    (some->> exception stacktrace/print-stack-trace)))

(defmethod t/report :scenario-succeed [{:keys [scenario]}]
  (t/with-test-out
    (t/inc-report-counter :pass)
    (t/inc-report-counter :scenarios-succeed)
    (println (utils/color-str :green (scenario-label scenario) " succeed !"))
    (println)))

(defmethod t/report :scenario-failed [{:keys [scenario]}]
  (t/with-test-out
    (reset! *feature-succeed* false)
    (t/inc-report-counter :fail)
    (t/inc-report-counter :scenarios-failed)
    (println (utils/color-str :red (scenario-label scenario) " FAILED"))
    (println)))

(defmethod t/report :missing-step [{:keys [step-sentence]}]
  (t/with-test-out
    (println (utils/color-str :yellow "Missing step for : " (:raw step-sentence)))
    (println (utils/color-str :grey (generate-step-fn step-sentence)))))

(defn run-feature [feature]
  (when-let [{feature-ast :scenari/feature-ast} (meta feature)]
    (let [{:keys [feature scenarios annotations description]} feature-ast]
      (run-hooks
       feature-ast
       (fn []
         (binding [*feature-succeed* (atom true)]
           (t/do-report {:type        :begin-feature
                         :feature     feature
                         :annotations annotations
                         :description description})
           (doseq [[previous scenario following] (map vector
                                                      (cons nil scenarios)
                                                      scenarios
                                                      (concat (rest scenarios) [nil]))]
             (when (and (:rule scenario) (not= (:rule previous) (:rule scenario)))
               (t/do-report {:type :begin-rule, :rule (:rule scenario)}))
             (t/do-report {:type :begin-scenario, :scenario scenario})
             (let [[steps-passed? hook-exception]
                   (try-hooks
                    scenario
                    (fn []
                      (loop [state (:default-state scenario)
                             [step & others] (:steps scenario)]
                        (if-not step
                          true
                          ;; report after running, so the step carries its
                          ;; :status and the sentence can be coloured by it
                          (let [step-result (run-step step state)]
                            (t/do-report {:type :begin-step, :step step-result})
                            (if (= (:status step-result) :fail)
                              (do
                                (t/do-report {:type :step-failed, :exception (:exception step-result)})
                                (doseq [pending others]
                                  (t/do-report {:type :begin-step, :step (assoc pending :status :pending)}))
                                false)
                              (do
                                (t/do-report {:type :step-succeed, :state (:output-state step-result)})
                                (recur (:output-state step-result) others)))))))
                    #(if % :success :fail))]
               ;; a hook that throws fails its scenario, and the next one runs
               (when hook-exception
                 ;; nil: a :pre hook threw, the steps never ran
                 (when (nil? steps-passed?)
                   (doseq [pending (:steps scenario)]
                     (t/do-report {:type :begin-step, :step (assoc pending :status :pending)})))
                 (t/do-report {:type :hook-failed, :exception hook-exception}))
               (if (and steps-passed? (not hook-exception))
                 (t/do-report {:type :scenario-succeed, :scenario scenario})
                 (t/do-report {:type :scenario-failed, :scenario scenario})))
             (when (and (:rule scenario) (not= (:rule scenario) (:rule following)))
               (t/do-report {:type :end-rule, :rule (:rule scenario)})))
           (t/do-report {:type :end-feature, :feature feature :succeed? @*feature-succeed*})))))))

(defn run-features
  ([] (apply run-features (filter #(some? (:scenari/feature-ast (meta %))) (vals (ns-interns *ns*)))))
  ([& features]
   (with-global-hooks
     #(doseq [feature features]
        (run-feature feature)))))
