(ns repairshop.repairopsllm
  "RepairOps-LLM client -- the *contained intelligence node* for the
  electronics-repair actor.

  It normalizes ticket intake, drafts a per-jurisdiction consumer-
  product-safety evidence checklist, screens tickets for a failed
  post-repair safety test, drafts the repair-completion action, and
  drafts the device-return action. CRITICAL: it is a smart-but-
  untrusted advisor. It returns a *proposal* (with a rationale + the
  fields it cited), never a committed record or a real repair
  completion/device return. Every output is censored downstream by
  `repairshop.governor` before anything touches the SSoT, and
  `:repair/complete`/`:device/return` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/complete-repair | :actuation/return-device | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [repairshop.facts :as facts]
            [repairshop.registry :as registry]
            [repairshop.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the customer, device/parts costs or jurisdiction.
  High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "修理票記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :ticket/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction consumer-product-safety evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `repairshop.facts` -- the Repair Shop Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [t (store/ticket db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction t))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "repairshop.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-safety
  "Post-repair safety-test screening draft. `:safety-test-passed?` on
  the ticket record injects the failure mode: the Repair Shop Governor
  must HOLD, un-overridably, on any failed test."
  [db {:keys [subject]}]
  (let [t (store/ticket db subject)]
    (cond
      (nil? t)
      {:summary "対象ticketが見つかりません" :rationale "no ticket record"
       :cites [] :effect :safety-screening/set :value {:ticket-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (false? (:safety-test-passed? t))
      {:summary    (str (:customer t) ": 修理後安全試験の不合格を検出")
       :rationale  "スクリーニングが不合格の安全試験結果を検出。人手確認とホールドが必須。"
       :cites      [:safety-check]
       :effect     :safety-screening/set
       :value      {:ticket-id subject :verdict :failed}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:customer t) ": 安全試験合格")
       :rationale  "安全試験スクリーニング完了。"
       :cites      [:safety-check]
       :effect     :safety-screening/set
       :value      {:ticket-id subject :verdict :passed}
       :stake      nil
       :confidence 0.9})))

(defn- propose-repair-completion
  "Draft the actual REPAIR-COMPLETION action -- completing a real
  repair. ALWAYS `:stake :actuation/complete-repair` -- this is a
  REAL-WORLD act (a real invoice is charged), never a draft the actor
  may auto-run. See README `Actuation`: no phase ever adds this op to
  a phase's `:auto` set (`repairshop.phase`); the governor also always
  escalates on `:actuation/complete-repair`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [t (store/ticket db subject)
        matches? (and t (registry/parts-cost-matches-claim? t))]
    {:summary    (str subject " 向け修理完了提案"
                      (when t (str " (customer=" (:customer t) ")")))
     :rationale  (if t
                   (str "claimed-parts-cost=" (:claimed-parts-cost t)
                        " independent-recompute=" (registry/compute-parts-cost t))
                   "ticketが見つかりません")
     :cites      (if t [subject] [])
     :effect     :ticket/mark-completed
     :value      {:ticket-id subject}
     :stake      :actuation/complete-repair
     :confidence (if matches? 0.9 0.3)}))

(defn- propose-device-return
  "Draft the actual DEVICE-RETURN action -- returning a real device to
  the customer. ALWAYS `:stake :actuation/return-device` -- this is a
  REAL-WORLD act (a potentially unsafe device could reach a customer),
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set (`repairshop.
  phase`); the governor also always escalates on `:actuation/return-
  device`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [t (store/ticket db subject)]
    {:summary    (str subject " 向け返却提案"
                      (when t (str " (customer=" (:customer t) ")")))
     :rationale  (if t
                   (str "safety-test-passed?=" (:safety-test-passed? t))
                   "ticketが見つかりません")
     :cites      (if t [subject] [])
     :effect     :ticket/mark-returned
     :value      {:ticket-id subject}
     :stake      :actuation/return-device
     :confidence (if (and t (:safety-test-passed? t)) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :ticket/intake           (normalize-intake db request)
    :jurisdiction/assess        (assess-jurisdiction db request)
    :safety/screen                 (screen-safety db request)
    :repair/complete                  (propose-repair-completion db request)
    :device/return                       (propose-device-return db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは電化製品修理店の修理完了・返却エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:ticket/upsert|:assessment/set|:safety-screening/set|"
       ":ticket/mark-completed|:ticket/mark-returned) "
       ":stake(:actuation/complete-repair か :actuation/return-device か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess  {:ticket (store/ticket st subject)}
    :safety/screen        {:ticket (store/ticket st subject)}
    :repair/complete      {:ticket (store/ticket st subject)}
    :device/return        {:ticket (store/ticket st subject)}
    {:ticket (store/ticket st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Repair Shop Governor
  escalates/holds -- an LLM hiccup can never auto-complete a repair or
  auto-return a device."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :repairopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
