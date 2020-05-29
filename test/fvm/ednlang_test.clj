(ns fvm.ednlang-test
  (:require [clojure.test :refer :all]
            [fvm.fvm :as fvm]
            [fvm.ednlang :as ednlang]))

(defn run [state]
  (fvm/interpret {::fvm/state state}))

(defn apply-op [op stack-in]
  (::ednlang/stack (run {::fvm/nodes [{::fvm/type op}]
                         ::ednlang/stack stack-in})))

(deftest math-test
  (let [stack-in [2 3]]
    (is (= [5] (apply-op :add stack-in)))
    (is (= [-1] (apply-op :sub stack-in)))
    (is (= [6] (apply-op :mul stack-in)))
    (is (= [2/3] (apply-op :div stack-in)))))

(deftest logic-test
  (let [eq-insn {::fvm/type :eq?
                 :then [{::fvm/type :pop}
                        {::fvm/type :pop}
                        {::fvm/type :push
                         ::ednlang/value true}]
                 :else [{::fvm/type :pop}
                        {::fvm/type :pop}
                        {::fvm/type :push
                         ::ednlang/value false}]}]
    (is (= {::fvm/nodes []
            ::ednlang/stack [true]}
           (run {::fvm/nodes [eq-insn]
                 ::ednlang/stack [:a :a]})))
    (is (= {::fvm/nodes []
            ::ednlang/stack [false]}
           (run {::fvm/nodes [eq-insn]
                 ::ednlang/stack [:a :b]})))))

(deftest stack-test
  (is (= [:a 1 2]
         (::ednlang/stack (run {::fvm/nodes [{::fvm/type :push
                                              ::ednlang/value :a}]
                                ::ednlang/stack [1 2]}))))
  (is (= [2] (apply-op :pop [1 2])))
  (is (= [:a :a] (apply-op :dup [:a])))
  (is (= [2 1] (apply-op :swap [1 2]))))

(deftest macros-test
  (is (= {::fvm/nodes []
          ::ednlang/stack [1 1]}
         (run {::fvm/nodes [{::fvm/type :push
                             ::ednlang/value [{::fvm/type :push
                                            ::ednlang/value 1}
                                           {::fvm/type :dup}]}
                            {::fvm/type :call}]})))
  #_(is (= {::fvm/nodes []
            ::ednlang/stack nil
            :ops {:add-2 {:name :add-2
                          ::ednlang/value [{::fvm/type :push
                                         ::ednlang/value 2}
                                        {::fvm/type :add}]
                          :dont-jit? true}}}
           (run {::fvm/nodes [{::fvm/type :defop
                               :name :add-2
                               ::ednlang/value [{::fvm/type :push
                                              ::ednlang/value 2}
                                             {::fvm/type :add}]}]}))))

(deftest io-test
  (is (= [3]
         (::ednlang/stack
          (with-in-str "1 2"
            (run {::fvm/nodes [{::fvm/type :read}
                               {::fvm/type :read}
                               {::fvm/type :add}]})))))
  (is (= "hi"
         (with-out-str
           (apply-op :print ["hi"])))))

(deftest requires-test
  #_(is (= {::fvm/nodes []
            :ops {:test/inc {:name :test/inc
                             ::ednlang/value [{::fvm/type :push, ::ednlang/value 1}
                                           {::fvm/type :add}]
                             :dont-jit? true}},
            ::ednlang/stack nil}
           (run {::fvm/nodes [{::fvm/type :requires
                               ::ednlang/value ["test/test.edn"]}]}))))
