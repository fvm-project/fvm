(ns fvm.core
  (:refer-clojure :exclude [compile])
  (:require [fvm.util :as u]))

;; API
;; ===
(defmulti eval-node
  (fn [state]
    (let [node (-> state ::nodes first)]
      (::type node))))

(defonce node-opts
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

(defn- trace? [type]
  (-> @node-opts type ::trace?))

(defn- jit? [type]
  (-> @node-opts type ::jit?))

(defn- branching? [type]
  (-> @node-opts type ::branching?))

(defn- get-check-state-fn [type]
  (-> @node-opts type ::check-state))


;; VM
;; ==
(defnode :default {}
  (fn [state]
    (let [type (-> state ::nodes first ::type)]
      (throw (Exception. ^String (str "Undefined node type: " type))))))

(declare compile)

(defn interpret
  [{::keys [state trace-atom trace-info compiled-nodes] :as S}]
  (let [trace-atom (or trace-atom (atom []))
        S (assoc S ::trace-atom trace-atom)]
    (if-let [[node] (-> state ::nodes seq)]
      (case (::type node)
        ::trace-start
        (let [traced-node (::node node)]
          (recur (-> S
                     (assoc-in [::trace-info traced-node ::trace-start-idx]
                               (count @trace-atom))
                     (update-in [::state ::nodes] rest))))

        ::trace-end
        (let [traced-node (::node node)
              start-idx (-> trace-info traced-node ::trace-start-idx)
              node-trace (drop start-idx @trace-atom)]
          (recur (-> S
                     (assoc ::compiled-nodes (compile node-trace compiled-nodes))
                     (u/dissoc-in [::trace-info traced-node ::trace-start-idx])
                     (u/dissoc-in [::trace-info traced-node ::called-ats])
                     (update-in [::state ::nodes] rest))))

        ;; default
        (do
          (swap! trace-atom conj state)
          (let [node-type (::type node)
                node-trace-info (get trace-info node-type)
                compiled-node (get compiled-nodes node-type)
                fallback? (::fallback? state)]
            (cond
              ;; run compiled
              (and (some? compiled-node)
                   (not fallback?))
              (recur (assoc S ::state (compiled-node state)))

              ;; run interpreted
              (or fallback?
                  (not (jit? node-type)))
              (recur (assoc S ::state (eval-node state)))

              :else
              (let [called-ats (::called-ats node-trace-info)
                    curr-millis (u/curr-millis)
                    two-ms-ago (- curr-millis 2)
                    calls-in-last-two-ms (filter #(<= two-ms-ago %)
                                                 called-ats)
                    new-called-ats (if (seq called-ats)
                                     (conj calls-in-last-two-ms curr-millis)
                                     [curr-millis])
                    times-called-in-last-two-ms (count calls-in-last-two-ms)
                    should-trace? (and (nil? (::trace-start-idx node-trace-info))
                                       (>= times-called-in-last-two-ms 5))
                    nodes (::nodes state)
                    new-state (if-not should-trace?
                                (eval-node state)
                                (assoc state ::nodes
                                       (u/fastcat [{::type ::trace-start
                                                    ::node node-type}
                                                   node
                                                   {::type ::trace-end
                                                    ::node node-type}]

                                                  (rest nodes))))]
                (recur (-> S
                           (assoc ::state new-state)
                           (assoc-in [::trace-info node-type ::called-ats] new-called-ats))))))))
      S)))


;; JIT
;; ===
(defnode ::guard
  {::trace? false}
  (fn [state]
    (let [node (-> state ::nodes first)
          safe? (::safe? node)
          fallback (::fallback node)]
      (if (safe? state)
        (update state ::nodes rest)
        (fallback)))))

(defn- make-guard [node safe?]
  (fn [state]
    (let [nodes (cons node (::nodes state))
          S {::state (assoc state
                            ::nodes nodes
                            ::fallback? true)}
          fallback #(-> (interpret S)
                        ::state
                        (assoc ::interpreted? true))
          guard-node {::type ::guard
                      ::safe? safe?
                      ::fallback fallback}]
      (eval-node (update state ::nodes #(cons guard-node %))))))

(defn- compile-node [node state compiled-nodes]
  (cond
    (branching? (::type node))
    (let [check-state (get-check-state-fn (::type node))
          bool (check-state state)
          unchanged? #(= bool (check-state %))]
      (make-guard node unchanged?))

    (contains? compiled-nodes (::type node))
    (let [compiled-node (compiled-nodes (::type node))]
      (fn [state*]
        (compiled-node state*)))

    :else
    (fn [state*]
      (eval-node (assoc state* ::nodes [node])))))

(defn- compile [node-trace compiled-nodes]
  (let [trace
        (filter (fn [state]
                  (-> state
                      ::nodes
                      first
                      ::type
                      trace?))
                node-trace)

        sub-nodes
        (map-indexed (fn [idx {::keys [nodes]}]
                       (compile-node (first nodes)
                                     (->> trace
                                          (drop (inc idx))
                                          first)
                                     compiled-nodes))
                     trace)]
    (fn [init-state]
      (reduce (fn [state sub-node]
                (if (::interpreted? state)
                  (reduced (dissoc state ::interpreted?))
                  (sub-node state)))
              init-state
              sub-nodes))))
