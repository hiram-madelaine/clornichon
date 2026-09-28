(ns scenari.utils
  (:require [clojure.string :as string]))

(def ^:private ansi-codes
  {:reset "0" :bold "1" :black "30" :red "31" :green "32" :yellow "33"
   :blue "34" :purple "35" :cyan "36" :white "37" :grey "90"})

(defn- colored?
  "Kaocha's --color/--no-color when Kaocha is loaded, colored otherwise. The var
  is looked up, not required: the clojure.test runner must not load Kaocha,
  which is not a dependency of the library.
  ponytail: without Kaocha nothing turns the colors off; read NO_COLOR here the
  day someone pipes a clojure.test run."
  []
  (if-let [v (resolve 'kaocha.output/*colored-output*)] @v true))

(defn ansi-code
  "Escape sequence for a color key, or for a vector of them ([:bold :cyan]).
  Empty string when colored output is off, so it stays safe to interpolate."
  [color]
  (if (colored?)
    (format "\u001b[%sm" (->> (if (sequential? color) color [color])
                              (map #(get ansi-codes % "0"))
                              (string/join ";")))
    ""))

(defn color-str [color & xs]
  (str (ansi-code color) (apply str xs) (ansi-code :reset)))
