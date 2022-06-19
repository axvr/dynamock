(ns uk.axvr.dynamock.http
  "HTTP mocking utilities for uk.axvr.dynamock."
  (:require [uk.axvr.dynamock :as mock]
            [uk.axvr.refrain  :as r]))

(defn http-stub-pred-matches?
  "Checks if an http-stub-predicate (pred) \"matches\" the parameters (params)
  passed to the mocked function and returns the stub."
  [params [pred stub]]
  (when
    (cond
      (fn? pred)   (try (apply pred params) (catch Exception _))
      (map? pred)  (r/submap? pred (first params))
      (coll? pred) (= pred params))
    (if (fn? stub)
      (apply stub params)
      stub)))

(defn- ->derefable [resp]
  (if (r/derefable? resp) resp (delay resp)))

(defn- <-derefable [resp]
  (if (r/derefable? resp) @resp resp))

(defn http-mock
  "Simple HTTP mocking function generator.  If you want to add more
  functionality to this, why not mock this function?

  Accepts an optional map of config options (opts).  If a `:derefable?` key is
  given and the value is truthy, the HTTP response will be derefable.  Set this
  to `false` if you are using clj-http.  Default: `true`."
  ([real-fn get-stubs]
   (http-mock real-fn get-stubs {}))
  ([real-fn get-stubs opts]
   (fn [& params]
     ((if (:derefable? opts true) ->derefable <-derefable)
      (if-let [resp (some (partial http-stub-pred-matches? params) (get-stubs))]
        resp
        (apply real-fn params))))))

(defn- macro-body-opts
  "For macros, extract a map of options from the body.  If there is more than
  one parameter, the first item will be treated as an options map if it is
  a map."
  [[opts & body :as params]]
  (if (and (seq body) (map? opts))
    [opts body]
    [{} params]))

(defmacro with-http-mock
  "Register the below scope with a mocked HTTP fn.

  If the first value in the body is a hash-map (and there is more than one form
  in the body) it will be treated as options and passed to `http-mock`."
  [fn & body]
  (let [[opts body] (macro-body-opts body)]
    `(mock/with-mock ~fn #(http-mock %1 %2 ~opts)
       ~@body)))

(def block-real-http-requests
  "Stub to block real HTTP requests."
  [(constantly true)
   #(throw (ex-info "uk.axvr.dynamock: real HTTP requests are not allowed." %))])
