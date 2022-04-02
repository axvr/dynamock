(ns uk.axvr.dynamock.http-test
  "Tests for uk.axvr.dynamock.http."
  (:require [clojure.test :refer :all]
            [uk.axvr.dynamock :refer :all]
            [uk.axvr.dynamock.http :refer :all]
            [clojure.string :as str])
  (:import clojure.lang.ExceptionInfo))


(defn ^:dynamic *http-fn* [req]
  (delay
    (condp = (:method req)
      :get  {:status  200
             :headers {:content-type "text/plain"}
             :body    "Got something."}
      :post {:status  201
             :headers {:content-type "text/plain"}
             :body    "Posted something."})))


(deftest dyn-http-fn-works
  (testing "*http-fn*"
    (is (= @(*http-fn* {:url "https://example.com", :method :get})
           {:status  200
            :headers {:content-type "text/plain"}
            :body    "Got something."}))
    (is (= @(*http-fn* {:url "https://example.com/foo", :method :post})
           {:status  201
            :headers {:content-type "text/plain"}
            :body    "Posted something."}))))


(deftest mock-http-fn
  (testing "Rebind, with stub"
    (with-http-mock *http-fn*
      (let [resp {:status 200 :body "Hello!"}]
        (with-stub [{:url "https://example.com"} resp]
          (is (= resp @(*http-fn*
                         {:url "https://example.com"
                          :method :get})))))))
  (testing "Rebind, with stub fn pred"
    (with-http-mock *http-fn*
      (with-stubs [[(constantly true) {:status 404}]
                   [(fn [req] (= (:method req) :put)) {:status 401}]]
        (is (= {:status 404} @(*http-fn* {:url "http://localhost:80"})))
        (is (= {:status 401} @(*http-fn* {:method :put}))))))
  (testing "Rebind, with stub map pred"
    (with-http-mock *http-fn*
      (with-stubs [[(constantly true) {:status 404}]
                   [{:method :put} {:status 401}]]
        (is (= {:status 404} @(*http-fn* {:url "http://localhost:80"})))
        (is (= {:status 401} @(*http-fn* {:method :put})))))))


(deftest test-block-real-http-requests
  (testing "Blocking real HTTP requests."
    (with-http-mock *http-fn*
      (stub! block-real-http-requests)
      (stub! [{:url "https://example.com/allowed"}
              {:status 200}])
      (is (thrown-with-msg?
            ExceptionInfo
            (re-pattern
              (str/re-quote-replacement
                "uk.axvr.dynamock: real HTTP requests are not allowed."))
            @(*http-fn* {:url "https://example.com"})))
      (is (= {:status 200} @(*http-fn* {:url "https://example.com/allowed"}))))))
