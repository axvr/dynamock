(ns uk.axvr.dynamock-test
  "Tests for uk.axvr.dynamock."
  (:require [clojure.test :refer :all]
            [uk.axvr.dynamock :refer :all]))


(defn ^:dynamic *test-fn-arity-0* [] :a)

(defn ^:dynamic *test-fn-arity-1* [x]
  (if x :a :b))

(defn ^:dynamic *test-fn-arity-rst* [x & ys]
  (if x
    (apply x ys)
    ys))


(deftest dyn-fns-work
  (testing "*test-fn-arity-0*"
    (is (= :a (*test-fn-arity-0*))))
  (testing "*test-fn-arity-1*"
    (is (= :a (*test-fn-arity-1* 1)))
    (is (= :a (*test-fn-arity-1* true)))
    (is (= :b (*test-fn-arity-1* nil)))
    (is (= :b (*test-fn-arity-1* false))))
  (testing "*test-fn-arity-rst*"
    (is (= 6 (*test-fn-arity-rst* + 1 2 3)))
    (is (= [1 2 3] (*test-fn-arity-rst* nil 1 2 3)))
    (is (empty? (*test-fn-arity-rst* nil)))
    (is (= [nil] (*test-fn-arity-rst* nil nil)))))


(deftest mock-*test-fn-arity-0*
  (testing "Rebound, no stubs"
    (with-mock *test-fn-arity-0* (fn [_] (fn [] :b))
      (is (= :b (*test-fn-arity-0*)))))
  (testing "Rebound, fallback to old"
    (with-mock *test-fn-arity-0* (fn [old] old)
      (is (= :a (*test-fn-arity-0*))))))


(deftest mock-*test-fn-arity-1*
  (testing "Rebound, no stubs"
    (with-mock *test-fn-arity-1* (fn [old] #(if (= :c %) :d (old %)))
      (is (= :a (*test-fn-arity-1* :a)))
      (is (= :b (*test-fn-arity-1* nil)))
      (is (= :d (*test-fn-arity-1* :c)))))
  (testing "Rebound, with stub"
    (with-mock *test-fn-arity-1* (fn [_] (fn [x] (some #(% x) @*stubs*)))
      (with-stub (fn [x] (if (= x :a) :x))
        (is (= :x (*test-fn-arity-1* :a)))
        (is (= nil (*test-fn-arity-1* :y))))))
  (testing "Rebound, with 2 stubs"
    (with-mock *test-fn-arity-1* (fn [_] (fn [x] (some #(% x) @*stubs*)))
      (with-stubs [identity
                   (fn [x] (if (= x :a) :x))]
        (is (= :x (*test-fn-arity-1* :a)))
        (is (= :y (*test-fn-arity-1* :y))))))
  (testing "Rebound, with stubs"
    (with-mock *test-fn-arity-1* (fn [_] (fn [x] (some #(% x) @*stubs*)))
      (stub! identity)
      (with-stub (fn [x] (when (= x :bye) "leave"))
        (stub! (fn [x] (when (= x :hi) "hello")))
        (is (= :a (*test-fn-arity-1* :a)))
        (is (= :b (*test-fn-arity-1* :b)))
        (is (= "hello" (*test-fn-arity-1* :hi)))
        (is (= "leave" (*test-fn-arity-1* :bye))))))
  (testing "Rebound, with scope"
    (with-mock *test-fn-arity-1* (fn [_] (fn [x] (some #(% x) @*stubs*)))
      (stub! identity)
      (stub! (fn [x] (when (= x :hi) "hello")))
      (with-stub (fn [x] (when (= x :bye) "leave"))
        (is (= :a (*test-fn-arity-1* :a)))
        (is (= :b (*test-fn-arity-1* :b)))
        (is (= "hello" (*test-fn-arity-1* :hi)))
        (is (= "leave" (*test-fn-arity-1* :bye))))
      (is (= :bye (*test-fn-arity-1* :bye)))
      (is (= "hello" (*test-fn-arity-1* :hi))))))


(deftest mock-*test-fn-arity-rst*
  (testing "Rebound, no stubs"
    (with-mock *test-fn-arity-rst* (fn [_]
                                     (fn [x & rst]
                                       (if (= :c x) :d (apply - rst))))
      (is (= 0 (*test-fn-arity-rst* :a 3 2 1)))
      (is (= 3 (*test-fn-arity-rst* :b 5 2)))
      (is (= :d (*test-fn-arity-rst* :c 1 2 3)))))
  (testing "Rebound, with stub"
    (with-mock *test-fn-arity-rst* (fn [old]
                                     (fn [x & rst]
                                       (apply (or (some #(% x) @*stubs*) old) x rst)))
      (with-stub (fn [& rst] (fn [& r] r))
        (is (= [:a] (*test-fn-arity-rst* :a)))
        (is (= [:y :a] (*test-fn-arity-rst* :y :a))))))
  (testing "Rebound, with 2 stubs"
    (with-mock *test-fn-arity-rst* (fn [old]
                                     (fn [x & rst]
                                       (apply (or (some #(% x) @*stubs*) old) x rst)))
      (with-stubs [(fn [& rst] (fn [& r] r))
                   (fn [x & rst] (if (= x :a) (fn [y & rs] y)))]
        (is (= :a (*test-fn-arity-rst* :a)))
        (is (= [:y 1 2 3] (*test-fn-arity-rst* :y 1 2 3))))))
  (testing "Rebound, with stubs"
    (with-mock *test-fn-arity-rst* (fn [old]
                                     (fn [x & rst]
                                       (apply (or (some #(% x) @*stubs*) old) x rst)))
      (stub! (fn [& rst] (fn [& r] r)))
      (with-stub (fn [x & rst] (when (= x :bye) (constantly "leave")))
        (stub! (fn [x & rst] (when (= x :hi) (constantly "hello"))))
        (is (= [:a] (*test-fn-arity-rst* :a)))
        (is (= [:b] (*test-fn-arity-rst* :b)))
        (is (= "hello" (*test-fn-arity-rst* :hi)))
        (is (= "leave" (*test-fn-arity-rst* :bye))))))
  (testing "Rebound, with scope"
    (with-mock *test-fn-arity-rst* (fn [old]
                                     (fn [x & rst]
                                       (apply (or (some #(% x) @*stubs*) old) x rst)))
      (stub! (fn [& rst] (fn [& r] r)))
      (stub! (fn [x & rst] (when (= x :hi) (constantly "hello"))))
      (with-stub (fn [x & rst] (when (= x :bye) (constantly "leave")))
        (is (= [:a] (*test-fn-arity-rst* :a)))
        (is (= [:b] (*test-fn-arity-rst* :b)))
        (is (= "hello" (*test-fn-arity-rst* :hi)))
        (is (= "leave" (*test-fn-arity-rst* :bye))))
      (is (= [:bye] (*test-fn-arity-rst* :bye)))
      (is (= "hello" (*test-fn-arity-rst* :hi))))))


(deftest http-mock-*test-fn-arity-1*
  (testing "Rebind, with stub"
    (with-http-mock *test-fn-arity-1*
      (let [resp {:status 200 :body "Hello!"}]
        (with-stub [{:url "https://example.com"} resp]
          (is (= resp @(*test-fn-arity-1*
                         {:url "https://example.com"
                          :method :get})))))))
  (testing "Rebind, with stub fn pred"
    (with-http-mock *test-fn-arity-1*
      (with-stubs [[(constantly true) {:status 404}]
                   [(fn [req] (= (:method req) :put)) {:status 401}]]
        (is (= {:status 404} @(*test-fn-arity-1* {:url "http://localhost:80"})))
        (is (= {:status 401} @(*test-fn-arity-1* {:method :put}))))))
  (testing "Rebind, with stub map pred"
    (with-http-mock *test-fn-arity-1*
      (with-stubs [[(constantly true) {:status 404}]
                   [{:method :put} {:status 401}]]
        (is (= {:status 404} @(*test-fn-arity-1* {:url "http://localhost:80"})))
        (is (= {:status 401} @(*test-fn-arity-1* {:method :put})))))))
