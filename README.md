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

The second of those lines is lower on the call stack, thus that call
occurs first in time during compiler execution.

It occurs when a value satisfies the condition `(value instanceof
IType)`, where `clojure.lang.IType` is a 'marker interface" for all
objects created by Clojure's `deftype` macro.

Clojure's primitive vectors have type `clojure.core.Vec`, and that
class is created using `deftype` in the source file `gvec.clj`.

In the `emitValue` code handling objects created via `deftype`, the
behavior is basically to iterate through all fields of the JVM object,
and emit the value of each of its fields.

One of the fields of `clojure.core.Vec` is `am`, for 'array manager',
and its value is the return value from a `reify` call in macro `mk-am`
in file `gvec.clj`.  That is where the object with 'reify' in its name
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
maybe print-dup not defined: " followed by the object with 'reify' in
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

Here is a 'skeleton' of the `emitValue` method (of class
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


## License

Copyright © 2020 Andy Fingerhut

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 1.0 which is available at
https://www.eclipse.org/org/documents/epl-v10.html
