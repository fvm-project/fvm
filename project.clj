(defproject fvm "0.1.0-SNAPSHOT"
  :description "fvm is an extensible self-optimizing VM"
  :url "https://github.com/divs1210/fvm"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :main ^:skip-aot fvm.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:plugins [[lein-shell "0.5.0"]]
                   :global-vars {*warn-on-reflection* true
                                 ;; *unchecked-math* :warn-on-boxed
                                 }}}
  :plugins [[lein-eftest "0.5.9"]
            [lein-cloverage "1.1.2"]]
  :aliases
  {"native"
   ["shell"
    "native-image"
    "--no-fallback"
    "--allow-incomplete-classpath"
    "--report-unsupported-elements-at-runtime"
    "--initialize-at-build-time"
    "-jar" "./target/uberjar/${:uberjar-name:-${:name}-${:version}-standalone.jar}"
    "-H:Name=./target/${:name}"]})
