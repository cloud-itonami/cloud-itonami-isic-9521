# ADR-0001: cloud-itonami-isic-9521 -- RepairOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603` ADR-0001s (the pattern this ADR ports);
  ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819 (`6612`/`6492`/`6920`/`6611`/`7120`/
  `8620`/`8530`/`9200`/`7500`/`9603`, the ten verticals built outside
  ADR-2607032000's original insurance/real-estate batch -- this is
  the eleventh)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `9603`, this ADR deepens `cloud-itonami-
  isic-9521` (repair of consumer electronics) from `:blueprint` to
  `:implemented`, the nineteenth actor in this fleet -- the first ISIC
  division 95 (repair services) vertical, and a second personal/
  repair-services build alongside `9603`'s division 96.

## Problem

An electronics-repair shop's completion/return workflow bundles
several distinct concerns under one governed workflow:

1. **Jurisdiction consumer-product-safety correctness** -- an official
   spec-basis citation from a real product-safety regulator (METI/
   CPSC/OPSS/state market-surveillance authorities), never
   fabricated.
2. **Parts-cost correctness** -- does a claimed parts cost actually
   equal parts-quantity times parts-unit-price? Reuses this fleet's
   established EXACT-MATCH independent-recompute family
   (`pension.registry`'s apportionment check/`reinsurance.registry`'s
   recovery check/`realty.registry`'s fee check/`brokerage.
   registry`'s order-value check/`wagering.registry`'s payout check)
   for a further domain (repair-invoice arithmetic).
3. **Post-repair safety verification** -- has a device actually passed
   its post-repair safety test before being returned to a customer?
   The repair-services-specific reuse of the unconditional-evaluation
   screening discipline this fleet's `casualty.governor/sanctions-
   violations` originally established -- an EIGHTH distinct grounding.
4. **Dual actuation, on the SAME entity** -- completing a repair and
   returning a device are two distinct real-world acts, both operating
   on the same repair ticket.

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run an electronics-repair shop with an LLM"
but "seal the LLM inside a trust boundary and layer evidence-
sufficiency, parts-cost-correctness verification, post-repair-safety
verification, audit and human-approval on top of it, while
structurally fixing both real actuation events as human-only."

## Decision

### 1. RepairOps-LLM is sealed into the bottom node; it never completes/returns directly

`repairshop.repairopsllm` returns exactly five kinds of proposal:
intake normalization, jurisdiction consumer-product-safety checklist,
post-repair safety screening, repair-completion draft, and device-
return draft. No proposal writes the SSoT or commits a real repair
completion/device return directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 electronics-repair operation

`repairshop.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `parts-cost-matches-claim?` reuses the EXACT-MATCH recompute family for a further domain

`parts-cost-mismatch-violations` reuses this fleet's established
EXACT-MATCH independent-recompute shape (no proposal inspection or
stored-verdict lookup needed at all, since its inputs --
`:parts-quantity`/`:parts-unit-price`/`:claimed-parts-cost` -- are
permanent facts already on the ticket) for repair-invoice arithmetic
-- a deliberate, straightforward reuse rather than a new shape, since
quantity x unit-price is genuinely the same arithmetic family
`wagering.registry/payout-matches-claim?` established for gaming
payouts.

### 4. Post-repair safety screening reuses the unconditional-evaluation discipline for an eighth distinct grounding

`safety-test-not-passed-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for BOTH `:safety/screen` and `:device/return` -- the SAME
shape this fleet's seven prior groundings (sanctions, market
surveillance, instrument calibration, clinician credential, academic
integrity, patron compliance, veterinarian credential, disposition
authorization) establish, now applied to post-repair device safety --
the eighth distinct application.

### 5. Dual actuation on the SAME ticket entity

`repairshop.governor`'s `high-stakes` set has two members
(`:actuation/complete-repair` and `:actuation/return-device`),
matching `6512`'s/`6622`'s/`6520`'s/`6530`'s/`6820`'s/`6920`'s/
`6611`'s/`8530`'s/`9200`'s dual-actuation shape -- this domain
genuinely has two distinct real-world acts, both operating on the
same repair ticket (mirroring `marketadmin.store`'s/`registrar.
store`'s/`wagering.store`'s dual-actuation-on-one-entity design).

### 6. Double-completion/double-return guards check dedicated boolean facts, not `:status`

`already-completed-violations`/`already-returned-violations` check
`:repair-completed?`/`:device-returned?`, dedicated booleans set once
and never cleared, rather than a `:status` value that could
legitimately advance past a checked state (the exact trap `cloud-
itonami-isic-6492`'s ADR-0001 documents in detail, explicitly avoided
BY DESIGN in `6920`'s, `6611`'s, `7120`'s, `8620`'s, `8530`'s, `9200`'s,
`7500`'s and `9603`'s equivalent guards). This actor's `:status`
never needs to encode "has this actuation already happened" at all --
a deliberate architectural choice applied here for a ninth consecutive
time.

### 7. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`, and unlike most
other actors in this fleet (each referencing its own `kotoba-lang/*`
capability lib), this vertical's service/member records are practice-
specific rather than a shared cross-operator data contract --
`repairshop.*` runs on the generic identity/forms/dmn/bpmn/audit-
ledger stack only, per the blueprint's own explicit statement.

### 8. No bug this time

Like `7120`/`8620`/`8530`/`9200`/`7500`/`9603` (and unlike `6492`'s
status-lifecycle bug or `6920`'s NullPointerException), this build's
test suite, lint, and demo-ledger verification all passed clean on the
first run. The demo (`clojure -M:dev:run`) was still independently
verified against the printed audit ledger -- basis tags `:no-spec-
basis` · `:parts-cost-mismatch` · `:safety-test-not-passed` ·
`:already-completed` · `:already-returned` all appear exactly where
the sim script intends, and the completion/return histories each
contain exactly one drafted record after their respective double-
actuation attempts are held -- the same discipline that caught every
real bug in this fleet so far, applied here and finding nothing to
fix.

## Consequences

- (+) Electronics-repair services get the same governed, auditable-
  actor treatment as the eighteen prior actors, and this fleet now has
  a SECOND division-95/96 personal/repair-services precedent
  (alongside `9603`), further proving the pattern generalizes across
  genuinely different domains.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/repairshop/phase_test.clj`'s `repair-
  complete-never-auto-at-any-phase`/`device-return-never-auto-at-any-
  phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/
  repairshop/store_contract_test.clj`, the same `:db-api`-driven swap
  pattern every sibling actor uses.
- (+) `parts-cost-matches-claim?`/`parts-cost-mismatch-violations`
  extends this fleet's exact-match-recompute family to a SIXTH domain
  instance, regression-tested by `test/repairshop/governor_contract_
  test.clj`'s `parts-cost-mismatch-is-held`.
- (+) `safety-test-not-passed-violations` extends the unconditional-
  evaluation screening discipline to an EIGHTH distinct grounding.
- (+) Both the demo and the full test suite passed clean on the first
  run -- no bug this time, unlike `6492`/`6920`.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `repairshop.facts/
  coverage` reports this honestly rather than claiming broader
  coverage.
- (-) `compute-parts-cost` models only a single flat quantity-times-
  unit-price calculation, not a full parts-catalog/labor/tax invoice
  engine -- see `cloud-itonami-isic-9521`'s own ADR-0001 and README
  coverage table for the full honest-scope accounting.
- Fleet-wide: 25 actors now `:implemented` out of 643 total registry
  entries (after this promotion); the next "pick a new ISIC blueprint
  vertical" firing remains free to select from ANY remaining
  `:blueprint`-tier `cloud-itonami-*` entry.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to any prior post-batch ADR | ❌ | All ten of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`; mixing a different ISIC division (95, distinct from those ten's divisions) into any would blur scope boundaries |
| Keep `cloud-itonami-isic-9521` at `:blueprint` only | ❌ | The standing direction continues past `9603`; electronics-repair is a natural, well-precedented next domain, and further diversifies personal/repair services beyond `9603`'s division 96 into division 95 |
| Invent a new arithmetic check shape for parts-cost correctness rather than reusing the exact-match family | ❌ | Quantity x unit-price is genuinely the same "independently recompute and compare for equality" concept this fleet's apportionment/recovery/fee/order-value/payout checks already establish -- inventing a superficially different shape would be artificial novelty |
| Model a full parts-catalog/labor/tax invoice engine for conformance-test rigor | ❌ | Genuinely more complex real-world repair-invoice logic that this R0 does not claim to model correctly -- honestly scoped to a single flat quantity-times-unit-price calculation instead, same as every sibling's "starting catalog, not exhaustive" posture |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/repair`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning `6920`'s/`7120`'s/`8620`'s/`8530`'s/`9200`'s/`7500`'s/`9603`'s ADRs already established |
