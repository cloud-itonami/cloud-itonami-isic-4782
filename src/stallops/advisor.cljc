(ns stallops.advisor
  "StallMarketAdvisor -- the *contained intelligence node* for the
  ISIC-4782 'Retail sale via stalls and markets of textiles, clothing
  and footwear' operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: inventory/sale data logging, stall placement/staffing
  scheduling, textile/clothing/footwear supply-order coordination, and
  quality-concern flagging (a suspected-counterfeit item, a
  defective-goods item, or a stall/market-permit concern -- e.g. an
  expired, unverifiable or suspicious market permit). CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a rationale
  + the fields it cited), never a committed record and NEVER a direct
  actuation -- every proposal's `:effect` is always `:propose`. Every
  output is censored downstream by `stallops.governor` before anything
  touches the SSoT.

  This advisor NEVER drafts a unit-price decision, a direct
  quality-dispute-resolution-finalization action (issuing a refund or
  replacement, voiding a sale, charging back a vendor, revoking or
  terminating a vendor's registration/contract, or otherwise declaring a
  quality dispute resolved), a direct counterfeit-authenticity-
  determination action (declaring an item counterfeit, certifying an
  item as genuine, or otherwise finalizing an authenticity
  determination), or any other quality-dispute-resolution or
  counterfeit-authenticity-resolution authority action -- those are
  permanently out of scope for this actor, not merely un-implemented.
  `stallops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised or
  confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :stall-id   str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-sales-record
  "Draft an inventory/sale transaction log entry. Pure logging of
  observed transactions (units sold, stock-count deltas at the stall) --
  never a unit-price decision."
  [_db {:keys [stall-id patch]}]
  {:op         :log-sales-record
   :stall-id   stall-id
   :summary    (str stall-id " の販売/在庫記録を記録: " (pr-str (keys patch)))
   :rationale  "屋台/マーケット出店における販売数量・在庫カウントの観察記録のみ。値付けや品質紛争・真贋判定は含まない。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.93})

(defn- propose-stall-operation
  "Draft a stall placement/staffing scheduling proposal (a pitch
  assignment/roster entry, never a direct enforcement action)."
  [_db {:keys [stall-id patch]}]
  {:op         :schedule-stall-operation
   :stall-id   stall-id
   :summary    (str stall-id " の出店場所/人員配置予定を提案: " (pr-str (keys patch)))
   :rationale  "マーケット内の出店区画割当・スタッフ配置調整提案のみ。最終配置は人間が確定する。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.88})

(defn- propose-supply-order
  "Draft a textile/clothing/footwear procurement coordination request
  naming a registered vendor (manufacturer/wholesaler/distributor) --
  never a finalized purchase order; a human always confirms procurement."
  [_db {:keys [stall-id patch]}]
  {:op         :coordinate-supply-order
   :stall-id   stall-id
   :summary    (str stall-id " 向け繊維/衣料/靴の在庫調達を提案: " (pr-str (keys patch)))
   :rationale  "繊維製品・衣料品・靴の在庫調達コーディネート提案のみ。確定発注は人間が行う。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.90})

(defn- propose-quality-concern
  "Surface an observed quality concern (a suspected-counterfeit item, a
  defective-goods item, or a stall/market-permit concern such as an
  expired, unverifiable or suspicious permit) for HUMAN triage. This op
  ALWAYS escalates in `stallops.governor` -- never auto-committed at any
  phase -- regardless of how confident the advisor is that the concern is
  real. Deliberately reports the OBSERVATION only, never a
  finalization/resolution/determination/enforcement action, so the
  default rationale never trips the governor's `scope-excluded-terms`
  (see that var's docstring)."
  [_db {:keys [stall-id patch]}]
  {:op         :flag-quality-concern
   :stall-id   stall-id
   :summary    (str stall-id " の品質懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "縫製・素材不良、真正性への疑念(模倣品の疑いを含む)、出店許可証(マーケット permit)の有効期限切れや真偽不明等の観察事実の報告。常に人間の確認・対応が必要。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-sales-record (propose-sales-record _db request)
                   :schedule-stall-operation (propose-stall-operation _db request)
                   :coordinate-supply-order (propose-supply-order _db request)
                   :flag-quality-concern (propose-quality-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually issued the refund and declared the item counterfeit to resolve the dispute")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :stall-id (:stall-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
