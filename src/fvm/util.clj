(ns fvm.util)

(defn defop
  [name body]
  [{:op :push
    :value body}
   {:op :push
    :value name}
   {:op :defop}])
