(ns scenari.v2.report-test
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest testing is]]
            [clojure.test :as t]
            [kaocha.output :as output]
            [scenari.v2.core :as core]
            [scenari.v2.test]))

;; captured at load time: kaocha redefines the clojure.test/report var while it
;; runs, and scenari's defmethods live on the original multimethod
(def ^:private report clojure.test/report)

(def ^:private esc (str (char 27)))

(defn- render [event]
  (let [out (java.io.StringWriter.)]
    (binding [t/*test-out* out]
      (report event))
    (str out)))

(def ^:private a-step
  {:sentence-keyword :when
   :sentence         "I create a product named \"iphone 6\" with properties"
   :status           :success
   :glue             {:ns   'my.glue
                      :step "I create a product named {string} with properties"}
   :params           [{:type :value :val "iphone 6"}
                      {:type :table :val [{:size "6" :weight "2"}
                                          {:size "12" :weight "3"}]}]})

(deftest end-rule-rendering-test
  (testing "a rule is closed by a line that names it"
    (binding [output/*colored-output* false]
      (is (= "End of rule : a cart can be emptied\n\n"
             (render {:type :end-rule :rule {:name "a cart can be emptied"}}))))))

(deftest begin-step-rendering-test
  (testing "without colors: keyword, sentence, glue source and the datatable, aligned"
    (binding [output/*colored-output* false]
      (let [out (render {:type :begin-step :step a-step})]
        (is (not (string/includes? out esc))
            "--no-color must not leak any ANSI escape")
        (is (= (str "  When I create a product named \"iphone 6\" with properties"
                    "         (from my.glue/\"I create a product named {string} with properties\")\n"
                    "      | size | weight |\n"
                    "      | 6    | 2      |\n"
                    "      | 12   | 3      |\n")
               out)))))

  (testing "without colors: a docstring param is rendered as its gherkin block"
    (binding [output/*colored-output* false]
      (is (= (str "  Given a doc string\n"
                  "      \"\"\"\n"
                  "      This is markdown\n"
                  "      \"\"\"\n")
             (render {:type :begin-step
                      :step {:sentence-keyword :given
                             :sentence         "a doc string"
                             :status           :success
                             :params           [{:type :doc-string :val "This is markdown"}]}})))))

  (testing "with colors: bold cyan keyword, yellow params, sentence colored by status"
    (binding [output/*colored-output* true]
      (let [out (render {:type :begin-step :step a-step})]
        (is (string/includes? out (str esc "[1;36mWhen" esc "[0m")) "keyword in bold cyan")
        (is (string/includes? out (str esc "[33m\"iphone 6\"" esc "[0m")) "param in yellow")
        (is (string/includes? out (str esc "[32mI create a product named")) "success sentence in green"))
      (is (string/includes? (render {:type :begin-step :step (assoc a-step :status :fail)})
                            (str esc "[31mI create a product named"))
          "failed sentence in red")
      (is (string/includes? (render {:type :begin-step :step (assoc a-step :status :pending)})
                            (str esc "[90mI create a product named"))
          "pending sentence in grey")))

  (testing "with colors: the datatable keeps grey pipes around cyan headers and yellow cells"
    (binding [output/*colored-output* true]
      (let [out (render {:type :begin-step :step a-step})]
        (is (string/includes? out (str esc "[90m|" esc "[0m " esc "[36msize" esc "[0m " esc "[90m|" esc "[0m"))
            "header row: a cyan column name between grey pipes")
        (is (string/includes? out (str esc "[33m6   " esc "[0m"))
            "cell in yellow, padded before colouring so the escapes stay out of the width")))))

(deftest wide-datatable-column-order-test
  (testing "a datatable wider than 8 columns keeps its feature-file column order"
    ;; ->feature-ast reports :missing-step for the unresolved glue, muted here
    (let [step (with-redefs [t/do-report (constantly nil)]
                 (-> (core/->feature-ast "Feature: t
  Scenario: s
    Given a table
      | a | b | c | d | e | f | g | h | i | j | k |
      | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
"
                                         {} 'user)
                     :scenarios first :steps first))]
      (binding [output/*colored-output* false]
        (is (= (str "  Given a table\n"
                    "      | a | b | c | d | e | f | g | h | i | j  | k  |\n"
                    "      | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |\n")
               (render {:type :begin-step :step (assoc step :status :success)})))))))

(deftest empty-column-rendering-test
  (testing "a datatable column whose header and cells are all empty used to
  format as %-0s, which java.util.Formatter rejects: the reporter threw instead
  of printing the step"
    (binding [output/*colored-output* false]
      (let [step (assoc a-step :params [{:type  :table
                                         :val   [{(keyword "") "" :val "1"}]}])
            out  (render {:type :begin-step :step step})]
        (is (string/includes? out "|   | val |\n"))
        (is (string/includes? out "|   | 1   |\n"))))))

(deftest one-row-datatable-rendering-test
  (testing "a one-row table - a key/value pair - is printed from its cells: the
  vector of maps it gives is empty, and the report used to print nothing"
    (binding [output/*colored-output* false]
      (let [table (with-redefs [t/do-report (constantly nil)]
                    (-> (core/->feature-ast "Feature: t\n  Scenario: s\n    Given a book\n      | title | Dune |\n" {} 'user)
                        :scenarios first :steps first))]
        (is (= (str "  Given a book\n"
                    "      | title | Dune |\n")
               (render {:type :begin-step :step (assoc table :status :success)})))))))

(deftest no-kaocha-in-the-library-namespaces-test
  (testing "Kaocha n'est pas une dépendance de la bibliothèque : aucun namespace
  scenari.* ne le requiert, le lanceur clojure.test tourne sans lui"
    (is (empty? (for [f     (file-seq (io/file "src/scenari"))
                      :when (.isFile f)
                      sym   (tree-seq coll? seq (read-string (slurp f)))
                      :when (and (symbol? sym) (string/starts-with? (str sym) "kaocha"))]
                  [(str f) sym])))))
