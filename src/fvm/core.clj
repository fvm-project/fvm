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
      (dissoc :default
              :requires
              :guard)))

(defn primitive? [op]
  (contains? (primitives)
             op))

;; Deps
;; ====
(defmethod eval-insn :requires
  [insn state]
  (let [libs (:value insn)
        insns (vec (mapcat #(u/load-source %)
                           libs))
        [no-op & code] (:code state)]
    (assoc state :code
           (u/fastcat [no-op]
                      insns
                      code))))


;; IO
;; ==
(defmethod eval-insn :read
  [insn state]
  (update state :stack
          (fn [stack]
            (cons (u/next-obj *in*)
                  stack))))

(defmethod eval-insn :print
  [insn state]
  (let [[x & rest] (:stack state)]
    (print x)
    (flush)
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

(defmethod eval-insn :swap
  [insn state]
  (update state :stack
          (fn [[x y & rest]]
            (cons y (cons x rest)))))


;; Arithmetic
;; ==========
(defmethod eval-insn :add
  [insn state]
  (let [stack (:stack state)
        [x y & rest] stack
        res (+' x y)]
    (assoc state :stack
           (cons res rest))))

(defmethod eval-insn :sub
  [insn state]
  (let [stack (:stack state)
        [x y & rest] stack
        res (- x y)]
    (assoc state :stack
           (cons res rest))))

(defmethod eval-insn :mul
  [insn state]
  (let [stack (:stack state)
        [x y & rest] stack
        res (*' x y)]
    (assoc state :stack
           (cons res rest))))

(defmethod eval-insn :div
  [insn state]
  (let [stack (:stack state)
        [x y & rest] stack
        res (/ x y)]
    (assoc state :stack
           (cons res rest))))


;; Logic
;; =====
(defmethod eval-insn :eq?
  [insn state]
  (let [[x y] (:stack state)
        {:keys [then else]} insn
        eq-fn (if (number? x) == =)
        op-code (if (eq-fn x y) then else)]
    (update state :code
            (fn [[insn & insns]]
              (u/fastcat [insn]
                         op-code
                         insns)))))


;; Macros
;; ======
(defmethod eval-insn :call
  [insn state]
  (let [[body & rest] (:stack state)]
    (-> state
        (assoc :stack rest)
        (update :code
                (fn [[no-op & insns]]
                  (u/fastcat [no-op]
                             body
                             insns))))))

(defmethod eval-insn :defop
  [insn state]
  (let [{:keys [name value]} insn]
    (update state :ops assoc-in [name :body] value)))

(defmethod eval-insn :default
  [insn state]
  (let [op (:op insn)
        _ (assert (contains? (:ops state) op)
                  (str "Undefined op: " op))
        op-info (-> state :ops op)
        op-code (:body op-info)
        compiled-op (:compiled-trace op-info)]
    (if (and (some? compiled-op)
             (not (:fallback? state)))
      (compiled-op state)
      (if (:fallback? state)
        (update state :code
                (fn [[no-op & insns]]
                  (u/fastcat [no-op]
                             op-code
                             insns)))
        (let [curr-millis (u/curr-millis)
              two-ms-ago (- curr-millis 2)
              called-ats (:called-ats op-info)

              calls-in-last-two-ms
              (->> state :ops op :called-ats
                   (filter #(<= two-ms-ago %)))

              times-called-in-last-two-ms
              (count calls-in-last-two-ms)

              should-trace? (and (nil? (:trace-start-idx op-info))
                                 (>= times-called-in-last-two-ms 5))
              new-op-code (if-not should-trace?
                            op-code
                            (u/fastcat [{:op :trace-start
                                         :value op}]
                                       op-code
                                       [{:op :trace-end
                                         :value op}]))]
          (-> state
              (assoc-in [:ops op :called-ats]
                        (if (seq called-ats)
                          (conj calls-in-last-two-ms curr-millis)
                          [curr-millis]))
              (update :code
                      (fn [[insn & insns]]
                        (u/fastcat [insn]
                                   new-op-code
                                   insns)))))))))


;; Interpreter
;; ===========
(declare compile)

(defn interpret
  [{:keys [code ops stack]}]
  (comment
    (println :interpreting)
    (println :code)
    (pp/pprint code)
    (println :stack stack)
    (println))
  (let [trace (atom [])]
    (try
      (loop [state {:code code
                    :ops (or ops {})
                    :stack stack}]
        (swap! trace conj (dissoc state :ops))
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
                  op-trace (drop start-idx @trace)
                  ops-being-traced (->> state
                                        :ops
                                        (#(dissoc % traced-op))
                                        vals
                                        (filter :trace-start-idx))]
              (when (empty? ops-being-traced)
                (swap! trace #(take-last 100 %)))
              (comment do
                (println :compiling traced-op)
                (println))
              (recur (-> state
                         (update :code rest)
                         (u/dissoc-in [:ops traced-op :trace-start-idx])
                         (assoc-in [:ops traced-op :compiled-trace]
                                   (compile (-> state :ops traced-op :body)
                                            op-trace
                                            trace)))))

            ;; default
            (recur (update (eval-insn insn state)
                           :code rest)))
          state))
      (catch Throwable e
        (throw (ex-info (str "Error! " e)
                        {:trace (reverse (u/clean-trace @trace))}))))))


;; JIT
;; ===
(defmethod eval-insn :guard
  [insn state]
  (let [stack (:stack state)
        check (:check insn)
        fallback (:fallback insn)]
    (if (check stack)
      state
      (fallback))))

(defn make-eq-guard
  [insn trace]
  (fn [state]
    (let [[x y] (-> trace first :stack)
          eq-fn (if (number? x) == =)
          bool (eq-fn x y)
          check (fn [[x y & _]]
                  (= bool (eq-fn x y)))]
      (eval-insn
       {:op :guard
        :check check
        :fallback #(assoc
                    (interpret (assoc state
                                      :code (if bool
                                              (:else insn)
                                              (:then insn))
                                      :fallback? true))
                    :interpreted? true)}
       state))))

(defn compile-insn [insn trace]
  (case (:op insn)
    :eq? (make-eq-guard insn trace)

    ;; default
    (fn [state]
      (eval-insn insn state))))

(defn compile
  [op-body op-trace trace-atom]
  (let [primitive-trace
        (filter (fn [state]
                  (-> state
                      :code
                      first
                      :op
                      primitive?))
                op-trace)

        ops
        (map-indexed (fn [idx {:keys [code]}]
                       (compile-insn (first code)
                                     (drop (inc idx)
                                           primitive-trace)))
                     primitive-trace)]
    (comment
      (println :compiled-trace)
      (pp/pprint (u/clean-trace primitive-trace))
      (println))
    (fn [init-state]
      ;; log the compiled instruction's trace
      ;; so other instructions can be compiled
      (doseq [insn op-body]
        (swap! trace-atom conj {:code [insn]}))
      (let [res (reduce (fn [state op-fn]
                          (if (:interpreted? state)
                            (reduced state)
                            (op-fn state)))
                        {:stack (:stack init-state)
                         :ops (:ops init-state)}
                        ops)]
        (assoc init-state :stack (:stack res))))))


;; Main
;; ====
(defn -main
  [filename]
  (try
    (interpret {:code [{:op :requires
                        :value [filename]}]})
    (catch Throwable e
      (println (.getMessage e))
      (u/print-stack-trace e)
      (System/exit 1))))
