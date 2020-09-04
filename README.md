# vec-data-reader

Just some code for testing the behavior of a Clojure data reader for a
tagged literal that returns a Clojure persistent vector of primitive
values, in this case bytes.  That is, it returns the same type as a
call like `(vector-of :byte 1 2 3)`.

There is some behavior that is not immediately obvious why it occurs,
where if you define such a data reader, and use it in your Clojure
code to contain a literal, and do not quote it, when such a vector is
evaluated by `eval`, it returns a vector with type
`clojure.lang.PersistentVector`, not the original type.


## Usage

Below is a sample REPL session that was started from the root
directory of this repository using the Clojure CLI tool, with the
command named `clojure`.

```clojure
user=> (require '[vec-data-reader.vdr :as vdr])
nil

user=> (def bv0 (vdr/hex-string-to-clojure-core-vec-of-byte "0123456789abcdef007f80ff"))
#'user/bv0

user=> (def bv1 (read-string "#my.ns/byte-vec \"0123456789abcdef007f80ff\""))
#'user/bv1

user=> (def bv2 #my.ns/byte-vec "0123456789abcdef007f80ff")
#'user/bv2

user=> (def bv3 '#my.ns/byte-vec "0123456789abcdef007f80ff")
#'user/bv3

user=> bv0
[1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]

user=> bv1
[1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]

user=> bv2
[1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]

user=> bv3
[1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]

user=> (type bv0)
clojure.core.Vec

user=> (type bv1)
clojure.core.Vec

user=> (type bv2)
clojure.lang.PersistentVector

user=> (type bv3)
clojure.core.Vec

user=> (type (eval bv0))
clojure.lang.PersistentVector

user=> (type (eval bv1))
clojure.lang.PersistentVector

user=> (type (eval bv2))
clojure.lang.PersistentVector

user=> (type (eval bv3))
clojure.lang.PersistentVector

user=> 
```


## License

Copyright © 2020 Andy Fingerhut

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 1.0 which is available at
https://www.eclipse.org/org/documents/epl-v10.html
