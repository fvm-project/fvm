(ns fvm.util
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn clean-trace
  [trace]
  (map #(-> % :code first)
       trace))

(defn ^Long curr-millis []
  (System/currentTimeMillis))

(defn next-obj [stream]
  (edn/read {:eof ::eof}
            stream))

(defn load-source
  "Load all instructions from an io/reader source (filename or io/resource)."
  [source]
  (try
    (with-open [r (io/reader source)]
      (let [stream (java.io.PushbackReader. r)]
        (loop [insn (next-obj stream)
               insns []]
          (if (not= ::eof insn)
            (recur (next-obj stream)
                   (conj insns insn))
            insns))))

    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e)))))

(defn print-stack-trace [ex]
  (let [{:keys [stack trace]} (ex-data ex)]
    (println "Stack:" stack)
    (println "Trace:")
    (doseq [insn trace]
      (print "\t")
      (prn insn))))

(defn push-all [coll stack]
  (let [rcoll (reverse coll)]
    (reduce (fn [acc x]
              (cons x acc))
            stack
            rcoll)))

(defn fastcat [& colls]
  (let [[stack & others] (reverse colls)]
    (reduce (fn [acc coll]
              (push-all coll acc))
            stack
            others)))
