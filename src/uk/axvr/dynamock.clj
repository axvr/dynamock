(ns uk.axvr.dynamock
  "Helper utilities for mocking functions.")

(def ^:dynamic *stubs* (atom (list)))

(defn stub!
  "Register a stub in the current stub scope."
  [stub]
  (swap! *stubs* conj stub))

(defmacro with-stub-scope
  "Scope the registered stubs to the lexical scope."
  [& body]
  `(binding [*stubs* (atom @*stubs*)]
     ~@body))

(defmacro with-mock
  "Register the below scope with a mocked fn."
  [fn mock & body]
  `(with-stub-scope
     (if (:dynamic (meta (var ~fn)))
       (binding [~fn (~mock ~fn)]
         ~@body)
       (with-redefs [~fn (~mock ~fn)]
         ~@body))))

(defmacro with-stubs
  "Like with-stub but registers multiple stubs at once."
  [stubs & body]
  `(with-stub-scope
     ~@(map (fn [s] `(stub! ~s)) stubs)
     ~@body))

(defmacro with-stub
  "Register a stub for tests in the body."
  [stub & body]
  `(with-stubs [~stub]
     ~@body))
