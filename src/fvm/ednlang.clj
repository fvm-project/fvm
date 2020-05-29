(ns fvm.ednlang
  (:require [fvm.fvm :as fvm]
            [fvm.util :as u]))

;; Deps
;; ====
(fvm/defnode ::requires {::fvm/trace? false}
  (fn [state]
    (let [insn (-> state ::fvm/nodes first)
          libs (::value insn)
          insns (vec (mapcat #(u/load-source %)
                             libs))
          [_ & nodes] (::fvm/nodes state)]
      (assoc state ::fvm/nodes
             (u/fastcat insns nodes)))))


;; IO
;; ==
(fvm/defnode ::read {}
  (fn [state]
    (-> state
        (update ::stack #(cons (u/next-obj *in*) %))
        (update ::fvm/nodes rest))))

(fvm/defnode ::print {}
  (fn [state]
    (let [[x & rest] (::stack state)]
      (print x)
      (flush)
      (-> state
          (assoc ::stack rest)
          (assoc ::fvm/nodes rest)))))


;; Memory
;; ======
(fvm/defnode ::push {}
  (fn [state]
    (let [insn (-> state ::fvm/nodes first)]
      (-> state
          (update ::stack #(cons (::value insn) %))
          (update ::fvm/nodes rest)))))

(fvm/defnode ::pop {}
  (fn [state]
    (-> state
        (update ::stack rest)
        (update ::fvm/nodes rest))))

(fvm/defnode ::dup {}
  (fn [state]
    (-> state
        (update ::stack #(cons (first %) %))
        (update ::fvm/nodes rest))))

(fvm/defnode ::swap {}
  (fn [state]
    (-> state
        (update ::stack
                (fn [[x y & rest]]
                  (cons y (cons x rest))))
        (update ::fvm/nodes rest))))


;; Arithmetic
;; ==========
(fvm/defnode ::add {}
  (fn [state]
    (let [stack (::stack state)
          [x y & rem] stack
          res (+' x y)]
      (-> state
          (assoc ::stack (cons res rem))
          (update ::fvm/nodes rest)))))

(fvm/defnode ::sub {}
  (fn [state]
    (let [stack (::stack state)
          [x y & rem] stack
          res (- x y)]
      (-> state
          (assoc ::stack (cons res rem))
          (update ::fvm/nodes rest)))))

(fvm/defnode ::mul {}
  (fn [state]
    (let [stack (::stack state)
          [x y & rem] stack
          res (*' x y)]
      (-> state
          (assoc ::stack (cons res rem))
          (update ::fvm/nodes rest)))))

(fvm/defnode ::div {}
  (fn [state]
    (let [stack (::stack state)
          [x y & rem] stack
          res (/ x y)]
      (-> state
          (assoc ::stack (cons res rem))
          (update ::fvm/nodes rest)))))


;; Logic
;; =====
(fvm/defnode ::eq? {::fvm/branching? true
                    ::fvm/check-state
                    (fn [state]
                      (let [[x y] (::stack state)
                            eq-fn (if (number? x) == =)]
                        (eq-fn x y)))}
  (fn [state]
    (let [insn (-> state ::fvm/nodes first)
          [x y] (::stack state)
          {::keys [then else]} insn
          eq-fn (if (number? x) == =)
          op-code (if (eq-fn x y) then else)]
      (update state ::fvm/nodes
              (fn [[_ & insns]]
                (u/fastcat op-code insns))))))


;; Macros
;; ======
(fvm/defnode ::call {}
  (fn [state]
    (let [[body & rem] (::stack state)]
      (-> state
          (assoc ::stack rem)
          (update ::fvm/nodes
                  (fn [[_ & insns]]
                    (u/fastcat body insns)))))))


(defn- branching? [insn]
  (or (::then insn)
      (::else insn)))

(defn- get-leaves [body]
  (let [last-insn (last body)]
    (if (branching? last-insn)
      (concat (get-leaves (::then last-insn))
              (get-leaves (::else last-insn)))
      [last-insn])))

(defn- self-tail-recursive? [op-info]
  (let [{::keys [name value]} op-info]
    (some #(= name (::fvm/type %))
          (get-leaves value))))

(fvm/defnode ::defop {}
  (fn [state]
    (let [insn (-> state ::fvm/nodes first)
          {::keys [name value dont-jit?]} insn
          dont-jit? (or dont-jit?
                        (not (self-tail-recursive? insn)))]
      (fvm/defnode name {::fvm/jit? (not dont-jit?)}
        (fn [state*]
          (update state* ::fvm/nodes
                  (fn [[_ & nodes]]
                    (u/fastcat value nodes)))))
      (update state ::fvm/nodes rest))))
