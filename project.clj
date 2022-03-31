(defn env [x] (eval (System/getenv x)))

(defproject precisely/rapids-active-doc "0.0.3"
  :description "Rapids active document"
  :min-lein-version "2.9.7"
  :url "http://github.com/precisely/rapids-active-doc"
  :license {:name "All rights reserved"
            :url  ""}
  :plugins [[lein-pprint "1.3.2"]
            [s3-wagon-private "1.3.4"]]
  :clean-targets ^{:protect false} ["target"]
  :repositories {"precisely" {:url        "s3p://precisely-maven-repo/"
                              :username   #=(env "MAVEN_REPO_AWS_ACCESS_KEY_ID")
                              :passphrase #=(env "MAVEN_REPO_AWS_ACCESS_KEY_SECRET")}}

  :deploy-repositories [["precisely" {:url           "s3p://precisely-maven-repo/"
                                      :username      #=(env "MAVEN_REPO_AWS_ACCESS_KEY_ID")
                                      :passphrase    #=(env "MAVEN_REPO_AWS_ACCESS_KEY_SECRET")
                                      :sign-releases false}]]
  :dependencies [[org.clojure/clojure]
                 [precisely/rapids]
                 [metosin/malli]
                 [org.clojure/core.match "1.0.0"]]
  :managed-dependencies [[org.clojure/clojure "1.10.1"]
                         [precisely/rapids "0.9.6"]
                         [metosin/malli "0.8.0"]]
  :profiles {:dev {:source-paths ["src"]
                   :plugins      [[s3-wagon-private "1.3.4"]
                                  [lein-cloverage "1.2.2"]]}}
  :repl-options {:init-ns rapids.active-doc}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"])

