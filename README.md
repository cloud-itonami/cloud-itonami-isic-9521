# cloud-itonami-9521

Open Business Blueprint for **ISIC Rev.5 9521**: Repair of consumer electronics.

This repository designs a forkable OSS business for repair of consumer electronics -- diagnosing and repairing TVs, audio equipment, computers and similar devices for customers -- run by a qualified operator so a community or
independent provider never surrenders customer/member data and ledgers to a
closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a diagnostic-bench robot assists physical device testing and repair,
under an actor that proposes actions and an independent **Repair Shop Governor**
that gates them. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Core Contract

```text
intake + identity + service/member records
        |
        v
RepairOps-LLM -> Repair Shop Governor -> hold, proceed, or human approval
        |
        v
service/member ledger + evidence record + audit
```

No automated proposal, by itself, can complete the following without governor
approval and audit evidence: performing a repair or returning a device to the customer.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9521`). This vertical's service/member records are practice-specific
rather than a shared cross-operator data contract, so it runs on the generic
identity/forms/dmn/bpmn/audit-ledger stack -- no bespoke domain capability lib.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Maturity

`:blueprint` -- this repository is the published business/operator design.
The governed actor implementation (`RepairOps-LLM` + `Repair Shop Governor` as
running code) is a follow-up, same as any other `:blueprint`-tier
`cloud-itonami-*` entry in `kotoba-lang/industry`'s registry.

## License

Code and implementation templates are AGPL-3.0-or-later.
