package org.cbioportal.service.impl;

import org.cbioportal.model.*;
import org.cbioportal.persistence.TreatmentRepository;
import org.cbioportal.service.SampleService;
import org.cbioportal.service.TreatmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Service
public class TreatmentServiceImpl implements TreatmentService {
    @Autowired
    TreatmentRepository treatmentRepository;
    
    @Override
    public List<SampleTreatmentRow> getAllSampleTreatmentRows(List<String> sampleIds, List<String> studyIds) {
        Map<String, List<DatedSample>> samplesByPatient = treatmentRepository.getSamplesByPatient(sampleIds, studyIds);
        Map<String, List<Treatment>> treatmentsByPatient = treatmentRepository.getTreatmentsByPatient(sampleIds, studyIds);

        Stream<SampleTreatmentRow> rows = samplesByPatient.keySet().stream()
            .flatMap(patientId -> getSampleTreatmentRowsForPatient(patientId, samplesByPatient, treatmentsByPatient))
            .filter(row -> row.getCount() != 0);
        return flattenAndSortRows(rows);
    }
    
    private Stream<SampleTreatmentRow> getSampleTreatmentRowsForPatient(
            String patientId,
            Map<String, List<DatedSample>> samplesByPatient,
            Map<String, List<Treatment>> treatmentsByPatient
    ) {
        List<Treatment> treatments = treatmentsByPatient.getOrDefault(patientId, new ArrayList<>());
        List<DatedSample> samples = samplesByPatient.get(patientId);

        Map<String, TreatmentRowTriplet> rows = new HashMap<>();

        for (Treatment treatment : treatments) {
            TreatmentRowTriplet triplet;
            
            if (!rows.containsKey(treatment.getTreatment())) {
                triplet = new TreatmentRowTriplet(samples, treatment.getTreatment());
                rows.put(treatment.getTreatment(), triplet);
            } else {
                triplet = rows.get(treatment.getTreatment());
            }
            
            triplet.moveSamplesToPost(treatment);
        }

        return rows.values().stream().flatMap(TreatmentRowTriplet::toRows);
    }

    private List<SampleTreatmentRow> flattenAndSortRows(Stream<SampleTreatmentRow> rows) {
        Map<String, SampleTreatmentRow> uniqueRows = new HashMap<>();
        rows.forEach(rowToAdd -> {
            if (uniqueRows.containsKey(rowToAdd.getTreatment() + rowToAdd.getTime().name())) {
                uniqueRows.get(rowToAdd.calculateKey()).add(rowToAdd);
            } else {
                uniqueRows.put(rowToAdd.getTreatment() + rowToAdd.getTime().name(), rowToAdd);
            }
        });

        List<SampleTreatmentRow> flattenedRows = new ArrayList<>(uniqueRows.values());
        flattenedRows.sort(Comparator.comparing(a -> (a.getTreatment() + a.getTime().name())));
        return flattenedRows;
    }


    /**
     * For a given treatment, you can have samples that are taken
     * before (pre), after (post), or that don't have a date (unknown)
     * 
     * This class accepts an initial list of samples and a treatment.
     * At the start, all samples are considered pre, as there hasn't been
     * any treatment start / stop times.
     * 
     * You then call moveSamplesToPost on this with a series of matching
     * treatments. Each call will move samples taken 
     */
    private static class TreatmentRowTriplet {
        private final List<DatedSample> pre, post, unknown;
        private final String treatment;
        private final Set<String> studyIds;

        TreatmentRowTriplet(List<DatedSample> samples, String treatment) {
            this.treatment = treatment;
            post = new ArrayList<>();
            pre = samples.stream()
                .filter(s -> s.getTimeTaken() != null)
                .collect(toList());
            unknown = samples.stream()
                .filter(s -> s.getTimeTaken() == null)
                .collect(toList());
            studyIds = samples.stream()
                .map(DatedSample::getStudyId)
                .collect(Collectors.toSet());
        }

        /**
         * Moves any samples marked as pre that were taken after the treatment
         * started to post.
         * 
         * @param treatment a treatment with a start value. It is assumed that
         *                  the treatment matches the treatment stored in this triplet
         */
        void moveSamplesToPost(Treatment treatment) {
            for (Iterator<DatedSample> iterator = pre.iterator(); iterator.hasNext(); ) {
                DatedSample datedSample = iterator.next();
                // edge case: is a sample taken the same day a treatment starts pre or post?
                // We're saying pre here
                if (datedSample.getTimeTaken() > treatment.getStart()) {
                    iterator.remove();
                    post.add(datedSample);
                }
            }
        }
        
        Stream<SampleTreatmentRow> toRows() {
            return Stream.of(
                    new SampleTreatmentRow(TemporalRelation.Pre, treatment, pre.size(), toStrings(pre), studyIds),
                    new SampleTreatmentRow(TemporalRelation.Post, treatment, post.size(), toStrings(post), studyIds),
                    new SampleTreatmentRow(TemporalRelation.Unknown, treatment, unknown.size(), toStrings(unknown), studyIds)
            );
        }
        
        private Set<String> toStrings(List<DatedSample> samples) {
            return samples.stream()
                    .map(DatedSample::getSampleId)
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public List<PatientTreatmentRow> getAllPatientTreatmentRows(List<String> sampleIds, List<String> studyIds) {
        Map<String, List<Treatment>> treatmentsByPatient = treatmentRepository.getTreatmentsByPatient(sampleIds, studyIds);
        Map<String, List<DatedSample>> samplesByPatient = treatmentRepository.getSamplesByPatient(sampleIds, studyIds);
        Set<String> treatments = treatmentRepository.getAllUniqueTreatments(sampleIds, studyIds);

        return treatments.stream()
            .map(t -> createPatientTreatmentRowForTreatment(t, treatmentsByPatient, samplesByPatient))
            .collect(Collectors.toList());
    }

    private PatientTreatmentRow createPatientTreatmentRowForTreatment(
        String treatment,
        Map<String, List<Treatment>> treatmentsByPatient,
        Map<String, List<DatedSample>> samplesByPatient
    ) {
        // find all the patients that have received this treatments
        List<Map.Entry<String, List<Treatment>>> matchingPatients = matchingPatients(treatment, treatmentsByPatient);

        // from those patients, extract the unique study ids
        Set<String> studies = matchingPatients
            .stream()
            .flatMap(entry -> entry.getValue().stream().map(Treatment::getStudyId))
            .collect(Collectors.toSet());

        // from those patients, extract the unique sample ids
        Set<String> samples = matchingPatients
            .stream()
            .map(Map.Entry::getKey)
            .flatMap(patient -> samplesByPatient.getOrDefault(patient, new ArrayList<>()).stream())
            .map(DatedSample::getSampleId)
            .collect(Collectors.toSet());


        return new PatientTreatmentRow(treatment, matchingPatients.size(), samples, studies);
    }

    private List<Map.Entry<String, List<Treatment>>> matchingPatients(
        String treatment,
        Map<String, List<Treatment>> treatmentsByPatient
    ) {
        return treatmentsByPatient.entrySet().stream()
            .filter(p -> p.getValue().stream().anyMatch(t -> t.getTreatment().equals(treatment)))
            .collect(toList());
    }
}
