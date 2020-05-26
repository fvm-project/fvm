(ns profiler
  (:require [clj-async-profiler.core :as prof]
            [fvm.core :as fvm]))

(defn -main []
  (prof/profile
   (fvm/-main "test/profile.edn"))
  (prof/serve-files 8080))
