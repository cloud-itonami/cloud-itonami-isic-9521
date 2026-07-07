(ns repairshop.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [repairshop.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka" (:customer (store/ticket s "ticket-1"))))
      (is (= "JPN" (:jurisdiction (store/ticket s "ticket-1"))))
      (is (= 2 (:parts-quantity (store/ticket s "ticket-1"))))
      (is (= 15 (:parts-unit-price (store/ticket s "ticket-1"))))
      (is (= 30.0 (:claimed-parts-cost (store/ticket s "ticket-1"))))
      (is (true? (:safety-test-passed? (store/ticket s "ticket-1"))))
      (is (false? (:safety-test-passed? (store/ticket s "ticket-4"))))
      (is (false? (:repair-completed? (store/ticket s "ticket-1"))))
      (is (false? (:device-returned? (store/ticket s "ticket-1"))))
      (is (= ["ticket-1" "ticket-2" "ticket-3" "ticket-4"]
             (mapv :id (store/all-tickets s))))
      (is (nil? (store/safety-screening-of s "ticket-1")))
      (is (nil? (store/assessment-of s "ticket-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/completion-history s)))
      (is (= [] (store/return-history s)))
      (is (zero? (store/next-completion-sequence s "JPN")))
      (is (zero? (store/next-return-sequence s "JPN")))
      (is (false? (store/ticket-already-completed? s "ticket-1")))
      (is (false? (store/ticket-already-returned? s "ticket-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :ticket/upsert
                                 :value {:id "ticket-1" :customer "Sakura Tanaka"}})
        (is (= "Sakura Tanaka" (:customer (store/ticket s "ticket-1"))))
        (is (= 2 (:parts-quantity (store/ticket s "ticket-1"))) "parts-quantity preserved"))
      (testing "assessment / safety-screening payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["ticket-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "ticket-1")))
        (store/commit-record! s {:effect :safety-screening/set :path ["ticket-1"]
                                 :payload {:ticket-id "ticket-1" :verdict :passed}})
        (is (= {:ticket-id "ticket-1" :verdict :passed} (store/safety-screening-of s "ticket-1"))))
      (testing "repair completion drafts a completion record and advances the completion sequence"
        (store/commit-record! s {:effect :ticket/mark-completed :path ["ticket-1"]})
        (is (= "JPN-RPR-000000" (get (first (store/completion-history s)) "record_id")))
        (is (= "repair-completion-draft" (get (first (store/completion-history s)) "kind")))
        (is (true? (:repair-completed? (store/ticket s "ticket-1"))))
        (is (= 1 (count (store/completion-history s))))
        (is (= 1 (store/next-completion-sequence s "JPN")))
        (is (true? (store/ticket-already-completed? s "ticket-1")))
        (is (false? (store/ticket-already-completed? s "ticket-2"))))
      (testing "device return drafts a return record and advances the return sequence"
        (store/commit-record! s {:effect :ticket/mark-returned :path ["ticket-1"]})
        (is (= "JPN-RTN-000000" (get (first (store/return-history s)) "record_id")))
        (is (= "device-return-draft" (get (first (store/return-history s)) "kind")))
        (is (true? (:device-returned? (store/ticket s "ticket-1"))))
        (is (= 1 (count (store/return-history s))))
        (is (= 1 (store/next-return-sequence s "JPN")))
        (is (true? (store/ticket-already-returned? s "ticket-1")))
        (is (false? (store/ticket-already-returned? s "ticket-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/ticket s "nope")))
    (is (= [] (store/all-tickets s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/completion-history s)))
    (is (= [] (store/return-history s)))
    (is (zero? (store/next-completion-sequence s "JPN")))
    (is (zero? (store/next-return-sequence s "JPN")))
    (store/with-tickets s {"x" {:id "x" :customer "c" :device "d" :device-type :laptop
                                :parts-quantity 1 :parts-unit-price 10 :claimed-parts-cost 10.0
                                :safety-test-passed? true :repair-completed? false :device-returned? false
                                :jurisdiction "JPN" :status :intake}})
    (is (= "c" (:customer (store/ticket s "x"))))))
