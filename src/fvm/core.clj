(ns fvm.core
  (:gen-class)
  (:require [clojure.pprint :as pp]
            [fvm.util :as u]))

(defmulti eval-insn
  (fn [insn state]
    (:op insn)))

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
               (concat [insn
                        {:op :pop}]
                       op-code
                       insns))))))

(defmethod eval-insn :neq
  [insn state]
  (let [[x y] (:stack state)
        {:keys [then else]} insn
        neq-fn (complement (if (number? x) == =))
        op-code (if (neq-fn x y) then else)]
    (update state :code
            (fn [[insn & insns]]
              (vec
               (concat [insn
                        {:op :pop}]
                       op-code
                       insns))))))


;; Macros
;; ======
(defmethod eval-insn :defop
  [insn state]
  (let [[name value & rest] (:stack state)]
    (-> state
        (update :ops assoc name value)
        (assoc :stack rest))))

(defmethod eval-insn :default
  [insn state]
  (let [op (:op insn)
        _ (assert (contains? (:ops state) op)
                  (str "Undefined op: " op))
        op-code (-> state :ops op)]
    (update state :code
            (fn [[insn & insns]]
              (vec
               (concat [insn]
                       op-code
                       insns))))))


;; Stdlib
;; ======
(def std
  (concat
   (u/defop :io/println
     [{:op :io/print}
      {:op :push
       :value "\n"}
      {:op :io/print}])

   (u/defop :neg
     [{:op :push
       :value 0}
      {:op :sub}])

   (u/defop :dec
     [{:op :push
       :value 1}
      {:op :sub}
      {:op :neg}])))


;; Interpreter
;; ===========
(defn interpret
  [code & [trace?]]
  (let [trace (atom [])]
    (try
      (loop [state {:code (concat std code)
                    :ops {}
                    :stack []}]
        (swap! trace conj (-> state
                              (assoc :insn (-> state :code first))
                              (dissoc :code :ops)))
        (if-let [[insn] (seq (:code state))]
          (recur (update (eval-insn insn state)
                         :code rest))
          (do
            (when trace?
              (println "Trace:")
              (pp/pprint @trace))
            (-> state :stack first))))
      (catch Throwable e
        (throw (ex-info (str "Error! " (.getMessage e))
                        {:trace (reverse @trace)}))))))


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
  [{:op :push
    :value [{:op :push
             :value 0}
            {:op :eq
             :then [{:op :pop}
                    {:op :push
                     :value 1}]
             :else [{:op :dup}
                    {:op :dec}
                    {:op :fact}
                    {:op :mul}]}]}   
   {:op :push
    :value :fact}
   {:op :defop}
   
   {:op :push
    :value 5}
   {:op :fact}])
