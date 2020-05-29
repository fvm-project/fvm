(ns fvm.util)

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

(defn ^Long curr-millis []
  (System/currentTimeMillis))

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
