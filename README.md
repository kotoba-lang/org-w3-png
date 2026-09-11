# kotoba-lang/org-w3-png

Zero-dep-beyond-`org-ietf-deflate` portable `.cljc` PNG decoder **and encoder** (W3C
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

Both runtimes, and both are load-bearing.

```sh
clojure -M:test                        # JVM: everything, incl. the javax.imageio oracle
nbb --classpath src:test run-tests.cljk   # ClojureScript: png.encode-test
```

`png.encode-test` is `.cljc` and `png.encode-jvm-test` is not, deliberately.
Until 2026-08-19 the writer's whole suite was `.clj`, and under ClojureScript
`(int c)` over a string gives 0 -- so every chunk type was written as four
zero bytes, and every PNG this repository produced outside the JVM was
unreadable. The file still had a signature, a plausible length and a correct
CRC over the zeroed type, so nothing said so until a reader ran off the end of
the data. **An assertion parked in a `.clj` file is an assertion ClojureScript
never makes.** `png.core-test` is still JVM-only; the reader is nevertheless
exercised on both runtimes, because every image the writer tests produce is
read back through `png.core/parse`.

## Encoding (`png.encode`)

The decoder has been here since the `kasane` extraction and nothing could
write one. That asymmetry only shows up when something already holds pixels
and needs a browser to see them — a PDF image XObject decodes to raw samples,
and raw samples are not a picture anyone can display.

```clojure
(require '[png.encode :as encode])
(encode/encode pixels {:width 640 :height 480 :color :rgb})
;; => PNG bytes
```

Bit depth 8, colour types `:gray :gray-alpha :rgb :rgba`, no interlace, one
`IDAT`, filter type 0 on every scanline. That is exactly what `png.core/parse`
can read back — R0 there does not unfilter sub-byte depths or Adam7 — so the
pair round-trips and a writer that emitted something its own reader could not
take would be a writer nobody could check. 16-bit is refused rather than
truncated; a sample count that does not fit the geometry is refused rather
than padded, because padding produces a file that opens and is wrong (the
image skewed diagonally, which is what a row-length error always looks like
and never says).

Per-line filter selection is deliberately absent — see the ns docstring. The
tests check the output against `javax.imageio` as well as against `png.core`,
because a writer checked only by its own reader can be wrong in the same
direction twice.
