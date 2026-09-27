(ns scenari.v2.core
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [scenari.v2.glue :as glue]
            [scenari.v2.step :refer [generate-step-fn]])
  (:import (io.cucumber.gherkin GherkinParser)
           (io.cucumber.messages.types Envelope Source SourceMediaType StepKeywordType)
           (java.io File)
           (java.util Optional UUID)
           (org.apache.commons.io FileUtils)))

;; ------------------------
;;          LOAD
;; ------------------------

(defn- opt
  "Java Optional -> value or nil. The gherkin message types return one for every
  field the format declares optional, which is most of them."
  [^Optional o]
  (.orElse o nil))

(defn argument->params
  "The block argument of a pickle step - datatable or docstring - as a param
  vector. Cells arrive trimmed and unescaped from the parser."
  [arg]
  (when arg
    (if-let [table (opt (.getDataTable arg))]
      (let [cells   (fn [row] (map #(.getValue %) (.getCells row)))
            rows    (.getRows table)
            headers (map (comp keyword string/trim) (cells (first rows)))]
        ;; array-map, not hash-map: it keeps the column order of the feature file
        ;; whatever the width, which the report relies on to print the table back
        [{:type :table :val (mapv #(apply array-map (interleave headers (cells %))) (rest rows))}])
      (when-let [doc (opt (.getDocString arg))]
        [(cond-> {:type :doc-string :val (.getContent doc)}
           ;; ```json marks the content type, and a step may want to know
           (opt (.getMediaType doc)) (assoc :media-type (opt (.getMediaType doc))))]))))

(defn file-from-fs-or-classpath [x]
  (let [r (io/resource x)
        f (when (and (instance? File x) (.exists x)) x)
        f-str (when (and (instance? String x) (.exists (io/as-file x))) x)]
    (io/as-file (or r f f-str))))

(defn get-feature-files [basedir]
  (letfn [(find-spec-files [basedir]
            (FileUtils/listFiles
             basedir
             (into-array ["story" "feature"])
             true                                          ;;recursive
             ))]
    (case (str (type basedir))
      "class java.lang.String" (if (.exists (File. ^String basedir))
                                 (find-spec-files (File. ^String basedir))
                                 (throw (RuntimeException. (str basedir " doesn't exists in path: " (System/getProperty "user.dir")))))
      "class java.io.File" (find-spec-files basedir))))

(defmulti read-source
  (fn [path]
    (letfn [(file-or-dir [x]
              (cond (.isFile x) :file
                    (.isDirectory x) :dir))]
      (if (instance? String path)
        (if-let [f (file-from-fs-or-classpath path)]
          (file-or-dir f)
          :feature-as-str)
        (if (instance? File path)
          (file-or-dir path)
          (throw (RuntimeException. (str "type " (type path) "for spec not accepted (only string or file)")))))))
  :default :file)

(defmethod read-source
  :dir
  [path]
  (doseq [spec-file (get-feature-files path)]
    (read-source spec-file)))

(defmethod read-source
  :file
  [path-or-source]
  (read-source (slurp (file-from-fs-or-classpath path-or-source))))

(defmethod read-source :feature-as-str [source] source)

;; ------------------------
;;    GHERKIN -> FEATURE
;; ------------------------

(def ^:private gherkin-parser
  (-> (GherkinParser/builder) (.includeSource false) (.build)))

(defn- envelopes
  "Parse `source` with the official gherkin parser. It yields a GherkinDocument -
  the syntax tree - followed by one *pickle* per runnable scenario: Background
  splicing, Rule flattening, tag inheritance and Scenario Outline expansion are
  all done there, so nothing downstream has to know those constructs exist."
  [source]
  (let [stream (.parse gherkin-parser
                       (Envelope/of (Source. "feature" source SourceMediaType/TEXT_X_CUCUMBER_GHERKIN_PLAIN)))
        envs   (doall (iterator-seq (.iterator stream)))]
    (when-let [err (some #(opt (.getParseError %)) envs)]
      (throw (ex-info (str "Cannot parse feature:\n" (.getMessage err))
                      {:source source :failure err})))
    envs))

(def ^:private keyword-types
  "A pickle resolves And/But against the step above it, which is what a glue
  needs; the report prints what the author wrote, so keep the conjunction."
  {StepKeywordType/CONTEXT :given
   StepKeywordType/ACTION  :when
   StepKeywordType/OUTCOME :then})

(defn- dedent
  "Descriptions keep their indentation in the syntax tree, and every consumer
  adds its own."
  [s]
  (when-not (string/blank? s)
    (->> (string/split-lines s) (map string/trim) (remove string/blank?) (string/join "\n"))))

(defn- step-nodes [steps]
  (into {} (map (fn [s] [(.getId s) {:sentence-keyword (keyword-types (opt (.getKeywordType s)) :and)}])) steps))

(defn- ast-nodes
  "astNodeId -> what a pickle drops on its way out of the compiler: a step's own
  keyword, and a scenario's description prefixed by its Rule's, which is context
  for every scenario the rule groups."
  [children rule-description]
  (into {}
        (mapcat (fn [child]
                  (concat
                   (some-> (opt (.getBackground child)) .getSteps step-nodes)
                   (when-let [sc (opt (.getScenario child))]
                     (cons [(.getId sc) {:description (some->> [rule-description (dedent (.getDescription sc))]
                                                               (remove nil?) (seq) (string/join "\n"))}]
                           (step-nodes (.getSteps sc)))))))
        children))

(defn- feature-nodes [feature]
  (let [children (.getChildren feature)]
    (into (ast-nodes children nil)
          (mapcat (fn [child]
                    (when-let [rule (opt (.getRule child))]
                      (ast-nodes (.getChildren rule) (dedent (.getDescription rule))))))
          children)))

(defn- check-empty-examples!
  "An Examples table with a header but no row expands to nothing, and the
  scenario simply vanishes from the pickles - the feature-level guard below then
  blames keyword recognition for it."
  [feature source]
  (doseq [child (.getChildren feature)
          :let [scenarios (keep #(opt (.getScenario %))
                                (concat [child] (some-> (opt (.getRule child)) .getChildren)))]
          sc scenarios
          ex (.getExamples sc)]
    (when (empty? (.getTableBody ex))
      (throw (ex-info (str "Examples table has no row, scenario " (.getName sc)
                           " would expand to nothing")
                      {:source source :scenario (.getName sc)})))))

(defn- tag-names [tags] (into #{} (map #(subs (.getName %) 1)) tags))

(defn pickle-step->map [ast order step ns-feature]
  (let [sentence (.getText step)
        kw       (:sentence-keyword (some ast (.getAstNodeIds step)) :and)
        ;; le bloc - datatable ou docstring - ne dépend pas du glue, et le
        ;; squelette proposé pour un step manquant compte dessus pour son arité
        block    (vec (argument->params (opt (.getArgument step))))
        step-map {:sentence-keyword kw
                  :sentence         sentence
                  :raw              (str (string/capitalize (name kw)) " " sentence)
                  :order            order
                  :params           block}
        glue     (glue/find-glue-by-step-regex step-map ns-feature)]
    (assoc step-map
           :glue glue
           ;; Les valeurs viennent du match : c'est le token du glue qui dit où
           ;; elles commencent et en quoi les convertir.
           :params (into (mapv #(hash-map :type :value :val %)
                               (when glue (glue/step-args glue sentence)))
                         block))))

(defn ->feature-ast [source {:keys [pre-run post-run pre-scenario-run post-scenario-run default-scenario-state] :as _options} ns-feature]
  (let [envs    (envelopes source)
        doc     (some #(opt (.getGherkinDocument %)) envs)
        feature (some-> doc .getFeature opt)
        _       (when feature (check-empty-examples! feature source))
        ast     (if feature (feature-nodes feature) {})
        ->hooks (fn [fns] (map #(assoc (meta %) :ref %) fns))
        scenarios
        (for [pickle (keep #(opt (.getPickle %)) envs)]
          (cond-> {:id            (.toString (UUID/randomUUID))
                   :scenario-name (.getName pickle)
                   :annotations   (tag-names (.getTags pickle))
                   :pre-run       (->hooks pre-scenario-run)
                   :post-run      (->hooks post-scenario-run)
                   :default-state (or default-scenario-state {})
                   :steps         (vec (map-indexed
                                        (fn [i step] (pickle-step->map ast i step ns-feature))
                                        (.getSteps pickle)))}
            (:description (some ast (.getAstNodeIds pickle)))
            (assoc :description (:description (some ast (.getAstNodeIds pickle))))))]
    (when (empty? scenarios)
      (throw (ex-info (str "Feature has no scenario. Lines whose keyword is not recognized "
                           "are parsed as free description:\n" (some-> feature .getDescription))
                      {:source source})))
    (cond-> {:scenarios (vec scenarios)
             :pre-run   (->hooks pre-run)
             :post-run  (->hooks post-run)}
      feature (assoc :feature (.getName feature))
      (some-> feature .getTags seq) (assoc :annotations (tag-names (.getTags feature)))
      (some-> feature .getDescription dedent) (assoc :description (dedent (.getDescription feature))))))

;; ------------------------
;;          RUN
;; ------------------------

(defn run-step [step scenario-state]
  (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
    (let [f (get-in step [:glue :ref])
          params (cons scenario-state (mapv :val (get step :params)))]
      (try (when-not f
             ;; sans glue, `(apply nil ...)` levait une NPE qui ne dit pas quel
             ;; step manque ni quoi écrire
             (throw (ex-info (str "Undefined step: " (:raw step) "\n" (generate-step-fn step))
                             {:step (:raw step)})))
           (let [result (apply f params)
                 out (last result)
                 ;; Un step qui finit par une assertion ou un effet de bord rend
                 ;; true/false/nil - jamais un etat voulu. Sans ca, un defthen
                 ;; sans `state` final remplace silencieusement l'etat par le
                 ;; booleen de son `is`, et le step suivant recoit true.
                 ;; ponytail: un step qui veut vraiment nil ou false comme etat
                 ;; ne peut pas ; le jour ou ca arrive, il faudra un marqueur
                 ;; explicite plutot qu'une heuristique sur le type.
                 state (if (or (nil? out) (boolean? out)) scenario-state out)
                 any-fail? (> (:fail (deref clojure.test/*report-counters*)) 0)]
             (-> step
                 (assoc :input-state scenario-state)
                 (assoc :output-state state)
                 (assoc :status (if any-fail? :fail :success))))
           (catch Throwable e
             (-> step
                 (assoc :input-state scenario-state)
                 (assoc :exception e)
                 (assoc :status :fail)))))))

(defn run-steps [steps state [step & others]]
  (if-not step
    steps
    (let [{:keys [output-state status] :as step-result} (run-step step state)
          steps (map #(if (= (:order step-result) (:order %)) step-result %) steps)]
      (if (= status :fail)
        steps
        (recur steps output-state others)))))

(defn run-hooks
  "Encadre f par les hooks :pre-run et :post-run de x. Le teardown est dans un
  finally : il doit tourner meme si un hook pre-run, la resolution d'un glue ou
  un report leve - c'est exactement le cas pour lequel il existe."
  [{:keys [pre-run post-run]} f]
  (try (run! (fn [{pre-run-fn :ref}] (pre-run-fn)) pre-run)
       (f)
       (finally (run! (fn [{post-run-fn :ref}] (post-run-fn)) post-run))))

(defn run-scenario [scenario]
  (let [pending-steps (map #(assoc % :status :pending) (:steps scenario))
        result-steps (run-hooks scenario
                                #(run-steps pending-steps (:default-state scenario) pending-steps))]
    (-> scenario
        (assoc :steps result-steps)
        (assoc :status (if (contains? (set (map :status result-steps)) :fail) :fail :success)))))

(defn run-scenarios [scenarios [scenario & others]]
  (if-not scenario
    scenarios
    (let [scenario-result (run-scenario scenario)
          scenarios (map #(if (= (:id %) (:id scenario)) scenario-result %) scenarios)]
      (recur scenarios others))))

(defn run-feature [feature]
  (let [{:keys [scenarios] :as feature-ast} (get (meta feature) :scenari/feature-ast)
        scenarios (run-hooks feature-ast #(run-scenarios scenarios scenarios))]
    (-> feature-ast
        (assoc :scenarios scenarios)
        (assoc :status (if (contains? (set (map :status scenarios)) :fail) :fail :success)))))

(defn run-features
  ([] (apply run-features (filter #(some? (:scenari/feature-ast (meta %))) (vals (ns-interns *ns*)))))
  ([& features] (mapv run-feature features)))

;; ------------------------
;;          DEFINE
;; ------------------------
(defmacro deffeature [name feature & [options]]
  (let [feature# `~(eval feature)
        name# `~(if (symbol? name) name (eval name))
        source# (read-source feature#)
        feature-ast# `(->feature-ast ~source# ~options *ns*)]
    `(do
       (ns-unmap *ns* '~name#)
       (require '[scenari.v2.test])
       (t/deftest ~(vary-meta name# assoc
                              :scenari/raw-feature source#
                              :scenari/feature-ast feature-ast#
                              :scenari/feature-test true) [] (scenari.v2.test/run-features (var ~name#))))))

(defn re->symbol
  "Le nom du var d'un glue, tiré de sa phrase. La barre oblique de l'alternance
  `complète/partielle` en ferait un symbole qualifié, que `defn` refuse."
  [re]
  (-> (str re)
      (string/replace #"\\\"\(\.\*\)\\\"" "param")
      (string/replace #" " "-")
      (string/replace "/" "-")
      symbol))

;; TODO make a step evaluable as a standalone fun
(defmacro defglue
  "Defines a step: an ordinary var carrying the step's regex as :step metadata,
  there is no registry. The keyword a step was written with plays no part in the
  definition, so defgiven / defwhen / defthen / defand are four names for this
  one macro. Returns the var, like every other Clojure def*."
  [regex params & body]
  (let [sym (re->symbol regex)]
    `(do (defn ~(vary-meta sym assoc :step regex) ~params (into [] [~@body]))
         ;; redefining a step in an already loaded ns leaves (count (all-ns))
         ;; unchanged, which is what all-glues memoizes on
         (glue/invalidate-glues-cache!)
         (var ~sym))))

(defmacro defgiven [regex params & body] `(defglue ~regex ~params ~@body))
(defmacro defwhen [regex params & body] `(defglue ~regex ~params ~@body))
(defmacro defthen [regex params & body] `(defglue ~regex ~params ~@body))
(defmacro defand [regex params & body] `(defglue ~regex ~params ~@body))
