(ns uk.axvr.dynamock
  "Helper utilities for mocking dynamically-scoped functions.")

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
  "Register the below scope with a mocked dynamic fn."
  [*dynfn* mock & body]
  `(with-stub-scope
     (binding [~*dynfn* (~mock ~*dynfn*)]
       ~@body)))

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
