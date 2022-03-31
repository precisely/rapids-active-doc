(ns rapids.active-doc-test
  (:require [clojure.test :refer :all]
            [rapids.active-doc :refer :all]
            [rapids :refer :all]
            [rapids.language.test :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(deflow data-updating-flow [adoc]
  (set-data! adoc :foo (<*)))

(deflow main-flow [adoc]
  (monitor-doc [adoc {:when   (fn [changes original]
                                (if (contains? changes :foo)
                                  {:old original, :changes changes}))
                      :handle (fn [i] {:interrupt-data (:data i)})}]
    (set-index! :data-updating-flow (:id (start! data-updating-flow [adoc])))
    (<*)))

(deftest active-doc-test
  (with-test-env
    (testing "Active doc creation"
      (testing "without any arguments"
        (let [adoc (create!)]
          (is (run? adoc))))
      (testing "with arguments"
        (let [adoc (create!
                     :data {:a 1, :b {:c 2}}
                     :schema [:map [:a :int] [:b :map]]
                     :index {:foo 1, :bar {:baz 2}})]

          (testing "the index is set correctly"
            (is (= (:index adoc)
                  {:foo 1 :bar {:baz 2}})))

          (testing "the data is set correctly"
            (is (= (-> adoc :stack first :bindings :data)
                  {:a 1, :b {:c 2}})))

          (testing "the schema is set correctly"
            (is (= (-> adoc :stack first :bindings :schema)
                  [:map [:a :int] [:b :map]])))

          (testing "getting data"
            (is (= (get-data adoc :a) 1)))

          (testing "getting hierarchical data"
            (is (= (get-data adoc [:b :c]) 2)))

          (testing "getting all the data"
            (is (= (get-data adoc []) {:a 1, :b {:c 2}}))
            (is (= (get-data adoc) {:a 1, :b {:c 2}})))

          (testing "setting data"
            (is (= (set-data! adoc :a 3) nil))
            (is (= (get-data adoc :a) 3)))

          (testing "setting all the data"
            (is (= (set-data! adoc [] {:foo 1}) nil))
            (is (= (get-data adoc) {:foo 1})))

          (testing "setting hierarchical data"
            (set-data! adoc [:b :c] "hello")
            (is (= (get-data adoc [:b :c]))))

          (testing "sending an invalid command"
            (is (thrown? ExceptionInfo (continue! adoc :input [:foo])))))))))

(deftest monitor-doc-test
  (with-test-env
    (let [adoc     (create! :data {:foo :initial, :bar :xyzzy})
          main-run (start! main-flow [adoc])]
      (is (= :running (:state main-run)))
      (let [updating-flow-id (-> main-run :index :data-updating-flow)]
        (is (uuid? updating-flow-id))
        (is (= :running (-> updating-flow-id get-run :state)))

        (testing "updating the doc triggers an interruption of the main run"
          (continue! updating-flow-id :input :new-value)
          (is (= :complete (:state main-run)))
          (testing "demonstrating that the test result was passed to the interruption handler"
            (is (= (:result main-run)
                  {:interrupt-data
                   {:old     {:foo :initial, :bar :xyzzy},
                    :changes {:foo :new-value}}}))))))))
