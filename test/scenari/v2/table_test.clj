(ns scenari.v2.table-test
  (:require [clojure.test :refer [deftest testing is]]
            [scenari.v2.core :as core]
            [scenari.v2.table :as table]))

(defn- table-of
  "The datatable a step receives, parsed from the gherkin `rows`."
  [rows]
  ;; ->feature-ast reports :missing-step for the unresolved glue, muted here
  (binding [clojure.test/report (constantly nil)]
    (-> (core/->feature-ast (str "Feature: f\n  Scenario: s\n    Given a table\n" rows) {} 'user)
        :scenarios first :steps first :params last :val)))

(deftest step-argument-unchanged-test
  (testing "a step still receives the vector of maps keyed by the first row"
    (is (= [{:name "Alice" :age "30"} {:name "Bob" :age "25"}]
           (table-of "| name | age |\n| Alice | 30 |\n| Bob | 25 |")))))

(deftest cells-test
  (testing "the cells as written, header row first"
    (is (= [["name" "age"] ["Alice" "30"]]
           (table/cells (table-of "| name | age |\n| Alice | 30 |")))))
  (testing "a one-row table keeps its only row, which the vector of maps drops"
    (is (= [] (table-of "| title | Dune |")))
    (is (= [["title" "Dune"]] (table/cells (table-of "| title | Dune |")))))
  (testing "a vector of maps built by hand is read back from its keys"
    (is (= [["a" "b"] ["1" "2"]] (table/cells [{:a "1" :b "2"}])))))

(deftest as-list-test
  (testing "a single column, its first row included"
    (is (= ["admin" "reader"] (table/as-list (table-of "| admin |\n| reader |")))))
  (testing "a single row"
    (is (= ["admin" "reader"] (table/as-list (table-of "| admin | reader |")))))
  (testing "anything wider throws, naming the shape"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"2 rows of 2 cells"
                          (table/as-list (table-of "| a | b |\n| c | d |"))))))

(deftest as-map-test
  (testing "a key/value table, keys as keywords"
    (is (= {:title "Dune" :isbn "978"}
           (table/as-map (table-of "| title | Dune |\n| isbn | 978 |")))))
  (testing "a table that is not two columns wide throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"two columns"
                          (table/as-map (table-of "| a | b | c |")))))
  (testing "a key written twice throws rather than drop a value"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"written twice"
                          (table/as-map (table-of "| a | 1 |\n| a | 2 |"))))))

(deftest transpose-test
  (testing "headers in the first column give the same vector of maps"
    (is (= [{:name "Alice" :age "30"} {:name "Bob" :age "25"}]
           (table/transpose (table-of "| name | Alice | Bob |\n| age | 30 | 25 |")))))
  (testing "the column order of the feature file holds past 8 columns"
    (let [names (map str "abcdefghij")]
      (is (= (map keyword names)
             (keys (first (table/transpose
                           (table-of (apply str (map #(str "| " % " | x |\n") names)))))))))))
