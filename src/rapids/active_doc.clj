(ns rapids.active-doc
  (:require [rapids :refer :all]
            [rapids.support.util :refer [reverse-interleave]]
            [malli.core :as s]))

(declare update-data non-suspending-fn? add-active-doc-interruption-action)

(deflow active-doc
  [data schema actions]
  {:pre [(map? data) (map? actions)]}
  (s/validate schema data)
  (loop [data    data
         actions {}]
    (let [[cmd inputs] (<*)
          [data actions] (cond
                           (= cmd :get) (do
                                          (println ">>>>>" {:data data :inputs inputs})
                                          (>* (get-in data (or inputs [])))
                                          [data actions])
                           (= cmd :set)
                           [(update-data data inputs schema actions) actions]
                           (= cmd :add-actions) [data (apply assoc actions inputs)]
                           (= cmd :remove-actions) [data (apply dissoc actions inputs)]
                           :otherwise (throw (ex-info "Invalid command to active-doc"
                                               {:type    :input-error
                                                :command cmd})))]

      (recur data actions))))

(defn get-data
  "Retrieves data from an active doc. Optional fields can be provided."
  ([adoc] (get-data adoc []))
  ([adoc fields]
   (-> (continue! adoc :input [:get (if (sequential? fields) fields [fields])])
     :output first)))

(defn set-data!
  "Updates data in the active doc.

  adoc - an active doc run
  field-values - a sequence of field / value pairs
                 Fields are keywords or vectors of keywords (for nested access)
  E.g.,
  (let [adoc (create :data {:a 1 {:c {:b 3}})]
    (update adoc :a \"foo\" [:c b] \"bar\")
    (get-data adoc))
    => {:a \"foo\" {:c {:b \"bar\"}}"
  [adoc & field-values]
  (continue! adoc :input [:set field-values])
  nil)

(defn create!
  "Creates a new active-doc. A run which holds data

  data - an optional map representing the data
  schema - an optional malli schema
  actions - an optional map of keywords to closures
  index - for indexing the document"
  [& {:keys [data index schema actions]
      :or   {data {}, schema :any, actions {}, index {}}}]
  (start! active-doc [data schema actions] :index index))

(defn add-actions!
  "Adds functions to be run when active-doc data changes.

   adoc - an active-doc run
   key-actions - a sequence of keyword closure pairs. The key is used to remove a closure.

  (add-notifiers adoc :my-flow) => nil"
  [adoc & key-actions]
  {:pre [(every? (fn [[k a]]
                   (and (keyword? k)
                     (non-suspending-fn? a)))
           (partition 2 key-actions))]}
  (continue! adoc :input [:add-actions key-actions])
  nil)

(defn remove-actions!
  "Removes actions. Returns nil."
  [adoc & keys]
  {:pre [(every? keyword? keys)]}
  (continue! adoc :input [:remove-actions keys])
  nil)

(defmacro monitor-doc
  "Monitors an active-doc for changes and triggers an interruption.

  doc - an active document
  detectors - [{:when test, :handle handler} ...]

  where test = (fn [changes original] ...) ; changes is a map of the changed keys, original is the original data
               if test returns truthy value, an interrupt is triggered and the result is passed as the interrupt :data
        handler = (fn [i] ...) a function which takes an interruption as argument
  Usage:
  (monitor-doc [adoc {:when (fn [changes original] (if (:foo changes) {:new (:foo changes) :old (:foo original)})))
                      :handle (flow [i] (do-something-with (:data i)))}]
    (do-things))"
  [[doc & detectors] & body]
  (let [[syms add-action-forms handlers]
        (reverse-interleave
          (apply concat (map-indexed
                          (fn [idx {when-fn :when, handle-fn :handle}]
                            (assert when-fn) (assert handle-fn)
                            (let [interrupt-symbol (keyword (str "monitor-doc-detector-" idx))
                                  test             (if (symbol? when-fn) `(quote ~when-fn) when-fn)
                                  handler          (if (symbol? handle-fn) `(quote ~handle-fn) handle-fn)]
                              [interrupt-symbol
                               `(add-active-doc-interruption-action ~'adoc ~interrupt-symbol ~test ~'run-id)
                               `(~'handle ~interrupt-symbol ~'i ~(if handler `(fcall ~handler ~'i) nil))]))
                          detectors))
          3)]
    `(let [~'adoc ~doc
           ~'run-id (current-run :id)]
       (attempt ~@add-action-forms
         ~@body
         ~@handlers
         (~'finally (remove-actions! ~'adoc ~@syms))))))

;; HELPERS
(defn- update-data
  "Given a map (data) and a sequence of assignments, returns a new
  non-destructively updated data structure.

  data - a map to be updated
  assignments - [[ks val]...]
     where ks is a keyword or a sequence of keywords representing nested field access

  (update-data
    {:a {:b 2} :d 3} ; the data
    [[:c 99] [[:a :b] 999]] ; the changes requested
    :any ; a permissive malli schema
    {:foo #'process-changes}) ; actions to be run
  this will call (process-changes {:a {:b 2}} :d 3} {:a {:b 999} :c 99})
  => {:a {:b 999} :c 99}"
  [data field-values schema actions]
  {:pre [(map? data) (sequential? field-values) (map? actions)]}
  (let [changes (reduce (fn [m [field val]] (assoc-in m (if (sequential? field) field [field]) val))
                  {}
                  (partition 2 field-values))
        result  (merge data changes)]
    (s/validate schema result)
    (doseq [action (vals actions)]
      (defer #(action changes data)))
    result))

(defn- non-suspending-fn? [o]
  (or (fn? o) (and (closure? o) (-> o :suspending not))))

(defn add-active-doc-interruption-action [adoc k test run-id]
  (add-actions! adoc k (fn [changes data]
                          (if-let [result (test changes data)]
                            (interrupt! run-id k :data result)))))
