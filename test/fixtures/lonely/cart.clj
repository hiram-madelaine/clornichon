(ns fixtures.lonely.cart
  "A feature no suite loads and nobody requires: `kaocha.type.scenari/-load` has
  to find it under its test-paths. Its glue sits under the glue-paths, and the
  feature does not require it either - see `load-requires-the-test-paths-test`."
  (:require [scenari.v2.core :refer [deffeature]]))

(deffeature lonely-cart
  "Feature: lonely cart
  Scenario: s
    Given a lonely cart")
