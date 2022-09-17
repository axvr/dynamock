(ns uk.axvr.dynamock.http
  "HTTP mocking utilities for uk.axvr.dynamock."
  (:require [uk.axvr.dynamock :as mock]
            [uk.axvr.refrain  :as r]))

(defn stub-pred-matches?
  "Checks if an http-stub-predicate (pred) \"matches\" the parameters (params)
  passed to the mocked function and returns the stub."
  [params [pred stub]]
  (when
    (cond
      (fn? pred)   (apply pred params)
      (map? pred)  (r/submap? pred (first params))
      (coll? pred) (= pred params))
    stub))

(defn stub->resp
  "Get the response from a stub + params."
  [stub params]
  (if (fn? stub) (apply stub params) stub))

(defn ->derefable
  "Returns `x` as a \"derefable\" object (if it is not already derefable)."
  [x]
  (if (r/derefable? x) x (delay x)))

(defonce
  ^{:doc "Atom containing a map of the default options for the
  `uk.axvr.dynamock.http/http-mock` function.

  See: `uk.axvr.dynamock.http/http-mock` for details on what options are
  available."}
  default-opts
  (atom {:transform-request  identity
         :transform-response (fn [_ resp] resp)}))

(defn http-mock
  "Simple HTTP mock function generator.

  Accepts an optional map of config options (opts):

    :transform-request

      Function that is passed a list of parameters that were given to the mocked
      function.  Returns the input with any desired alterations made.

      Note that the original unmodified parameters will still be passed to the
      original base HTTP function if no matching stub was found.

      Default: `clojure.core/identity`

      Example: Parse JSON body into Clojure data for better stub selection.

        (fn [params]
          (update-in params [0 :body]
            cheshire.core/parse-string true))

    :transform-response

      Function to make final modifications to the response before returning it.
      It is passed the parameters given to the mocked function and the
      response.  It is expected to return the response.

      Default:

        (fn [params response] response)

      Example: behave like HttpKit, by making responses derefable.

        (fn [params response]
          (uk.axvr.dynamock.http/->derefable response))

  Use `uk.axvr.dynamock.http/default-opts` to set default options."
  ([real-fn get-stubs]
   (http-mock real-fn get-stubs {}))
  ([real-fn get-stubs opts]
   (let [{:keys [transform-request transform-response]} (merge @default-opts opts)]
     (fn [& params]
       (let [tformed-params (transform-request params)]
         (transform-response
           tformed-params
           (if-let [stub (some (partial stub-pred-matches? tformed-params) (get-stubs))]
             (stub->resp stub tformed-params)
             (apply real-fn params))))))))

(defmacro with-http-mock
  "Register the below scope with a mocked HTTP fn.

  If the first value in the body is a map (and there is more than one form in
  the body) it will be treated as options and passed to
  `uk.axvr.dynamock.http/http-mock`.

  See: `uk.axvr.dynamock.http/http-mock` for supported configuration options."
  [fn & body]
  (let [[opts body] (r/macro-body-opts body)]
    `(mock/with-mock ~fn #(http-mock %1 %2 ~opts)
       ~@body)))

(def block-real-http-requests
  "Stub to block real HTTP requests."
  [(constantly true)
   #(throw (ex-info "uk.axvr.dynamock: real HTTP requests are not allowed." %))])
