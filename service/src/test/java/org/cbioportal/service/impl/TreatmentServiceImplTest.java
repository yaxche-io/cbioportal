package org.cbioportal.service.impl;

import org.cbioportal.model.*;
import org.cbioportal.persistence.TreatmentRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;
import java.util.stream.Collectors;


@RunWith(MockitoJUnitRunner.class)
public class TreatmentServiceImplTest {
    
    private static final String STUDY_ID = "cancer_study";

    @InjectMocks
    private TreatmentServiceImpl treatmentService;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Test
    public void getAllSampleTreatmentsSingleRow() {
        mockTreatmentsByPatient(
            makeTreatment("madeupanib", "P0", 0, 10)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5)
        );
        mockAllTreatments("madeupanib");

        PatientTreatmentRow rowA = makePatientRow("madeupanib", 1, setOf("S0"));
        List<PatientTreatmentRow> expected = Collections.singletonList(rowA);
        List<PatientTreatmentRow> actual = treatmentService.getAllPatientTreatmentRows(null, null);
        
        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentsOneSampleTwoTreatmentsOnePatient() {
        mockTreatmentsByPatient(
            makeTreatment("madeupanib", "P0", 0, 10),
            makeTreatment("fakedrugazol", "P0", 0, 10)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5)
        );
        mockAllTreatments("madeupanib", "fakedrugazol");


        PatientTreatmentRow rowA = makePatientRow("fakedrugazol", 1, setOf("S0"));
        PatientTreatmentRow rowB = makePatientRow("madeupanib", 1, setOf("S0"));
        List<PatientTreatmentRow> expected = Arrays.asList(rowA, rowB);
        List<PatientTreatmentRow> actual = treatmentService.getAllPatientTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentsTwoSamplesOnePatientOneTreatment() {
        mockTreatmentsByPatient(
            makeTreatment("fakedrugazol", "P0", 0, 10)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5),
            makeSample("S1", "P0", 10)
        );
        mockAllTreatments("fakedrugazol");

        // even though there are two samples, you expect a count of 1,
        // because this is from the patient level, and both samples are for the same patient
        PatientTreatmentRow rowA = makePatientRow("fakedrugazol", 1, setOf("S0", "S1"));
        List<PatientTreatmentRow> expected = Collections.singletonList(rowA);
        List<PatientTreatmentRow> actual = treatmentService.getAllPatientTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentsTwoSamplesTwoPatientsTwoTreatments() {
        mockTreatmentsByPatient(
            makeTreatment("fakedrugazol", "P0", 0, 10),
            makeTreatment("fakedrugazol", "P1", 0, 10)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5),
            makeSample("S1", "P1", 10)
        );
        mockAllTreatments("fakedrugazol");

        // now there are two patients, so you expect a count of two
        PatientTreatmentRow rowA = makePatientRow("fakedrugazol", 2, setOf("S0", "S1"));
        List<PatientTreatmentRow> expected = Collections.singletonList(rowA);
        List<PatientTreatmentRow> actual = treatmentService.getAllPatientTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentsTwoSamplesTwoPatientsTwoDifferentTreatments() {
        mockTreatmentsByPatient(
            makeTreatment("fakedrugazol", "P0", 0, 10),
            makeTreatment("madeupanib", "P1", 0, 10)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5),
            makeSample("S1", "P1", 10)
        );
        mockAllTreatments("fakedrugazol", "madeupanib");

        PatientTreatmentRow rowA = makePatientRow("fakedrugazol", 1, setOf("S0"));
        PatientTreatmentRow rowB = makePatientRow("madeupanib", 1, setOf("S1"));
        List<PatientTreatmentRow> expected = Arrays.asList(rowA, rowB);
        List<PatientTreatmentRow> actual = treatmentService.getAllPatientTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentRowsOneSampleBef0reOneTreatment() {
        mockTreatmentsByPatient(
            makeTreatment("fabricatin", "P0", 10, 20)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5)
        );

        SampleTreatmentRow rowA = makeSampleRow(TemporalRelation.Pre, "fabricatin", 1, setOf("S0"));
        List<SampleTreatmentRow> expected = Collections.singletonList(rowA);
        List<SampleTreatmentRow> actual = treatmentService.getAllSampleTreatmentRows(null, null);
        
        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentRowsOneSampleAfterOneTreatment() {
        mockTreatmentsByPatient(
            makeTreatment("fabricatin", "P0", 10, 20)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 25)
        );

        SampleTreatmentRow rowA = makeSampleRow(TemporalRelation.Post, "fabricatin", 1, setOf("S0"));
        List<SampleTreatmentRow> expected = Collections.singletonList(rowA);
        List<SampleTreatmentRow> actual = treatmentService.getAllSampleTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentRowsOneSampleBeforeOneSampleAfterOneTreatment() {
        mockTreatmentsByPatient(
            makeTreatment("fabricatin", "P0", 10, 20)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5),
            makeSample("S1", "P0", 25)
        );

        SampleTreatmentRow rowA = makeSampleRow(TemporalRelation.Post, "fabricatin", 1, setOf("S1"));
        SampleTreatmentRow rowB = makeSampleRow(TemporalRelation.Pre, "fabricatin", 1, setOf("S0"));
        List<SampleTreatmentRow> expected = Arrays.asList(rowA, rowB);
        List<SampleTreatmentRow> actual = treatmentService.getAllSampleTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentRowsThreeSamplesTwoTreatmentsSameDrug() {
        mockTreatmentsByPatient(
            makeTreatment("fabricatin", "P0", 10, 20),
            makeTreatment("fabricatin", "P0", 30, 40)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5),
            makeSample("S1", "P0", 25),
            makeSample("S2", "P0", 45)
        );

        SampleTreatmentRow rowA = makeSampleRow(TemporalRelation.Post, "fabricatin", 2, setOf("S1", "S2"));
        SampleTreatmentRow rowB = makeSampleRow(TemporalRelation.Pre, "fabricatin", 1, setOf("S0"));
        List<SampleTreatmentRow> expected = Arrays.asList(rowA, rowB);
        List<SampleTreatmentRow> actual = treatmentService.getAllSampleTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentRowsThreeSamplesTwoTreatmentsDifferentDrug() {
        mockTreatmentsByPatient(
            makeTreatment("fabricatin", "P0", 10, 20),
            makeTreatment("fauxan", "P0", 30, 40)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5),
            makeSample("S1", "P0", 25),
            makeSample("S2", "P0", 45)
        );

        // Keep in mind, adding a second drug that has samples that appear before and after it
        // adds two rows to the result: pre and post
        SampleTreatmentRow rowA = makeSampleRow(TemporalRelation.Post, "fabricatin", 2, setOf("S1", "S2"));
        SampleTreatmentRow rowB = makeSampleRow(TemporalRelation.Pre, "fabricatin", 1, setOf("S0"));
        SampleTreatmentRow rowC = makeSampleRow(TemporalRelation.Pre, "fauxan", 2, setOf("S0", "S1"));
        SampleTreatmentRow rowD = makeSampleRow(TemporalRelation.Post, "fauxan", 1, setOf("S2"));
        List<SampleTreatmentRow> expected = Arrays.asList(rowA, rowB, rowC, rowD);
        List<SampleTreatmentRow> actual = treatmentService.getAllSampleTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    @Test
    public void getAllSampleTreatmentRowsTwoPatients() {
        mockTreatmentsByPatient(
            makeTreatment("fabricatin", "P0", 10, 20),
            makeTreatment("fabricatin", "P1", 10, 20),
            makeTreatment("fauxan", "P0", 30, 40),
            makeTreatment("fauxan", "P1", 30, 40)
        );
        mockSamplesByPatient(
            makeSample("S0", "P0", 5),
            makeSample("S1", "P0", 25),
            makeSample("S2", "P0", 45),
            makeSample("S3", "P1", 5),
            makeSample("S4", "P1", 25),
            makeSample("S5", "P1", 45)
        );

        // Keep in mind, adding a second drug that has samples that appear before and after it
        // adds two rows to the result: pre and post
        SampleTreatmentRow rowA = makeSampleRow(TemporalRelation.Post, "fabricatin", 4, setOf("S1", "S2", "S4", "S5"));
        SampleTreatmentRow rowB = makeSampleRow(TemporalRelation.Pre, "fabricatin", 2, setOf("S0", "S3"));
        SampleTreatmentRow rowC = makeSampleRow(TemporalRelation.Pre, "fauxan", 4, setOf("S0", "S1", "S3", "S4"));
        SampleTreatmentRow rowD = makeSampleRow(TemporalRelation.Post, "fauxan", 2, setOf("S2", "S5"));
        List<SampleTreatmentRow> expected = Arrays.asList(rowA, rowB, rowC, rowD);
        List<SampleTreatmentRow> actual = treatmentService.getAllSampleTreatmentRows(null, null);

        Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
    }

    private void mockTreatmentsByPatient(Treatment... treatments) {
        Map<String, List<Treatment>> treatmentsByPatient = Arrays.stream(treatments)
            .collect(Collectors.groupingBy(Treatment::getPatientId));
        Mockito.when(treatmentRepository.getTreatmentsByPatient(Matchers.any(), Matchers.any()))
            .thenReturn(treatmentsByPatient);
    }
    
    private void mockSamplesByPatient(DatedSample... samples) {
        Map<String, List<DatedSample>> samplesByPatient = Arrays.stream(samples)
            .collect(Collectors.groupingBy(DatedSample::getPatientId));
        Mockito.when(treatmentRepository.getSamplesByPatient(Matchers.any(), Matchers.any()))
            .thenReturn(samplesByPatient);
    }
    
    private void mockAllTreatments(String... treatments) {
        Set<String> allTreatments = new HashSet<>(Arrays.asList(treatments));
        Mockito.when(treatmentRepository.getAllUniqueTreatments(Matchers.any(), Matchers.any()))
            .thenReturn(allTreatments);
    }

    private Treatment makeTreatment(String treatment, String patientId, Integer start, Integer stop) {
        Treatment t = new Treatment();
        t.setTreatment(treatment);
        t.setStudyId(STUDY_ID);
        t.setPatientId(patientId);
        t.setStart(start);
        t.setStop(stop);
        return t;
    }
    
    private DatedSample makeSample(String sampleId, String patientId, Integer timeTaken) {
        DatedSample s = new DatedSample();
        s.setSampleId(sampleId);
        s.setPatientId(patientId);
        s.setStudyId(STUDY_ID);
        s.setTimeTaken(timeTaken);
        return s;
    }
    
    private SampleTreatmentRow makeSampleRow(TemporalRelation time, String treatment, int count, Set<String> samples) {
        return new SampleTreatmentRow(time, treatment, count, samples, setOf(STUDY_ID));
    }
    
    private PatientTreatmentRow makePatientRow(String treatment, int count, Set<String> samples) {
        return new PatientTreatmentRow(treatment, count, samples, setOf(STUDY_ID));
    }
    
    private Set<String> setOf(String... strings) {
        return new HashSet<>(Arrays.asList(strings));
    }
}