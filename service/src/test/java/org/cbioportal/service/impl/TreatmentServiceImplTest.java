package org.cbioportal.service.impl;

import org.cbioportal.persistence.TreatmentRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


@RunWith(MockitoJUnitRunner.class)
public class TreatmentServiceImplTest {

    @InjectMocks
    private TreatmentServiceImpl treatmentService;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Test
    public void getAllSampleTreatments() {
        Assert.assertEquals(0, 0);
    }
}