# Dynamock

A Clojure library for function mocking, with included utilities for mocking
HTTP requests.


## Installation

Add the following to your `deps.edn` file:

```clojure
{:deps {uk.axvr/dynamock
         {:git/sha "ca8ef81af8af35c5503355174486495fa3a621e1"
          :git/url "https://github.com/axvr/dynamock.git"}}}
```


## Usage

**Note**: Dynamock is still a work-in-progress.  Until it reaches v1.0, expect
backwards incompatible changes.

Examples using the provided HTTP mocking utilities:

```clojure
(require '[uk.axvr.dynamock :refer :all]
         '[uk.axvr.dynamock.http :refer :all]
         '[org.httpkit.client :as http]
         '[clojure.string :as str])

(def http-fn http/request)  ; Also works with clj-http.

;; HTTP requests work as expected.
@(http-fn {:url "https://example.com"})      ; => {:status 200, ...}
@(http-fn {:url "https://example.com/foo"})  ; => {:status 404, ...}

(with-http-mock http-fn
  ;; Register a stub.
  (with-stub http-fn [{:url "https://exmaple.com/foo"}
                      {:status 200, :body "Hello world!"}]
    ;; Real network request.
    @(http-fn {:url "https://example.com"})        ; => {:status 200, ...}
    ;; Uses the stub we defined.
    @(http-fn {:url "https://example.com/foo"})))  ; => {:status 200, :body "Hello world!"}

(with-http-mock http-fn
  ;; Disallow real network requests.
  ;;   As this is fairly common, there is already a stub provided for this:
  ;;     (stub! http-fn uk.axvr.dynamock.http/block-real-http-requests)
  (stub! http-fn [(constantly true)
                  (fn [req]
                    (throw (ex-info "Real HTTP requests are not allowed!" req)))])
  ;; Register a stub, that will only be used by requests in this with-stub block.
  (with-stub http-fn [{:url "https://example.com/works"
                       :method :get}
                      {:status 200, :body "Works!"}]
    @(http-fn {:url "https://example.com"})         ; => throws exception!
    @(http-fn {:url "https://example.com/works"})   ; => {:status 200, :body "Works!"}
    @(http-fn {:url "https://example.com/works"     ; => throws exception!
                 :method :post}))
  ;; Outside of the previous stub-scope, so request fails.
  @(http-fn {:url "https://example.com/works"}))    ; => throws exception!

(defn some-fn-that-uses-http-fn [method url]
  (:body @(http-fn {:method method, :url url})))

(with-http-mock http-fn
  ;; Disallow real network requests, except those to "http://localhost:8080".
  (stub! http-fn [(fn [req]
                    #(not (str/starts-with? (:url req) "http://localhost:8080")))
                  (fn [req]
                    (throw (ex-info "External HTTP requests are not allowed!" req)))])
  ;; You can register multiple stubs at once.
  (with-stubs http-fn [[#(str/starts-with? (:url %) "https://clojure.org")
                        (fn [req]
                          (if (= :get (:method req))
                            {:status 200, :body "Clojure is great!"}
                            {:status 401, :body "Unauthorized!"}))]
                       [#(= (:url %) "http://localhost:8080/foo")
                        {:status 500, :body "Server error!"}]]
    (some-fn-that-uses-http-fn :get "https://clojure.org/great")   ; => "Clojure is great!"
    (some-fn-that-uses-http-fn :post "https://clojure.org")        ; => "Unauthorized!"
    (some-fn-that-uses-http-fn :get "http://localhost:8080/foo"))  ; => "Server error!"
  (some-fn-that-uses-http-fn :get "http://localhost:8080/foo")  ; => contacts local server
  (some-fn-that-uses-http-fn :get "https://clojure.org"))       ; => throws exception!
```


## Legal

No rights reserved.

All source code, documentation and associated files packaged and distributed
with "uk.axvr.dynamock" are dedicated to the public domain. A full copy of the
CC0 (Creative Commons Zero 1.0 Universal) public domain dedication can be found
in the `COPYING` file.

The author is not aware of any patent claims which may affect the use,
modification or distribution of this software.
