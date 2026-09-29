(ns scenari.v2.core
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [scenari.v2.glue :as glue]
            [scenari.v2.step :refer [generate-step-fn]])
  (:import (io.cucumber.gherkin GherkinParser)
           (io.cucumber.tagexpressions Expression TagExpressionParser)
           (io.cucumber.messages.types Envelope Source SourceMediaType StepKeywordType)
           (java.io File)
           (java.util Optional)))

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
  vector. Cells arrive trimmed and unescaped from the parser. A table is a
  vector of maps keyed by its first row, carrying every row as written under
  `:scenari/cells` in its metadata, for `scenari.v2.table`."
  [arg]
  (when arg
    (if-let [table (opt (.getDataTable arg))]
      (let [cells   (mapv (fn [row] (mapv #(.getValue %) (.getCells row))) (.getRows table))
            headers (map (comp keyword string/trim) (first cells))]
        ;; array-map, not hash-map: it keeps the column order of the feature file
        ;; whatever the width
        [{:type :table :val (with-meta (mapv #(apply array-map (interleave headers %)) (rest cells))
                              {:scenari/cells cells})}])
      (when-let [doc (opt (.getDocString arg))]
        [(cond-> {:type :doc-string :val (.getContent doc)}
           ;; ```json marks the content type, and a step may want to know
           (opt (.getMediaType doc)) (assoc :media-type (opt (.getMediaType doc))))]))))

(defn file-from-fs-or-classpath [x]
  (let [;; io/resource ne prend qu'une chaîne, et levait sur un File
        r (when (string? x) (io/resource x))
        f (when (and (instance? File x) (.exists x)) x)
        f-str (when (and (instance? String x) (.exists (io/as-file x))) x)]
    (io/as-file (or r f f-str))))

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
          (throw (RuntimeException. (str "type " (type path) " for spec not accepted (only string or file)")))))))
  :default :file)

(defmethod read-source
  :dir
  [path]
  ;; lire le répertoire n'a jamais marché, et un deffeature est le deftest
  ;; d'une feature : le dire plutôt que laisser `slurp` échouer sur le répertoire
  (throw (ex-info (str path " is a directory: a deffeature reads one feature, write one per file")
                  {:path path})))

(defmethod read-source
  :file
  [path-or-source]
  (read-source (slurp (file-from-fs-or-classpath path-or-source))))

(defmethod read-source :feature-as-str [source] source)

;; ------------------------
;;    GHERKIN -> FEATURE
;; ------------------------

(def ^:private gherkin-parser
  (-> (GherkinParser/builder) (.build)))

(defn- envelopes
  "Parse `source` with the official gherkin parser. It yields a GherkinDocument -
  the syntax tree - followed by one *pickle* per runnable scenario: Background
  splicing, Rule flattening, tag inheritance and Scenario Outline expansion are
  all done there, so nothing downstream has to know those constructs exist."
  [source uri]
  (let [stream (.parse gherkin-parser
                       (Envelope/of (Source. uri source SourceMediaType/TEXT_X_CUCUMBER_GHERKIN_PLAIN)))
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

(defn- tag-names [tags] (into #{} (map #(subs (.getName %) 1)) tags))

(defn- ast-nodes
  "astNodeId -> what a pickle drops on its way out of the compiler: a step's own
  keyword, a scenario's description, and the Rule that groups it - the pickle
  keeps the rule's tags and background, not the rule itself."
  [children rule]
  (into {}
        (mapcat (fn [child]
                  (concat
                   (some-> (opt (.getBackground child)) .getSteps step-nodes)
                   (when-let [sc (opt (.getScenario child))]
                     (cons [(.getId sc) (cond-> {:description (dedent (.getDescription sc))}
                                          rule (assoc :rule rule))]
                           (step-nodes (.getSteps sc)))))))
        children))

(defn- rule-map [rule]
  (cond-> {:id (.getId rule) :name (.getName rule)}
    (seq (.getTags rule))              (assoc :annotations (tag-names (.getTags rule)))
    (dedent (.getDescription rule))    (assoc :description (dedent (.getDescription rule)))))

(defn- feature-nodes [feature]
  (let [children (.getChildren feature)]
    (into (ast-nodes children nil)
          (mapcat (fn [child]
                    (when-let [rule (opt (.getRule child))]
                      (ast-nodes (.getChildren rule) (rule-map rule)))))
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

(defn pickle-step->map [ast order step ns-feature]
  (let [sentence (.getText step)
        kw       (:sentence-keyword (some ast (.getAstNodeIds step)) :and)
        ;; le bloc - datatable ou docstring - ne dépend pas du glue, et le
        ;; squelette proposé pour un step manquant compte dessus pour son arité
        block    (vec (argument->params (opt (.getArgument step))))
        step-map {:id               (.getId step)
                  :sentence-keyword kw
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

(defn- ->hook
  "Un hook tel que run-hooks l'appelle : sa var avec ses métadonnées - `:arglists`
  dit s'il veut le contexte - et `:scenari/tags` compilée une fois, ici, plutôt
  qu'à chacun des scénarios qu'il encadre."
  [f]
  (let [m (meta f)]
    (cond-> (assoc m :ref f)
      (:scenari/tags m)
      (assoc :tag-expr (try (TagExpressionParser/parse (:scenari/tags m))
                            (catch Exception e
                              (throw (ex-info (str "hook " f " : " (.getMessage e))
                                              {:hook f :tags (:scenari/tags m)} e))))))))

(defn ->feature-ast
  "`uri` names the feature in the cucumber messages - the file it was read from."
  ([source options ns-feature] (->feature-ast source options ns-feature "feature"))
  ([source {:keys [pre-run post-run pre-scenario-run post-scenario-run default-scenario-state] :as _options} ns-feature uri]
   (let [envs    (envelopes source uri)
         doc     (some #(opt (.getGherkinDocument %)) envs)
         feature (some-> doc .getFeature opt)
         _       (when feature (check-empty-examples! feature source))
         ast     (if feature (feature-nodes feature) {})
         ->hooks (fn [fns] (mapv ->hook fns))
         scenarios
         (for [pickle (keep #(opt (.getPickle %)) envs)
               :let [{:keys [description rule]} (some ast (.getAstNodeIds pickle))]]
           (cond-> {:id            (.getId pickle)
                    :scenario-name (.getName pickle)
                    :annotations   (tag-names (.getTags pickle))
                    :pre-run       (->hooks pre-scenario-run)
                    :post-run      (->hooks post-scenario-run)
                    :default-state (or default-scenario-state {})
                    :steps         (vec (map-indexed
                                         (fn [i step] (pickle-step->map ast i step ns-feature))
                                         (.getSteps pickle)))}
             description (assoc :description description)
             rule        (assoc :rule rule)))]
     (when (empty? scenarios)
       (throw (ex-info (str "Feature has no scenario. Lines whose keyword is not recognized "
                            "are parsed as free description:\n" (some-> feature .getDescription))
                       {:source source})))
     (cond-> {:scenarios (vec scenarios)
              :pre-run   (->hooks pre-run)
              :post-run  (->hooks post-run)
              ;; source, GherkinDocument et pickles, tels que le parser les
              ;; émet : le début du flux cucumber-messages d'un run
              :messages  envs}
       feature (assoc :feature (.getName feature))
       (some-> feature .getTags seq) (assoc :annotations (tag-names (.getTags feature)))
       (some-> feature .getDescription dedent) (assoc :description (dedent (.getDescription feature)))))))

;; ------------------------
;;          RUN
;; ------------------------

(defn- assertion-failed?
  "Un `is` a échoué depuis que `*report-counters*` est lié. Il ne lève rien : le
  reporter l'affiche et le compte, c'est ce compteur qui le dit. :error compris :
  un `is` dont la forme lève, clojure.test l'attrape et le compte là."
  []
  (let [{:keys [fail error]} @t/*report-counters*]
    (pos? (+ fail error))))

(defn run-step [step scenario-state]
  (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
    (let [f (get-in step [:glue :ref])
          params (cons scenario-state (mapv :val (get step :params)))
          started-at (System/currentTimeMillis)
          t0 (System/nanoTime)]
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
                 any-fail? (assertion-failed?)]
             (-> step
                 (assoc :input-state scenario-state)
                 (assoc :output-state state)
                 (assoc :started-at started-at :duration-ns (- (System/nanoTime) t0))
                 (assoc :status (if any-fail? :fail :success))))
           (catch Throwable e
             (-> step
                 (assoc :input-state scenario-state)
                 ;; sous --fail-fast kaocha lève un marqueur depuis le `is` qui
                 ;; échoue, pour sauter la suite du test : l'assertion est déjà
                 ;; rapportée, le marqueur n'est pas l'exception du step
                 (cond-> (not (:kaocha/fail-fast (ex-data e))) (assoc :exception e))
                 (assoc :started-at started-at :duration-ns (- (System/nanoTime) t0))
                 (assoc :status :fail)))))))

(defn run-steps
  "Joue `todo` dans l'ordre à partir de `state` et s'arrête au premier step qui
  échoue. Rend `steps`, ceux qui ont tourné remplacés par leur résultat : les
  autres gardent leur statut. C'est la seule boucle sur les steps, les trois
  runners passent par elle."
  [steps state todo]
  (let [ran (loop [ran {} state state [step & others] todo]
              (if-not step
                ran
                (let [{:keys [output-state status] :as result} (run-step step state)
                      ran (assoc ran (:order result) result)]
                  (if (= status :fail)
                    ran
                    (recur ran output-state others)))))]
    ;; un seul passage : remplacer à chaque step empilait des `map` paresseux,
    ;; 870 ms pour 5 000 steps
    (mapv #(get ran (:order %) %) steps)))

(defn- call-hook
  "Un hook qui n'a qu'une arité à un argument reçoit ctx ; tout autre est appelé
  sans argument, comme avant. L'arité 0 l'emporte : un hook existant en a
  forcément une, et son arité 1 attend autre chose que ctx - chez Electre,
  `add-perimetres-contractuels` a `[]` et `[perimetres]`. Un hook marqué
  `:scenari/tags` ne tourne que si les tags de ctx satisfont l'expression.

  Un `is` qui échoue dans le hook ne lève rien : le reporter l'affiche et le
  compte, et personne ne lit ce compteur. Il est lu ici, comme `run-step` le
  fait pour un glue, et le hook lève ce qu'il n'a pas levé."
  [{f :ref :keys [arglists ^Expression tag-expr]} ctx]
  (when (or (nil? tag-expr)
            ;; les tags sont stockés sans le @, l'expression le veut
            (.evaluate tag-expr (mapv #(str "@" %) (:annotations ctx))))
    (binding [t/*report-counters* (ref t/*initial-report-counters*)]
      (if (and (some #(= 1 (count %)) arglists) (not-any? empty? arglists))
        (f ctx)
        (f))
      (when (assertion-failed?)
        (throw (ex-info (str "hook " f " : an assertion failed") {:hook f}))))))

(def hook-keys
  "Les valeurs de `:scenari/hook` : ce qu'un hook global encadre."
  #{:before-all :after-all :before-feature :after-feature :before-scenario :after-scenario})

(defn global-hooks
  "Les vars marquées `:scenari/hook` dans les namespaces chargés, privées
  comprises, par clé de `hook-keys`, dans l'ordre des namespaces puis des
  lignes. Cherchées à l'exécution et pas au parsing : un namespace de hooks que
  personne ne requiert peut se charger après les features."
  []
  (->> (all-ns)
       ;; ns-interns : un hook `defn-` tourne, il n'a pas à être public
       (mapcat #(vals (ns-interns %)))
       (filter #(:scenari/hook (meta %)))
       (sort-by (juxt #(str (:ns (meta %))) #(:line (meta %) 0)))
       (reduce (fn [m v]
                 (let [{h :scenari/hook tags :scenari/tags} (meta v)]
                   ;; une faute de frappe ferait un hook qui ne tourne jamais, sans rien dire
                   (when-not (hook-keys h)
                     (throw (ex-info (str "hook " v " : :scenari/hook " h " is not one of " (sort hook-keys))
                                     {:hook v :scenari/hook h})))
                   ;; la suite n'a pas de tags : l'expression serait toujours fausse
                   (when (and tags (#{:before-all :after-all} h))
                     (throw (ex-info (str "hook " v " : :scenari/tags has no effect on " h
                                          ", the suite has no tags")
                                     {:hook v :scenari/hook h :scenari/tags tags})))
                   (update m h (fnil conj []) (->hook v))))
               {})))

(def ^:dynamic *global-hooks*
  "Les hooks globaux d'un run, cherchés une fois à son entrée - `run-suite`,
  `run-features` - plutôt qu'à chaque scénario : le balayage parcourt toutes les
  vars chargées. Sans run englobant, chaque appel les cherche, ce qui voit aussi
  un hook ajouté au REPL entre deux appels."
  nil)

(defn with-global-hooks
  "Appelle f avec les hooks globaux résolus pour toute sa durée."
  [f]
  (binding [*global-hooks* (or *global-hooks* (global-hooks))]
    (f)))

(defn- around
  "Encadre f par pre-run et post-run, et rend [r e] : r le retour de f - nil s'il
  n'a pas tourné -, e ce qu'un hook a levé. Un pre-run qui lève saute les
  pre-run suivants et f. Tous les post-run tournent, quoi que lèvent un pre-run,
  f ou un autre post-run : c'est le cas pour lequel le teardown existe. La
  première exception porte les suivantes en suppressed. Ce que f lève n'est pas
  l'échec d'un hook : il remonte tel quel, une fois les post-run passés."
  [pre-run post-run ctx f ->status]
  (let [status  (volatile! (when ->status :fail))
        thrown  (volatile! nil)
        f-threw (volatile! false)
        r       (volatile! nil)
        note    (fn [^Throwable e]
                  (cond (nil? @thrown) (vreset! thrown e)
                        ;; un delay en échec relève la même instance, et
                        ;; addSuppressed refuse une exception sur elle-même
                        (not (identical? e @thrown)) (.addSuppressed ^Throwable @thrown e)))]
    (try (run! #(call-hook % ctx) pre-run)
         (catch Throwable e (note e)))
    (when-not @thrown
      (try (vreset! r (f))
           (when ->status (vreset! status (->status @r)))
           (catch Throwable e (note e) (vreset! f-threw true))))
    (doseq [hook post-run]
      (try (call-hook hook (cond-> ctx @status (assoc :status @status)))
           (catch Throwable e (note e))))
    (when @f-threw (throw @thrown))
    [@r @thrown]))

(defn try-hooks
  "Encadre f par les hooks :pre-run et :post-run de x - un scénario s'il a un
  :scenario-name, une feature sinon - et par les hooks globaux de ce niveau, en
  oignon : les globaux entrent avant ceux de x et sortent après eux.

  Un hook à un argument reçoit le nom et les tags de x ; avec ->status, qui tire
  :success ou :fail du retour de f, les :post-run reçoivent aussi :status -
  :fail si f n'a pas tourné ou a levé.

  Rend [r e], voir `around` : un hook qui lève ne lève pas ici, c'est à
  l'appelant d'en faire l'échec de x."
  ([x f] (try-hooks x f nil))
  ([{:keys [pre-run post-run] :as x} f ->status]
   (let [[before after] (if (contains? x :scenario-name)
                          [:before-scenario :after-scenario]
                          [:before-feature :after-feature])
         hooks          (or *global-hooks* (global-hooks))]
     (around (into (get hooks before []) pre-run)
             (into (vec post-run) (get hooks after))
             (select-keys x [:feature :scenario-name :annotations])
             f
             ->status))))

(defn- return-or-throw [[r e]]
  (if e (throw e) r))

(defn run-hooks
  "`try-hooks`, qui rend le retour de f et lève ce qu'un hook a levé."
  ([x f] (run-hooks x f nil))
  ([x f ->status] (return-or-throw (try-hooks x f ->status))))

(defn try-suite
  "Encadre f - toute la suite - par les hooks globaux :before-all et :after-all,
  appelés sans tags : un hook à un argument reçoit une map vide. Rend [r e],
  comme `try-hooks` : r est nil si un :before-all a levé."
  [f]
  (with-global-hooks
    #(around (:before-all *global-hooks*) (:after-all *global-hooks*) {} f nil)))

(defn run-suite
  "`try-suite`, qui rend le retour de f et lève ce qu'un hook a levé."
  [f]
  (return-or-throw (try-suite f)))

(defn- steps-status [steps]
  (if (some #(= :fail (:status %)) steps) :fail :success))

(defn run-scenario
  "Le scénario une fois joué, entre ses hooks. Un hook qui lève en fait un
  scénario :fail, qui porte l'exception sous :exception - ses steps restent
  :pending si c'est un hook d'entrée. Avec `failure`, ce qu'un hook de sa
  feature a levé, le scénario échoue de même sans rien jouer."
  ([scenario] (run-scenario scenario nil))
  ([scenario failure]
   (let [started-at (System/currentTimeMillis)
         pending-steps (mapv #(assoc % :status :pending) (:steps scenario))
         [result-steps e] (if failure
                            [nil failure]
                            (try-hooks scenario
                                       #(run-steps pending-steps (:default-state scenario) pending-steps)
                                       steps-status))
         result-steps (or result-steps pending-steps)]
     (-> scenario
         (assoc :steps result-steps)
         (assoc :started-at started-at :finished-at (System/currentTimeMillis))
         (assoc :status (if e :fail (steps-status result-steps)))
         (cond-> e (assoc :exception e))))))

(defn run-scenarios
  "Joue `todo` dans l'ordre et rend `scenarios`, ceux qui ont tourné remplacés
  par leur résultat."
  [scenarios todo]
  ;; en un passage, comme run-steps : les `map` empilés levaient une
  ;; StackOverflowError à 20 000 scénarios
  (let [ran (into {} (map (juxt :id identity)) (mapv run-scenario todo))]
    (mapv #(get ran (:id %) %) scenarios)))

(defn run-feature [feature]
  (let [{:keys [scenarios] :as feature-ast} (get (meta feature) :scenari/feature-ast)
        [ran e] (try-hooks feature-ast #(run-scenarios scenarios scenarios))
        ;; un hook d'entrée a levé, rien n'a tourné : chaque scénario échoue à sa place
        scenarios (or ran (mapv #(run-scenario % e) scenarios))]
    (-> feature-ast
        (assoc :scenarios scenarios)
        (assoc :status (if (or e (contains? (set (map :status scenarios)) :fail)) :fail :success))
        (cond-> e (assoc :exception e)))))

(defn run-features
  ([] (apply run-features (filter #(some? (:scenari/feature-ast (meta %))) (vals (ns-interns *ns*)))))
  ([& features] (with-global-hooks #(mapv run-feature features))))

;; ------------------------
;;          DEFINE
;; ------------------------
(defmacro deffeature [name feature & [options]]
  (let [feature# `~(eval feature)
        name# `~(if (symbol? name) name (eval name))
        source# (read-source feature#)
        ;; un texte inline revient tel quel de read-source : il n'a pas de fichier
        uri# (if (= source# feature#) (str *ns* "/" name#) (str feature#))
        feature-ast# `(->feature-ast ~source# ~options *ns* ~uri#)]
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

(defn check-glue-name!
  "Lève si `sym` nomme déjà, dans `ns`, le glue d'une autre phrase. `a b` et
  `a-b` font le même nom de var : le second `defn` remplaçait le premier glue
  sans rien dire, et ses steps se retrouvaient sans définition. La même phrase
  repasse, c'est un rechargement."
  [^clojure.lang.Namespace ns sym step]
  (let [defined (:step (meta (.findInternedVar ns sym)))]
    (when (and defined (not= (str defined) (str step)))
      (throw (ex-info (str "glue " ns "/" sym " : \"" step "\" and \"" defined
                           "\" make the same var name, the second definition would"
                           " replace the first. Reword one; if the first is no longer"
                           " in the source, (ns-unmap '" ns " '" sym ")")
                      {:ns (ns-name ns) :name sym :step step :defined defined})))))

;; TODO make a step evaluable as a standalone fun
(defmacro defglue
  "Defines a step: an ordinary var carrying the step's regex as :step metadata,
  there is no registry. The keyword a step was written with plays no part in the
  definition, so defgiven / defwhen / defthen / defand are four names for this
  one macro. Returns the var, like every other Clojure def*. Throws when the
  name drawn from the sentence is already the one of another sentence."
  [regex params & body]
  (let [sym (re->symbol regex)]
    `(do (check-glue-name! (the-ns '~(ns-name *ns*)) '~sym ~regex)
         (defn ~(vary-meta sym assoc :step regex) ~params (into [] [~@body]))
         ;; redefining a step in an already loaded ns leaves (count (all-ns))
         ;; unchanged, which is what all-glues memoizes on
         (glue/invalidate-glues-cache! (the-ns '~(ns-name *ns*)))
         (var ~sym))))

(defmacro defgiven [regex params & body] `(defglue ~regex ~params ~@body))
(defmacro defwhen [regex params & body] `(defglue ~regex ~params ~@body))
(defmacro defthen [regex params & body] `(defglue ~regex ~params ~@body))
(defmacro defand [regex params & body] `(defglue ~regex ~params ~@body))

(defn define-parameter-type!
  "Defines a `{type-name}` token for the sentence matchers. Its capture is
  `regex`'s match, converted by `transform`: one argument per capture group of
  `regex`, or the whole match when it has none.

    (define-parameter-type! \"isbn\" #\"\\d{13}\" parse-isbn)
    (defgiven \"the book {isbn}\" [state isbn] ...)

  Call it before loading the glues that use the token: a sentence is compiled
  when its feature is parsed, and an unknown token raises there. Defining a
  type again replaces it. Returns `type-name`."
  [type-name regex transform]
  (glue/define-parameter-type! type-name regex transform))
