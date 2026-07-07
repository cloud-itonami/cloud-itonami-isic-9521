(ns repairshop.registry-test
  (:require [clojure.test :refer [deftest is]]
            [repairshop.registry :as r]))

;; ----------------------------- compute-parts-cost / parts-cost-matches-claim? -----------------------------

(deftest compute-parts-cost-is-quantity-times-unit-price
  (is (= 30.0 (r/compute-parts-cost {:parts-quantity 2 :parts-unit-price 15})))
  (is (= 40.0 (r/compute-parts-cost {:parts-quantity 1 :parts-unit-price 40}))))

(deftest parts-cost-matches-claim-when-equal
  (is (r/parts-cost-matches-claim? {:parts-quantity 2 :parts-unit-price 15 :claimed-parts-cost 30.0}))
  (is (r/parts-cost-matches-claim? {:parts-quantity 2 :parts-unit-price 15 :claimed-parts-cost 30})))

(deftest parts-cost-does-not-match-claim-when-different
  (is (not (r/parts-cost-matches-claim? {:parts-quantity 2 :parts-unit-price 15 :claimed-parts-cost 50.0})))
  (is (not (r/parts-cost-matches-claim? {:parts-quantity 2 :parts-unit-price 15 :claimed-parts-cost 29.99}))))

;; ----------------------------- register-repair-completion -----------------------------

(deftest repair-completion-is-a-draft-not-a-real-completion
  (let [result (r/register-repair-completion "ticket-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest repair-completion-assigns-completion-number
  (let [result (r/register-repair-completion "ticket-1" "JPN" 7)]
    (is (= (get result "completion_number") "JPN-RPR-000007"))
    (is (= (get-in result ["record" "ticket_id"]) "ticket-1"))
    (is (= (get-in result ["record" "kind"]) "repair-completion-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest repair-completion-validation-rules
  (is (thrown? Exception (r/register-repair-completion "" "JPN" 0)))
  (is (thrown? Exception (r/register-repair-completion "ticket-1" "" 0)))
  (is (thrown? Exception (r/register-repair-completion "ticket-1" "JPN" -1))))

(deftest completion-history-is-append-only
  (let [c1 (r/register-repair-completion "ticket-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-repair-completion "ticket-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-RPR-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-RPR-000001" (get-in hist2 [1 "record_id"])))))

;; ----------------------------- register-device-return -----------------------------

(deftest device-return-is-a-draft-not-a-real-return
  (let [result (r/register-device-return "ticket-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest device-return-assigns-return-number
  (let [result (r/register-device-return "ticket-1" "JPN" 7)]
    (is (= (get result "return_number") "JPN-RTN-000007"))
    (is (= (get-in result ["record" "ticket_id"]) "ticket-1"))
    (is (= (get-in result ["record" "kind"]) "device-return-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest device-return-validation-rules
  (is (thrown? Exception (r/register-device-return "" "JPN" 0)))
  (is (thrown? Exception (r/register-device-return "ticket-1" "" 0)))
  (is (thrown? Exception (r/register-device-return "ticket-1" "JPN" -1))))

(deftest return-history-is-append-only
  (let [d1 (r/register-device-return "ticket-1" "JPN" 0)
        hist (r/append [] d1)
        d2 (r/register-device-return "ticket-2" "JPN" 1)
        hist2 (r/append hist d2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-RTN-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-RTN-000001" (get-in hist2 [1 "record_id"])))))
