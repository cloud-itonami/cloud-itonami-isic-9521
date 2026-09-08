(ns repairshop.registry
  "Pure-function repair-completion + device-return record construction
  -- an append-only repair-shop book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a repair-completion or device-return
  reference number -- every shop/jurisdiction assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `repairshop.facts` uses.

  `parts-cost-matches-claim?` reuses this fleet's EXACT-MATCH
  independent-recompute family (`pension.registry`'s apportionment
  check/`reinsurance.registry`'s recovery check/`realty.registry`'s
  fee check/`brokerage.registry`'s order-value check/`wagering.
  registry`'s payout check) for a further domain (repair-invoice
  arithmetic: a claimed parts cost must equal parts-quantity x parts-
  unit-price, computed independently from the ticket's own permanent
  fields).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real repair-shop-management system. It builds the
  RECORD a shop would keep, not the act of completing a repair or
  returning a device itself (that is `repairshop.operation`'s
  `:repair/complete`/`:device/return`, always human-gated -- see
  README `Actuation`)."
  (:require [kotoba.lang.text :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  repair shop's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(def ^:private money-scale
  "Sub-minor-unit scale used when comparing two money amounts: 1/10000 of
  a unit. Coarser than double representation error by many orders of
  magnitude, finer than any real currency's minor unit (2 decimals for
  most, 3 for KWD/BHD/OMR, 0 for JPY/KRW)."
  10000)

(defn- money=
  "Exact-at-money-precision equality for two amounts.

  `==` on raw doubles is NOT the right comparison for money: the product
  of a quantity and a 2-decimal unit price is routinely not the double
  nearest the true total, so a CORRECT claim compared false. Measured on
  this exact `quantity x unit-price` shape across 1-24 units x $0.01 to
  $199.99: 14,213 of 68,568 combinations -- 20.7% -- were rejected while
  correct. A customer presenting their own right total was told it did
  not match.

  Rounding both sides to `money-scale` before comparing removes the
  representation error while preserving every distinction money can
  actually carry."
  [x y]
  (and (number? x) (number? y)
       (= (Math/round (* money-scale (double x)))
          (Math/round (* money-scale (double y))))))

(defn compute-parts-cost
  "The ground-truth parts cost owed for `ticket`'s own `:parts-
  quantity` and `:parts-unit-price` -- a single flat quantity x unit-
  price calculation, not a full parts-catalog/labor/tax invoice
  engine."
  [{:keys [parts-quantity parts-unit-price]}]
  ;; nil when either field is not a number: an un-recomputable ticket is
  ;; un-verifiable, which is neither `correct` nor a ClassCastException
  ;; thrown out of the caller.
  (when (and (number? parts-quantity) (number? parts-unit-price))
    (* (double parts-quantity) (double parts-unit-price))))

(defn parts-cost-matches-claim?
  "Does `ticket`'s own `:claimed-parts-cost` equal the independently
  recomputed `compute-parts-cost`? A pure ground-truth check against
  the ticket's own permanent fields -- reuses this fleet's EXACT-MATCH
  independent-recompute family for a further domain."
  [{:keys [claimed-parts-cost] :as ticket}]
  (money= claimed-parts-cost (compute-parts-cost ticket)))

(defn register-repair-completion
  "Validate + construct the REPAIR-COMPLETION registration DRAFT -- the
  shop's own legal act of completing a real repair. Pure function --
  does not touch any real repair-shop-management system; it builds
  the RECORD a shop would keep. `repairshop.governor` independently
  re-verifies the ticket's own parts-cost arithmetic, and blocks a
  double-completion of the same ticket, before this is ever allowed to
  commit."
  [ticket-id jurisdiction sequence]
  (when-not (and ticket-id (not= ticket-id ""))
    (throw (ex-info "repair-completion: ticket_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "repair-completion: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "repair-completion: sequence must be >= 0" {})))
  (let [completion-number (str (str/upper jurisdiction) "-RPR-" (zero-pad sequence 6))
        record {"record_id" completion-number
                "kind" "repair-completion-draft"
                "ticket_id" ticket-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "completion_number" completion-number
     "certificate" (unsigned-certificate "RepairCompletion" completion-number completion-number)}))

(defn register-device-return
  "Validate + construct the DEVICE-RETURN registration DRAFT -- the
  shop's own legal act of returning a real device to the customer.
  Pure function -- does not touch any real repair-shop-management
  system; it builds the RECORD a shop would keep. `repairshop.
  governor` independently re-verifies the ticket's own post-repair
  safety-test status, and blocks a double-return of the same ticket,
  before this is ever allowed to commit."
  [ticket-id jurisdiction sequence]
  (when-not (and ticket-id (not= ticket-id ""))
    (throw (ex-info "device-return: ticket_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "device-return: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "device-return: sequence must be >= 0" {})))
  (let [return-number (str (str/upper jurisdiction) "-RTN-" (zero-pad sequence 6))
        record {"record_id" return-number
                "kind" "device-return-draft"
                "ticket_id" ticket-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "return_number" return-number
     "certificate" (unsigned-certificate "DeviceReturn" return-number return-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
