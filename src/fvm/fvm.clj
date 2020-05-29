(ns fvm.fvm
  (:refer-clojure :exclude [compile])
  (:require [fvm.util :as u]))

;; API
;; ===
(defmulti eval-node
  (fn [state]
    (let [node (-> state ::nodes first)]
      (::type node))))

(def node-opts
  (atom {}))

(defn defnode
  [type opts handle]
  (swap! node-opts assoc
         type (merge {::branching? false
                      ::trace? true
                      ::jit? false}
                     opts))
  (.addMethod ^clojure.lang.MultiFn eval-node
              type
              handle))

(defn trace? [type]
  (-> @node-opts type ::trace?))

(defn branching? [type]
  (-> @node-opts type ::branching?))

(defn get-check-state-fn [type]
  (-> @node-opts type ::check-state-fn))


;; VM
;; ==
(defnode :default {}
  (fn [state]
    (let [type (-> state ::nodes first ::type)]
      (throw (Exception. (str "Undefined node type: " type))))))

(declare compile)

(defn interpret
  [{::keys [state trace-atom trace-info] :as S}]
  (let [trace-atom (or trace-atom (atom []))
        S (assoc S ::trace-atom trace-atom)]
    (if-let [[node] (-> state ::nodes seq)]
      (case (::type node)
        ::trace-start
        (let [traced-node (::node node)]
          (recur (assoc-in S [::trace-info traced-node ::trace-start-idx]
                           (count @trace-atom))))

        ::trace-end
        (let [traced-node (::node node)
              start-idx (-> trace-info traced-node ::trace-start-idx)
              node-trace (drop start-idx @trace-atom)]
          (recur (-> S
                     (u/dissoc-in [::trace-info traced-node ::trace-start-idx])
                     (u/dissoc-in [::trace-info traced-node ::called-ats])
                     (assoc-in [::trace-info traced-node ::compiled-node]
                               (compile node-trace)))))

        ;; default
        (do
          (swap! trace-atom conj state)
          (recur (assoc S ::state (eval-node state)))))
      state)))


;; JIT
;; ===
(defnode ::guard
  {::trace? false}
  (fn [state]
    (let [node (-> state ::nodes first)
          safe? (::safe? state)
          fallback (::fallback node)]
      (if (safe? state)
        state
        (fallback)))))

(defn make-guard [node safe?]
  (fn [state]
    (eval-node {::type ::guard
                ::safe? safe?
                ::fallback #(assoc
                             (interpret (assoc state
                                               ::nodes [node]
                                               ::fallback? true))
                             ::interpreted? true)}
               state)))

(defn compile-node [node state]
  (if (branching? (::type node))
    (let [check-state (get-check-state-fn node)
          bool (check-state state)
          unchanged? #(= bool (check-state %))]
      (make-guard node unchanged?))

    eval-node))

(defn compile [node-trace]
  (let [trace
        (filter (fn [state]
                  (-> state
                      ::nodes
                      first
                      ::type
                      trace?))
                node-trace)

        compiled-nodes
        (map-indexed (fn [idx {::keys [nodes]}]
                       (compile-node (first nodes)
                                     (->> trace
                                          (drop (inc idx))
                                          first)))
                     trace)]
    (fn [init-state]
      (reduce (fn [state compiled-node]
                (if (::interpreted? state)
                  (reduced (dissoc state ::interpreted?))
                  (compiled-node state)))
              init-state
              compiled-nodes))))
