(ns scenari.utils
  (:require [clojure.string :as string]
            [kaocha.output :as output]))

(def ^:private ansi-codes
  {:reset "0" :bold "1" :black "30" :red "31" :green "32" :yellow "33"
   :blue "34" :purple "35" :cyan "36" :white "37" :grey "90"})

(defn ansi-code
  "Escape sequence for a color key, or for a vector of them ([:bold :cyan]).
  Empty string when colored output is off, so it stays safe to interpolate.
  Honours kaocha's --color/--no-color through kaocha.output/*colored-output*."
  [color]
  (if output/*colored-output*
    (format "\u001b[%sm" (->> (if (sequential? color) color [color])
                              (map #(get ansi-codes % "0"))
                              (string/join ";")))
    ""))

(defn color-str [color & xs]
  (str (ansi-code color) (apply str xs) (ansi-code :reset)))
