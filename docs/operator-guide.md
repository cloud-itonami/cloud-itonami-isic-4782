# Operator Guide

## First Deployment
1. Register operator, stalls and vendors; independently confirm each
   stall's market-authority registration/permit and each vendor's
   registration before seeding `stallops.store`.
2. Import existing sales/inventory history.
3. Run read-only sales-record-logging and stall-operation dry-runs
   (Phase 0-1).
4. Configure the rollout phase and the `coordinate-supply-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run quality-concern flag and audit export.

## Minimum Production Controls
- stall-registration/permit-verification check before ANY proposal for
  that stall
- vendor-registration/verification check before ANY `:coordinate-
  supply-order` proposal
- governor gate on every proposal before commit
- human sign-off for `:flag-quality-concern` (always) and high-cost
  `:coordinate-supply-order` proposals
- audit export for every commit, hold and approval
- backup manual back-office process

## Certification
Certified operators must prove stall/vendor-verification discipline,
governor-bypass resistance, evidence-backed quality-concern and
counterfeit-authenticity reporting, and human review for every
escalation-gated action.
