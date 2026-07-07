# cloud-itonami-isic-9521

Open Business Blueprint for **ISIC Rev.5 9521**: Repair of consumer
electronics. This repository publishes an electronics-repair actor --
ticket intake, jurisdiction assessment, post-repair safety screening,
repair completion and device return -- as an OSS business that any
qualified repair-shop operator can fork, deploy, run, improve and
sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603)) --
a second personal/repair-services vertical (ISIC division 95) in this
fleet. Here it is **RepairOps-LLM ⊣ Repair Shop Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a repair
> summary, normalizing intake, and checking whether a claimed parts
> cost actually equals parts-quantity times unit-price -- but it has
> **no notion of which jurisdiction's consumer-product-safety
> requirements are official, no license to complete a real repair or
> return a real device to a customer, and no way to know on its own
> whether a device has actually passed its post-repair safety test**.
> Letting it complete a repair or return a device directly invites
> fabricated jurisdiction citations, a parts-cost claim that doesn't
> match the actual invoice arithmetic, and a device that failed its
> post-repair safety test being quietly returned to a customer -- and
> liability for whoever runs it. This project seals the RepairOps-LLM
> into a single node and wraps it with an independent **Repair Shop
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers ticket intake through jurisdiction assessment, post-
repair safety screening, repair completion and device return. It does
**not**, by itself, hold any license required to operate a repair shop
in a given jurisdiction, and it does not claim to. It also does
**not** model a full parts-catalog/labor/tax invoice engine -- no
line-item parts catalog, no labor-hour billing, no tax calculation
(see `repairshop.registry/compute-parts-cost`'s own docstring for the
honest simplification this makes: a single flat quantity-times-unit-
price calculation, not a full invoice engine). Whoever deploys and
operates a live instance (a repair-shop operator) supplies any
jurisdiction-specific licensing, the real diagnostic/repair expertise
and the real repair-shop-management-system integrations, and bears
that jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have
to build the compliance layer from scratch for every new market.

### Actuation

**Completing a real repair and returning a real device to the
customer are never autonomous, at any phase, by construction.** Two
independent layers enforce this (`repairshop.governor`'s `:actuation/
complete-repair`/`:actuation/return-device` high-stakes gate and
`repairshop.phase`'s phase table, which never puts `:repair/complete`/
`:device/return` in any phase's `:auto` set) -- see `repairshop.
phase`'s docstring and `test/repairshop/phase_test.clj`'s `repair-
complete-never-auto-at-any-phase`/`device-return-never-auto-at-any-
phase`. The actor may draft, check and recommend; a human repair
technician/shop owner is always the one who actually completes a
repair or returns a device. Like `6512`/`6622`/`6520`/`6530`/`6820`/
`6920`/`6611`/`8530`/`9200`, this actor has TWO actuation events.

## The core contract

```
ticket intake + jurisdiction facts (repairshop.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ RepairOps-   │ ─────────────▶ │ Repair Shop                 │  (independent system)
   │ LLM (sealed) │  + citations    │ Governor: spec-basis ·      │
   └──────────────┘                 │ evidence-incomplete ·        │
                             commit ◀────┼──────────▶ hold │ parts-cost-mismatch
                                 │             │           │ (exact-match
                           record + ledger  escalate ─▶ human   independent recompute) ·
                                             (ALWAYS for         safety-test-not-passed ·
                                              :repair/complete /   already-completed/-returned
                                              :device/return)
```

**The RepairOps-LLM never completes a repair or returns a device the
Repair Shop Governor would reject, and never does so without a human
sign-off.** Hard violations (fabricated jurisdiction requirements;
unsupported repair evidence; a claimed parts cost that doesn't match
quantity times unit-price; a device returned without a passed safety
test; a double completion or return) force **hold** and *cannot* be
approved past; a clean repair/return proposal still always routes to
a human.

## Run

```bash
clojure -M:dev:run     # walk two clean lifecycles (repair completion, device return) + four HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a diagnostic-bench robot
assists physical device testing and repair, under the actor, gated by
the independent **Repair Shop Governor**. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Repair Shop Governor, repair-completion + device-return draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9521`). Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`, this
vertical's service/member records are practice-specific rather than a
shared cross-operator data contract, so `repairshop.*` runs on the
generic identity/forms/dmn/bpmn/audit-ledger stack only -- no bespoke
domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/repairshop/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate repair-completion/device-return history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded ticket, and the double-completion/double-return guards check dedicated `:repair-completed?`/`:device-returned?` booleans rather than a `:status` value |
| `src/repairshop/registry.cljc` | Repair-completion + device-return draft records, plus `compute-parts-cost`/`parts-cost-matches-claim?` -- an EXACT-MATCH independent recompute (claimed parts cost must equal quantity x unit-price), reusing this fleet's established recompute family for a further domain |
| `src/repairshop/facts.cljc` | Per-jurisdiction consumer-product-safety catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/repairshop/repairopsllm.cljc` | **RepairOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/safety-screening/repair-completion/device-return proposals |
| `src/repairshop/governor.cljc` | **Repair Shop Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · parts-cost-mismatch, pure ground-truth exact-match recompute · safety-test-not-passed, unconditional evaluation) + already-completed/already-returned guards + 1 soft (confidence/actuation gate) |
| `src/repairshop/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (repair/return actuation always human; ticket intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/repairshop/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/repairshop/sim.cljc` | demo driver |
| `test/repairshop/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers ticket intake through jurisdiction assessment, post-
repair safety screening, repair completion and device return -- the
core governed lifecycle this blueprint's own `docs/business-model.md`
names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Ticket intake + per-jurisdiction consumer-product-safety checklisting, HARD-gated on an official spec-basis citation (`:ticket/intake`/`:jurisdiction/assess`) | A full parts-catalog/labor/tax invoice engine (line-item parts catalog, labor-hour billing, tax calculation -- see `compute-parts-cost`'s docstring) |
| Post-repair safety screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:safety/screen`) | Real repair-shop-management-system integration, warranty/recall reporting |
| Repair completion, HARD-gated on the claimed parts cost matching quantity times unit-price and a double-completion guard (`:repair/complete`) | Ongoing device-diagnostic workflows themselves |
| Device return, HARD-gated on the device having passed its post-repair safety test and a double-return guard (`:device/return`) | |
| Immutable audit ledger for every intake/assessment/screening/completion/return decision | |

Extending coverage is additive: add the next gate (e.g. a warranty-
coverage check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`repairshop.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `repairshop.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `repairshop.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `RepairOps-LLM` + `Repair Shop Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the
eighteen prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
