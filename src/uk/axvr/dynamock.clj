(ns uk.axvr.dynamock
  "Helper utilities for mocking functions.")

(def ^:dynamic *stubs* (atom {}))

(defmacro stub!
  "Register a stub in the current stub scope."
  [fn stub]
  `(swap! *stubs* update #'~fn conj ~stub))

(defmacro with-stub-scope
  "Scope the registered stubs to the lexical scope."
  [& body]
  `(binding [*stubs* (atom @*stubs*)]
     ~@body))

(defmacro with-mock
  "Register the below scope with a mocked fn."
  [fn mock & body]
  `(with-stub-scope
     (let [get-stubs# #(get @*stubs* #'~fn)]
       (if (:dynamic (meta #'~fn))
         (binding [~fn (~mock ~fn get-stubs#)]
           ~@body)
         (with-redefs [~fn (~mock ~fn get-stubs#)]
           ~@body)))))

(defmacro with-stubs
  "Like with-stub but registers multiple stubs at once."
  [fn stubs & body]
  `(with-stub-scope
     ~@(map (clojure.core/fn [s] `(stub! ~fn ~s)) stubs)
     ~@body))

(defmacro with-stub
  "Register a stub for tests in the body."
  [fn stub & body]
  `(with-stubs ~fn [~stub]
     ~@body))
