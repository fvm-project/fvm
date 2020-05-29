(ns fvm.core
  (:gen-class)
  (:require [fvm.fvm :as fvm]
            [fvm.ednlang :as ednlang]
            [fvm.util :as u]))

(defn -main
  [filename]
  (try
    (fvm/interpret {::fvm/state {::fvm/nodes [{::fvm/type ::ednlang/requires
                                               ::ednlang/value [filename]}]}})
    (catch Throwable e
      (println (.getMessage e))
      (u/print-stack-trace e)
      (System/exit 1))))
