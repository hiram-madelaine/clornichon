(ns kaocha.plugin.scenari-doc
  "`--doc-html FILE`: the HTML documentation of the selected scenarios.

  One document: a clickable table of contents, one section per feature, one
  anchor per scenario. It is written from the test-plan, so *after* the filters -
  what would have run is exactly what gets documented. For that the plugin must
  come after `:kaocha.plugin/scenari-tags` in `:kaocha/plugins`; kaocha's own
  filters (`--focus`, `--skip-meta`) always run first.

  Nothing is executed: this is static documentation, not a run report. The
  suites are marked `::testable/skip` once the file is written.

  `--doc-report FILE` writes the same document after the run, annotated with
  each scenario's and step's result."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kaocha.output :as output]
            [kaocha.plugin :refer [defplugin]]
            [kaocha.testable :as testable]
            [scenari.v2.table :as table]))

;; les clés que kaocha.type.scenari pose sur la feature - écrites en toutes
;; lettres pour ne pas charger ce namespace juste pour deux mots-clés
(def ^:private feature-annotations :kaocha.type.scenari/annotations)
(def ^:private feature-description :kaocha.type.scenari/description)

(defn- esc [s]
  (str/escape (str s) {\& "&amp;" \< "&lt;" \> "&gt;" \" "&quot;"}))

;; l'id tel quel : HTML5 accepte `.` et `/` dans un id, et aplatir la
;; ponctuation faisait se confondre `:ns/f-s` et le scénario `:ns.f/s`
(defn- anchor [id]
  (esc (subs (str id) 1)))

(defn- kept
  "The children not skipped, whether the node comes from the test-plan
  (`--doc-html`) or from the run's result (`--doc-report`) - both trees have the
  same shape, under two keys. kaocha.plugin.filter only marks the node that does
  not pass, not its children: stop on it, do not test them one by one. The leaf
  that carries what an `:after-feature` or `:after-all` hook threw is no
  scenario."
  [testable]
  (remove #(or (::testable/skip %) (= :kaocha.type/scenari-hook (::testable/type %)))
          (or (:kaocha.test-plan/tests testable)
              (:kaocha.result/tests testable))))

(defn selected-features
  "The scenari features kept by the filters, each with its kept scenarios under
  `::scenarios` - those of its rules included, in file order: a scenario knows
  its rule by its `:rule`."
  [tree]
  (for [suite   (kept tree)
        feature (kept suite)
        :when   (= :kaocha.type/scenari-feature (::testable/type feature))]
    (assoc feature ::scenarios (mapcat #(if (= :kaocha.type/scenari-rule (::testable/type %)) (kept %) [%])
                                       (kept feature)))))

;; ------------------------
;;         RENDU
;; ------------------------

(defn- tags-html [annotations]
  (when (seq annotations)
    (str "<p class=\"tags\">" (esc (str/join " " (map #(str "@" %) (sort annotations)))) "</p>")))

(defn- desc-html [description]
  (when-not (str/blank? description)
    (str "<pre class=\"desc\">" (esc description) "</pre>")))

(defn- table-html [t]
  ;; les cellules telles qu'écrites, comme le rendu terminal
  (let [[header & rows] (table/cells t)]
    (str "<table><thead><tr>"
         (apply str (for [h header] (str "<th>" (esc h) "</th>")))
         "</tr></thead><tbody>"
         (apply str (for [row rows]
                      (str "<tr>" (apply str (for [c row] (str "<td>" (esc c) "</td>"))) "</tr>")))
         "</tbody></table>")))

(defn- params-html
  "Les params blocs - docstring et datatable. Les params valeurs sont déjà dans
  la phrase du step."
  [params]
  (apply str (for [{:keys [type val]} params]
               (case type
                 :doc-string (str "<pre class=\"docstring\">" (esc val) "</pre>")
                 :table      (table-html val)
                 nil))))

;; `:status` n'existe qu'après un run (`--doc-report`) : sans lui le document
;; est la même doc statique, sans classe ni pastille
(defn- status-class [status] (if status (str " " (name status)) ""))

(defn- badge [status]
  (when status
    (str "<span class=\"badge " (name status) "\">" (esc (name status)) "</span>")))

(defn- error-html [^Throwable e]
  (when e
    (str "<pre class=\"error\">" (esc (or (ex-message e) (str e))) "</pre>")))

(defn- step-html [{:keys [sentence-keyword sentence params status exception]}]
  (str "<li class=\"step" (status-class status) "\">"
       "<span class=\"kw\">" (esc (str/capitalize (name sentence-keyword))) "</span> "
       (esc sentence)
       (params-html params)
       (error-html exception)
       "</li>"))

(defn- scenario-html [scenario]
  (str "<section class=\"scenario" (status-class (:status scenario))
       "\" id=\"" (anchor (::testable/id scenario)) "\">"
       "<h3>" (esc (::testable/desc scenario)) " " (badge (:status scenario)) "</h3>"
       (tags-html (:annotations scenario))
       (desc-html (:description scenario))
       "<ol class=\"steps\">" (apply str (map step-html (:steps scenario))) "</ol>"
       ;; what a hook threw
       (error-html (:exception scenario))
       "</section>"))

(defn- feature-status [feature]
  (let [statuses (set (map :status (::scenarios feature)))]
    (cond (statuses :fail)    :fail
          (statuses :success) :success)))

(defn- feature-html [feature]
  (str "<section class=\"feature" (status-class (feature-status feature))
       "\" id=\"" (anchor (::testable/id feature)) "\">"
       "<h2>" (esc (::testable/desc feature)) " " (badge (feature-status feature)) "</h2>"
       (tags-html (feature-annotations feature))
       (desc-html (feature-description feature))
       (apply str (for [group (partition-by (comp :id :rule) (::scenarios feature))
                        :let  [rule (:rule (first group))]]
                    (if rule
                      (str "<section class=\"rule\"><h3>Rule : " (esc (:name rule)) "</h3>"
                           (tags-html (:annotations rule))
                           (desc-html (:description rule))
                           (apply str (map scenario-html group))
                           "</section>")
                      (apply str (map scenario-html group)))))
       "</section>"))

(defn- toc-html [features]
  (str "<nav><h2>Contents</h2><ul>"
       (apply str
              (for [feature features]
                (str "<li class=\"" (str/trim (status-class (feature-status feature))) "\">"
                     "<a href=\"#" (anchor (::testable/id feature)) "\">"
                     (esc (::testable/desc feature)) "</a><ul>"
                     (apply str
                            (for [scenario (::scenarios feature)]
                              (str "<li class=\"" (str/trim (status-class (:status scenario))) "\">"
                                   "<a href=\"#" (anchor (::testable/id scenario)) "\">"
                                   (esc (::testable/desc scenario)) "</a></li>")))
                     "</ul></li>")))
       "</ul></nav>"))

(def ^:private css "
body{font:16px/1.5 system-ui,sans-serif;max-width:52rem;margin:2rem auto;padding:0 1rem;color:#222}
h1{font-size:1.6rem} h2{font-size:1.3rem;margin-top:2rem} h3{font-size:1.05rem;margin-bottom:.2rem}
nav{background:#f6f6f6;padding:.5rem 1rem;border-radius:4px}
nav ul{list-style:none;padding-left:1rem} nav>ul{padding-left:0}
a{color:#0a58ca;text-decoration:none} a:hover{text-decoration:underline}
.feature{border-top:1px solid #ddd}
.scenario{margin:1rem 0 1rem 1rem}
.rule{margin-left:1rem} .rule>h3{color:#555}
.tags{color:#0a7d7d;font-family:monospace;margin:.2rem 0}
.desc,.docstring{color:#555;background:#f6f6f6;padding:.4rem .6rem;white-space:pre-wrap;font-size:.9rem}
.steps{list-style:none;padding-left:0} .steps li{margin:.15rem 0}
.kw{color:#0a58ca;font-weight:600}
table{border-collapse:collapse;margin:.4rem 0;font-size:.9rem}
th,td{border:1px solid #ccc;padding:.15rem .5rem;text-align:left}
.badge{font-size:.7rem;text-transform:uppercase;padding:.1rem .4rem;border-radius:3px;vertical-align:middle;color:#fff}
.badge.success{background:#1a7f37} .badge.fail{background:#b3261e} .badge.pending{background:#888}
.scenario.fail{border-left:3px solid #b3261e;padding-left:.7rem}
.scenario.success{border-left:3px solid #1a7f37;padding-left:.7rem}
.step.fail{color:#b3261e} .step.pending{color:#999}
.error{color:#b3261e;background:#fdf0ef;padding:.4rem .6rem;white-space:pre-wrap;font-size:.85rem}
nav li.fail>a{color:#b3261e}
")

(defn- counts [features]
  (let [scenarios (mapcat ::scenarios features)]
    (str (count features) " feature(s), " (count scenarios) " scenario(s)"
         (when-let [failed (seq (filter #(= :fail (:status %)) scenarios))]
           (str ", " (count failed) " failed"))
         ".")))

(defn document [features]
  (str "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
       "<title>Features</title><style>" css "</style></head><body>"
       "<h1>Features</h1>"
       "<p>" (counts features) "</p>"
       (toc-html features)
       (apply str (map feature-html features))
       "</body></html>"))

(defn- write! [target features]
  (io/make-parents target)
  (spit target (document features))
  (if (seq features)
    (println "Wrote" target "-" (counts features))
    (output/warn "No scenario selected, " target " is empty.")))

(defplugin kaocha.plugin/scenari-doc
  "Writes the HTML documentation of the selected scenarios, without running them."

  (cli-options [opts]
               (-> opts
                   (conj [nil "--doc-html FILE" (str "Write the selected scenarios to FILE as an HTML "
                                                     "document (table of contents + one anchor per "
                                                     "scenario) instead of running them.")])
                   (conj [nil "--doc-report FILE" (str "Same document, but run the scenarios first and "
                                                       "annotate it with their result.")])))

  (config [config]
          (let [{:keys [doc-html doc-report]} (:kaocha/cli-options config)]
            (cond-> config
              doc-html   (assoc ::target-file doc-html)
              doc-report (assoc ::report-file doc-report))))

  (pre-load [config]
            ;; chacun marque les suites skippées, et l'autre lit alors un arbre vide
            (when (and (::target-file config) (::report-file config))
              (output/error-and-throw {:kaocha/early-exit 248} nil
                                      "--doc-html and --doc-report cannot be combined: "
                                      "--doc-html runs nothing, so the report would be empty."))
            config)

  (post-load [test-plan]
             ;; ::target-file est une clé de config ordinaire :
             ;; `:kaocha.plugin.scenari-doc/target-file` dans tests.edn marche aussi
             (if-let [target (::target-file test-plan)]
               (do (write! target (selected-features test-plan))
                   ;; doc statique : on documente, on n'exécute pas
                   (update test-plan :kaocha.test-plan/tests
                           (partial mapv #(assoc % ::testable/skip true))))
               test-plan))

  (post-run [result]
            (when-let [target (::report-file result)]
              (write! target (selected-features result)))
            result))
