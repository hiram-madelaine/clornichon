(ns scenari.v2.glue-test
  (:require [clojure.test :refer :all]
            [scenari.v2.core]
            [scenari.v2.glue :as glue]))

;; all-glues cache tests
(deftest all-glues-cache-invalidation-test
  (testing "a step defined in an already loaded namespace shows up in all-glues"
    (glue/all-glues)                                        ;; warm up the cache
    (binding [*ns* (find-ns 'scenari.v2.glue-test)]          ;; no new namespace, so the ns count doesn't change
      (eval '(scenari.v2.core/defgiven "a step defined after the cache was warmed up" [state] state)))
    (is (some #(= "a step defined after the cache was warmed up" (:step %)) (glue/all-glues))))
  (testing "a new namespace is read alone, the glues already found are not compiled again"
    (let [before (glue/all-glues)
          fresh  (create-ns (gensym "scenari.v2.glue-test.fresh"))]
      (try
        (binding [*ns* fresh]
          (refer-clojure)
          (eval '(scenari.v2.core/defgiven "a step of a namespace loaded after the cache" [state] state)))
        (let [after (glue/all-glues)]
          (is (some #(= "a step of a namespace loaded after the cache" (:step %)) after))
          (is (every? (fn [g] (some #(identical? (:expression g) (:expression %)) after)) before)))
        (finally (remove-ns (ns-name fresh))))))
  (testing "a glue unmapped from a namespace already read is gone"
    (let [sentence "a step unmapped after the cache"
          found?   (fn [] (some #(= sentence (:step %)) (glue/all-glues)))
          unmap!   #(ns-unmap 'scenari.v2.glue-test 'a-step-unmapped-after-the-cache)
          define!  #(binding [*ns* (find-ns 'scenari.v2.glue-test)]
                      (eval (list 'scenari.v2.core/defgiven sentence '[state] 'state)))]
      (testing "once a namespace is loaded"
        (define!)
        (is (found?))
        (unmap!)
        (let [fresh (create-ns (gensym "scenari.v2.glue-test.fresh"))]
          (try (is (not (found?)))
               (finally (remove-ns (ns-name fresh))))))
      (testing "once its namespace is invalidated by name"
        (define!)
        (is (found?))
        (unmap!)
        (is (nil? (glue/invalidate-glues-cache! 'scenari.v2.glue-test)))
        (is (not (found?)))))))

(deftest glue-name-collision-test
  (let [define #(binding [*ns* (find-ns 'scenari.v2.glue-test)] (eval %))]
    (define '(scenari.v2.core/defgiven "a name made twice" [state] :first))
    (testing "une autre phrase qui fait le même nom de var lève, et le premier glue reste"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"\"a name made-twice\" and \"a name made twice\" make the same var name"
                            (define '(scenari.v2.core/defgiven "a name made-twice" [state] :second))))
      (is (= "a name made twice"
             (:step (meta (ns-resolve 'scenari.v2.glue-test 'a-name-made-twice))))))
    (testing "la même phrase repasse : c'est un rechargement"
      (is (var? (define '(scenari.v2.core/defgiven "a name made twice" [state] :again)))))
    (testing "une regex et une chaîne de même texte sont la même phrase"
      (is (var? (define '(scenari.v2.core/defgiven #"a name made twice" [state] :regex)))))))

;; ns-proximity-score tests
(deftest ns-proximity-score-test
  (testing "Calculates proximity score between namespaces"
    (is (= 0 (glue/ns-proximity-score "some.ns" "other.ns")))
    (is (= 1 (glue/ns-proximity-score "some.ns" "some.other")))
    (is (= 2 (glue/ns-proximity-score "some.ns.path" "some.ns.other")))
    (is (= 3 (glue/ns-proximity-score "some.ns.path.one" "some.ns.path.two")))
    (is (= 0 (glue/ns-proximity-score "some" "other")))
    (is (= 0 (glue/ns-proximity-score "" "some")))
    (is (= 0 (glue/ns-proximity-score "some" "")))
    (is (= 1 (glue/ns-proximity-score "" "")))
    (is (= 1 (glue/ns-proximity-score "scenari" "scenari")))
    (is (= 3 (glue/ns-proximity-score "scenari.v2.test" "scenari.v2.test")))))

;; find-closest-glues-by-ns tests
(deftest find-closest-glues-by-ns-test
  (testing "Finds closest glues based on namespace proximity"
    (let [matched-glues [{:ns 'some.ns.far :name 'test-step}
                         {:ns 'some.ns :name 'other-step}
                         {:ns 'other.ns :name 'another-step}]]
      (is (= 2 (count (glue/find-closest-glues-by-ns matched-glues 'some.ns.feature))))
      (is (every? #(contains? #{'some.ns.far 'some.ns} (:ns %))
                 (glue/find-closest-glues-by-ns matched-glues 'some.ns.feature))))))

(deftest multiple-glues-same-score-test
  (testing "When there are multiple glues with same proximity score"
    (let [matched-glues [{:ns 'scenari.v2.glue :name 'test-step}
                         {:ns 'scenari.v2.other :name 'other-step}
                         {:ns 'other.ns :name 'another-step}]]
      (is (= 2 (count (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.test))))
      (is (every? #(contains? #{'scenari.v2.glue 'scenari.v2.other} (:ns %))
                 (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.test))))))

(deftest real-world-namespace-test
  (testing "With real-world namespace examples"
    (let [matched-glues [{:ns 'scenari.v2.glue :name 'test-step}
                         {:ns 'scenari.v2.other-glue.glue :name 'other-step}
                         {:ns 'scenari.other :name 'another-step}]]
      ;; Same namespace matches exactly
      (is (= 1 (count (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.glue))))
      (is (= 'scenari.v2.glue (:ns (first (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.glue)))))

      ;; Namespaces at same depth but different names
      (is (= 2 (count (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.feature))))
      (is (every? #(contains? #{'scenari.v2.glue 'scenari.v2.other-glue.glue} (:ns %))
                 (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.feature)))

      ;; Feature namespace is deeper than glue namespaces
      (is (= 2 (count (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.feature.test))))
      (is (every? #(contains? #{'scenari.v2.glue 'scenari.v2.other-glue.glue} (:ns %))
                 (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.feature.test))))))

;; find-glue-by-step-regex tests
(deftest find-glue-by-step-regex-test
  (testing "Finding glue with exact match"
    (let [step {:sentence "I do something special"}
          ns-feature 'test.ns
          glues [{:step "I do something special"
                  :ns 'test.ns
                  :name 'exact-match-fn}]]
      (is (= 'exact-match-fn (:name (glue/find-glue-by-step-regex step ns-feature glues))))
      (is (= 'test.ns (:ns (glue/find-glue-by-step-regex step ns-feature glues))))))

  (testing "Finding glue with regex pattern"
    (let [step {:sentence "I count 42 items"}
          ns-feature 'test.ns
          glues [{:step "I count {number} items"
                  :ns 'test.ns
                  :name 'matching-fn}]]
      (is (= 'matching-fn (:name (glue/find-glue-by-step-regex step ns-feature glues))))))

  (testing "Finding glue with string pattern"
    (let [step {:sentence "I use \"test-value\" as parameter"}
          ns-feature 'test.ns
          glues [{:step "I use {string} as parameter"
                  :ns 'test.ns
                  :name 'string-param-fn}]]
      (is (= 'string-param-fn (:name (glue/find-glue-by-step-regex step ns-feature glues))))))

  (testing "Multiple matching glues with different namespace proximity"
    (let [step {:sentence "I do common action"}
          ns-feature 'test.ns.feature
          glues [{:step "I do common action"
                  :ns 'other.ns
                  :name 'first-fn}
                 {:step "I do common action"
                  :ns 'test.ns.glue
                  :name 'second-fn}]]
      ;; It should prefer the glue with namespace closer to feature namespace
      (is (= 'second-fn (:name (glue/find-glue-by-step-regex step ns-feature glues))))
      (is (= 'test.ns.glue (:ns (glue/find-glue-by-step-regex step ns-feature glues))))))

  (testing "With token replacement in sentence"
    (let [step {:sentence "The value is 123"}
          ns-feature 'test.ns
          glues [{:step "The value is {number}"
                  :ns 'test.ns
                  :name 'number-fn}]]
      (is (= 'number-fn (:name (glue/find-glue-by-step-regex step ns-feature glues)))))))

(deftest find-glue-with-conflict-test
  (testing "With exact conflict in different namespaces - should throw exception"
    (let [step {:sentence "Duplicate in different namespaces"}
          ns-feature 'test.ns.feature
          glues [{:step "Duplicate in different namespaces"
                  :ns 'test.ns.one
                  :name 'first-fn}
                 {:step "Duplicate in different namespaces"
                  :ns 'test.ns.two
                  :name 'second-fn}]]
      ;; Both namespaces have the same proximity score to feature, should throw
      (is (thrown-with-msg?
            RuntimeException
            #"2 matching functions were found for the following step sentence"
            (with-redefs [clojure.test/do-report (fn [_m] nil)]
              (glue/find-glue-by-step-regex step ns-feature glues)))))))

(deftest simple-missing-glue-test
  (testing "No matching glue returns nil"
    (let [step {:sentence "This has no matching glue"}
          ns-feature 'test.ns
          glues []]
      (is (nil? (with-redefs [clojure.test/do-report (fn [_m] nil)]
                  (glue/find-glue-by-step-regex step ns-feature glues)))))))


(comment
  (run-tests))
(defn- match-name
  "Le nom du glue qui matche la phrase, nil sinon. `do-report` est neutralisé :
  l'absence de match émet un :missing-step, que ce test n'a pas à afficher."
  [step-expression sentence]
  (with-redefs [clojure.test/do-report (fn [_] nil)]
    (:name (glue/find-glue-by-step-regex {:sentence sentence} 'test.ns
                                         [{:step step-expression :ns 'test.ns :name 'g}]))))

(deftest cucumber-expression-test
  (testing "les tokens de cucumber expressions, en plus des deux connus"
    (are [expression sentence] (= 'g (match-name expression sentence))
      "j'ai {int} pommes"        "j'ai 42 pommes"
      "j'ai {int} pommes"        "j'ai -42 pommes"
      "le prix est {float} euro" "le prix est 12.5 euro"
      "j'ai {word} pommes"       "j'ai trois pommes"
      "j'ai {string} pommes"     "j'ai \"trois\" pommes"))

  (testing "{number} n'est pas un type cucumber : il est redéfini pour les glues
  déjà écrits, et accepte désormais le signe et les décimales"
    (are [sentence] (= 'g (match-name "la valeur est {number}" sentence))
      "la valeur est 123"
      "la valeur est -3"
      "la valeur est 2.5"))

  (testing "texte optionnel et alternance"
    (are [expression sentence] (= 'g (match-name expression sentence))
      "j'ai 1 pomme(s)"         "j'ai 1 pomme"
      "j'ai 2 pomme(s)"         "j'ai 2 pommes"
      "le cafe est chaud/froid" "le cafe est froid"))

  (testing "une barre oblique litterale s'echappe - le cas se trouve dans de
  vraies features, `les dossiers {string} / {string}`"
    (is (nil? (match-name "les dossiers {string} \\/ {string}" "les dossiers \"a\" | \"b\"")))
    (is (= 'g (match-name "les dossiers {string} \\/ {string}" "les dossiers \"a\" / \"b\""))))

  (testing "une phrase ancree reste lue comme une regex"
    (is (= 'g (match-name "^j'invoque (.*) sur l'URL$" "j'invoque un GET sur l'URL"))))

  (testing "un token inconnu nomme le glue fautif, au lieu d'une
  PatternSyntaxException sans provenance"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"glue my.glue/x : (?s).*Undefined parameter type 'produit'"
                          (glue/step->expression {:step "la valeur {produit}" :ns 'my.glue :name 'x})))))

(deftest regex-glue-test
  (testing "un glue defini par un litteral #\"...\" reste une regex quoi qu'elle
  contienne. Lue comme une expression, son `([^\"]*)` deviendrait du texte
  optionnel et son `\\\"` leverait, ce qui cassait tous les glues regex."
    (is (= 'g (match-name #"l'utilisateur recherche l'expression \"([^\"]*)\""
                          "l'utilisateur recherche l'expression \"jack london\"")))
    (is (= ["jack london"]
           (glue/step-args {:step #"l'utilisateur recherche l'expression \"([^\"]*)\""}
                           "l'utilisateur recherche l'expression \"jack london\""))))

  (testing "une regex matche la phrase entiere, comme le faisait re-matches"
    (is (nil? (match-name #"l'utilisateur mange" "hier l'utilisateur mange une pomme")))
    (is (= 'g (match-name #"l'utilisateur mange" "l'utilisateur mange"))))

  (testing "une alternance de premier niveau n'est pas coupee par l'ancrage"
    (is (= 'g (match-name #"il fait chaud|il fait froid" "il fait froid")))))
