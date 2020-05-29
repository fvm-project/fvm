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
    (is (= [5] (apply-op ::ednlang/add stack-in)))
    (is (= [-1] (apply-op ::ednlang/sub stack-in)))
    (is (= [6] (apply-op ::ednlang/mul stack-in)))
    (is (= [2/3] (apply-op ::ednlang/div stack-in)))))

(deftest logic-test
  (let [eq-insn {::fvm/type ::ednlang/eq?
                 ::ednlang/then [{::fvm/type ::ednlang/pop}
                                 {::fvm/type ::ednlang/pop}
                                 {::fvm/type ::ednlang/push
                                  ::ednlang/value true}]
                 ::ednlang/else [{::fvm/type ::ednlang/pop}
                                 {::fvm/type ::ednlang/pop}
                                 {::fvm/type ::ednlang/push
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
         (::ednlang/stack (run {::fvm/nodes [{::fvm/type ::ednlang/push
                                              ::ednlang/value :a}]
                                ::ednlang/stack [1 2]}))))
  (is (= [2] (apply-op ::ednlang/pop [1 2])))
  (is (= [:a :a] (apply-op ::ednlang/dup [:a])))
  (is (= [2 1] (apply-op ::ednlang/swap [1 2]))))

(deftest macros-test
  (is (= {::fvm/nodes []
          ::ednlang/stack [1 1]}
         (run {::fvm/nodes [{::fvm/type ::ednlang/push
                             ::ednlang/value [{::fvm/type ::ednlang/push
                                               ::ednlang/value 1}
                                              {::fvm/type ::ednlang/dup}]}
                            {::fvm/type ::ednlang/call}]})))
  (is (= {::fvm/nodes []
          ::ednlang/stack [7]}
         (run {::fvm/nodes [{::fvm/type ::ednlang/defop
                             ::ednlang/name ::ednlang/add-2
                             ::ednlang/value [{::fvm/type ::ednlang/push
                                               ::ednlang/value 2}
                                              {::fvm/type ::ednlang/add}]}

                            {::fvm/type ::ednlang/push
                             ::ednlang/value 5}
                            {::fvm/type ::ednlang/add-2}]}))))

(deftest io-test
  (is (= [3]
         (::ednlang/stack
          (with-in-str "1 2"
            (run {::fvm/nodes [{::fvm/type ::ednlang/read}
                               {::fvm/type ::ednlang/read}
                               {::fvm/type ::ednlang/add}]})))))
  (is (= "hi"
         (with-out-str
           (apply-op ::ednlang/print ["hi"])))))

(deftest requires-test
  (is (= {::fvm/nodes []
          ::ednlang/stack [2]}
         (run {::fvm/nodes [{::fvm/type ::ednlang/requires
                             ::ednlang/value ["test/test.edn"]}
                            {::fvm/type ::ednlang/push
                             ::ednlang/value 1}
                            {::fvm/type :test/inc}]}))))
