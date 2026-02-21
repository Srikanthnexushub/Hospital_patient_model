package com.ainexus.hospital.patient.intelligence;

import com.ainexus.hospital.patient.dto.DrugInteractionDto;
import com.ainexus.hospital.patient.entity.InteractionSeverity;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Static curated drug-interaction knowledge base.
 *
 * <p>Contains ≥40 clinically significant drug-drug interaction pairs covering
 * anticoagulants, cardiac drugs, CNS drugs, diabetes medications, antibiotics,
 * respiratory medications, NSAIDs, and common OTC risks.
 *
 * <p>All lookups are O(1) — drugs are indexed bidirectionally under their normalised
 * (lowercase, trimmed) name. Partial-name and prefix matching is NOT performed here;
 * callers should normalise drug names before querying.
 */
@Component
public class DrugInteractionDatabase {

    /**
     * Key = normalised pair key (sorted: "drug_a|drug_b"), Value = interaction details.
     * Bidirectional index maintained by {@link #add}.
     */
    private final Map<String, DrugInteractionDto> interactionMap = new HashMap<>();

    public DrugInteractionDatabase() {
        // -------------------------------------------------------------------
        // ANTICOAGULANTS
        // -------------------------------------------------------------------
        add("warfarin", "ibuprofen", InteractionSeverity.MAJOR,
                "NSAID inhibits platelet aggregation and increases gastric bleeding risk; warfarin potentiated",
                "Significantly increased risk of serious bleeding",
                "Avoid combination; use paracetamol (acetaminophen) for analgesia if possible");

        add("warfarin", "naproxen", InteractionSeverity.MAJOR,
                "NSAID inhibits platelet aggregation; warfarin anticoagulant effect potentiated",
                "Increased risk of GI and intracranial bleeding",
                "Avoid combination; monitor INR closely if unavoidable");

        add("warfarin", "aspirin", InteractionSeverity.MAJOR,
                "Aspirin inhibits platelet aggregation and displaces warfarin from plasma proteins",
                "Significantly increased bleeding risk",
                "Use low-dose aspirin only when benefit clearly outweighs risk; monitor INR");

        add("warfarin", "clopidogrel", InteractionSeverity.MAJOR,
                "Dual antiplatelet + anticoagulant combination",
                "Very high risk of major bleeding events",
                "Triple therapy (warfarin + aspirin + clopidogrel) requires specialist oversight");

        add("warfarin", "amiodarone", InteractionSeverity.MAJOR,
                "Amiodarone inhibits CYP2C9 and CYP3A4, substantially increasing warfarin exposure",
                "INR can double or triple within days; severe bleeding risk",
                "Reduce warfarin dose by 30-50% and monitor INR twice weekly when starting amiodarone");

        add("warfarin", "fluconazole", InteractionSeverity.MAJOR,
                "Fluconazole strongly inhibits CYP2C9 metabolism of warfarin",
                "INR markedly elevated; major bleeding risk",
                "Reduce warfarin dose; monitor INR closely during and after course");

        add("warfarin", "metronidazole", InteractionSeverity.MAJOR,
                "Metronidazole inhibits CYP2C9, reducing warfarin clearance",
                "INR elevation and bleeding risk",
                "Monitor INR during metronidazole course; consider dose reduction");

        // -------------------------------------------------------------------
        // CARDIAC DRUGS
        // -------------------------------------------------------------------
        add("digoxin", "amiodarone", InteractionSeverity.MAJOR,
                "Amiodarone inhibits P-glycoprotein and reduces renal clearance of digoxin",
                "Digoxin toxicity: bradycardia, heart block, nausea, visual disturbances",
                "Reduce digoxin dose by 50%; monitor serum digoxin levels and ECG");

        add("digoxin", "verapamil", InteractionSeverity.MAJOR,
                "Verapamil inhibits P-glycoprotein-mediated elimination of digoxin",
                "Digoxin toxicity: bradycardia, AV block",
                "Reduce digoxin dose; monitor serum levels and heart rate");

        add("digoxin", "spironolactone", InteractionSeverity.MODERATE,
                "Spironolactone may alter digoxin renal clearance and interfere with assay",
                "Risk of digoxin toxicity; spuriously elevated digoxin levels in some assays",
                "Monitor digoxin levels using assay unaffected by spironolactone");

        add("lisinopril", "spironolactone", InteractionSeverity.MAJOR,
                "Both drugs reduce potassium excretion by different mechanisms",
                "Severe hyperkalaemia, potentially fatal cardiac arrhythmias",
                "Avoid unless heart failure protocol with careful K+ monitoring; start low dose");

        add("ramipril", "spironolactone", InteractionSeverity.MAJOR,
                "ACE inhibitor + K-sparing diuretic → additive hyperkalaemia",
                "Life-threatening hyperkalaemia",
                "Monitor K+ closely; avoid combination unless clinically necessary");

        add("enalapril", "potassium", InteractionSeverity.MAJOR,
                "ACE inhibitor reduces aldosterone, increasing K+ retention",
                "Hyperkalaemia risk, especially with K+ supplements",
                "Monitor serum K+; avoid routine K+ supplementation");

        add("atenolol", "verapamil", InteractionSeverity.MAJOR,
                "Additive negative chronotropic and dromotropic effects",
                "Severe bradycardia, AV block, or asystole",
                "Avoid combination; if necessary, use with telemetry monitoring");

        add("amlodipine", "simvastatin", InteractionSeverity.MODERATE,
                "Amlodipine inhibits CYP3A4, increasing simvastatin exposure",
                "Increased risk of myopathy and rhabdomyolysis",
                "Do not exceed simvastatin 20mg daily; consider alternative statin");

        // -------------------------------------------------------------------
        // CNS / PSYCHIATRY
        // -------------------------------------------------------------------
        add("ssri", "maoi", InteractionSeverity.CONTRAINDICATED,
                "Both drugs increase serotonergic neurotransmission by different mechanisms",
                "Serotonin syndrome: hyperthermia, rigidity, myoclonus, autonomic instability",
                "Contraindicated: allow 14-day washout after stopping MAOI before starting SSRI");

        add("fluoxetine", "phenelzine", InteractionSeverity.CONTRAINDICATED,
                "Fluoxetine (SSRI) + phenelzine (MAOI) → serotonin syndrome",
                "Life-threatening serotonin syndrome",
                "Contraindicated; 5-week washout after fluoxetine due to long half-life");

        add("sertraline", "tramadol", InteractionSeverity.MAJOR,
                "Sertraline (SSRI) reduces CYP2D6 metabolism of tramadol; additive serotonergic effect",
                "Serotonin syndrome; seizures",
                "Avoid combination or use lowest effective doses with close monitoring");

        add("fluoxetine", "tramadol", InteractionSeverity.MAJOR,
                "Fluoxetine inhibits CYP2D6, reducing tramadol conversion to active metabolite and increasing parent drug",
                "Serotonin syndrome risk; paradoxical reduced analgesia",
                "Avoid; use alternative analgesic");

        add("lithium", "ibuprofen", InteractionSeverity.MAJOR,
                "NSAIDs reduce renal clearance of lithium",
                "Lithium toxicity: tremor, confusion, renal damage",
                "Avoid NSAIDs with lithium; use paracetamol (acetaminophen) instead");

        add("lithium", "naproxen", InteractionSeverity.MAJOR,
                "NSAID reduces renal prostaglandin synthesis, decreasing lithium excretion",
                "Lithium toxicity",
                "Avoid; monitor lithium levels if NSAID unavoidable");

        add("clozapine", "ciprofloxacin", InteractionSeverity.MAJOR,
                "Ciprofloxacin inhibits CYP1A2, the primary metabolic pathway for clozapine",
                "Clozapine toxicity: sedation, seizures, agranulocytosis risk",
                "Avoid or reduce clozapine dose by 50%; monitor closely");

        // -------------------------------------------------------------------
        // DIABETES
        // -------------------------------------------------------------------
        add("metformin", "contrast", InteractionSeverity.MAJOR,
                "Iodinated contrast media cause transient renal impairment, reducing metformin clearance",
                "Lactic acidosis — potentially fatal",
                "Withhold metformin 48h before and after IV contrast; ensure renal function normal before restarting");

        add("metformin", "alcohol", InteractionSeverity.MAJOR,
                "Alcohol potentiates metformin inhibition of hepatic gluconeogenesis",
                "Increased risk of lactic acidosis",
                "Avoid excessive alcohol use with metformin");

        add("glibenclamide", "fluconazole", InteractionSeverity.MAJOR,
                "Fluconazole inhibits CYP2C9 metabolism of glibenclamide (glyburide)",
                "Severe prolonged hypoglycaemia",
                "Avoid combination; monitor blood glucose closely if unavoidable");

        // -------------------------------------------------------------------
        // ANTIBIOTICS
        // -------------------------------------------------------------------
        add("ciprofloxacin", "antacids", InteractionSeverity.MODERATE,
                "Divalent cations (Al, Mg, Ca) chelate ciprofloxacin in gut lumen",
                "Reduced ciprofloxacin absorption by up to 85%; treatment failure",
                "Separate administration by at least 2 hours (ciprofloxacin first)");

        add("ciprofloxacin", "theophylline", InteractionSeverity.MAJOR,
                "Ciprofloxacin inhibits CYP1A2, substantially increasing theophylline levels",
                "Theophylline toxicity: tachycardia, seizures, arrhythmias",
                "Reduce theophylline dose by 50%; monitor serum theophylline levels");

        add("metronidazole", "alcohol", InteractionSeverity.MAJOR,
                "Metronidazole inhibits aldehyde dehydrogenase (disulfiram-like reaction)",
                "Flushing, tachycardia, nausea, vomiting (disulfiram reaction)",
                "Avoid alcohol during treatment and 48h after completion");

        add("trimethoprim", "methotrexate", InteractionSeverity.MAJOR,
                "Additive antifolate effect; trimethoprim inhibits dihydrofolate reductase",
                "Severe myelosuppression, megaloblastic anaemia",
                "Avoid combination or use with folinic acid supplementation under specialist guidance");

        add("doxycycline", "antacids", InteractionSeverity.MODERATE,
                "Divalent cations chelate tetracyclines in gut",
                "Reduced absorption of doxycycline; treatment failure",
                "Take doxycycline 2 hours before or 6 hours after antacids");

        add("rifampicin", "warfarin", InteractionSeverity.MAJOR,
                "Rifampicin is a potent CYP inducer; dramatically increases warfarin metabolism",
                "Markedly reduced anticoagulant effect; thrombosis risk",
                "Monitor INR very frequently; may need to double or triple warfarin dose");

        add("rifampicin", "oral contraceptive", InteractionSeverity.MAJOR,
                "Rifampicin induces CYP3A4 and UGT enzymes, reducing oestrogen and progestogen levels",
                "Contraceptive failure; unintended pregnancy",
                "Use additional non-hormonal contraception during and 4 weeks after rifampicin");

        // -------------------------------------------------------------------
        // RESPIRATORY
        // -------------------------------------------------------------------
        add("theophylline", "ciprofloxacin", InteractionSeverity.MAJOR,
                "Ciprofloxacin inhibits CYP1A2 — the primary metabolic pathway for theophylline",
                "Theophylline toxicity: tachycardia, seizures, hypokalaemia",
                "Reduce theophylline dose by 50% when starting ciprofloxacin; monitor levels");

        add("theophylline", "erythromycin", InteractionSeverity.MAJOR,
                "Erythromycin inhibits CYP3A4 and CYP1A2, increasing theophylline levels",
                "Theophylline toxicity",
                "Use alternative antibiotic if possible; monitor levels closely");

        // -------------------------------------------------------------------
        // NSAIDs + ACE INHIBITORS / ARBs
        // -------------------------------------------------------------------
        add("ibuprofen", "lisinopril", InteractionSeverity.MODERATE,
                "NSAIDs reduce renal prostaglandin synthesis; impair ACE inhibitor renal effects",
                "Reduced antihypertensive effect; risk of acute kidney injury",
                "Avoid regular NSAID use; monitor renal function and blood pressure");

        add("ibuprofen", "ramipril", InteractionSeverity.MODERATE,
                "NSAID reduces ACE inhibitor efficacy and increases renal injury risk",
                "Blood pressure elevation; acute kidney injury in susceptible patients",
                "Use paracetamol instead; monitor renal function if unavoidable");

        add("naproxen", "lisinopril", InteractionSeverity.MODERATE,
                "Same mechanism as ibuprofen/ACE inhibitor interaction",
                "Reduced antihypertensive efficacy; renal impairment",
                "Avoid; prefer alternative analgesic");

        // -------------------------------------------------------------------
        // ANTIPLATELET / ANTICOAGULANT + NSAID
        // -------------------------------------------------------------------
        add("aspirin", "methotrexate", InteractionSeverity.MAJOR,
                "Aspirin (NSAID) reduces renal tubular secretion of methotrexate",
                "Methotrexate toxicity: severe myelosuppression, mucositis",
                "Avoid combination; if necessary, use with leucovorin rescue and frequent monitoring");

        add("clopidogrel", "omeprazole", InteractionSeverity.MODERATE,
                "Omeprazole inhibits CYP2C19, reducing conversion of clopidogrel to active metabolite",
                "Reduced antiplatelet effect; possible increased cardiovascular events",
                "Use pantoprazole (lower CYP2C19 inhibition) as alternative PPI");

        // -------------------------------------------------------------------
        // ADDITIONAL CLINICALLY SIGNIFICANT PAIRS
        // -------------------------------------------------------------------
        add("simvastatin", "erythromycin", InteractionSeverity.MAJOR,
                "Erythromycin inhibits CYP3A4-mediated statin metabolism",
                "Severe myopathy and rhabdomyolysis",
                "Withhold simvastatin during course of erythromycin; use azithromycin instead");

        add("sildenafil", "nitrate", InteractionSeverity.CONTRAINDICATED,
                "Both drugs lower blood pressure via different mechanisms (cGMP pathway)",
                "Life-threatening hypotension",
                "Contraindicated; do not use together");

        add("ssri", "tramadol", InteractionSeverity.MAJOR,
                "Additive serotonergic effect; SSRI inhibits CYP2D6 metabolism of tramadol",
                "Serotonin syndrome; seizures",
                "Avoid; use non-serotonergic analgesic");

        add("tacrolimus", "fluconazole", InteractionSeverity.MAJOR,
                "Fluconazole inhibits CYP3A4 and CYP2C19; tacrolimus levels increase greatly",
                "Tacrolimus toxicity: nephrotoxicity, neurotoxicity, QT prolongation",
                "Reduce tacrolimus dose by 50%; monitor levels closely");
    }

    /**
     * Returns all interactions involving the specified drug (normalised to lowercase).
     *
     * @param drugName must already be normalised (lowercase, trimmed) by the caller
     */
    public List<DrugInteractionDto> findInteractionsFor(String drugName) {
        String key = normalise(drugName);
        return interactionMap.entrySet().stream()
                .filter(e -> e.getKey().startsWith(key + "|") || e.getKey().endsWith("|" + key))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Returns the interaction between exactly two drugs, if one exists.
     */
    public Optional<DrugInteractionDto> findInteraction(String drug1, String drug2) {
        String key = pairKey(normalise(drug1), normalise(drug2));
        return Optional.ofNullable(interactionMap.get(key));
    }

    /**
     * Returns the total count of entries in the database (useful for health-checks / tests).
     */
    public int size() {
        return interactionMap.size();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void add(String drug1, String drug2, InteractionSeverity severity,
                     String mechanism, String clinicalEffect, String recommendation) {
        DrugInteractionDto dto = new DrugInteractionDto(
                drug1, drug2, severity, mechanism, clinicalEffect, recommendation);
        interactionMap.put(pairKey(drug1, drug2), dto);
    }

    /** Canonical key: always drug with lower lexicographic order first. */
    private String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private String normalise(String name) {
        return name == null ? "" : name.strip().toLowerCase();
    }
}
