# Governance

`cloud-itonami-isic-4782` is an OSS open-business blueprint for
market-stall textile/clothing/footwear retail operations coordination
(ISIC Rev.5 4782 -- retail sale via stalls and markets of textiles,
clothing and footwear).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered market stall, or a supply
  order naming an unverified/unregistered vendor, can never commit.
- the StallMarketGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, quality-dispute-
  resolution or counterfeit-authenticity-determination finalization
  content, an op outside the closed allowlist) cannot be overridden by
  human approval.
- every sales-record log, stall-operation schedule, supply-order
  coordination and quality-concern flag is auditable.
- customer, employee and supplier data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing sale-record, stall-operation, supply-order or quality-concern
  policy checks
- mishandling customer, employee or supplier data
- misrepresenting certification status
- failing to respond to security, quality-dispute or counterfeit-
  authenticity incidents
