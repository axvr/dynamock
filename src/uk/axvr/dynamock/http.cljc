(ns uk.axvr.dynamock.http
  "HTTP mocking utilities for uk.axvr.dynamock."
  (:require [uk.axvr.dynamock :as mock]))

;;; https://github.com/clojure/spec-alpha2/blob/74ada9d5111aa17c27fdef9c626ac6b4b1551a3e/src/test/clojure/clojure/test_clojure/spec.clj#L18,L25
(defn- submap?
  "Returns true if map1 is a subset of map2."
  [map1 map2]
  (if (and (map? map1) (map? map2))
    (every? (fn [[k v]]
              (and (contains? map2 k)
                   (submap? v (get map2 k))))
            map1)
    (= map1 map2)))

(defn- http-stub-pred-matches?
  "Checks if an http-stub-predicate (pred) \"matches\" the parameters (params)
  passed to the mocked function and returns the stub."
  [params [pred stub]]
  (when
    (cond
      (fn? pred)   (try (apply pred params) (catch Exception _))
      (map? pred)  (submap? pred (first params))
      (coll? pred) (= pred params))
    (if (fn? stub)
      (apply stub params)
      stub)))

(defn http-mock
  "Simple HTTP mocking function generator."
  [real-fn get-stubs]
  (fn [& params]
    (if-let [resp (some (partial http-stub-pred-matches? params) (get-stubs))]
      (delay resp)
      (apply real-fn params))))

(def block-real-http-requests
  "Stub to block real HTTP requests."
  [(constantly true)
   #(throw (ex-info "uk.axvr.dynamock: real HTTP requests are not allowed." %))])

(defmacro with-http-mock
  "Register the below scope with a mocked HTTP fn."
  [fn & body]
  `(mock/with-mock ~fn http-mock
     ~@body))
