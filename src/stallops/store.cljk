(ns stallops.store
  "SSoT for the ISIC-4782 'Retail sale via stalls and markets of
  textiles, clothing and footwear' operations-COORDINATION actor, behind
  a `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the back-office operations of a market-stall/
  street-market textile, clothing and footwear vendor: inventory/sale
  data logging, stall placement and staffing scheduling, textile/
  clothing/footwear supply-order coordination with registered vendors
  (manufacturers/wholesalers/distributors), and quality-concern flagging
  (a counterfeit item, a defective-goods item, or a stall/market-permit
  concern). It never sets or authorizes a unit price, and never directly
  finalizes a quality-dispute resolution OR a counterfeit-authenticity
  determination (issuing a refund/replacement, voiding a sale, charging
  back a vendor, revoking a vendor's registration, declaring an item
  counterfeit or certifying it as genuine) -- see `stallops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `stalls` directory keyed by `:stall-id` STRING and a
  `vendors` directory keyed by `:vendor-id` STRING (never keywords --
  consistent keying from the start, avoiding the silent-miss bug that has
  plagued earlier sibling actors).

  A registered/verified stall record (market-authority registration +
  stall/market permit) must exist before ANY proposal targeting that
  stall may ever commit or escalate -- `stallops.governor`'s
  `stall-unverified-violations` re-derives this from the stall's own
  `:registered?`/`:verified?` fields, never from proposal self-report. A
  `:coordinate-supply-order` proposal additionally names a registered
  vendor via its own `:vendor-id`; the SAME 'ground truth, not self-
  report' discipline applies via `vendor-unverified-violations` -- a
  supply-chain-specific counterparty-verification gate this vertical
  shares with sibling 47xx retail actors.

  The ledger stays append-only: which stall a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (stall-record [s stall-id] "Registered market-stall record, or nil.
    Stall map: {:stall-id .. :name .. :registered? bool :verified? bool}.")
  (all-stall-records [s])
  (vendor-record [s vendor-id] "Registered vendor record, or nil.
    Vendor map: {:vendor-id .. :name .. :registered? bool :verified? bool}.")
  (all-vendor-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-stall-records [s stalls] "replace/seed the stall directory (map stall-id->stall)")
  (with-vendor-records [s vendors] "replace/seed the vendor directory (map vendor-id->vendor)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained stall/vendor directory covering both the happy
  path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:stalls
   {"stall-1" {:stall-id "stall-1" :name "Riverside Night Market Textile & Clothing Stall"
               :registered? true :verified? true}
    "stall-2" {:stall-id "stall-2" :name "Harborfront Flea Market Footwear Pitch"
               :registered? true :verified? true}
    "stall-3" {:stall-id "stall-3" :name "Pop-Up Weekend Market Clothing Stall (permit pending)"
               :registered? true :verified? false}}
   :vendors
   {"vendor-1" {:vendor-id "vendor-1" :name "Cascade Textile & Footwear Wholesale Co."
                :registered? true :verified? true}
    "vendor-2" {:vendor-id "vendor-2" :name "Unverified Import Clothing Broker Co."
                :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (stall-record [_ stall-id] (get-in @a [:stalls stall-id]))
  (all-stall-records [_] (sort-by :stall-id (vals (:stalls @a))))
  (vendor-record [_ vendor-id] (get-in @a [:vendors vendor-id]))
  (all-vendor-records [_] (sort-by :vendor-id (vals (:vendors @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-stall-records [s stalls] (when (seq stalls) (swap! a assoc :stalls stalls)) s)
  (with-vendor-records [s vendors] (when (seq vendors) (swap! a assoc :vendors vendors)) s))

(defn seed-db
  "A MemStore seeded with the demo stall/vendor directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with explicit `stalls`/`vendors` maps (stall-id/
  vendor-id string -> record map) -- the primary test/dev entry point.
  Either may be empty (an unregistered-everywhere stall)."
  ([stalls] (mem-store stalls {}))
  ([stalls vendors]
   (->MemStore (atom {:stalls (or stalls {}) :vendors (or vendors {})
                       :ledger [] :coordination-log []}))))
