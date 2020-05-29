(ns fvm.core
  (:gen-class)
  (:require [fvm.fvm :as fvm]
            [fvm.ednlang :as ednlang]))

(defn -main
  [filename]
  (fvm/interpret {::fvm/state {::fvm/nodes [{::fvm/type ::ednlang/requires
                                             ::ednlang/value [filename]}]}}))
