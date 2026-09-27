(ns scenari.v2.table
  "Other readings of a datatable than the vector of maps a step receives.

    (defgiven \"a book\" [state table]
      (assoc state :book (table/as-map table)))

  Each function takes that vector as the step received it: it carries the cells
  of the feature file in its metadata, header row included, so a table whose
  first row is not a header - a list, a key/value table - can still be read.")

(defn cells
  "The table as the feature file wrote it: a vector of rows, each a vector of
  strings, the header row first. A vector of maps built by hand, without the
  parser's metadata, is read back from its keys and values."
  [table]
  (or (:scenari/cells (meta table))
      (when-let [headers (keys (first table))]
        (into [(mapv name headers)]
              (map (fn [row] (mapv #(get row % "") headers)))
              table))))

(defn as-list
  "A single-column or a single-row table as the vector of its values, first row
  included.

    | admin  |
    | reader |      => [\"admin\" \"reader\"]"
  [table]
  (let [c (cells table)]
    (cond (every? #(= 1 (count %)) c) (mapv first c)
          (= 1 (count c))             (first c)
          :else (throw (ex-info (str "as-list wants a single column or a single row, got "
                                     (count c) " rows of " (count (first c)) " cells")
                                {:cells c})))))

(defn as-map
  "A two-column table as a map: keys from the first column, as keywords like the
  headers of the vector of maps, values from the second.

    | title | Dune |
    | isbn  | 978  |  => {:title \"Dune\" :isbn \"978\"}

  A key written twice throws rather than keep one of the two values."
  [table]
  (let [c (cells table)]
    (when-not (every? #(= 2 (count %)) c)
      (throw (ex-info (str "as-map wants two columns, got " (count (first c))) {:cells c})))
    (when-not (apply distinct? (map first c))
      (throw (ex-info (str "as-map: a key is written twice in " (mapv first c)) {:cells c})))
    (into {} (map (fn [[k v]] [(keyword k) v])) c)))

(defn transpose
  "A table whose headers are in the first column, read as the vector of maps it
  would have given with its headers in the first row.

    | name | Alice | Bob |
    | age  | 30    | 25  |  => [{:name \"Alice\" :age \"30\"} {:name \"Bob\" :age \"25\"}]"
  [table]
  (let [[headers & rows] (apply mapv vector (cells table))]
    ;; array-map, like scenari.v2.core/argument->params: the column order holds
    (mapv #(apply array-map (interleave (map keyword headers) %)) rows)))
