package org.cbioportal.web;

import org.cbioportal.service.SampleService;
import org.cbioportal.service.TreatmentService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration("/applicationContext-web-test.xml")
@Configuration
public class TreatmentControllerTest {
    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private TreatmentService treatmentService;
    
    @Bean
    public TreatmentService treatmentService() {
        return Mockito.mock(TreatmentService.class);
    }

    private MockMvc mockMvc;

    @Before
    public void setUp() throws Exception {

        Mockito.reset(treatmentService);
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    public void fakeTest() {
        Assert.assertTrue(true);
    }
}