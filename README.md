# Dynamock

A collection of simple utilities for mocking [Clojure][] functions, with extra
utilities for mocking HTTP requests and (eventually) more.

Dynamock is based around the concepts of [mocks and stubs][].  Mocks have
access to a set of registered stubs which it can use to choose/build
appropriate responses.

> **Note**
> Dynamock cannot mock macros or [inlined functions][].

[Clojure]: https://clojure.org
[mocks and stubs]: https://martinfowler.com/articles/mocksArentStubs.html#TheDifferenceBetweenMocksAndStubs
[inlined functions]: http://bytopia.org/2014/07/07/inline-functions-in-clojure/

([_Installation instructions_](#installation).)


## Mocking functions

Dynamock is best explained through examples.  (You may find it particularly
helpful to experiment with them at a REPL.)


### Simple mocking

```clojure
(require '[uk.axvr.dynamock :refer [with-mock]])

;; The function we want to mock: increments a number...
(defn my-fn [x]
  (+ x 1))

;; Increments numbers as expected...
(my-fn 3)  ; => 4
(my-fn 4)  ; => 5

;; Create a function that when called with create our mock.
(defn my-mock [orig-fn get-stubs]  ; `get-stubs` will be explained later.
  (fn [x]
    (if (even? x)
      (orig-fn x)  ; Even: call the original `my-fn` function: i.e. increment.
      (- x 1))))   ; Odd: decrement x.

;; Replace `my-fn` with your mock only in the macro body...
(with-mock my-fn my-mock
  ;; Odd numbers are now decremented...
  (my-fn 3)   ; => 2
  (my-fn 4))  ; => 5

;; Outside of `with-mock` block; `my-fn` works as before...
(my-fn 3)  ; => 4
(my-fn 4)  ; => 5
```

Dynamock can also mock functions used internally by other functions, actually
this is probably how you would mostly use it.

```clojure
(require '[uk.axvr.dynamock :refer [with-mock]])

(defn my-fn [x]
  (filter even? x))

;; Removes non-even (odd) values from collection...
(my-fn [1 2 3 4 5 6])  ; => (2 4 6)

;; Mock `filter` as `remove`.
(with-mock filter (constantly remove)
  ;; Removes even numbers from collection...
  (my-fn [1 2 3 4 5 6]))  ; => (1 3 5)
```


### Adding stubs to your mocks

The real power of Dynamock shows when combining your mocks with stubs.

```clojure
(require '[uk.axvr.dynamock :refer [with-mock with-stub]])

(defn my-fn [x]
  (+ x 1))

(defn my-mock [orig-fn get-stubs]
  ;; You should be able to work out what this does from the examples below...
  (fn [x]
    ;; `(get-stubs)` will return a list of registered stubs for this mock with
    ;; the most recently registered stubs first.
    (if-let [stub (->> (get-stubs)
                       (filter #(= x (first %)))
                       (some second))]
      stub
      (orig-fn x))))

(with-mock my-fn my-mock

  ;; Map 4 -> 8, else use original `my-fn`...
  (with-stub my-fn [4 8]
    (my-fn 3)   ; => 4
    (my-fn 4))  ; => 8

  ;; Map 3 -> 9, else use original `my-fn`...
  (with-stub my-fn [3 9]
    (my-fn 3)   ; => 9
    (my-fn 4))  ; => 5

  ;; You can register multiple stubs at once...
  (with-stub my-fn [3 9]
    (with-stub my-fn [4 8]
      (my-fn 3)    ; => 9
      (my-fn 4)))  ; => 8

  ;; Later stub registrations override earlier ones.
  (with-stub my-fn [3 9]
    (with-stub my-fn [3 7]
      (my-fn 3)    ; => 7
      (my-fn 4)))  ; => 5

  ;; Stubs don't apply outside of their scope.
  (my-fn 3)   ; => 4
  (my-fn 4))  ; => 5
```

Why not use functions in your stubs?

```clojure
(require '[uk.axvr.dynamock :refer [with-mock with-stub]])

(defn my-fn [x]
  (+ x 1))

(defn my-mock [orig-fn get-stubs]
  (fn [x]
    (if-let [stub (->> (get-stubs)
                       (filter #((first %) x))  ; Call the stub predicate on `x`.
                       (some second))]
      (stub x)  ; If a predicate fn returned true, we invoke the stub on `x`.
      (orig-fn x))))

(with-mock my-fn my-mock

  ;; Double even numbers, else use original `my-fn`...
  (with-stub my-fn [even? #(* 2 %)]
    (my-fn 3)   ; => 4
    (my-fn 4))  ; => 8

  ;; Triple odd numbers, else use original `my-fn`...
  (with-stub my-fn [odd? (fn [x] (* x 3))]
    (my-fn 3)    ; => 9
    (my-fn 4)))  ; => 5
```


### Other ways to register stubs

Dynamock includes several convenience macros that you can use to make your
tests easier to read.

```clojure
(require '[uk.axvr.dynamock :refer [with-mock with-stub with-stubs stub! with-stub-scope]])

;; ...

;; Avoid nesting `with-stub` calls by using `with-stubs`...
(with-stubs my-fn [[3 9] [4 8]]
  (my-fn 3)
  (my-fn 4))
;; Equivalent to:
(with-stub my-fn [3 9]
  (with-stub my-fn [4 8]
    (my-fn 3)
    (my-fn 4)))

;; Instead of `with-stub` you can use its building blocks independently.
;;   - `with-stub-scope`, restricts stubs registered in its body to that body.
;;   - `stub!`, register a stub in the current stub scope.
(with-stub-scope
  (stub! my-fn [3 9])
  (my-fn 3)
  (my-fn 4))
;; Equivalent to:
(with-stub my-fn [3 9]
  (my-fn 3)
  (my-fn 4))

;; ...
```


## HTTP mocking

Dynamock contains a few helper utilities for mocking HTTP requests.  These
utilities are under the `uk.axvr.dynamock.http` namespace.

```clojure
(require '[uk.axvr.dynamock :refer [stub! with-stub with-stubs]]
         '[uk.axvr.dynamock.http :as dyn-http :refer [with-http-mock]]
         '[org.httpkit.client :as http]
         '[clojure.string :as str])

;; Make responses derefable to behave like HttpKit.
(swap! dyn-http/default-opts
       assoc :transform-response #(dyn-http/->derefable %2))

;; HTTP requests work as expected.
@(http/get "https://example.com")      ; => {:status 200, ...}
@(http/get "https://example.com/foo")  ; => {:status 404, ...}

(with-http-mock http/request
  ;; Register a stub.
  (with-stub http/request [{:url "https://exmaple.com/foo"}
                           {:status 200, :body "Hello world!"}]
    ;; Real network request.
    @(http/get "https://example.com")        ; => {:status 200, ...}
    ;; Uses the stub we defined.
    @(http/get "https://example.com/foo")))  ; => {:status 200, :body "Hello world!"}

(with-http-mock http/request
  ;; Disallow real network requests.
  ;;   As this is fairly common, a stub is provided for this:
  ;;     (stub! http/request dyn-http/block-real-http-requests)
  (stub! http/request [(constantly true)
                       (fn [req]
                         (throw (ex-info "Real HTTP requests are not allowed!" req)))])
  ;; Register a stub, that will only be used by requests in this with-stub block.
  (with-stub http/request [{:url    "https://example.com/works"
                            :method :get}
                           {:status 200, :body "Works!"}]
    @(http/get "https://example.com")         ; => throws exception!
    @(http/get "https://example.com/works")   ; => {:status 200, :body "Works!"}
    @(http/post "https://example.com/works")  ; => throws exception!
    @(http/request {:url "https://example.com/works, :method :post"})}))  ; => same as above: throws exception.
  ;; Outside of the previous stub-scope, so request fails.
  @(http/get "https://example.com/works"))    ; => throws exception!

(defn do-something [method url]
  (:body @(http/request {:method method, :url url})))

(with-http-mock http/request
  ;; Disallow real network requests, except those to "http://localhost:8080".
  (stub! http/request [(fn [req]
                         #(not (str/starts-with? (:url req) "http://localhost:8080")))
                       (fn [req]
                         (throw (ex-info "External HTTP requests are not allowed!" req)))])
  ;; You can register multiple stubs at once.
  (with-stubs http/request [[#(str/starts-with? (:url %) "https://clojure.org")
                             (fn [req]
                               (if (= :get (:method req))
                                 {:status 200, :body "Clojure is great!"}
                                 {:status 401, :body "Unauthorized!"}))]
                            [#(= (:url %) "http://localhost:8080/foo")
                             {:status 500, :body "Server error!"}]]
    (do-something :get "https://clojure.org/great")   ; => "Clojure is great!"
    (do-something :post "https://clojure.org")        ; => "Unauthorized!"
    (do-something :get "http://localhost:8080/foo"))  ; => "Server error!"
  (do-something :get "http://localhost:8080/foo")     ; => contacts local server
  (do-something :get "https://clojure.org"))          ; => throws exception!
```


## Installation

> **Warning**
> Dynamock is still a work-in-progress.  Until it reaches v1.0, expect
> backwards incompatible changes.


### tools.deps

Add the following to your `deps.edn` file:

```clojure
{:deps {uk.axvr/dynamock
        {:git/tag "v0.3" :git/sha "de0fa67"
         :git/url "https://github.com/axvr/dynamock.git"}}}
```


### Leiningen

To install Dynamock with Leiningen, you will need to use [lein-git-down][] as
Dynamock is not distributed as a JAR.  This is an example `project.clj` file:

[lein-git-down]: https://github.com/reifyhealth/lein-git-down

```clojure
(defproject my-project "0.1.0"
  :plugins      [[reifyhealth/lein-git-down "0.4.1"]]
  :middleware   [lein-git-down.plugin/inject-properties]
  :repositories [["public-github" {:url "git://github.com"}]]
  :git-down     {uk.axvr/dynamock {:coordinates axvr/dynamock}}
  :dependencies [[uk.axvr/dynamock "de0fa67c469448e5ca474b756856bed39452349f"]])
```


## Legal

No rights reserved.

All source code, documentation and associated files packaged and distributed
with "uk.axvr.dynamock" are dedicated to the public domain. A full copy of the
CC0 (Creative Commons Zero 1.0 Universal) public domain dedication can be found
in the `COPYING` file.

The author is not aware of any patent claims which may affect the use,
modification or distribution of this software.
