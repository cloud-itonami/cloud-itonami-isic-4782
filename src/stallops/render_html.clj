(ns stallops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave2 flagship item2): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`stallops.operation` -> `stallops.governor` -> `stallops.store`)
  through a scenario adapted from this repo's own `stallops.sim` demo
  driver (`clojure -M:dev:run`, confirmed BEFORE writing this file to
  produce a sensible ledger against the real seeded stall ids
  `stall-1`..`stall-3` and vendor ids `vendor-1`..`vendor-2` -- ids that
  DO match `stallops.store/demo-data`, so it was safe to reuse rather
  than author from scratch), trimmed to a representative subset (clean
  auto-commits at phase 3, always-escalate ops that a human then
  approves, and five distinct HARD-hold reasons including
  stall-unverified / vendor-unverified / effect-not-propose /
  scope-excluded) and rendered deterministically -- no invented numbers,
  no timestamps in the page content, byte-identical across reruns
  against the same seed (verify by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [stallops.advisor :as advisor]
            [stallops.store :as store]
            [stallops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private coordinator
  {:actor-id "coord-1" :actor-role :market-stall-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context coordinator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "market-stall-coordinator-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: stall-1 clears a clean sales-record log (phase-3
  auto-commit), a stall-operation schedule (auto-commit), a low-cost
  supply-order coordination naming verified vendor-1 (auto-commit), a
  high-cost supply-order (ALWAYS escalates -- approved), and a
  quality-concern flag (ALWAYS escalates -- approved); stall-99
  HARD-holds as unregistered; stall-3 HARD-holds as
  registered-but-unverified; a supply-order naming unverified vendor-2
  HARD-holds on vendor-unverified; a stall-operation proposal whose
  advisor injects `:effect :commit` HARD-holds on effect-not-propose; a
  sales-record proposal that drifts into quality-dispute-resolution /
  counterfeit-authenticity-determination finalization scope HARD-holds
  permanently. Every HARD hold never reaches a human. Returns the
  resulting store -- every field read by `render` below is real
  governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1-sales" {:op :log-sales-record :stall-id "stall-1"
                             :patch {:units-sold 22 :stock-count-delta -22}})

    (exec! actor "t2-schedule" {:op :schedule-stall-operation :stall-id "stall-1"
                                :patch {:pitch "riverside-night-market-row-b"
                                        :date "2026-07-20"
                                        :window "16:00-22:00"}})

    (exec! actor "t3-supply-low" {:op :coordinate-supply-order :stall-id "stall-1"
                                  :patch {:item "seasonal scarf & footwear restock"
                                          :quantity 40
                                          :estimated-cost 320.0
                                          :vendor-id "vendor-1"}})

    (exec! actor "t4-supply-high" {:op :coordinate-supply-order :stall-id "stall-1"
                                   :patch {:item "bulk woven-textile bolt order"
                                           :quantity 25
                                           :estimated-cost 1800.0
                                           :vendor-id "vendor-1"}})
    (approve! actor "t4-supply-high")

    (exec! actor "t5-flag" {:op :flag-quality-concern :stall-id "stall-1"
                            :patch {:concern "footwear batch #4782 has torn stitching and the fabric-content label may not match, and the goods may be a suspected-counterfeit of a branded original"
                                    :confidence 0.9}})
    (approve! actor "t5-flag")

    (exec! actor "t6-unregistered" {:op :log-sales-record :stall-id "stall-99"
                                    :patch {:units-sold 0}})

    (exec! actor "t7-unverified" {:op :log-sales-record :stall-id "stall-3"
                                  :patch {:units-sold 5}})

    (exec! actor "t8-vendor" {:op :coordinate-supply-order :stall-id "stall-1"
                              :patch {:item "imported clothing lot"
                                      :quantity 10
                                      :estimated-cost 200.0
                                      :vendor-id "vendor-2"}})

    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "t9-effect" {:op :schedule-stall-operation :stall-id "stall-1"
                                       :patch {:pitch "riverside-night-market-row-c"
                                               :date "2026-07-22"}}))

    (exec! actor "t10-scope" {:op :log-sales-record :stall-id "stall-1"
                              :out-of-scope? true
                              :patch {}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger stall-id]
  (last (filter #(= (:stall-id %) stall-id) ledger)))

(defn- status-cell [ledger stall-id]
  (let [f (last-fact-for ledger stall-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (or (some-> f :violations first :rule)
                     (some-> f :basis first))]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- verified-cell [{:keys [registered? verified?]}]
  (cond
    (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
    registered? "<span class=\"warn\">registered, verification pending</span>"
    :else "<span class=\"critical\">unregistered</span>"))

(defn- stall-row [ledger {:keys [stall-id] :as s}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc stall-id) (esc (:name s))
          (verified-cell s)
          (status-cell ledger stall-id)))

(defn- vendor-row [{:keys [vendor-id] :as v}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc vendor-id) (esc (:name v))
          (verified-cell v)))

(defn- ledger-row [{:keys [t op stall-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc stall-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(defn- coordination-row [{:keys [op stall-id value]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name (or op :n-a))) (esc stall-id)
          (esc (pr-str (or value {})))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README Ops / Features, `stallops.governor`/`stallops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-sales-record</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &amp; high confidence (inventory/sale observation logging only)</span></td></tr>"
   "        <tr><td><code>:schedule-stall-operation</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &amp; high confidence (pitch/staffing schedule draft)</span></td></tr>"
   "        <tr><td><code>:coordinate-supply-order</code></td><td><span class=\"warn\">phase-3 auto when low cost + verified vendor; ALWAYS human approval when estimated-cost &gt; threshold; HARD hold on unverified vendor</span></td></tr>"
   "        <tr><td><code>:flag-quality-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase &middot; surfaces concern only, never finalizes quality dispute or counterfeit-authenticity determination</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        stalls (store/all-stall-records db)
        vendors (store/all-vendor-records db)
        stall-rows (str/join "\n" (map (partial stall-row ledger) stalls))
        vendor-rows (str/join "\n" (map vendor-row vendors))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        coord-rows (str/join "\n" (map coordination-row (store/coordination-log db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4782 &middot; market-stall-textile-clothing-footwear</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Retail sale via stalls and markets of textiles, clothing and footwear (ISIC 4782) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · quality concern / high-cost supply always human-approved · HARD holds permanent</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Market stalls</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>stallops.store</code> via <code>stallops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated from the real actor stack.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Stall</th><th>Name</th><th>Registration / verification</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     stall-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Registered supply-order vendors</h2>\n"
     "    <p class=\"muted\">A <code>:coordinate-supply-order</code> proposal must name a registered &amp; verified textile/clothing/footwear vendor; unverified import brokers are HARD-held (ground truth, not self-report).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Vendor</th><th>Name</th><th>Registration / verification</th></tr></thead>\n"
     "      <tbody>\n"
     vendor-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Stall Market Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden: stall unverified (market-authority registration + stall/market permit), vendor unverified (supply-order counterparty), effect not <code>:propose</code>, and permanently out-of-scope quality-dispute-resolution / counterfeit-authenticity-determination finalization territory. An unregistered or unverified stall, or an unverified vendor, never reaches a human.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination log (this run)</h2>\n"
     "    <p class=\"muted\">Records written only after governor-clean commit (auto or human-approved).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Stall</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     coord-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Stall</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        parent (.getParentFile (java.io.File. out))]
    (when parent (.mkdirs parent))
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "coordination commits )")))
