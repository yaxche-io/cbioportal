package org.cbioportal.model;

import java.util.Objects;
import java.util.Set;

public class SampleTreatmentRow {
    private TemporalRelation time;
    private String treatment;
    private int count;
    private Set<String> samples;
    private Set<String> studies;

    public SampleTreatmentRow() {}

    public SampleTreatmentRow(TemporalRelation time, String treatment, int count, Set<String> samples, Set<String> studies) {
        this.time = time;
        this.treatment = treatment;
        this.count = count;
        this.samples = samples;
        this.studies = studies;
    }

    public TemporalRelation getTime() {
        return time;
    }

    public void setTime(TemporalRelation time) {
        this.time = time;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Set<String> getSamples() {
        return samples;
    }

    public void setSamples(Set<String> samples) {
        this.samples = samples;
    }

    public Set<String> getStudies() {
        return studies;
    }

    public void setStudies(Set<String> studies) {
        this.studies = studies;
    }
    
    public String calculateKey() {
        return getTreatment() + getTime().name();
    }
    
    public void add(SampleTreatmentRow toAdd) {
        setCount(getCount() + toAdd.getCount());
        getSamples().addAll(toAdd.getSamples());
        getStudies().addAll(toAdd.getStudies());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SampleTreatmentRow that = (SampleTreatmentRow) o;
        return getCount() == that.getCount() &&
            getTime() == that.getTime() &&
            getTreatment().equals(that.getTreatment()) &&
            Objects.equals(getSamples(), that.getSamples()) &&
            Objects.equals(getStudies(), that.getStudies());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTime(), getTreatment(), getCount(), getSamples(), getStudies());
    }
}
