(ns uk.axvr.dynamock-test
  "Tests for uk.axvr.dynamock."
  (:require [clojure.test :refer :all]
            [uk.axvr.dynamock :refer :all]))


;;; --------------------------------------------
;;; Dynamically-scoped function.


(defn ^:dynamic *test-fn-arity-0* [] :a)

(defn ^:dynamic *test-fn-arity-1* [x]
  (if x :a :b))

(defn ^:dynamic *test-fn-arity-rst* [x & ys]
  (if x
    (apply x ys)
    ys))


(deftest fns-work-dynamic
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
    (with-mock *test-fn-arity-0* (fn [_ _] (fn [] :b))
      (is (= :b (*test-fn-arity-0*)))))
  (testing "Rebound, fallback to old"
    (with-mock *test-fn-arity-0* (fn [old _] old)
      (is (= :a (*test-fn-arity-0*))))))


(deftest mock-*test-fn-arity-1*
  (testing "Rebound, no stubs"
    (with-mock *test-fn-arity-1* (fn [old _] #(if (= :c %) :d (old %)))
      (is (= :a (*test-fn-arity-1* :a)))
      (is (= :b (*test-fn-arity-1* nil)))
      (is (= :d (*test-fn-arity-1* :c)))))
  (testing "Rebound, with stub"
    (with-mock *test-fn-arity-1* (fn [_ stb] (fn [x] (some #(% x) (stb))))
      (with-stub *test-fn-arity-1* (fn [x] (if (= x :a) :x))
        (is (= :x (*test-fn-arity-1* :a)))
        (is (= nil (*test-fn-arity-1* :y))))))
  (testing "Rebound, with 2 stubs"
    (with-mock *test-fn-arity-1* (fn [_ stb] (fn [x] (some #(% x) (stb))))
      (with-stubs *test-fn-arity-1* [identity
                                     (fn [x] (if (= x :a) :x))]
        (is (= :x (*test-fn-arity-1* :a)))
        (is (= :y (*test-fn-arity-1* :y))))))
  (testing "Rebound, with stubs"
    (with-mock *test-fn-arity-1* (fn [_ stb] (fn [x] (some #(% x) (stb))))
      (stub! *test-fn-arity-1* identity)
      (with-stub *test-fn-arity-1* (fn [x] (when (= x :bye) "leave"))
        (stub! *test-fn-arity-1* (fn [x] (when (= x :hi) "hello")))
        (is (= :a (*test-fn-arity-1* :a)))
        (is (= :b (*test-fn-arity-1* :b)))
        (is (= "hello" (*test-fn-arity-1* :hi)))
        (is (= "leave" (*test-fn-arity-1* :bye))))))
  (testing "Rebound, with scope"
    (with-mock *test-fn-arity-1* (fn [_ stb] (fn [x] (some #(% x) (stb))))
      (stub! *test-fn-arity-1* identity)
      (stub! *test-fn-arity-1* (fn [x] (when (= x :hi) "hello")))
      (with-stub *test-fn-arity-1* (fn [x] (when (= x :bye) "leave"))
        (is (= :a (*test-fn-arity-1* :a)))
        (is (= :b (*test-fn-arity-1* :b)))
        (is (= "hello" (*test-fn-arity-1* :hi)))
        (is (= "leave" (*test-fn-arity-1* :bye))))
      (is (= :bye (*test-fn-arity-1* :bye)))
      (is (= "hello" (*test-fn-arity-1* :hi))))))


(deftest mock-*test-fn-arity-rst*
  (testing "Rebound, no stubs"
    (with-mock *test-fn-arity-rst* (fn [_ _]
                                     (fn [x & rst]
                                       (if (= :c x) :d (apply - rst))))
      (is (= 0 (*test-fn-arity-rst* :a 3 2 1)))
      (is (= 3 (*test-fn-arity-rst* :b 5 2)))
      (is (= :d (*test-fn-arity-rst* :c 1 2 3)))))
  (testing "Rebound, with stub"
    (with-mock *test-fn-arity-rst* (fn [old stb]
                                     (fn [x & rst]
                                       (apply (or (some #(% x) (stb)) old) x rst)))
      (with-stub *test-fn-arity-rst* (fn [& rst] (fn [& r] r))
        (is (= [:a] (*test-fn-arity-rst* :a)))
        (is (= [:y :a] (*test-fn-arity-rst* :y :a))))))
  (testing "Rebound, with 2 stubs"
    (with-mock *test-fn-arity-rst* (fn [old stb]
                                     (fn [x & rst]
                                       (apply (or (some #(% x) (stb)) old) x rst)))
      (with-stubs *test-fn-arity-rst* [(fn [& rst] (fn [& r] r))
                                       (fn [x & rst] (if (= x :a) (fn [y & rs] y)))]
        (is (= :a (*test-fn-arity-rst* :a)))
        (is (= [:y 1 2 3] (*test-fn-arity-rst* :y 1 2 3))))))
  (testing "Rebound, with stubs"
    (with-mock *test-fn-arity-rst* (fn [old stb]
                                     (fn [x & rst]
                                       (apply (or (some #(% x) (stb)) old) x rst)))
      (stub! *test-fn-arity-rst* (fn [& rst] (fn [& r] r)))
      (with-stub *test-fn-arity-rst* (fn [x & rst] (when (= x :bye) (constantly "leave")))
        (stub! *test-fn-arity-rst* (fn [x & rst] (when (= x :hi) (constantly "hello"))))
        (is (= [:a] (*test-fn-arity-rst* :a)))
        (is (= [:b] (*test-fn-arity-rst* :b)))
        (is (= "hello" (*test-fn-arity-rst* :hi)))
        (is (= "leave" (*test-fn-arity-rst* :bye))))))
  (testing "Rebound, with scope"
    (with-mock *test-fn-arity-rst* (fn [old stb]
                                     (fn [x & rst]
                                       (apply (or (some #(% x) (stb)) old) x rst)))
      (stub! *test-fn-arity-rst* (fn [& rst] (fn [& r] r)))
      (stub! *test-fn-arity-rst* (fn [x & rst] (when (= x :hi) (constantly "hello"))))
      (with-stub *test-fn-arity-rst* (fn [x & rst] (when (= x :bye) (constantly "leave")))
        (is (= [:a] (*test-fn-arity-rst* :a)))
        (is (= [:b] (*test-fn-arity-rst* :b)))
        (is (= "hello" (*test-fn-arity-rst* :hi)))
        (is (= "leave" (*test-fn-arity-rst* :bye))))
      (is (= [:bye] (*test-fn-arity-rst* :bye)))
      (is (= "hello" (*test-fn-arity-rst* :hi))))))


;;; --------------------------------------------
;;; Regular function.


(defn test-fn-arity-0 [] :a)

(defn test-fn-arity-1 [x]
  (if x :a :b))

(defn test-fn-arity-rst [x & ys]
  (if x
    (apply x ys)
    ys))


(deftest fns-work
  (testing "test-fn-arity-0"
    (is (= :a (test-fn-arity-0))))
  (testing "test-fn-arity-1"
    (is (= :a (test-fn-arity-1 1)))
    (is (= :a (test-fn-arity-1 true)))
    (is (= :b (test-fn-arity-1 nil)))
    (is (= :b (test-fn-arity-1 false))))
  (testing "test-fn-arity-rst"
    (is (= 6 (test-fn-arity-rst + 1 2 3)))
    (is (= [1 2 3] (test-fn-arity-rst nil 1 2 3)))
    (is (empty? (test-fn-arity-rst nil)))
    (is (= [nil] (test-fn-arity-rst nil nil)))))


(deftest mock-test-fn-arity-0
  (testing "Rebound, no stubs"
    (with-mock test-fn-arity-0 (fn [_ _] (fn [] :b))
      (is (= :b (test-fn-arity-0)))))
  (testing "Rebound, fallback to old"
    (with-mock test-fn-arity-0 (fn [old _] old)
      (is (= :a (test-fn-arity-0))))))


(deftest mock-test-fn-arity-1
  (testing "Rebound, no stubs"
    (with-mock test-fn-arity-1 (fn [old _] #(if (= :c %) :d (old %)))
      (is (= :a (test-fn-arity-1 :a)))
      (is (= :b (test-fn-arity-1 nil)))
      (is (= :d (test-fn-arity-1 :c)))))
  (testing "Rebound, with stub"
    (with-mock test-fn-arity-1 (fn [_ stb] (fn [x] (some #(% x) (stb))))
      (with-stub test-fn-arity-1 (fn [x] (if (= x :a) :x))
        (is (= :x (test-fn-arity-1 :a)))
        (is (= nil (test-fn-arity-1 :y))))))
  (testing "Rebound, with 2 stubs"
    (with-mock test-fn-arity-1 (fn [_ stb] (fn [x] (some #(% x) (stb))))
      (with-stubs test-fn-arity-1 [identity
                                   (fn [x] (if (= x :a) :x))]
        (is (= :x (test-fn-arity-1 :a)))
        (is (= :y (test-fn-arity-1 :y))))))
  (testing "Rebound, with stubs"
    (with-mock test-fn-arity-1 (fn [_ stb] (fn [x] (some #(% x) (stb))))
      (stub! test-fn-arity-1 identity)
      (with-stub test-fn-arity-1 (fn [x] (when (= x :bye) "leave"))
        (stub! test-fn-arity-1 (fn [x] (when (= x :hi) "hello")))
        (is (= :a (test-fn-arity-1 :a)))
        (is (= :b (test-fn-arity-1 :b)))
        (is (= "hello" (test-fn-arity-1 :hi)))
        (is (= "leave" (test-fn-arity-1 :bye))))))
  (testing "Rebound, with scope"
    (with-mock test-fn-arity-1 (fn [_ stb] (fn [x] (some #(% x) (stb))))
      (stub! test-fn-arity-1 identity)
      (stub! test-fn-arity-1 (fn [x] (when (= x :hi) "hello")))
      (with-stub test-fn-arity-1 (fn [x] (when (= x :bye) "leave"))
        (is (= :a (test-fn-arity-1 :a)))
        (is (= :b (test-fn-arity-1 :b)))
        (is (= "hello" (test-fn-arity-1 :hi)))
        (is (= "leave" (test-fn-arity-1 :bye))))
      (is (= :bye (test-fn-arity-1 :bye)))
      (is (= "hello" (test-fn-arity-1 :hi))))))


(deftest mock-test-fn-arity-rst
  (testing "Rebound, no stubs"
    (with-mock test-fn-arity-rst (fn [_ _]
                                   (fn [x & rst]
                                     (if (= :c x) :d (apply - rst))))
      (is (= 0 (test-fn-arity-rst :a 3 2 1)))
      (is (= 3 (test-fn-arity-rst :b 5 2)))
      (is (= :d (test-fn-arity-rst :c 1 2 3)))))
  (testing "Rebound, with stub"
    (with-mock test-fn-arity-rst (fn [old stb]
                                   (fn [x & rst]
                                     (apply (or (some #(% x) (stb)) old) x rst)))
      (with-stub test-fn-arity-rst (fn [& rst] (fn [& r] r))
        (is (= [:a] (test-fn-arity-rst :a)))
        (is (= [:y :a] (test-fn-arity-rst :y :a))))))
  (testing "Rebound, with 2 stubs"
    (with-mock test-fn-arity-rst (fn [old stb]
                                   (fn [x & rst]
                                     (apply (or (some #(% x) (stb)) old) x rst)))
      (with-stubs test-fn-arity-rst [(fn [& rst] (fn [& r] r))
                                     (fn [x & rst] (if (= x :a) (fn [y & rs] y)))]
        (is (= :a (test-fn-arity-rst :a)))
        (is (= [:y 1 2 3] (test-fn-arity-rst :y 1 2 3))))))
  (testing "Rebound, with stubs"
    (with-mock test-fn-arity-rst (fn [old stb]
                                   (fn [x & rst]
                                     (apply (or (some #(% x) (stb)) old) x rst)))
      (stub! test-fn-arity-rst (fn [& rst] (fn [& r] r)))
      (with-stub test-fn-arity-rst (fn [x & rst] (when (= x :bye) (constantly "leave")))
        (stub! test-fn-arity-rst (fn [x & rst] (when (= x :hi) (constantly "hello"))))
        (is (= [:a] (test-fn-arity-rst :a)))
        (is (= [:b] (test-fn-arity-rst :b)))
        (is (= "hello" (test-fn-arity-rst :hi)))
        (is (= "leave" (test-fn-arity-rst :bye))))))
  (testing "Rebound, with scope"
    (with-mock test-fn-arity-rst (fn [old stb]
                                   (fn [x & rst]
                                     (apply (or (some #(% x) (stb)) old) x rst)))
      (stub! test-fn-arity-rst (fn [& rst] (fn [& r] r)))
      (stub! test-fn-arity-rst (fn [x & rst] (when (= x :hi) (constantly "hello"))))
      (with-stub test-fn-arity-rst (fn [x & rst] (when (= x :bye) (constantly "leave")))
        (is (= [:a] (test-fn-arity-rst :a)))
        (is (= [:b] (test-fn-arity-rst :b)))
        (is (= "hello" (test-fn-arity-rst :hi)))
        (is (= "leave" (test-fn-arity-rst :bye))))
      (is (= [:bye] (test-fn-arity-rst :bye)))
      (is (= "hello" (test-fn-arity-rst :hi))))))
