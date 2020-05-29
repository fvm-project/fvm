# fvm

**fvm** is an extensible stack-based self-optimizing VM.

## Example

fvm comes with an example interpreter for a simple language called **ednlang**.

Here's a ednlang program that calculates and prints `factorial(5)`:
```clojure
;; import the standard library
{:fvm.fvm/type :fvm.ednlang/requires
 :fvm.ednlang/value ["lib/std.edn"]}

;; define a new opcode for factorial
{:fvm.fvm/type :fvm.ednlang/defop
 :fvm.ednlang/name :test/fact
 :fvm.ednlang/value [{:fvm.fvm/type :fvm.ednlang/push
                      :fvm.ednlang/value 0}
                     {:fvm.fvm/type :fvm.ednlang/eq?
                      :fvm.ednlang/then [{:fvm.fvm/type :fvm.ednlang/pop}
                                         {:fvm.fvm/type :fvm.ednlang/pop}
                                         {:fvm.fvm/type :fvm.ednlang/push
                                          :fvm.ednlang/value 1}]
                      :fvm.ednlang/else [{:fvm.fvm/type :fvm.ednlang/pop}
                                         {:fvm.fvm/type :fvm.ednlang/dup}
                                         {:fvm.fvm/type :fvm.ednlang/dec}
                                         {:fvm.fvm/type :test/fact}
                                         {:fvm.fvm/type :fvm.ednlang/mul}]}]}

;; call it
{:fvm.fvm/type :fvm.ednlang/push
 :fvm.ednlang/value 5}
{:fvm.fvm/type :test/fact}

;; print the result
{:fvm.fvm/type :fvm.ednlang/println}
```

## Usage

### JVM

To run the example factorial program, do:
```
$ lein run test/fact.edn
```

### Native

#### Build from source

Make sure you have GraalVM installed and `$GRAALVM_HOME` pointing to it, then do:
```
$ ./compile
```

#### Run

Now you can do:
```
$ target/fvm test/fact.edn
```

## Tests

```
$ lein eftest
```

## Properties

### fvm

- A pure Clojure library for writing JITing VMs (like RPython and Truffle/GraalVM)
- Simple, functional interface
- Hot loops are traced and inlined at runtime

### ednlang

- Simple language implemented on top of fvm
- Custom ops (like `fact` above) are inlined and called at runtime
- No recursion limit - try running the factorial program for large values
- Code is data is code - anonymous ops can be stored and called
- Designed to be easily parsable and an easy compilation target
- Does not require a GC, being completely stack based

## Status

This is a research project with rough edges - here be dragons.

## License

Copyright © 2020 Divyansh Prakash

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
