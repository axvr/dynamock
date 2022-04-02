(ns uk.axvr.dynamock
  "Helper utilities for mocking dynamically-scoped functions.")

(def ^:dynamic *stubs* (atom (list)))

;;; https://github.com/clojure/spec-alpha2/blob/74ada9d5111aa17c27fdef9c626ac6b4b1551a3e/src/test/clojure/clojure/test_clojure/spec.clj#L18,L25
(defn- submap?
  "Is m1 a subset of m2?"
  [m1 m2]
  (if (and (map? m1) (map? m2))
    (every? (fn [[k v]]
              (and (contains? m2 k)
                   (submap? v (get m2 k))))
            m1)
    (= m1 m2)))

(defn- stub-pred-matches?
  "Simple function to check if a stub-predicate (pred) \"matches\" the
  parameters (params) passed to the mocked function and returns the stub."
  [params [pred stub]]
  (when
    (cond
      (fn? pred)   (try (apply pred params) (catch Exception _))
      (map? pred)  (submap? pred (first params))
      (coll? pred) (= pred params))
    (if (fn? stub)
      (apply stub params)
      stub)))

;; TODO: make this non-http-specific?
(defn http-mock
  "Simple HTTP mocking function generator."
  [real-fn]
  (fn [& params]
    (if-let [resp (some (partial stub-pred-matches? params) @*stubs*)]
      (future resp)
      ;; TODO: may not always want to fallback to real requests.
      ;;   - block-real-requests function?
      (apply real-fn params))))

(defmacro with-stub-scope
  "Scope the registered stubs to the lexical scope."
  [& body]
  `(binding [*stubs* (atom @*stubs*)]
     ~@body))

(defmacro with-mock
  "Register the below scope with a mocked dynamic fn."
  [*dynfn* mock & body]
  `(with-stub-scope
     (binding [~*dynfn* (~mock ~*dynfn*)]
       ~@body)))

(defmacro with-http-mock
  "Register the below scope with a mocked HTTP fn."
  [*dynfn* & body]
  `(with-mock ~*dynfn* http-mock
     ~@body))

(defn stub!
  "Register a stub in the current stub scope."
  [stub]
  (swap! *stubs* conj stub))

(defmacro with-stub
  "Register a stub for tests in the body."
  [stub & body]
  `(with-stub-scope
     (stub! ~stub)
     ~@body))

(defmacro with-stubs
  "Like with-stub but registers multiple stubs at once."
  [[stub & stubs] & body]
  (if (seq stubs)
    `(with-stub ~stub
       (with-stubs ~stubs
         ~@body))
    `(with-stub ~stub
       ~@body)))
