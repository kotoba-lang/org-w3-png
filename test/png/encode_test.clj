(ns png.encode-test
  "What a written PNG has to be true of.

  Two oracles, and both matter. `png.core/parse` reading back what
  `png.encode` wrote proves the pair agree; `javax.imageio` reading it proves
  they agree with everybody else, which is the part a self-consistent pair
  cannot tell you. A writer checked only against its own reader is a writer
  that can be wrong in the same direction twice."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [png.core :as png]
            [png.encode :as encode]))

(def grammar (edn/read-string (slurp (io/resource "png/grammar.edn"))))

(defn- ->image
  "Through the JVM's own PNG reader, or nil if it refuses the bytes."
  [bytes]
  (javax.imageio.ImageIO/read
   (java.io.ByteArrayInputStream. (byte-array (map unchecked-byte bytes)))))

;; ── round trip ───────────────────────────────────────────────────────────────

(deftest what-is-written-is-what-comes-back
  (let [pixels (vec (for [y (range 4) x (range 6) c (range 3)]
                      (mod (+ (* y 7) (* x 3) c) 256)))
        out (encode/encode pixels {:width 6 :height 4 :color :rgb})
        back (png/parse grammar out)]
    (is (= png/signature (take 8 out)))
    (is (= 6 (:width (:ihdr back))))
    (is (= 4 (:height (:ihdr back))))
    (is (= 8 (:bit-depth (:ihdr back))))
    (is (= :rgb (:color-type (:ihdr back))))
    (is (= 0 (:interlace (:ihdr back))))
    (is (= pixels (vec (:pixels back))) "every sample, unchanged")))

(deftest every-colour-type-round-trips
  (doseq [[color n] {:gray 1 :gray-alpha 2 :rgb 3 :rgba 4}]
    (testing (name color)
      (let [pixels (vec (for [i (range (* 3 2 n))] (mod (* i 11) 256)))
            back (png/parse grammar (encode/encode pixels {:width 3 :height 2
                                                           :color color}))]
        (is (= pixels (vec (:pixels back))))))))

;; ── somebody else's reader ───────────────────────────────────────────────────

(deftest the-jvm-s-own-decoder-accepts-it
  ;; The check a self-consistent pair cannot make. If the CRC, the zlib
  ;; wrapper or the IHDR were wrong in a way both halves here shared, this
  ;; is what would say so.
  (let [pixels (vec (for [y (range 3) x (range 3) c (range 3)]
                      (if (= c 0) (* 80 x) (if (= c 1) (* 80 y) 40))))
        image (->image (encode/encode pixels {:width 3 :height 3 :color :rgb}))]
    (is (some? image) "javax.imageio read it")
    (is (= 3 (.getWidth image)))
    (is (= 3 (.getHeight image)))
    (testing "and the pixel at (2,1) is the one that was written"
      (let [rgb (.getRGB image 2 1)]
        (is (= 160 (bit-and (bit-shift-right rgb 16) 0xff)) "red = 80×2")
        (is (= 80 (bit-and (bit-shift-right rgb 8) 0xff)) "green = 80×1")
        (is (= 40 (bit-and rgb 0xff)))))))

(deftest alpha-survives-a-foreign-reader
  (let [pixels [255 0 0 255,  0 255 0 128]
        image (->image (encode/encode pixels {:width 2 :height 1 :color :rgba}))]
    (is (= 255 (bit-and (bit-shift-right (.getRGB image 0 0) 24) 0xff)))
    (is (= 128 (bit-and (bit-shift-right (.getRGB image 1 0) 24) 0xff)))))

;; ── the CRC is over the type as well as the data ─────────────────────────────

(deftest the-chunk-crc-covers-the-type
  ;; Easy to get wrong because the LENGTH field deliberately excludes the
  ;; type. A CRC that did too would validate a chunk whose type had been
  ;; changed, which is the one thing the field exists to catch.
  (let [a (encode/chunk-bytes "IEND" [])
        b (encode/chunk-bytes "IDAT" [])]
    (is (= 12 (count a)))
    (is (not= (take-last 4 a) (take-last 4 b))
        "same (empty) data, different type, different CRC")))

;; ── refusals ─────────────────────────────────────────────────────────────────

(deftest a-sample-count-that-does-not-fit-the-geometry-is-refused
  ;; Padding or truncating produces a file that opens and is wrong — the
  ;; image skewed diagonally, which is what a row-length error always looks
  ;; like and never says.
  (is (= :png/sample-count-mismatch
         (:type (try (encode/encode [1 2 3] {:width 2 :height 2 :color :rgb})
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))))))

(deftest an-unwritable-colour-type-is-refused-by-name
  (is (= :png/unsupported-color
         (:type (try (encode/encode [1] {:width 1 :height 1 :color :indexed})
                     (catch clojure.lang.ExceptionInfo e (ex-data e))))))
  (is (= :png/bad-geometry
         (:type (try (encode/encode [] {:width 0 :height 1 :color :gray})
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))))))

;; ── size ─────────────────────────────────────────────────────────────────────

(deftest a-flat-image-compresses
  ;; Filter type 0 everywhere still leaves deflate something to do, and an
  ;; encoder whose output was larger than its input would be one nobody
  ;; should use over a network.
  (let [pixels (vec (repeat (* 64 64 3) 200))
        out (encode/encode pixels {:width 64 :height 64 :color :rgb})]
    (is (< (count out) (/ (count pixels) 10))
        "one colour, so this should be tiny")))
