(ns uk.axvr.dynamock
  "The core of Dynamock.")

(def ^:dynamic *stubs*
  "Dynamock stub registry.  (Avoid accessing and manipulating this directly.)"
  (atom {}))

(defmacro with-stub-scope
  "Scope stubs registered in the body with `stub!` to a new dynamic scope."
  [& body]
  `(binding [*stubs* (atom @*stubs*)]
     ~@body))

(defmacro stub!
  "Register a stub in the current stub-scope for a given mocked function (f)."
  [f stub]
  `(swap! *stubs* update #'~f conj ~stub))

(defmacro with-mock
  "Mock invocations of function (f) in the body.  The function will be replaced
  with the result of the given mock-builder function (mock).

  The mock-builder will be passed 2 parameters:

    1. the original function, which the mock can optionally use.
    2. a function, taking no parameters which when called returns a list of
       registered stubs for the mock.

  After invocation, the mock-builder should return a function (the mock) with
  a compatible parameter list as the function being mocked.

  Automatically defines a new stub-scope for the body."
  [f mock & body]
  `(with-stub-scope
     (let [get-stubs# #(get @*stubs* #'~f)]
       (if (:dynamic (meta #'~f))
         (binding [~f (~mock ~f get-stubs#)]
           ~@body)
         (with-redefs [~f (~mock ~f get-stubs#)]
           ~@body)))))

(defmacro with-stubs
  "Same as `with-stub` but registers multiple stubs at once when grouped
  together in a vector."
  [f stubs & body]
  `(with-stub-scope
     ~@(map (fn [s] `(stub! ~f ~s)) stubs)
     ~@body))

(defmacro with-stub
  "Register stub for a mocked function (f) that will be available in body.
  Automatically places the stub in a new stub-scope."
  [f stub & body]
  `(with-stubs ~f [~stub]
     ~@body))
