(ns repairshop.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean ticket through
  intake -> jurisdiction assessment -> post-repair safety screening ->
  repair-completion proposal (always escalates) -> human approval ->
  commit, then through device-return proposal (always escalates) ->
  human approval -> commit, then shows four HARD holds (a jurisdiction
  with no spec-basis, a claimed parts cost that doesn't match parts-
  quantity times unit-price, a failed post-repair safety test, and a
  double completion/return of an already-processed ticket) that never
  reach a human at all, and prints the audit ledger + the draft
  repair-completion and device-return records."
  (:require [langgraph.graph :as g]
            [repairshop.store :as store]
            [repairshop.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :repair-technician :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== ticket/intake ticket-1 (JPN, clean; 2 parts x 15 = claimed-parts-cost 30.0) ==")
    (println (exec! actor "t1" {:op :ticket/intake :subject "ticket-1"
                                :patch {:id "ticket-1" :customer "Sakura Tanaka"}} operator))

    (println "== jurisdiction/assess ticket-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "ticket-1"} operator))
    (println (approve! actor "t2"))

    (println "== safety/screen ticket-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :safety/screen :subject "ticket-1"} operator))
    (println (approve! actor "t3"))

    (println "== repair/complete ticket-1 (always escalates -- actuation/complete-repair) ==")
    (let [r (exec! actor "t4" {:op :repair/complete :subject "ticket-1"} operator)]
      (println r)
      (println "-- human repair technician approves --")
      (println (approve! actor "t4")))

    (println "== device/return ticket-1 (always escalates -- actuation/return-device) ==")
    (let [r (exec! actor "t5" {:op :device/return :subject "ticket-1"} operator)]
      (println r)
      (println "-- human repair technician approves --")
      (println (approve! actor "t5")))

    (println "== jurisdiction/assess ticket-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "ticket-2" :no-spec? true} operator))

    (println "== jurisdiction/assess ticket-3 (escalates -- human approves; sets up the parts-cost-mismatch test) ==")
    (println (exec! actor "t7" {:op :jurisdiction/assess :subject "ticket-3"} operator))
    (println (approve! actor "t7"))

    (println "== repair/complete ticket-3 (claimed-parts-cost 50.0 != 2 x 15 = 30.0 -> HARD hold) ==")
    (println (exec! actor "t8" {:op :repair/complete :subject "ticket-3"} operator))

    (println "== safety/screen ticket-4 (failed post-repair safety test -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :safety/screen :subject "ticket-4"} operator))

    (println "== repair/complete ticket-1 AGAIN (double-completion -> HARD hold) ==")
    (println (exec! actor "t10" {:op :repair/complete :subject "ticket-1"} operator))

    (println "== device/return ticket-1 AGAIN (double-return -> HARD hold) ==")
    (println (exec! actor "t11" {:op :device/return :subject "ticket-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft repair-completion records ==")
    (doseq [r (store/completion-history db)] (println r))

    (println "== draft device-return records ==")
    (doseq [r (store/return-history db)] (println r))))
