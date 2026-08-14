(ns repairshop.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2: this repo previously had NO demo
  page and no generator at all. This namespace drives the REAL actor
  stack (`repairshop.operation` -> `repairshop.governor` ->
  `repairshop.store`, through langgraph's `g/run*`, exactly as
  `repairshop.sim` does) and renders whatever came back. Every ticket
  id, customer name, device, jurisdiction, parts figure, record id,
  rule keyword and hold detail on the page is read out of the live run
  -- there is no hand-written HTML row anywhere in this file.

  The scenario extends this repo's own `repairshop.sim` driver
  (`clojure -M:dev:run`, run and read BEFORE this file was written) so
  that ALL SIX of the governor's HARD rules fire, not the five the sim
  reaches: `repair/complete ticket-4` is attempted with no
  jurisdiction assessment on file, which is the only way to isolate
  `:evidence-incomplete`. A seventh outcome -- a human REJECTING a
  clean escalation -- is included so the page shows that approval is a
  real decision with two branches, not a rubber stamp.

  Determinism: no clock, no randomness, no network, no timestamp in
  the page body. Two runs against the same seed are byte-identical
  (verify by rendering into two scratch dirs and diffing).

  Honesty invariants, enforced in `-main` rather than left to
  convention (the `cloud-itonami-isic-2513` precedent):
    - a console showing ZERO `:governor-hold` facts is not evidence of
      a governor, so `-main` throws rather than write one;
    - `expected-hard-rules` below is this renderer's own claim about
      which rules the scenario exercises. If a rule fails to fire,
      `-main` throws instead of quietly emitting a page whose coverage
      table is thinner than advertised. A check that cannot report
      what it failed to measure is the failure mode ADR-2608136000
      names; here the unmeasured case is a build error, not a pass.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [repairshop.facts :as facts]
            [repairshop.governor :as governor]
            [repairshop.operation :as op]
            [repairshop.phase :as phase]
            [repairshop.registry :as registry]
            [repairshop.store :as store]))

(def ^:private operator
  "The human operator this scenario runs as -- the repair technician /
  shop owner who is handed every escalation."
  {:actor-id "op-1" :actor-role :repair-technician :phase 3})

(def ^:private expected-hard-rules
  "Every HARD rule `repairshop.governor` can raise. The scenario below
  is built to fire all six; `-main` refuses to write the console if any
  of them stays silent."
  [:no-spec-basis :evidence-incomplete :parts-cost-mismatch
   :safety-test-not-passed :already-completed :already-returned])

;; ----------------------------- the real run -----------------------------

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- resume! [actor tid decision]
  (g/run* actor {:approval decision} {:thread-id tid :resume? true}))

(defn- approve! [actor tid]
  (resume! actor tid {:status :approved :by (:actor-id operator)}))

(defn- reject! [actor tid]
  (resume! actor tid {:status :rejected :by (:actor-id operator)}))

(defn run-demo!
  "Drives a freshly seeded store through every disposition this actor
  can reach, against the real seed tickets in `repairshop.store/
  demo-data`.

  ticket-1 (Sakura Tanaka, JPN, 2 parts x 15 = the claimed 30.0, safety
  test passed) clears a full lifecycle: intake auto-commits (phase 3,
  no capital risk), then a jurisdiction assessment, a post-repair
  safety screening, a repair completion and a device return each
  escalate to the human and are approved. The last two ALWAYS escalate
  -- `:actuation/complete-repair` and `:actuation/return-device` are
  absent from every phase's `:auto` set AND are in the governor's
  high-stakes set, two independent layers agreeing.

  The six HARD holds each isolate one rule, and none of them ever
  reaches a human:
    ticket-2  jurisdiction \"ATL\" has no entry in `repairshop.facts/
              catalog`, so its assessment cites nothing  -> :no-spec-basis
    ticket-4  repair completion attempted with no assessment on file
                                                         -> :evidence-incomplete
    ticket-3  claimed parts cost 50.0 vs the independently recomputed
              2 x 15 = 30.0                              -> :parts-cost-mismatch
    ticket-4  safety screening that itself detects the failed
              post-repair safety test                    -> :safety-test-not-passed
    ticket-1  completing the same repair twice           -> :already-completed
    ticket-1  returning the same device twice            -> :already-returned

  Finally ticket-2's safety screening is clean, escalates, and the
  human REJECTS it -- the other branch of the soft gate.

  Returns {:db store :runs [{:tid .. :request .. :state ..} ..]}. Every
  field the renderer prints comes from here."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        runs (atom [])
        record! (fn [tid request result]
                  (swap! runs conj {:tid tid :request request :state (:state result)})
                  result)
        step! (fn [tid request] (record! tid request (exec! actor tid request)))
        settle! (fn [tid request decide!]
                  (step! tid request)
                  (record! tid request (decide! actor tid)))]

    ;; --- ticket-1: the full repair lifecycle, human-approved throughout ---
    (step! "t1-intake" {:op :ticket/intake :subject "ticket-1"
                        :patch {:id "ticket-1" :customer "Sakura Tanaka"}})
    (settle! "t1-assess"   {:op :jurisdiction/assess :subject "ticket-1"} approve!)
    (settle! "t1-safety"   {:op :safety/screen       :subject "ticket-1"} approve!)
    (settle! "t1-complete" {:op :repair/complete     :subject "ticket-1"} approve!)
    (settle! "t1-return"   {:op :device/return       :subject "ticket-1"} approve!)

    ;; --- HARD holds, one rule each ---
    (step! "t2-assess"        {:op :jurisdiction/assess :subject "ticket-2"})
    (step! "t4-complete"      {:op :repair/complete     :subject "ticket-4"})
    (settle! "t3-assess"      {:op :jurisdiction/assess :subject "ticket-3"} approve!)
    (step! "t3-complete"      {:op :repair/complete     :subject "ticket-3"})
    (step! "t4-safety"        {:op :safety/screen       :subject "ticket-4"})
    (step! "t1-complete-again" {:op :repair/complete    :subject "ticket-1"})
    (step! "t1-return-again"  {:op :device/return       :subject "ticket-1"})

    ;; --- the soft gate's other branch: a clean proposal the human refuses ---
    (settle! "t2-safety" {:op :safety/screen :subject "ticket-2"} reject!)

    {:db db :runs @runs}))

;; ----------------------------- derivations -----------------------------

(defn- holds
  "The HARD governor holds on the ledger."
  [ledger]
  (filterv #(= :governor-hold (:t %)) ledger))

(defn- rejections [ledger]
  (filterv #(= :approval-rejected (:t %)) ledger))

(defn- commits [ledger]
  (filterv #(= :committed (:t %)) ledger))

(defn- fired-rules
  "The set of HARD rule keywords this run actually raised -- read off
  the ledger, never assumed."
  [ledger]
  (into #{} (mapcat :basis) (holds ledger)))

(defn- audit-facts
  "Every audit fact produced across all runs, in run order. The store
  ledger keeps commits and holds; approval grants live only here, which
  is exactly why the approver-retention table below has to join the
  two."
  [runs]
  (into [] (mapcat #(get-in % [:state :audit] [])) runs))

(defn- approvals-by-subject
  "subject -> {op -> approver} for every `:approval-granted` audit fact."
  [runs]
  (reduce (fn [m {:keys [t op subject by]}]
            (if (= :approval-granted t) (assoc-in m [subject op] by) m))
          {}
          (audit-facts runs)))

(defn- approver-of [approvals subject op]
  (get-in approvals [subject op]))

(defn- retained?
  "Does this stored record itself carry approver attribution? Probed on
  the value the store actually kept, so the table below self-corrects
  the day `store/commit-record!` changes -- nothing here hardcodes a
  verdict."
  [record]
  (boolean
   (when (map? record)
     (some #(contains? record %) [:approved-by "approved_by" :approver "approver"]))))

(defn- attribution-rows
  "One row per committed register, joining the approver from the audit
  fact against whether the STORED record kept it. Derived, not
  asserted: `retained?` inspects the real record."
  [db approvals]
  (let [assessment (store/assessment-of db "ticket-1")
        screening (store/safety-screening-of db "ticket-1")
        completion (first (store/completion-history db))
        ret (first (store/return-history db))
        ticket (store/ticket db "ticket-1")]
    (->> [{:register ":assessment/set"      :effect ":assessment/set"
           :subject "ticket-1" :op :jurisdiction/assess :record assessment
           :ref (:spec-basis assessment)}
          {:register ":safety-screening/set" :effect ":safety-screening/set"
           :subject "ticket-1" :op :safety/screen :record screening
           :ref (some-> (:verdict screening) name)}
          {:register "completion history"    :effect ":ticket/mark-completed"
           :subject "ticket-1" :op :repair/complete :record completion
           :ref (get completion "record_id")}
          {:register "return history"        :effect ":ticket/mark-returned"
           :subject "ticket-1" :op :device/return :record ret
           :ref (get ret "record_id")}
          {:register "ticket directory"      :effect ":ticket/upsert"
           :subject "ticket-1" :op :ticket/intake :record ticket
           :ref (:id ticket)}]
         (mapv (fn [row]
                 (assoc row
                        :approver (approver-of approvals (:subject row) (:op row))
                        :retained? (retained? (:record row))))))))

(defn- jurisdictions-in-play
  "The jurisdictions this run's tickets actually name, sorted."
  [db]
  (vec (sort (distinct (keep :jurisdiction (store/all-tickets db))))))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- label
  "Render a keyword/string uniformly (ledger `:basis` holds rule
  keywords for holds and free-form citation strings for commits)."
  [v]
  (if (keyword? v) (name v) (str v)))

(defn- joined [xs]
  (if (seq xs) (str/join ", " (map label xs)) ""))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title note body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       (if note (str "    <p class=\"muted\">" note "</p>\n") "")
       body
       "  </section>\n"))

(defn- pill [class text] (str "<span class=\"" class "\">" text "</span>"))

(defn- yes-no [b yes no]
  (if b (pill "ok" yes) (pill "warn" no)))

;; ----------------------------- sections -----------------------------

(defn- summary-section [db ledger runs]
  (let [hs (holds ledger)
        stats [["requests driven through the actor" (count (distinct (map :tid runs)))]
               ["ledger facts (append-only)" (count ledger)]
               ["committed ops" (count (commits ledger))]
               ["HARD governor holds" (count hs)]
               ["distinct HARD rules fired" (count (fired-rules ledger))]
               ["escalations rejected by the human" (count (rejections ledger))]
               ["repair-completion drafts" (count (store/completion-history db))]
               ["device-return drafts" (count (store/return-history db))]]]
    (section
     "This run at a glance"
     (str "Counted from the live run, not asserted. Every number below is a "
          (code "count") " over real store/ledger output.")
     (table ["Measure" "Value"]
            (mapv (fn [[k v]] (row (esc k) (str "<strong>" v "</strong>"))) stats)))))

(defn- ticket-status-cell [ledger id]
  (let [f (last (filter #(= (:subject %) id) ledger))]
    (case (:t f)
      :committed (pill "ok" "committed")
      :governor-hold (pill "critical"
                           (str "HARD hold &middot; " (esc (joined (:basis f)))))
      :approval-rejected (pill "warn" "human rejected")
      (pill "muted" "no activity"))))

(defn- lifecycle-cell [{:keys [repair-completed? device-returned?]}]
  (cond
    device-returned? (pill "ok" "repaired &amp; returned")
    repair-completed? (pill "warn" "repaired, awaiting return")
    :else (pill "muted" "in repair")))

(defn- tickets-section [db ledger]
  (section
   "Repair tickets"
   (str "The seeded ticket directory from " (code "repairshop.store/demo-data")
        ", after the run. The parts column shows the ticket's own claim beside "
        "the value " (code "repairshop.registry/compute-parts-cost")
        " recomputes independently from " (code ":parts-quantity")
        " &times; " (code ":parts-unit-price") " -- the governor trusts the "
        "recompute, never the claim.")
   (table ["Ticket" "Customer" "Device" "Jurisdiction"
           "Claimed parts cost" "Recomputed" "Match" "Safety test" "Lifecycle" "Last op"]
          (mapv (fn [{:keys [id customer device jurisdiction claimed-parts-cost
                             parts-quantity parts-unit-price safety-test-passed?]
                      :as t}]
                  (row (code id)
                       (esc customer)
                       (esc device)
                       (code jurisdiction)
                       (esc claimed-parts-cost)
                       (str (esc parts-quantity) " &times; " (esc parts-unit-price)
                            " = " (esc (registry/compute-parts-cost t)))
                       (yes-no (registry/parts-cost-matches-claim? t) "matches" "mismatch")
                       (yes-no safety-test-passed? "passed" "failed")
                       (lifecycle-cell t)
                       (ticket-status-cell ledger id)))
                (store/all-tickets db)))))

(defn- holds-section [ledger]
  (let [hs (holds ledger)]
    (section
     "Governor HARD holds"
     (str "Each row is a real " (code ":governor-hold") " fact on the append-only "
          "ledger. A HARD violation cannot be overridden by a human approver -- "
          "these proposals never reached the approval node at all. The "
          (code "detail") " text is the governor's own, not a rewrite.")
     (table ["Op" "Ticket" "Rule" "Governor detail" "Advisor confidence"]
            (mapv (fn [{:keys [op subject basis violations confidence]}]
                    (row (code op)
                         (code subject)
                         (pill "critical" (esc (joined basis)))
                         (esc (str/join " / " (map :detail violations)))
                         (esc confidence)))
                  hs)))))

(defn- rule-coverage-section [ledger]
  (let [fired (fired-rules ledger)]
    (section
     "HARD rule coverage"
     (str "Which of " (code "repairshop.governor") "'s HARD rules this scenario "
          "actually exercised. A rule that never fires has not been shown to "
          "work, so " (code "-main") " refuses to write this page unless every "
          "row below reads <em>fired</em>.")
     (table ["Rule" "Exercised by this run"]
            (mapv (fn [r]
                    (row (code r)
                         (yes-no (contains? fired r) "fired" "NOT EXERCISED")))
                  expected-hard-rules)))))

(defn- op-gate-section []
  (let [auto-ops (get-in phase/phases [3 :auto])
        ops (sort-by name phase/write-ops)]
    (section
     "Action gate, by op"
     (str "Derived from " (code "repairshop.phase/phases") " and "
          (code "repairshop.governor/high-stakes") " -- this table is read out of "
          "the actor's own data, so it cannot drift from the code it describes. "
          "Two independent layers keep actuation human: an op is auto-eligible "
          "only if phase 3 lists it AND the governor does not treat its stake as "
          "high.")
     (table ["Op" "Writes at phase 3" "In phase 3 :auto set" "Governor high-stakes" "Effective gate"]
            (mapv (fn [o]
                    (let [writes? (contains? (get-in phase/phases [3 :writes]) o)
                          auto? (contains? auto-ops o)
                          stake (case o
                                  :repair/complete :actuation/complete-repair
                                  :device/return :actuation/return-device
                                  nil)
                          high? (boolean (governor/high-stakes stake))]
                      (row (code o)
                           (yes-no writes? "yes" "no")
                           (yes-no auto? "yes" "no")
                           (if high? (pill "warn" "yes") (pill "muted" "no"))
                           (cond
                             high? (pill "warn" "ALWAYS human approval &middot; never auto at any phase")
                             auto? (pill "ok" "auto-commit when governor-clean")
                             :else (pill "warn" "human approval (not yet auto-eligible)")))))
                  ops)))))

(defn- phases-section []
  (section
   "Rollout phases"
   (str "Read straight out of " (code "repairshop.phase/phases")
        ". Note that " (code ":repair/complete") " and " (code ":device/return")
        " are absent from every " (code ":auto") " set including phase 3 -- a "
        "permanent structural fact, not a milestone still to come.")
   (table ["Phase" "Label" "Writes allowed" "Auto-commit allowed"]
          (mapv (fn [[n {:keys [label writes auto]}]]
                  (row (str (esc n) (when (= n phase/default-phase)
                                      (str " " (pill "badge" "default"))))
                       (esc label)
                       (if (seq writes)
                         (str/join " " (map #(code %) (sort-by name writes)))
                         (pill "muted" "none"))
                       (if (seq auto)
                         (str/join " " (map #(code %) (sort-by name auto)))
                         (pill "muted" "none"))))
                (sort-by key phase/phases)))))

(defn- jurisdictions-section [db]
  (let [in-play (jurisdictions-in-play db)
        cov (facts/coverage in-play)]
    (section
     "Jurisdiction spec-basis catalog"
     (str "The G2-style citation table from " (code "repairshop.facts/catalog")
          " that every jurisdiction proposal is checked against. A jurisdiction "
          "absent from this table has NO spec-basis and the governor holds any "
          "proposal that invents one -- which is exactly what happens to "
          (code "ATL") " below.")
     (str
      (table ["ISO3" "Jurisdiction" "Owner authority" "Legal basis" "Required evidence" "Provenance"]
             (mapv (fn [[iso3 {:keys [name owner-authority legal-basis required-evidence provenance]}]]
                     (row (code iso3)
                          (esc name)
                          (esc owner-authority)
                          (esc legal-basis)
                          (str (count required-evidence) " items")
                          (str "<code>" (esc provenance) "</code>")))
                   (sort-by key facts/catalog)))
      "    <p class=\"note\">Coverage over the jurisdictions this run's tickets name ("
      (esc (str/join ", " in-play)) "): <strong>"
      (esc (:covered cov)) " of " (esc (:requested cov))
      "</strong> have an official spec-basis. Missing: "
      (if (seq (:missing-jurisdictions cov))
        (str "<code>" (esc (str/join ", " (:missing-jurisdictions cov))) "</code>")
        "none")
      ". " (esc (:note cov)) "</p>\n"))))

(defn- registry-section [db]
  (let [rows (concat
              (map (fn [r] [":ticket/mark-completed" r]) (store/completion-history db))
              (map (fn [r] [":ticket/mark-returned" r]) (store/return-history db)))]
    (section
     "Registry drafts"
     (str "The book-of-record drafts " (code "repairshop.registry")
          " built for the two committed actuations. Reference numbers are "
          "jurisdiction-scoped sequences this actor assigns -- there is no "
          "international check-digit standard for a repair-completion or "
          "device-return number, and this actor does not invent one. Every "
          "certificate it produces is UNSIGNED: signature is the shop's act, "
          "not the actor's.")
     (table ["Effect" "Record id" "Kind" "Ticket" "Jurisdiction" "Immutable"]
            (mapv (fn [[effect r]]
                    (row (code effect)
                         (code (get r "record_id"))
                         (esc (get r "kind"))
                         (code (get r "ticket_id"))
                         (code (get r "jurisdiction"))
                         (yes-no (get r "immutable") "true" "false")))
                  rows)))))

(defn- attribution-section [db approvals]
  (let [rows (attribution-rows db approvals)
        lost (filter #(and (:approver %) (not (:retained? %))) rows)]
    (section
     "Approver attribution, per register"
     (str "Who authorised each committed write, and whether the STORED record "
          "itself kept that fact. The right-hand column is <em>probed</em> at "
          "render time by inspecting the real record for an approver key, not "
          "asserted here -- so this table corrects itself if "
          (code "repairshop.store/commit-record!") " changes. Where the record "
          "does not retain it, the approver is still shown, joined from the "
          (code ":approval-granted") " audit fact and labelled as such: "
          "silently omitting it would leave a reader unable to tell "
          "<em>nobody approved</em> from <em>the store did not keep it</em>.")
     (str
      (table ["Register" "Commit effect" "Record reference" "Approver" "Retained in record?"]
             (mapv (fn [{:keys [register effect ref approver retained?]}]
                     (row (esc register)
                          (code effect)
                          (if ref (code ref) (pill "muted" "&mdash;"))
                          (cond
                            (and approver retained?) (esc approver)
                            approver (str (esc approver) " "
                                          (pill "warn" "(audit only &mdash; not retained in record)"))
                            :else (pill "muted" "auto-committed &mdash; no approver"))
                          (yes-no retained? "yes" "no")))
                   rows))
      "    <p class=\"note\">Measured on this run: "
      (esc (count (filter :retained? rows))) " of " (esc (count rows))
      " registers retain approver attribution. "
      (if (seq lost)
        (str "The " (esc (count lost))
             " that do not are the actuation registers -- "
             (str/join ", " (map #(str "<code>" (esc (:effect %)) "</code>") lost))
             " -- whose records are rebuilt by <code>repairshop.registry</code> "
             "from the ticket's own fields and carry no approver field at all. "
             "The attribution survives only in the audit stream, which is why "
             "it is joined and labelled above rather than dropped.")
        "No approver known to the audit stream was lost.")
      "</p>\n"))))

(defn- ledger-section [ledger]
  (section
   "Audit ledger (this run)"
   (str "The complete append-only decision log the store kept, in commit order. "
        "Holds carry the rule that fired; commits carry the citations the "
        "advisor was allowed to rely on.")
   (table ["#" "Fact" "Op" "Ticket" "Disposition" "Basis / citations"]
          (map-indexed
           (fn [i {:keys [t op subject disposition basis]}]
             (row (esc (inc i))
                  (case t
                    :committed (pill "ok" "committed")
                    :governor-hold (pill "critical" "governor-hold")
                    :approval-rejected (pill "warn" "approval-rejected")
                    (esc (label t)))
                  (code op)
                  (code subject)
                  (esc (label disposition))
                  (esc (joined basis))))
           ledger))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the operator console from a completed `run-demo!` result."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))
        approvals (approvals-by-subject runs)]
    (str
     "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-9521 &middot; consumer electronics repair &mdash; Operator Console</title>\n"
     "<style>\n" (jp-go-dds.skin/dds+skin) "\n</style>\n"
     "</head>\n<body>\n<div class=\"container\">\n"
     "<header>\n"
     "  <h1>Repair of consumer electronics (ISIC 9521) &mdash; Operator Console</h1>\n"
     "  <p><span class=\"badge\">read-only sample</span> <span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">repair completion &amp; device return always human-approved</span></p>\n"
     "  <p class=\"muted\">Generated at build time by <code>repairshop.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) by driving the real "
     "<code>repairshop.operation</code> actor graph over a freshly seeded "
     "<code>repairshop.store</code>. No figure on this page is hand-written; "
     "no usage, revenue or performance metric is claimed anywhere.</p>\n"
     "</header>\n<main>\n"
     (summary-section db ledger runs)
     (tickets-section db ledger)
     (holds-section ledger)
     (rule-coverage-section ledger)
     (op-gate-section)
     (phases-section)
     (jurisdictions-section db)
     (registry-section db)
     (attribution-section db approvals)
     (ledger-section ledger)
     "</main>\n"
     "<footer class=\"muted\"><p>Deterministic build artifact &mdash; no clock, no "
     "randomness, no network. Rendered from the "
     "<code>repairshop.store/demo-data</code> seed; re-running the generator "
     "against the same seed produces a byte-identical file. The advisor is the "
     "deterministic mock <code>repairshop.repairopsllm/mock-advisor</code>, so "
     "this page shows the governor contract, not a language model's "
     "output.</p></footer>\n"
     "</div>\n</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        hs (holds ledger)
        fired (fired-rules ledger)
        missing (remove fired expected-hard-rules)]
    ;; A console that shows no real HARD hold is not evidence of a governor.
    (when (empty? hs)
      (throw (ex-info "no :governor-hold fact on the ledger -- refusing to write a console that shows no real hold"
                      {:ledger-facts (count ledger)})))
    ;; A coverage table that silently reports fewer rules than claimed is the
    ;; same failure in a quieter form.
    (when (seq missing)
      (throw (ex-info "expected HARD rules did not fire -- refusing to write a console whose coverage table is thinner than advertised"
                      {:missing (vec missing) :fired (vec (sort-by name fired))})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count hs) " HARD holds covering "
                  (count fired) "/" (count expected-hard-rules) " rules, "
                  (count (commits ledger)) " commits, "
                  (count (rejections ledger)) " human rejection)"))))
