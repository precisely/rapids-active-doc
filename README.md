# rapids-active-doc
[![rapids tests](https://github.com/precisely/rapids-active-doc/actions/workflows/tests.yml/badge.svg)](https://github.com/precisely/rapids-active-doc/actions/workflows/tests.yml)

Share and coordinate data amongst runs. Active documents store data, verify that data using a schema and allow for actions to be run when certain conditions are met.   

## Installation

1. Add the following to your project dependencies:
   ```clojure
   [precisely/rapids-active-doc "0.0.1"]
   ```
2. Ensure you have something like the following in your project.clj file:
   ```clojure
   :repositories {"precisely" {:url        "s3p://precisely-maven-repo/"
                              :username   #=(eval (System/getenv "MAVEN_REPO_AWS_ACCESS_KEY_ID"))
                              :passphrase #=(eval (System/getenv "MAVEN_REPO_AWS_ACCESS_KEY_SECRET"))}}
   ```
   Where your environment has the access key and secret set appropriately (ask Aneil or Constantine)

## Usage
Active doc stores hierarchical data, accessible by `set-data!` and `get-data`. The `add-actions` and `remove-actions` methods allow detecting changes to the data and acting on them. The `monitor-doc` macro uses these methods to detect and handle changes made by other runs within a body of code.

Note that since the active doc is in fact a rapids Run, the `set-index!` method may be used with it.

### Example
```clojure
(ns myns 
  (:require [rapids.active-doc :as adoc]))
  
(deflow main []
  (let [doc (adoc/create!)]
    (set-data! doc [:foo :bar] "initial") ; (get-data doc) => {:foo {:bar "initial"}}
    (monitor-doc [doc {:when (fn [changes _] (if (get-in changes [:foo :bar]) true))
                       :handle (flow [i] (handle-the-change))}]
      ;; code here can be interrupted by a change to the document
     )))
```
### create!
Creates an active document - takes optional keyword arguments.
```clojure
(create! :data {:foo 1} :schema [:map [:foo :int]] 
         :actions {:foo-greater-than-10 (fn [changes _] 
                                          (if (and (contains? changes :foo) (> 10 (:foo changes)))
                                            (send-alert!)))
         :index {:type :foo-document})
; data - an optional map representing the data
; schema - an optional malli schema
; actions - an optional map of keywords to closures
; index - for indexing the document"
```

### get-data
Gets data from the active-doc.
```clojure
(get-data adoc :foo :bar) ; Equivalent to (get-in (get-data adoc) [:foo :bar])
```

### set-data!
Sets data in the active doc.
```clojure
(set-data! adoc [:foo :bar] 1 :baz 3) ; data is now {:foo {:bar 1} :baz 3}
```

### add-actions!
Adds one or more functions to the active doc. These functions will be called whenever a change is made (using `set-data!`).

```clojure
(add-actions! adoc :foo-change (fn [changes original] (if (contains? changes :foo) (do-something))))
```

### remove-actions!
Removes one or more actions added with `add-actions!`:
```clojure
(remove-actions! adoc :foo-changes)
```

### monitor-doc
Monitors an active-doc for one or more changes and trigger a corresponding interruption. Code inside the body of this macro may be interrupted when the active doc is changed by other runs or by the code in the body.

```clojure
  (monitor-doc [adoc {:when (fn [changes original] 
                               (if (:foo changes) 
                                 :value-passed-to-interruption-data
                      :interrupt (flow [i] (:data i))}]
    (do-things))
```

## License

Copyright © 2022 Precise.ly, Inc

All rights reserved
