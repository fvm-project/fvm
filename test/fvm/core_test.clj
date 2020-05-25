(ns fvm.core-test
  (:require [clojure.test :refer :all]
            [fvm.core :as fvm]
            [fvm.std :refer [std]]))

(def to-zero-script
  (concat std
          [{:op :defop
            :name :to-zero
            :value [{:op :push
                     :value 0}
                    {:op :eq?
                     :then [{:op :pop}
                            {:op :pop}]
                     :else [{:op :pop}
                            {:op :dup}
                            {:op :dec}
                            {:op :to-zero}]}]}]))

(deftest interpreter-test
  (let [N 10
        final-state (fvm/interpret
                     {:code (concat to-zero-script
                                    [{:op :push
                                      :value N}
                                     {:op :to-zero}])})]
    (is (= (range 1 (inc N))
           (:stack final-state)))))

(deftest jit-test
  (let [N 10
        final-state (fvm/interpret
                     {:code (concat to-zero-script
                                    [{:op :push
                                      :value N}
                                     {:op :to-zero}])})
        compiled-fn (-> final-state :ops :to-zero :compiled-trace)]
    (is (= (range 1 6)
           (:stack (compiled-fn [5]))))

    (is (= (range 1 21)
           (:stack (compiled-fn [20]))))))
