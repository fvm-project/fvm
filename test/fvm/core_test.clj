(ns fvm.core-test
  (:require [clojure.test :refer :all]
            [fvm.core :as fvm]))

(def to-zero-script
  (concat fvm/std
          [{:op :push
            :value [{:op :push
                     :value 0}
                    {:op :eq
                     :then [{:op :pop}
                            {:op :pop}]
                     :else [{:op :pop}
                            {:op :dup}
                            {:op :dec}
                            {:op :to-zero}]}]}
           {:op :push
            :value :to-zero}
           {:op :defop}]))

(deftest fvm-test
  (let [N 10
        final-state (fvm/interpret
                     {:code (concat to-zero-script
                                    [{:op :push
                                      :value N}
                                     {:op :to-zero}])})
        compiled-fn (-> final-state :ops :to-zero :compiled-trace)]
    (testing "interpreter"
      (is (= (range 1 (inc N))
             (:stack final-state))))

    (testing "jit"
      (is (= (range 1 6)
             (:stack (compiled-fn [5]))))

      (is (= (range 1 21)
             (:stack (compiled-fn [20])))))))
