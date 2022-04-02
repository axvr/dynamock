(ns uk.axvr.dynamock.http
  "HTTP mocking utilities for uk.axvr.dynamock."
  (:require [uk.axvr.dynamock :as dyn]))

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

;;; TODO: make a generic/non-http version of this for the core ns.
(defn http-mock
  "Simple HTTP mocking function generator."
  [real-fn]
  (fn [& params]
    (if-let [resp (some (partial stub-pred-matches? params) @dyn/*stubs*)]
      (delay resp)
      (apply real-fn params))))

(def block-real-http-requests
  "Stub to block real HTTP requests."
  [(constantly true)
   #(throw (ex-info "uk.axvr.dynamock: real HTTP requests are not allowed." %))])

(defmacro with-http-mock
  "Register the below scope with a mocked HTTP fn."
  [*dynfn* & body]
  `(dyn/with-mock ~*dynfn* http-mock
     ~@body))
