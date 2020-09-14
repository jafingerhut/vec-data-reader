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

This repository was created to record experiments and thinking I did
while trying to answer this question:
https://ask.clojure.org/index.php/9567/possible-create-tagged-literal-reader-for-clojure-core-vec


## Usage

Below is a sample REPL session that was started from the root
directory of this repository using the Clojure CLI tool, with the
command named `clojure`.

```clojure
user=> (require '[vec-data-reader.vdr :as vdr])
nil

user=> (def bv0 (vdr/hex-string-to-clojure-core-vec-of-byte "0123456789abcdef007f80ff"))
#'user/bv0

user=> (type bv0)
clojure.core.Vec

user=> (def bv1 (read-string "#my.ns/byte-vec \"0123456789abcdef007f80ff\""))
#'user/bv1

user=> (type bv1)
clojure.core.Vec

user=> (def bv2 #my.ns/byte-vec "0123456789abcdef007f80ff")
#'user/bv2

user=> (type bv2)
clojure.lang.PersistentVector

user=> (def bv3 '#my.ns/byte-vec "0123456789abcdef007f80ff")
#'user/bv3

user=> (type bv3)
clojure.core.Vec

user=> bv0
[1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]

user=> bv1
[1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]

user=> bv2
[1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]

user=> bv3
[1 35 69 103 -119 -85 -51 -17 0 127 -128 -1]

user=> (type (eval bv0))
clojure.lang.PersistentVector

user=> (type (eval bv1))
clojure.lang.PersistentVector

user=> (type (eval bv2))
clojure.lang.PersistentVector

user=> (type (eval bv3))
clojure.lang.PersistentVector

user=> (class #my.ns/byte-vec "0123456789abcdef007f80ff")
clojure.lang.PersistentVector

;; See [Note 1] below for some details on why the exception below
;; occurs, and why there is an object with 'reify' in its name
;; involved.

;; Note that this same error occurs regardless of whether one defines
;; the print-dup method for class clojure.core.Vec, or not.

user=> (class '#my.ns/byte-vec "0123456789abcdef007f80ff")
Syntax error compiling fn* at (REPL:1:1).
Can't embed object in code, maybe print-dup not defined: clojure.core$reify__8311@625abb97

user=> (pst)
Note: The following stack trace applies to the reader or compiler, your code was not executed.
CompilerException Syntax error compiling fn* at (1:1). #:clojure.error{:phase :compile-syntax-check, :line 1, :column 1, :source "NO_SOURCE_PATH", :symbol fn*}
	clojure.lang.Compiler.analyzeSeq (Compiler.java:7115)
	clojure.lang.Compiler.analyze (Compiler.java:6789)
	clojure.lang.Compiler.eval (Compiler.java:7174)
	clojure.lang.Compiler.eval (Compiler.java:7132)
	clojure.core/eval (core.clj:3214)
	clojure.core/eval (core.clj:3210)
	clojure.main/repl/read-eval-print--9086/fn--9089 (main.clj:437)
	clojure.main/repl/read-eval-print--9086 (main.clj:437)
	clojure.main/repl/fn--9095 (main.clj:458)
	clojure.main/repl (main.clj:458)
	clojure.main/repl-opt (main.clj:522)
	clojure.main/main (main.clj:667)
Caused by:
RuntimeException Can't embed object in code, maybe print-dup not defined: clojure.core$reify__8311@4795ded0
	clojure.lang.Util.runtimeException (Util.java:221)
	clojure.lang.Compiler$ObjExpr.emitValue (Compiler.java:4893)
	clojure.lang.Compiler$ObjExpr.emitValue (Compiler.java:4808)
	clojure.lang.Compiler$ObjExpr.emitConstants (Compiler.java:4934)
	clojure.lang.Compiler$ObjExpr.compile (Compiler.java:4612)
	clojure.lang.Compiler$FnExpr.parse (Compiler.java:4106)
	clojure.lang.Compiler.analyzeSeq (Compiler.java:7105)
	clojure.lang.Compiler.analyze (Compiler.java:6789)
nil

;; Same-looking exception message if you replace 'class' with 'type' or 'inc'

user=> (defn doit [x] (print (class x)) x)

user=> 
```

[Note 1] Thanks to Kevin Downey (aka hiredman on Clojurians Slack) for
details on what is happening here.

When the Clojure compiler compiles Clojure to JVM byte code, it embeds
code for constructing objects that represent those Clojure values that
appear as literals in the Clojure source.

One place in the Clojure compiler where this is done is in method
`emitValue` of class `Compiler$ObjExpr` in source file `Compiler.java`
in the Clojure implementation.  That is the method where a couple of
the lines in the stack trace occur:

```
	clojure.lang.Compiler$ObjExpr.emitValue (Compiler.java:4893)
	clojure.lang.Compiler$ObjExpr.emitValue (Compiler.java:4808)
```

(These line numbers come from using version 1.10.1 of Clojure, so
those line numbers are relevant for that version of the Clojure source
code.)

The second of those lines is lower on the call stack, thus that call
occurs first in time during compiler execution.

It occurs when a value satisfies the condition `(value instanceof
IType)`, where `clojure.lang.IType` is a "marker interface" for all
objects created by Clojure's `deftype` macro.

Clojure's primitive vectors have type `clojure.core.Vec`, and that
class is created using `deftype` in the source file `gvec.clj`.

In the `emitValue` code handling objects created via `deftype`, the
behavior is basically to iterate through all fields of the JVM object,
and emit the value of each of its fields.

One of the fields of `clojure.core.Vec` is `am`, for "array manager",
and its value is the return value from a `reify` call in macro `mk-am`
in file `gvec.clj`.  That is where the object with "reify" in its name
comes from.

So the time order of compiler events includes: do `emitValue` on an
instance of `clojure.core.Vec`, which involves iterating through each
of its fields and calling `emitValue` on them.  When it gets to the
`am` field, which has a value returned by `reify`, `emitValue` tries
all of its cases, finally reaching the default `else` case at the end
of a long `if-then-else-if` daisy chain, which tries to call
`RT.printString(value)` on that object returned by `reify`.  That call
to `RT.printString(value)` throws an exception, which is caught in
`emitValue` and results in the message "Can't embed object in code,
maybe print-dup not defined: " followed by the object with "reify" in
its name.

Because there is an explicit case for objects implementing the `IType`
interface in `emitValue`, that is higher priority than the one that
calls `RT.printString`, it seems that the only way to prevent objects
with class `clojure.core.Vec` from attempting to call `emitValue` on
all of its fields would be to change this `emitValue` method in the
compiler.  That is, defining a `print-dup` method for objects with
class `clojure.core.Vec` will not avoid the current `emitValue`
behavior.

Defining a `print-dup` method for objects with classes of objects
returned by `reify` _might_ help, but only if the value printed, then
read and evaluated, actually returned a usable object, similar to one
returned from the original `reify` call, and that seems potentially
tricky to do.

Here is a "skeleton" of the `emitValue` method (of class
`Compiler$ObjExpr`) and which conditions it checks on the `value`
passed to it:

```java
		if(value == null)
		else if(value instanceof String)
		else if(value instanceof Boolean)
		else if(value instanceof Integer)
		else if(value instanceof Long)
		else if(value instanceof Double)
		else if(value instanceof Character)
		else if(value instanceof Class)
		else if(value instanceof Symbol)
		else if(value instanceof Keyword)
		else if(value instanceof Var)
		else if(value instanceof IType)
		else if(value instanceof IRecord)
		else if(value instanceof IPersistentMap)
		else if(value instanceof IPersistentVector)
		else if(value instanceof PersistentHashSet)
		else if(value instanceof ISeq || value instanceof IPersistentList)
		else if(value instanceof Pattern)
		else
```

The last `else` branch is the only one that contains a call to
`RT.printString`.

It seems that if one wanted to change the Clojure compiler to allow
objects created by `deftype` to have more control over how their
literal values were emitted in JVM byte code, one way would be to, in
the `(value instanceof IType)` branch, check if the class had an
implemention of `print-dup`, and if it did, use that.  Only if it did
not, then fall back to the current behavior for such objects.

Note: I have not _tried_ that approach yet, and there could easily be
problems with it that I have not thought of.

Thinking about it more, there are definitions of the multi-method
`print-dup` in Clojure for all of the classes and interfaces listed as
the output of the last expression below.

Notes: When declaring a multi-function in Clojure using `defmulti`
like `print-dup`, the Var `print-dup` has a value that is of class
`clojure.lang.MultiFn`.  When one later declares methods for that
multi-function using `defmethod`, a key/value pair is added to a
private field named `methodTable` of that object, which can be
retrieved using Clojure's `methods` function.  In that map, the key is
the multi-method's dispatch value, which is the class of the first
argument for `print-dup`, and the value is the function that is the
body of the `defmethod` call.

```clojure
$ clojure
Clojure 1.10.1

user=> (class print-dup)
clojure.lang.MultiFn

user=> (->> (methods print-dup)
            keys
            (map #(if (nil? %) "nil" (str %)))
            sort
            pprint)

("class clojure.lang.BigInt"
 "class clojure.lang.Keyword"
 "class clojure.lang.LazilyPersistentVector"
 "class clojure.lang.Namespace"
 "class clojure.lang.PersistentHashMap"
 "class clojure.lang.PersistentHashSet"
 "class clojure.lang.PersistentVector"
 "class clojure.lang.Ratio"
 "class clojure.lang.Symbol"
 "class clojure.lang.Var"
 "class java.lang.Boolean"
 "class java.lang.Character"
 "class java.lang.Class"
 "class java.lang.Double"
 "class java.lang.Long"
 "class java.lang.Number"
 "class java.lang.String"
 "class java.math.BigDecimal"
 "class java.sql.Timestamp"
 "class java.util.Calendar"
 "class java.util.Date"
 "class java.util.UUID"
 "class java.util.regex.Pattern"
 "interface clojure.lang.Fn"
 "interface clojure.lang.IPersistentCollection"
 "interface clojure.lang.IPersistentList"
 "interface clojure.lang.IPersistentMap"
 "interface clojure.lang.IRecord"
 "interface clojure.lang.ISeq"
 "interface java.util.Collection"
 "interface java.util.Map"
 "nil")
nil

```

Note the class `clojure.lang.PersistentVector` and the interface
`clojure.lang.IPersistentCollection` have `print-dup` methods defined
for them.

```clojure
(defn emitValue-branch-used [value]
  (cond
    (nil? value) "null"
    (instance? String value) "String"
    (instance? Boolean value) "Boolean"
    (instance? Integer value) "Integer"
    (instance? Long value) "Long"
    (instance? Double value) "Double"
    (instance? Character value) "Character"
    (instance? Class value) "Class"
    (instance? clojure.lang.Symbol value) "clojure.lang.Symbol"
    (instance? clojure.lang.Keyword value) "clojure.lang.Keyword"
    (instance? clojure.lang.Var value) "clojure.lang.Var"
    (instance? clojure.lang.IType value) "clojure.lang.IType (interface that is implemented by all classes created via deftype)"
    (instance? clojure.lang.IRecord value) "clojure.lang.IRecord (interface that is implemented by all classes created via defrecord)"
    (instance? clojure.lang.IPersistentMap value) "clojure.lang.IPersistentMap"
    (instance? clojure.lang.IPersistentVector value) "clojure.lang.IPersistentVector"
    (instance? clojure.lang.PersistentHashSet value) "clojure.lang.PersistentHashSet"
    (instance? clojure.lang.ISeq value) "clojure.lang.ISeq"
    (instance? clojure.lang.IPersistentList value) "clojure.lang.IPersistentList"
    (instance? java.util.regex.Pattern value) "java.util.regex.Pattern"
    :else "other"))

user=> (def inst1 #inst "2020-09-14T01:00:00")
#'user/inst1

user=> inst1
#inst "2020-09-14T01:00:00.000-00:00"

user=> (emitValue-branch-used inst1)
"other"

user=> (emitValue-branch-used [1 2 3])
"clojure.lang.IPersistentVector"

user=> (emitValue-branch-used (vector-of :byte 1 2 3))
"clojure.lang.IType (interface that is implemented by all classes created via deftype)"
```

Given the existing Clojure implementation in Java as of Clojure
1.10.1, the following are some options that occur to me for creating a
literal representation of Clojure persistent vectors containing
primitive values.


## Analysis of current behavior

The root cause of the "Can't embed object in code, maybe `print-dup`
not defined" with an object that has "reify" and a bunch of hex digits
in its printed representation, is the following combination of
factors:

(1) Clojure primitive vectors are defined with `deftype`.

(2) For all types defined via `deftype`, there is an `emitValue` Java
    method inside of Clojure's `Compiler.java` source file that has
    many cases for deciding how to embed a literal value in JVM byte
    code.  You can search that file for the first occurrence of
    "IType", which is a Java interface that Clojure `deftype`-created
    types all implement, in order to later recognize that they were
    objects of a class created via deftype.  When such an object is a
    literal inside of Clojure code, `emitValue` attempts to create JVM
    byte code that can construct the original value when that JVM byte
    code is later executed, and for `deftype`-created objects, it
    always tries to iterate through all fields of the object, and emit
    code for the field and its value.

(3) Clojure primitive vectors have a field `am`, short for "array
    manager", that is an object created by calling Clojure's `reify`
    function.  This object is used to implement several Java methods
    on "leaves" of the tree used to represent Clojure primitive
    vectors, one such object for each different primitive type.  The
    JVM byte code for dealing with arrays of each primitive type is
    different.  Rich Hickey in the `gvec.clj` code was probably going
    for run-time efficiency here by not detecting the primitive type
    at run time and doing a multi-way branch on every operation, but
    instead having an object that already had baked into it code for
    dealing with that vector's primitive type.

(4) `emitValue`, when called with an object that is the return of a
    `reify` call, tries to call `RT.printString` on it, which would
    work if a `print-dup` method were defined to handle such objects.
    However, implementing a `print-dup` that produced readable
    representations of all possible objects returned by `reify` would
    be very tricky, since such objects can have arbitrary references
    to other JVM objects with internal state, or can have internal
    state themselves.


## Possible approaches to creating a literal for primitive vectors that can be embedded in compiled Clojure code

What could be done about this?

There are probably many alternatives I haven't thought of, but here
are a few potential approaches, most of which would require changing
Clojure's implementation in some way.

### Approach #1a

Change Clojure's primitive vector implementation so that all of its
field values were immutable values with printable representations,
i.e. no objects returned from `reify`, nor any function references.
Since primitive vectors are trees with O(log_32 n) depth, the
representation created via `emitValue` would reflect that tree
structure, but it seems like it could be made to work correctly.  This
would likely lead to some lower run-time performance of operations on
primitive vectors, since there would need to be a run-time multiway
branch, e.g. `case`, to handle the different primitive types in leaf
nodes.

### Approach #1b

Create a new implementation of Clojure primitive vectors that uses
`deftype`, but has the changes suggested in Approach #1a above.  No
changes to Clojure's implementation would be required, since it would
be a third party implementation that can make its own implementation
choices.

### Approach #2

Change the `emitValue` method in `Compiler.java` so that for
`deftype`-created objects, it somehow checked whether there was a
`print-dup` method for that object's class first, and used it if it
was available, falling back to the current approach if there was not.

That would be somewhat tricky in this case, because Clojure primitive
vectors implement the `clojure.lang.IPersistentCollection` interface,
which already has a `print-dup` method that will not work for
primitive vectors.  One possibility is not to simply call `print-dup`
and see what happens, but to check whether the `print-dup` multimethod
has an implementation for _exactly_ the class of the object one is
trying to do `emitValue` on, e.g. `clojure.core.Vec` for primitive
vectors.  Such an exact class check for multimethod implementations
seems against the philosophy of multimethods in Clojure, and seems a
bit hackish.

Another cleaner variation on this idea would be to define a new
`emittable` interface in Clojure's implementation, and if a
`deftype`-created class implemented it, then `emitValue` would use the
`emit` method of that interface on objects that implemented it.

### Approach #3

Create a separate Clojure primitive vector implementation that does
not use `deftype`, nor `defrecord`, and falls into the last `else`
case of the long if-then-else daisy chain of Clojure's `emitValue`.
This seems difficult, or maybe impossible, to me, without changing the
`emitValue` method, because it currently has a case for
`clojure.lang.IPersistentVector` before the last `else`, and it would
be very strange to try creating a Clojure primitive vector
implementation that did not implement that interface.


### Summary of approaches

Of the ones I have thought about, Approach #1b, or the last variant of
approach #2, seem possibly workable.  Approach #1b requires no changes
to Clojure's implementation.  Approach #2 definitely does.  Approach
#3 probably isn't really a viable alternative, for reasons stated
above.


## License

Copyright © 2020 Andy Fingerhut

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 1.0 which is available at
https://www.eclipse.org/org/documents/epl-v10.html



## Scratch experiments

```clojure

(require '[clj-java-decompiler.core :refer [decompile disassemble]])

;; What do calls to Clojure multi-methods look like in JVM byte code?

(defmulti andymultifn (fn [x arg2] (class x)))

(defmethod andymultifn clojure.lang.IPersistentCollection [x ^java.io.Writer w]
  (.write w "andymultifn clojure.lang.IPersistentCollection"))

(defmethod andymultifn clojure.lang.PersistentVector [x ^java.io.Writer w]
  (.write w "andymultifn clojure.lang.PersistentVector"))

(defmethod andymultifn clojure.core.Vec [x ^java.io.Writer w]
  (.write w "andymultifn clojure.core.Vec"))

(andymultifn [1 2 3] *out*)
;; andymultifn clojure.lang.PersistentVector, as expected
(andymultifn {1 2 3 4} *out*)
;; andymultifn clojure.lang.IPersistentCollection, as expected

(andymultifn (vector-of :byte 1 2 3) *out*)
;; If you do not define a method for clojure.core.Vec, then output is:
;; andymultifn clojure.lang.IPersistentCollection
;; Note that the output does _not_ contain
;; clojure.lang.PersistentVector, because clojure.core.Vec is not a
;; subclass of clojure.lang.PersistentVector.

;; If you do define a method for clojure.core.Vec, then output is:
;; andymultifn clojure.core.Vec

(defn f1 [x]
  (andymultifn x))

;; These outputs are not very interesting, just the loading of the Var's value
(disassemble f1)
(decompile f1)
(decompile andymultifn)

;; These are more interesting
(disassemble
  (defn f1 [x]
    (andymultifn x)))
(decompile
  (defn f1 [x]
    (andymultifn x)))
(decompile
(defmethod andymultifn clojure.core.Vec [x ^java.io.Writer w]
  (.write w "andymultifn clojure.core.Vec"))
)
(decompile
(defmulti andymultifn (fn [x arg2] (class x)))
)

(def iv1 (vector-of :int 1 2 3))
(def lv1 (vector-of :long 1 2 3))
(def fv1 (vector-of :float 1 2 3))
(def dv1 (vector-of :double 1 2 3))
(def bv1 (vector-of :byte 1 2 3))
(def sv1 (vector-of :short 1 2 3))
(def cv1 (vector-of :char \1 \2 \3))
(def boolv1 (vector-of :boolean false true))
(map #(class (% 0)) [iv1 lv1 fv1 dv1 bv1 sv1 cv1 boolv1])
(pprint (map #(.am %) [iv1 lv1 fv1 dv1 bv1 sv1 cv1 boolv1]))
(.am iv1)

(def gvec-am-to-element-type-keyword
  (into {}
        (for [[k v] @#'clojure.core/ams]
	  [v k])))

(defn gvec-elem-type [gvec]
  (gvec-am-to-element-type-keyword (.am gvec)))

(map #(count %) [iv1 lv1 fv1 dv1 bv1 sv1 cv1 boolv1])
(map #(count (empty %)) [iv1 lv1 fv1 dv1 bv1 sv1 cv1 boolv1])

(map gvec-elem-type [iv1 lv1 fv1 dv1 bv1 sv1 cv1 boolv1])
(map #(gvec-elem-type (empty %)) [iv1 lv1 fv1 dv1 bv1 sv1 cv1 boolv1])

(defmethod print-dup clojure.core.Vec [v ^java.io.Writer w]
  (.write w "#=(vector-of ")
  (print-dup (gvec-elem-type v) w)
  (.write w " ")
  ;; using clojure.core/pr-on causes type Byte elements to print-dup as #=(java.lang.Byte. "1") which is unnecessarily verbose
  ;; using print-method leads to just elements of 1 2 3 like I want
  ;;(#'clojure.core/print-sequential "" #'clojure.core/pr-on " " "" v w)
  (#'clojure.core/print-sequential "" print-method " " "" v w)
  (.write w ")"))

(print bv1)
(print iv1)
(binding [*print-dup* true] (print bv1))
(binding [*print-dup* true] (print iv1))
(binding [*print-dup* true] (print (empty iv1)))

(def bv2 (read-string (with-out-str (binding [*print-dup* true] (print bv1)))))
bv2
(class bv2)
(class (bv2 0))
(= bv1 bv2)
(= (class bv1) (class bv2))
(= (class (bv1 0)) (class (bv2 0)))

(def iv2 (read-string (with-out-str (binding [*print-dup* true] (print iv1)))))
iv2
(class iv2)
(class (iv2 0))
(= bv1 iv2)
(= (class iv1) (class iv2))
(= (class (iv1 0)) (class (iv2 0)))

(let [] #'print-sequential)
(let [] #'clojure.core/print-sequential)
(#'clojure.core/print-sequential "<" #'clojure.core/pr-on ", " ">" (vector-of :byte 1 2 3) *out*)
(#'clojure.core/print-sequential "" #'clojure.core/pr-on " " "" (vector-of :byte 1 2 3) *out*)


andymultifn
(require '[clojure.reflect :as refl])
(class andymultifn)
;; clojure.lang.MultiFn
(def d1 (refl/type-reflect clojure.lang.MultiFn))
(count (:members d1))
;; 49
(def d1flds (filter #(not (contains? % :return-type)) (:members d1)))
(count d1flds)
;; 14
(pprint d1flds)
(:name (nth (seq d1flds) 0))
(class (:name (nth (seq d1flds) 0)))
(pprint (seq (.getDeclaredFields clojure.lang.MultiFn)))
(map #(.getName %) (seq (.getDeclaredFields clojure.lang.MultiFn)))

(defn get-obj-field-by-field [obj ^java.lang.reflect.Field field]
  (. field setAccessible true)
  (.get field obj))

(defn get-obj-field-by-name [obj field-name-str]
  (let [kls (class obj)
        fld (first (filter #(= field-name-str (.getName %))
                           (.getDeclaredFields kls)))]
    (get-obj-field-by-field obj fld)))

(def mfn-mt2 (get-obj-field-by-name andymultifn "methodTable"))
(pprint (->> mfn-mt2 keys (map str) sort))

(andymultifn #{1 2 3} *out*)
(andymultifn {1 2 3 4} *out*)
(andymultifn [1 2 3] *out*)
(andymultifn (vector-of :byte 1 2 3) *out*)

(pprint (sort (map str (keys (methods print-dup)))))

(defn str-nil [x]
  (if (nil? x)
    "nil"
    (str x)))

(defn pprint-methodmap-keys [multimethod xform]
  (->> (methods multimethod)
       keys
       (map xform)
       sort
       pprint))

(->> (methods print-dup)
     keys
     (map #(if (nil? %) "nil" (str %)))
     sort
     pprint)

(pprint-methodmap-keys print-dup str-nil)
(pprint-methodmap-keys print-method str-nil)

(defmulti print-method (fn [x writer]
                         (let [t (get (meta x) :type)]
                           (if (keyword? t) t (class x)))))

(defn print-method-dispatch-fn [x]
  (let [t (get (meta x) :type)]
    (if (keyword? t) t (class x))))

(print-method-dispatch-fn [1 2 3])
(print-method-dispatch-fn (vector-of :byte 1 2 3))
(= (get-method print-method [1 2 3])
   (get-method print-method (vector-of :byte 1 2 3)))


(->> (methods print-dup)
     keys
     (map #(with-out-str (print-dup % *out*)))
     sort
     pprint)

(:name (nth (seq d1flds) 1))

```
