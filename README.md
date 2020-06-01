# fvm [![Clojars Project](https://img.shields.io/clojars/v/fvm-project/fvm.svg)](https://clojars.org/fvm-project/fvm)

**fvm** is a Clojure library for writing self-optimizing interpreters.

## How it works

fvm provides a function called `defnode` for defining instruction nodes with the following signature:

```clojure
(fvm/defnode <node-type> <opts> <handler-fn>)
```

- any node with the option `{::fvm/jit? true}` is considered a loop
- if such a node is executed frequently enough, it's next execution is put in "trace-mode"
- in trace-mode, all the sub-nodes executed are logged in a "trace"
- the trace is effectively an unrolled version of the loop with function calls inlined 
- once the node finishes executing, this trace is "compiled" into a Clojure function
- the compiled version is used on future executions of this node

## Examples

[ednlang](https://github.com/fvm-project/ednlang) is a simple stack-based concatenative language implemented using fvm.

ednlang is meant to showcase all the features of fvm, and is also used to test and profile fvm.

Work is ongoing to implement a lisp on top of fvm.

## Status

This is **EXPERIMENTAL** software. Here be dragons.

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
