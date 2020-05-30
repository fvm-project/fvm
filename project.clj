(defproject fvm-project/fvm "0.1.0"
  :description "fvm is a Clojure library for writing self-optimizing interpreters"
  :url "https://github.com/fvm-project/fvm"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]]
  :target-path "target/%s"
  :profiles {:dev {:global-vars {*warn-on-reflection* true}}})
