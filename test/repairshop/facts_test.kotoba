(ns repairshop.facts-test
  (:require [clojure.test :refer [deftest is]]
            [repairshop.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest zaf-has-a-spec-basis
  (let [entry (facts/spec-basis "ZAF")]
    (is (some? entry))
    (is (string? (:provenance entry)))
    (is (= "South Africa" (:name entry)))
    (is (= "National Consumer Commission (NCC)" (:owner-authority entry)))
    (is (re-find #"Consumer Protection Act 68 of 2008" (:legal-basis entry)))
    (is (= 4 (count (:required-evidence entry))))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))
