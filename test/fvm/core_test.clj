(ns fvm.core-test
  (:require [clojure.test :refer :all]
            [fvm.core :as fvm]))

(def range-script
  [{:op :requires
    :value ["lib/std.edn"]}

   {:op :defop
    :name :range
    :value [{:op :push
             :value 0}
            {:op :eq?
             :then [{:op :pop}
                    {:op :pop}]
             :else [{:op :pop}
                    {:op :dup}
                    {:op :dec}
                    {:op :range}]}]}])

(def fact-script
  [{:op :requires
    :value ["lib/std.edn"]}

   {:op :defop
    :name :fact
    :value [{:op :push
             :value 0}
            {:op :eq?
             :then [{:op :pop}
                    {:op :pop}
                    {:op :push
                     :value 1}]
             :else [{:op :pop}
                    {:op :dup}
                    {:op :dec}
                    {:op :fact}
                    {:op :mul}]}]}])

(deftest interpreter-test
  (let [N 10
        final-state (fvm/interpret
                     {:code (concat range-script
                                    [{:op :push
                                      :value N}
                                     {:op :range}])})]
    (is (= (range 1 (inc N))
           (:stack final-state)))))

(deftest jit-test
  (testing "tail recursive ops are jitted"
    (let [N 100
          final-state (fvm/interpret
                       {:code (concat range-script
                                      [{:op :push
                                        :value N}
                                       {:op :range}])})
          compiled-fn (-> final-state :ops :range :compiled-trace)
          test-compiled-fn (fn [n]
                             (-> {:stack [n]
                                  :ops (:ops final-state)}
                                 compiled-fn
                                 :stack))]
      (is (= (range 1 6)
             (test-compiled-fn 5)))

      (is (= (range 1 201)
             (test-compiled-fn 200)))))

  (testing "non-tail recursive ops are interpreted succesfully"
    (let [N 100
          run-fact #(fvm/interpret
                     {:code (concat fact-script
                                    [{:op :push
                                      :value %}
                                     {:op :fact}])})
          final-state (run-fact N)
          fact-info (-> final-state :ops :fact)]      
      (is (:dont-jit? fact-info))

      (is (nil? (:compiled-trace fact-info)))

      (is (= (apply *' (range 1 6))
             (-> (run-fact 5) :stack first)))

      (is (= (apply *' (range 1 201))
             (-> (run-fact 200) :stack first))))))
