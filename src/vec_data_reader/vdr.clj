(ns vec-data-reader.vdr)


(defn first-non-hex-char [string]
  (re-find #"[^0-9a-fA-F]" string))

(comment
(first-non-hex-char "z")
;; "z"
(first-non-hex-char "9")
;; nil
(first-non-hex-char "deadbeefghost")
;; "g"
)

(defn hex-string-to-clojure-core-vec-of-byte [hex-string]
  (if-let [bad-hex-digit-string (first-non-hex-char hex-string)]
    (throw (ex-info (format "String that should consist of only hexadecimal digits contained: %s (UTF-16 code point %d)"
                            bad-hex-digit-string
                            (int (first bad-hex-digit-string)))
                    {:input-string hex-string
                     :bad-hex-digit-string bad-hex-digit-string}))
    (if (not (zero? (mod (count hex-string) 2)))
      (throw (ex-info (format "String contains odd number %d of hex digits.  Should be even number of digits."
                              (count hex-string))
                      {:input-string hex-string
                       :length (count hex-string)}))
      ;; There are likely more efficient ways to do this, if
      ;; performance is critical for you.  I have done no performance
      ;; benchmarking on this code.  This code is taking advantage of
      ;; JVM library calls that I was already aware of.
      (let [hex-digit-pairs (re-seq #"[0-9a-fA-F]{2}" hex-string)
            byte-list (map (fn [two-hex-digit-str]
                             (.byteValue
                              (java.lang.Short/valueOf two-hex-digit-str 16)))
                           hex-digit-pairs)]
        (apply vector-of :byte byte-list)))))

(defn clojure-vec-of-byte-to-hex-string [vec]
  (apply str (map #(format "%02x" %) vec)))

;; TBD: This only correctly handles the case of a clojure.core.Vec
;; object whose elements are of type Byte.

#_(defmethod print-dup clojure.core.Vec [o ^java.io.Writer w]
  (.write w (str "#my.ns/byte-vec \"" (clojure-vec-of-byte-to-hex-string o)
                 "\"")))

(comment

(require '[vec-data-reader.vdr :as vdr] :reload)
(def bv0 (vdr/hex-string-to-clojure-core-vec-of-byte "0123456789abcdef007f80ff"))
(def bv1 (read-string "#my.ns/byte-vec \"0123456789abcdef007f80ff\""))
(def bv2 #my.ns/byte-vec "0123456789abcdef007f80ff")
(def bv3 '#my.ns/byte-vec "0123456789abcdef007f80ff")

(vdr/clojure-vec-of-byte-to-hex-string bv0)
;; "0123456789abcdef007f80ff"

(binding [*print-dup* false] (print bv0))
;; [1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]nil

(binding [*print-dup* true] (print bv0))
;; #my.ns/byte-vec "0123456789abcdef007f80ff"nil

bv0
bv1
bv2
bv3

(type bv0)
(type bv1)
(type bv2)
(type bv3)

(type (eval bv0))
(type (eval bv1))
(type (eval bv2))
(type (eval bv3))

)


(comment

(doc re-seq)
(re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef")
(map (fn [two-hex-digit-str]
       (java.lang.Byte/valueOf two-hex-digit-str 16))
     (re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef"))
;; "89" is out of range for Byte
(map (fn [two-hex-digit-str]
       (java.lang.Byte/parseByte two-hex-digit-str 16))
     (re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef"))
;; "89" is out of range for Byte
(map (fn [two-hex-digit-str]
       (java.lang.Byte/decode (str "0x" two-hex-digit-str)))
     (re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef"))
;; Value 137 out of range from input 0x89
(map (fn [two-hex-digit-str]
       (java.lang.Short/valueOf two-hex-digit-str 16))
     (re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef"))
;; No exception, and returns desired list of Short values:
;; (1 35 69 103 137 171 205 239)

;; Now try converting that to list of Byte values, where Short values
;; 128 through 255 should be negative Byte values.

(->> (re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef")
     (map (fn [two-hex-digit-str]
            (.byteValue (java.lang.Short/valueOf two-hex-digit-str 16)))))
;; (1 35 69 103 -119 -85 -51 -17)

;; Looks correct.  Double check by converting back to hex strings.

(->> (re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef")
     (map (fn [two-hex-digit-str]
            (.byteValue (java.lang.Short/valueOf two-hex-digit-str 16))))
     (map #(format "%2x" %)))
;; (" 1" "23" "45" "67" "89" "ab" "cd" "ef")

;; test min and max possible values of Byte, both negative and positive
(->> (re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef007f80ff")
     (map (fn [two-hex-digit-str]
            (.byteValue (java.lang.Short/valueOf two-hex-digit-str 16))))
     (map #(format "%2x" %)))
;; (" 1" "23" "45" "67" "89" "ab" "cd" "ef" " 0" "7f" "80" "ff")

(->> (re-seq #"[0-9a-fA-F]{2}" "0123456789abcdef007f80ff")
     (map (fn [two-hex-digit-str]
            (.byteValue (java.lang.Short/valueOf two-hex-digit-str 16)))))
;; (1 35 69 103 -119 -85 -51 -17 0 127 -128 -1)

(def bv1 (vector-of :byte 1 2 3 5))
bv1
(type bv1)
;; clojure.core.Vec

(defn list-to-clojure-core-vec-of-byte [byte-list]
  (apply vector-of :byte byte-list))

(def bv2 (list-to-clojure-core-vec-of-byte '(1 2 3 5)))
bv2
(type bv2)

(= bv1 bv2)

*data-readers*
;; {}

(def bv3
  (binding [*data-readers*
            (assoc *data-readers*
                   'my.ns/byte-vec user/list-to-clojure-core-vec-of-byte)]
    (read-string "#my.ns/byte-vec (2 3 5 8)")))

bv3
(type bv3)

)
