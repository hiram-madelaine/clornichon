(ns scenari.v2.step
  (:require [clojure.string :as string]
            [scenari.v2.glue :as glue])
  (:import (io.cucumber.cucumberexpressions CucumberExpressionGenerator)))

(defn- generator
  "Le générateur de cucumber, sur le même registre de types que le matching :
  ce qu'il propose est exactement ce que `find-glue-by-step-regex` saura relire."
  []
  (CucumberExpressionGenerator. @glue/parameter-type-registry))

(defn- as-clojure-string
  "L'expression va dans un littéral chaîne à coller : ses échappements - le
  générateur pose `\\/` devant une barre oblique littérale - doivent survivre au
  lecteur Clojure."
  [s]
  (string/escape s {\\ "\\\\" \" "\\\""}))

(defn generate-step-fn
  "return a string representing a spexec macro call corresponding to the sentence step"
  [{:keys [sentence sentence-keyword params]}]
  (let [expression (first (.generateExpressions (generator) sentence))
        ;; un paramètre par token de la phrase, plus le bloc - datatable ou
        ;; docstring - que le step porte déjà
        arity      (+ (count (.getParameterNames expression)) (count params))]
    (str "(def" (name (or sentence-keyword :when))
         " \"" (as-clojure-string (.getSource expression)) "\"  "
         "[state " (string/join " " (map #(str "arg" %) (range arity))) "]  "
         (case sentence-keyword
           :given "(do \"setup or assert correct tested component state\"))"
           :then  "(do \"assert the result of when step\"))"
           "(do \"something\"))"))))
