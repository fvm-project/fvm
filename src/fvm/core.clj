(ns fvm.core
  (:gen-class)
  (:refer-clojure :exclude [compile])
  (:require [clojure.pprint :as pp]
            [fvm.util :as u]))

(defmulti eval-insn
  (fn [insn state]
    (:op insn)))

(defn primitives []
  (-> eval-insn
      methods
      (dissoc :default)))

(defn primitive? [op]
  (contains? (primitives)
             op))


;; IO
;; ==
(defmethod eval-insn :io/print
  [insn state]
  (let [[x & rest] (:stack state)]
    (print x)
    (assoc state :stack rest)))


;; Memory
;; ======
(defmethod eval-insn :push
  [insn state]
  (update state :stack
          #(cons (:value insn) %)))

(defmethod eval-insn :pop
  [insn state]
  (update state :stack rest))

(defmethod eval-insn :dup
  [insn state]
  (update state :stack
          (fn [stack]
            (cons (first stack) stack))))


;; Arithmetic
;; ==========
(defmethod eval-insn :add
  [insn state]
  (let [stack (:stack state)
        [x y & rest] stack
        res (+' ^BigDecimal x
                ^BigDecimal y)]
    (assoc state :stack
           (cons res rest))))

(defmethod eval-insn :sub
  [insn state]
  (let [stack (:stack state)
        [x y & rest] stack
        res (- ^BigDecimal x
               ^BigDecimal y)]
    (assoc state :stack
           (cons res rest))))

(defmethod eval-insn :mul
  [insn state]
  (let [stack (:stack state)
        [x y & rest] stack
        res (*' ^BigDecimal x
                ^BigDecimal y)]
    (assoc state :stack
           (cons res rest))))

(defmethod eval-insn :div
  [insn state]
  (let [stack (:stack state)
        [x y & rest] stack
        res (/ ^BigDecimal x
               ^BigDecimal y)]
    (assoc state :stack
           (cons res rest))))

(defmethod eval-insn :neg
  [insn state]
  (let [stack (:stack state)
        [x & rest] stack
        res (- ^BigDecimal x)]
    (assoc state :stack
           (cons res rest))))


;; Logic
;; =====
(defmethod eval-insn :eq
  [insn state]
  (let [[x y] (:stack state)
        {:keys [then else]} insn
        eq-fn (if (number? x) == =)
        op-code (if (eq-fn x y) then else)]
    (update state :code
            (fn [[insn & insns]]
              (vec
               (concat [insn]
                       op-code
                       insns))))))


;; Macros
;; ======
(defmethod eval-insn :defop
  [insn state]
  (let [[name value & rest] (:stack state)]
    (-> state
        (update :ops assoc-in [name :body] value)
        (assoc :stack rest))))

(defmethod eval-insn :default
  [insn state]
  (let [op (:op insn)
        _ (assert (contains? (:ops state) op)
                  (str "Undefined op: " op))
        op-code (-> state :ops op :body)
        curr-millis (u/curr-millis)
        two-ms-ago (- curr-millis 2)
        called-ats (-> state :ops op :called-ats)

        calls-in-last-two-ms
        (->> state :ops op :called-ats
             (filter #(<= two-ms-ago %)))

        times-called-in-last-two-ms
        (count calls-in-last-two-ms)

        should-trace? (and (not (-> state :ops op :trace-start-idx))
                           (>= times-called-in-last-two-ms 5))
        new-op-code (if-not should-trace?
                      op-code
                      (vec (concat [{:op :trace-start
                                     :value op}]
                                   op-code
                                   [{:op :trace-end
                                     :value op}])))]
    (-> state
        (assoc-in [:ops op :called-ats]
                  (if (seq called-ats)
                    (conj calls-in-last-two-ms curr-millis)
                    [curr-millis]))
        (update :code
                (fn [[insn & insns]]
                  (vec
                   (concat [insn]
                           new-op-code
                           insns)))))))


;; Stdlib
;; ======
(def std
  (concat
   (u/defop :io/println
     [{:op :io/print}
      {:op :push
       :value "\n"}
      {:op :io/print}])

   (u/defop :dec
     [{:op :push
       :value 1}
      {:op :sub}
      {:op :neg}])))


;; Interpreter
;; ===========
(declare compile)

(defn interpret
  [{:keys [code ops stack]} & [trace?]]
  (let [trace (atom [])]
    (try
      (loop [state {:code code
                    :ops (or ops {})
                    :stack stack}]
        (swap! trace conj state)
        (if-let [[insn] (seq (:code state))]
          (case (:op insn)
            :trace-start
            (let [traced-op (:value insn)]
              (recur (-> state
                         (update :code rest)
                         (assoc-in [:ops traced-op :trace-start-idx]
                                   (count @trace)))))

            :trace-end
            (let [traced-op (:value insn)
                  start-idx (-> state :ops traced-op :trace-start-idx)
                  op-trace (drop start-idx @trace)]
              (recur (-> state
                         (update :code rest)
                         (assoc-in [:ops traced-op :compiled-trace]
                                   (compile op-trace)))))

            ;; default
            (recur (update (eval-insn insn state)
                           :code rest)))
          (do
            (when trace?
              (println "Trace:")
              (pp/pprint (u/clean-trace @trace)))
            state)))
      (catch Throwable e
        (throw (ex-info (str "Error! " e)
                        {:trace (reverse (u/clean-trace @trace))}))))))


;; JIT
;; ===
(defmethod eval-insn :guard
  [insn state]
  (let [stack (:stack state)
        [x y] stack
        check (:check insn)
        bool (:value insn)
        fallback (:fallback insn)]
    (if (= bool (check x y))
      state
      (fallback stack))))

(defn make-eq-guard
  [insn trace]
  (fn [state]
    (let [[x y] (-> trace first :stack)
          eq-fn (if (number? x) == =)
          bool (eq-fn x y)]
      (eval-insn
       {:op :guard
        :value bool
        :check #(eq-fn %1 %2)
        :fallback
        (fn [stack]
          (interpret {:ops (-> trace first :ops)
                      :code (if bool
                              (:else insn)
                              (:then insn))
                      :stack stack}))}
       state))))

(defn compile-insn [insn trace]
  (case (:op insn)
    :eq (make-eq-guard insn trace)

    ;; default
    (partial eval-insn insn)))

(defn compile
  [trace]
  (let [primitive-trace
        (filter (fn [state]
                  (-> state
                      :code
                      first
                      :op
                      primitive?))
                trace)

        ops
        (map-indexed (fn [idx {:keys [code]}]
                       [(first code)
                        (compile-insn (first code)
                                      (drop (inc idx)
                                            primitive-trace))])
                     primitive-trace)]
    (fn [stack]
      (reduce (fn [state [insn op-fn]]
                #_(println insn)
                #_(println (:stack state))
                #_(println)
                (op-fn state))
              {:stack stack
               :ops (-> trace last :ops)}
              ops))))


;; Main
;; ====
(defn -main
  [filename]
  (-> filename
      slurp
      read-string
      interpret))


;; Test
;; ====
(def factorial
  (concat
   std
   [{:op :push
     :value [{:op :push
              :value 0}
             {:op :eq
              :then [{:op :pop}
                     {:op :pop}
                     {:op :push
                      :value 1}]
              :else [{:op :pop}
                     {:op :dup}
                     {:op :dec}
                     {:op :fact}
                     {:op :mul}]}]}   
    {:op :push
     :value :fact}
    {:op :defop}]))
