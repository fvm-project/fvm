(ns fvm.core-test
  (:require [clojure.test :refer :all]
            [fvm.core :as fvm]
            [fvm.util :as u]))

(defmacro timing [expr]
  `(let [start-ms# (u/curr-millis)
         _# ~expr
         end-ms# (u/curr-millis)]
     (- end-ms# start-ms#)))

(deftest interpreter-test
  (testing "self tail recursive ops are jitted"
    (let [range-script [{:op :requires
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
                                         {:op :range}]}]}]

          run-interpreted
          #(fvm/interpret
            {:code (concat range-script
                           [{:op :push
                             :value %}
                            {:op :range}])})

          test-interpreted-op #(:stack (run-interpreted %))
          N 100
          final-state (run-interpreted N)
          compiled-op (-> final-state :ops :range :compiled-trace)
          test-compiled-op (fn [n]
                             (-> {:stack [n]
                                  :ops (:ops final-state)}
                                 compiled-op
                                 :stack))]
      (testing "correctness"
        (is (= (range 1 6)
               (test-compiled-op 5)))

        (is (= (range 1 201)
               (test-compiled-op 200))))

      (testing "performance"
        (is (<= (timing (test-compiled-op 300))
                (timing (test-interpreted-op 300)))))))

  (testing "non-tail recursive ops are excluded from jit"
    (let [fact-script [{:op :requires
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
                                        {:op :mul}]}]}]
          N 100
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
