(ns build
  (:require
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as dd]
    [scenari.meta :refer [version tag]]))

(def lib-name 'io.github.hiram-madelaine/clornichon)
(def jar-content "target/classes")
(def basis
  ;; Kaocha optional in the pom: cljdoc loads the kaocha.* namespaces with it,
  ;; tools.deps and Maven do not pass it on to who depends on Clornichon
  (b/create-basis {:project "deps.edn"
                   :extra   {:deps {'lambdaisland/kaocha {:mvn/version "1.87.1366" :optional true}}}}))
(def jar-file (format "target/%s-%s.jar" (name lib-name) version))

(defn clean "Clean target dir" [_]
  (println "Clean target directory...")
  (b/delete {:path "target"}))

(defn jar "Build jar and pom into target dir" [_]
  (clean nil)
  (println "Copy sources...")
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir jar-content})
  (println "Write pom into target/META-INF...")
  (b/write-pom {:class-dir jar-content
                :lib       lib-name
                :version   version
                :basis     basis
                :src-dirs  ["src"]
                :pom-data  [[:description "BDD for Clojure: Gherkin specifications executed by Clojure, with Cucumber's parser and expressions, clojure.test and Kaocha."]
                            [:url "https://github.com/hiram-madelaine/clornichon"]
                            [:licenses
                             [:license
                              [:name "MIT License"]
                              [:url "https://opensource.org/licenses/MIT"]]]]
                :scm       {:connection          "scm:git:git://github.com:hiram-madelaine/clornichon.git"
                            :developerConnection "scm:git:ssh://github.com:hiram-madelaine/clornichon.git"
                            :url                 "https://github.com/hiram-madelaine/clornichon"
                            :tag                 tag}})
  (println "Build jar...")
  (b/jar {:class-dir jar-content
          :jar-file  jar-file})

  (println "Install jar and pom to local maven repository...")
  (b/install {:class-dir jar-content
              :lib       lib-name
              :version   version
              :basis     basis
              :src-dirs  ["src"]
              :jar-file  jar-file}))

(defn deploy "Deploy the JAR to Clojars." [_]
  (let [pom-path (b/pom-path {:class-dir jar-content
                              :lib       lib-name})]
    (println (format "Deploy jar %s and pom %s to clojars " jar-file pom-path))
    (dd/deploy {:installer :remote
                :artifact  (b/resolve-path jar-file)
                :pom-file  (b/pom-path {:class-dir jar-content
                                        :lib       lib-name})})))

(comment
  (jar nil)
  (deploy nil))