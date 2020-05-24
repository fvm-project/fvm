(ns fvm.util)

(defn defop
  [name body]
  [{:op :push
    :value body}
   {:op :push
    :value name}
   {:op :defop}])

(defn clean-trace
  [trace]
  (map (fn [state]
         (-> state
             (assoc :insn (-> state :code first))
             (dissoc :code :ops)))
       trace))

(defn ^Long curr-millis []
  (System/currentTimeMillis))
