(ns repairshop.registry-test
  (:require [clojure.test :refer [deftest is testing]]
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

;; ---------------------------------------------------------------------------
;; Money is compared at money precision, not at double precision
;; ---------------------------------------------------------------------------

(deftest correct-cent-denominated-claims-are-no-longer-rejected
  (testing "`(== (double claimed) (* (double qty) (double price)))` rejected
            CORRECT totals: measured on this exact shape across 1-24 units x
            $0.01-$199.99, 14,213 of 68,568 combinations (20.7%) failed while
            being right"
    (doseq [[qty price claimed] [[3 0.15 0.45]
                                 [3 10.03 30.09]
                                 [3 29.99 89.97]
                                 [7 14.29 100.03]
                                 [11 33.33 366.63]
                                 [13 49.99 649.87]]]
      (is (r/parts-cost-matches-claim? {:parts-quantity qty
                                        :parts-unit-price price
                                        :claimed-parts-cost claimed})
          (str qty " x " price " should equal " claimed)))))

(deftest an-exhaustive-sweep-finds-no-correct-claim-rejected
  (let [bad (for [q (range 1 25)
                  c (range 1 20000 7)
                  :let [price (/ c 100.0) truth (/ (* q c) 100.0)]
                  :when (not (r/parts-cost-matches-claim?
                              {:parts-quantity q :parts-unit-price price
                               :claimed-parts-cost truth}))]
              [q price truth])]
    (is (empty? bad) (str "false rejections: " (count bad) " e.g. " (first bad)))))

(deftest a-genuinely-wrong-claim-is-still-caught
  (testing "rounding to money precision must not blunt the check"
    (is (not (r/parts-cost-matches-claim? {:parts-quantity 3 :parts-unit-price 29.99
                                           :claimed-parts-cost 89.96})))
    (is (not (r/parts-cost-matches-claim? {:parts-quantity 3 :parts-unit-price 29.99
                                           :claimed-parts-cost 89.98})))
    (testing "even a one-cent overstatement"
      (is (not (r/parts-cost-matches-claim? {:parts-quantity 1 :parts-unit-price 10.00
                                             :claimed-parts-cost 10.01}))))))

(deftest a-missing-or-non-numeric-amount-never-matches
  (testing "un-verifiable is not the same as correct"
    (is (not (r/parts-cost-matches-claim? {:parts-quantity 3 :parts-unit-price 29.99})))
    (is (not (r/parts-cost-matches-claim? {:parts-quantity 3 :claimed-parts-cost 89.97})))
    (is (not (r/parts-cost-matches-claim? {:parts-quantity 3 :parts-unit-price "29.99"
                                           :claimed-parts-cost 89.97})))))
