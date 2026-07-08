# kotoba-lang/org-w3-png

Zero-dep-beyond-`org-ietf-deflate` portable `.cljc` PNG decoder (W3C
Recommendation, also ISO/IEC 15948). Named `org-w3-png` — same
`org-w3-<spec>` pattern as `org-w3-svg`/`org-w3-woff` (PNG's canonical spec
home is `w3.org/TR/png`).

Extracted from `kotoba-lang/kasane` (kasane.png, ADR-2606272100). Chunk
structure is a small EDN grammar interpreted by a self-contained copy of the
generic binary-grammar decode engine (`png.decode`, duplicated from
kasane.decode so this repo has no back-dependency on kasane); IDAT payloads
are zlib, decoded via `org-ietf-deflate`; scanlines are unfiltered per PNG
§9 (None/Sub/Up/Average/Paeth). R0 supports bit-depth 8/16; sub-byte depths
and Adam7 interlace are not yet unfiltered.

## Usage

```clojure
(require '[clojure.edn :as edn] '[clojure.java.io :as io] '[png.core :as png])

(def grammar (edn/read-string (slurp (io/resource "png/grammar.edn"))))
(png/parse grammar png-bytes)
;; => {:ihdr {:width :height :bit-depth :color-type ...} :chunks [...] :pixels [...]}
```

## Test

```sh
clojure -M:test
```
