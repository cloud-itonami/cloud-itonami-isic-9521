# Business Model: Repair of consumer electronics

## Classification

- Repository: `cloud-itonami-isic-9521`
- ISIC Rev.5: `9521`
- Activity: repair of consumer electronics -- diagnosing and repairing TVs, audio equipment, computers and similar devices for customers
- Social impact: community access, data sovereignty, transparent audit

## Customer

- independent electronics-repair shops
- cooperative repair collectives
- community right-to-repair programs

## Offer

- device intake
- diagnostic/quote proposal
- repair-completion proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per shop
- support: monthly retainer with SLA
- migration: import from an incumbent repair-shop system
- per-repair fee

## Trust Controls

- no repair is performed and no device is returned without human sign-off
- a fabricated diagnostic forces a hold, not an override
- every repair path is auditable
- customer device data (personal content) stays outside Git
- emergency manual override paths remain outside LLM control
