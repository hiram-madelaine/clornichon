(ns scenari.v2.glue
  (:require [clojure.test :as t]
            [clojure.string :as string])
  (:import (io.cucumber.cucumberexpressions CaptureGroupTransformer ExpressionFactory ParameterType ParameterTypeRegistry Transformer)
           (java.util.regex Pattern)
           (java.lang.reflect Type)
           (java.util Locale)))

(def ^:private glues-cache (atom nil))

(def ^:private no-types
  "Le second argument de `.match` déclare les types attendus par la fonction
  glue. Une fn Clojure ne déclare rien, la conversion reste celle du token."
  (into-array Type []))

(defn invalidate-glues-cache!
  "Invalidate the `all-glues` cache. Called by the step definition macros: a glue
   can appear in an already loaded namespace, which the namespace count alone
   cannot detect."
  []
  (reset! glues-cache nil))

(def ^:private number-type
  "`{number}` n'est pas un type cucumber - il est défini ici pour les glues déjà
  écrits, et gagne au passage le signe et les décimales. `useForSnippets` false :
  un squelette généré propose les types de cucumber, `{int}` et `{double}`,
  plutôt que de propager le nôtre."
  (ParameterType. "number" "-?\\d+(?:\\.\\d+)?" Object
                  (reify Transformer (transform [_ s] (read-string s)))
                  false false))

;; defonce : ce sont les types de l'utilisateur, un rechargement de ce ns ne
;; doit pas les perdre
(defonce ^:private custom-types (atom {}))

(defn- registry-of [types]
  (let [r (ParameterTypeRegistry. Locale/ENGLISH)]
    (run! #(.defineParameterType r %) (cons number-type (vals types)))
    r))

(def parameter-type-registry
  "Les types de token connus des expressions : ceux de cucumber, `{number}` et
  ceux de `define-parameter-type!`. Un atome, parce que cucumber refuse de
  redéfinir un type : le redéfinir reconstruit le registre."
  (atom (registry-of @custom-types)))

(defn define-parameter-type!
  "Voir `scenari.v2.core/define-parameter-type!`."
  [type-name regex transform]
  (let [t (ParameterType. ^String type-name (str regex) Object
                          (reify CaptureGroupTransformer
                            (transform [_ groups] (apply transform groups)))
                          true false)]
    ;; le registre est construit avant d'être publié : un nom invalide ou une
    ;; regex en conflit lève ici, sans laisser un registre à moitié fait
    (reset! parameter-type-registry (registry-of (assoc @custom-types type-name t)))
    (swap! custom-types assoc type-name t)
    ;; les glues déjà compilés l'ont été contre l'ancien registre
    (invalidate-glues-cache!)
    type-name))

(defn- expression-factory
  "Les *cucumber expressions*, la moitié que gherkin ne couvre pas : `{int}`
  `{float}` `{word}` `{string}`, le texte optionnel `apple(s)` et l'alternance
  `hot/cold`. Une phrase encadrée de `^...$` ou de `/.../` reste lue comme une
  regex, comme avant."
  []
  (ExpressionFactory. @parameter-type-registry))

(defn step->expression
  "La phrase d'un glue, compilée. Un litéral `#\"...\"` est une regex quoi qu'elle
  contienne : la lire comme une expression ferait d'un `([^\"]*)` du texte
  optionnel, et son `\\\"` lèverait. Elle est ancrée parce qu'un glue regex a
  toujours matché la phrase entière. Lève sur un token inconnu - `{produit}` sans
  ParameterType - en nommant le glue fautif, sinon l'erreur ne dit pas lequel des
  400 glues chargés est en cause."
  [{:keys [step ns name]}]
  (try (.createExpression (expression-factory)
                          (if (instance? Pattern step)
                            (str "^(?:" step ")$")
                            (str step)))
       (catch Exception e
         (throw (ex-info (str "glue " ns "/" name " : " (.getMessage e))
                         {:step step :ns ns :name name} e)))))

(defn all-glues
  "Find all glue functions (step definitions) in all loaded namespaces.

   Resolving the glue of every step of every feature means scanning all the public
   vars of all the loaded namespaces once per step, which dominates the loading
   time of a large feature suite. The result is therefore memoized, and recomputed
   as soon as a namespace is loaded or a step is (re)defined.

   Each glue carries its `:expression`, compiled here once, because matching a step
   compares it against every glue: compiling it in the matching loop instead built
   one Pattern per (step, glue) pair — 1.3M compilations on a suite of
   3000 steps and 400 glues."
  []
  (let [nss (all-ns)
        k   (count nss)
        {:keys [ns-count glues]} @glues-cache]
    (if (= ns-count k)
      glues
      (let [computed (into []
                           (comp (mapcat #(vals (ns-publics %)))
                                 (map #(assoc (meta %) :ref %))
                                 (filter #(contains? % :step))
                                 (map #(assoc % :expression (step->expression %))))
                           nss)]
        (reset! glues-cache {:ns-count k :glues computed})
        computed))))

(defn ns-proximity-score
  "Calculate proximity score between two namespaces based on common segments"
  [ns-glue ns-feature]
  (loop [[ns-glue & child-ns-glue] (string/split ns-glue #"\.")
         [ns-feature & child-ns-feature] (string/split ns-feature #"\.")
         score 0]
    (if (or (not ns-feature) (not ns-glue) (not= ns-glue ns-feature))
      score
      (recur child-ns-glue child-ns-feature (inc score)))))

(defn find-closest-glues-by-ns
  "Find glues with closest namespace proximity to the feature namespace"
  [matched-glues ns-feature]
  (let [[_score closest-glues-by-ns] (->> matched-glues
                                          (map #(hash-map (ns-proximity-score (str ns-feature) (str (:ns %))) [%]))
                                          (apply merge-with into)
                                          (apply max-key key))]
    closest-glues-by-ns))

(defn- match
  "Les arguments capturés par le glue sur cette phrase, nil s'il ne matche pas.
  Une liste vide est un match sans paramètre - à ne pas confondre avec nil."
  [glue sentence]
  (.orElse (.match (or (:expression glue) (step->expression glue)) sentence no-types) nil))

(defn step-args
  "Les valeurs que la phrase donne au glue, converties par leur token : {int}
  rend un entier, {string} la chaîne sans ses guillemets."
  [glue sentence]
  (mapv #(.getValue %) (match glue sentence)))

(defn find-glue-by-step-regex
  "Return the tuple of fn/regex as a vector that match the step-sentence"
  ([step ns-feature] (find-glue-by-step-regex step ns-feature (all-glues)))
  ([step ns-feature glues]
   (let [{:keys [sentence]} step
         ;; `:expression` est précompilée par `all-glues` ; le repli couvre un
         ;; glue construit à la main et passé directement à cette arité.
         matched-glues (filter #(some? (match % sentence)) glues)]
     (cond
       (empty? matched-glues)
       (do (t/do-report {:type :missing-step, :step-sentence step})
           nil)

       (> (count matched-glues) 1)
       (let [[matched-glue & conflicts] (find-closest-glues-by-ns matched-glues ns-feature)]
         (if conflicts
           (throw (RuntimeException.
                   (str (+ (count conflicts) 1)
                        " matching functions were found for the following step sentence:\n "
                        sentence
                        ", please refine your regexes that match: \n"
                        matched-glue "\n"
                        (string/join "\n" conflicts))))
           (assoc matched-glue
                  :warning (str (count matched-glues) " matching functions were found for this step sentence"))))

       :else (first matched-glues)))))
