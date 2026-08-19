#!/usr/bin/env nbb
;; nbb --classpath src:test run-tests.cljs   (from the repository root)
;;
;; The ClojureScript half of the suite. `png.core-test` and
;; `png.encode-jvm-test` are `.clj` -- the first is not ported yet, the second
;; needs `javax.imageio` and never will be -- so what runs here is
;; `png.encode-test`, which exercises the writer and reads every image back
;; through `png.core/parse`. Both halves therefore run on this runtime.
(ns run-tests
  (:require [cljs.test :refer [run-tests]]
            [png.encode-test]))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m)
    (js/process.exit 1)))

(run-tests 'png.encode-test)
