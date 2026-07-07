(ns repairshop.facts
  "Per-jurisdiction consumer-product-safety regulatory catalog -- the
  G2-style spec-basis table the Repair Shop Governor checks every
  jurisdiction/assess proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's consumer-product-
  safety requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official product-
  safety regulator (see `:provenance`); they are a STARTING catalog,
  not a from-scratch survey of all ~194 jurisdictions. Extending
  coverage is additive: add one map to `catalog`, cite a real source,
  done -- never invent a jurisdiction's requirements to make coverage
  look bigger.

  Like `clinic.facts`'s/`veterinary.facts`'s federated jurisdictions,
  the DEU entry here cites the state-level market-surveillance
  authorities (administering the federal ProdSG) rather than a single
  centralized body -- an honest representative citation, not a state-
  by-state survey, the same simplification every prior catalog makes
  when a jurisdiction's real regulatory structure is itself
  federated.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  diagnostic-report/parts-used-documentation/repair-technician-
  certification/post-repair-safety-test-record evidence set submitted
  in some form; `:legal-basis` / `:owner-authority` / `:provenance`
  are the G2 citation the governor requires before any :jurisdiction/
  assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 (Ministry of Economy, Trade and Industry, METI)"
          :legal-basis "消費生活用製品安全法 (Consumer Product Safety Act)"
          :national-spec "PSCマーク制度・長期使用製品安全点検制度"
          :provenance "https://www.meti.go.jp/"
          :required-evidence ["故障診断書 (diagnostic report)"
                              "使用部品記録 (parts-used documentation)"
                              "修理技術者資格確認記録 (repair-technician certification)"
                              "修理後安全試験記録 (post-repair safety-test record)"]}
   "USA" {:name "United States"
          :owner-authority "U.S. Consumer Product Safety Commission (CPSC)"
          :legal-basis "Consumer Product Safety Act (15 U.S.C. §§2051 et seq.)"
          :national-spec "CPSC product-safety standards and recall requirements"
          :provenance "https://www.cpsc.gov/"
          :required-evidence ["Diagnostic report"
                              "Parts-used documentation"
                              "Repair-technician certification"
                              "Post-repair safety-test record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Office for Product Safety and Standards (OPSS)"
          :legal-basis "General Product Safety Regulations 2005"
          :national-spec "OPSS product-safety enforcement standards"
          :provenance "https://www.gov.uk/government/organisations/office-for-product-safety-and-standards"
          :required-evidence ["Diagnostic report"
                              "Parts-used documentation"
                              "Repair-technician certification"
                              "Post-repair safety-test record"]}
   "DEU" {:name "Germany"
          :owner-authority "Marktüberwachungsbehörden der Länder (state market-surveillance authorities)"
          :legal-basis "Produktsicherheitsgesetz (ProdSG, Product Safety Act)"
          :national-spec "ProdSG Marktüberwachungsanforderungen"
          :provenance "https://www.baua.de/DE/Themen/Anwendungssichere-Produkte-und-Anlagen/Produktsicherheit/Produktsicherheit_node.html"
          :required-evidence ["Diagnosebericht (diagnostic report)"
                              "Ersatzteilnachweis (parts-used documentation)"
                              "Reparaturtechniker-Qualifikationsnachweis (repair-technician certification)"
                              "Sicherheitsprüfungsprotokoll nach Reparatur (post-repair safety-test record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to complete a
  repair or return a device on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9521 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `repairshop.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
