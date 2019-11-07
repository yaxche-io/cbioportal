package org.cbioportal.web.util;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.MultiKeyMap;
import org.cbioportal.model.*;
import org.cbioportal.service.*;
import org.cbioportal.web.parameter.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StudyViewFilterApplier {

    private static final String MUTATION_COUNT = "MUTATION_COUNT";
    private static final String FRACTION_GENOME_ALTERED = "FRACTION_GENOME_ALTERED";
    private SampleService sampleService;
    private MutationService mutationService;
    private DiscreteCopyNumberService discreteCopyNumberService;
    private MolecularProfileService molecularProfileService;
    private GenePanelService genePanelService;
    private ClinicalDataService clinicalDataService;
    private ClinicalDataEqualityFilterApplier clinicalDataEqualityFilterApplier;
    private ClinicalDataIntervalFilterApplier clinicalDataIntervalFilterApplier;
    private StudyViewFilterUtil studyViewFilterUtil;
    private GeneService geneService;
    private ClinicalAttributeService clinicalAttributeService;

    @Autowired
    public StudyViewFilterApplier(SampleService sampleService,
                                  MutationService mutationService,
                                  DiscreteCopyNumberService discreteCopyNumberService,
                                  MolecularProfileService molecularProfileService,
                                  GenePanelService genePanelService,
                                  ClinicalDataService clinicalDataService,
                                  ClinicalDataEqualityFilterApplier clinicalDataEqualityFilterApplier,
                                  ClinicalDataIntervalFilterApplier clinicalDataIntervalFilterApplier,
                                  StudyViewFilterUtil studyViewFilterUtil,
                                  GeneService geneService,
                                  ClinicalAttributeService clinicalAttributeService) {
        this.sampleService = sampleService;
        this.mutationService = mutationService;
        this.discreteCopyNumberService = discreteCopyNumberService;
        this.molecularProfileService = molecularProfileService;
        this.genePanelService = genePanelService;
        this.clinicalDataService = clinicalDataService;
        this.clinicalDataEqualityFilterApplier = clinicalDataEqualityFilterApplier;
        this.clinicalDataIntervalFilterApplier = clinicalDataIntervalFilterApplier;
        this.studyViewFilterUtil = studyViewFilterUtil;
        this.geneService = geneService;
        this.clinicalAttributeService = clinicalAttributeService;
    }

    Function<Sample, SampleIdentifier> sampleToSampleIdentifier = new Function<Sample, SampleIdentifier>() {

        public SampleIdentifier apply(Sample sample) {
            SampleIdentifier sampleIdentifier = new SampleIdentifier();
            sampleIdentifier.setSampleId(sample.getStableId());
            sampleIdentifier.setStudyId(sample.getCancerStudyIdentifier());
            return sampleIdentifier;
        }
    };

    public List<SampleIdentifier> apply(StudyViewFilter studyViewFilter) {
        return this.apply(studyViewFilter, false);
    }

    public List<SampleIdentifier> apply(StudyViewFilter studyViewFilter, Boolean negateFilters) {

        List<SampleIdentifier> sampleIdentifiers = new ArrayList<>();
        if (studyViewFilter == null) {
            return sampleIdentifiers;
        }

        if (studyViewFilter != null && studyViewFilter.getSampleIdentifiers() != null && !studyViewFilter.getSampleIdentifiers().isEmpty()) {
            List<String> studyIds = new ArrayList<>();
            List<String> sampleIds = new ArrayList<>();
            studyViewFilterUtil.extractStudyAndSampleIds(studyViewFilter.getSampleIdentifiers(), studyIds, sampleIds);
            sampleIdentifiers = sampleService.fetchSamples(studyIds, sampleIds, Projection.ID.name()).stream()
                .map(sampleToSampleIdentifier).collect(Collectors.toList());
        } else {
            sampleIdentifiers = sampleService.getAllSamplesInStudies(studyViewFilter.getStudyIds(), Projection.ID.name(),
                null, null, null, null).stream().map(sampleToSampleIdentifier).collect(Collectors.toList());
        }

        List<String> studyIds = sampleIdentifiers.stream().map(SampleIdentifier::getStudyId).distinct()
                .collect(Collectors.toList());

        List<ClinicalDataFilter> clinicalDataEqualityFilters = new ArrayList<>();
        List<ClinicalDataFilter> clinicalDataIntervalFilters = new ArrayList<>();

        List<ClinicalDataFilter> clinicalDataFilters = studyViewFilter.getClinicalDataFilters();

        if (!CollectionUtils.isEmpty(clinicalDataFilters)) {
            List<String> attributeIds = clinicalDataFilters.stream().map(ClinicalDataFilter::getAttributeId)
                    .collect(Collectors.toList());
            List<ClinicalAttribute> clinicalAttributes = clinicalAttributeService
                    .getClinicalAttributesByStudyIdsAndAttributeIds(studyIds, attributeIds);

            Map<String, ClinicalAttribute> clinicalAttributeMap = clinicalAttributes.stream()
                    .collect(Collectors.toMap(ClinicalAttribute::getAttrId, Function.identity(), (a, b) -> {
                        return a.getDatatype().equals("STRING") ? a : b;
                    }));

            clinicalDataFilters.forEach(clinicalDataFilter -> {
                String attributeId = clinicalDataFilter.getAttributeId();
                if (clinicalAttributeMap.containsKey(attributeId)) {
                    if (clinicalAttributeMap.get(attributeId).getDatatype().equals("STRING")) {
                        clinicalDataEqualityFilters.add(clinicalDataFilter);
                    } else {
                        clinicalDataIntervalFilters.add(clinicalDataFilter);
                    }
                }
            });
        }

        if (!CollectionUtils.isEmpty(clinicalDataEqualityFilters)) {
            sampleIdentifiers = equalityFilterClinicalData(sampleIdentifiers, clinicalDataEqualityFilters, negateFilters);
        }
        
        if (!CollectionUtils.isEmpty(clinicalDataIntervalFilters)) {
            sampleIdentifiers = intervalFilterClinicalData(sampleIdentifiers, clinicalDataIntervalFilters, negateFilters);
        }

        List<MutationGeneFilter> mutatedGenes = studyViewFilter.getMutatedGenes();
        if (mutatedGenes != null && !sampleIdentifiers.isEmpty()) {
            sampleIdentifiers = filterMutatedGenes(mutatedGenes, sampleIdentifiers);
        }

        List<CopyNumberGeneFilter> cnaGenes = studyViewFilter.getCnaGenes();
        if (cnaGenes != null && !sampleIdentifiers.isEmpty()) {
            sampleIdentifiers = filterCNAGenes(cnaGenes, sampleIdentifiers);
        }

        List<FusionGeneFilter> fusionGenes = studyViewFilter.getFusionGenes();
        if (fusionGenes != null && !sampleIdentifiers.isEmpty()) {
            sampleIdentifiers = filterFusionGenes(fusionGenes, sampleIdentifiers);
        }

        Boolean withMutationData = studyViewFilter.getWithMutationData();
        if (withMutationData != null && !sampleIdentifiers.isEmpty()) {
            sampleIdentifiers = filterByProfiled(sampleIdentifiers, withMutationData, molecularProfileService::getFirstMutationProfileIds);
        }

        Boolean withCNAData = studyViewFilter.getWithCNAData();
        if (withCNAData != null && !sampleIdentifiers.isEmpty()) {
            sampleIdentifiers = filterByProfiled(sampleIdentifiers, withCNAData, molecularProfileService::getFirstDiscreteCNAProfileIds);
        }

        RectangleBounds mutationCountVsCNASelection = studyViewFilter.getMutationCountVsCNASelection();
        if (mutationCountVsCNASelection != null && !sampleIdentifiers.isEmpty()) {
            sampleIdentifiers = filterMutationCountVsCNASelection(mutationCountVsCNASelection, sampleIdentifiers);
        }

        return sampleIdentifiers;
    }

    private List<SampleIdentifier> intervalFilterClinicalData(List<SampleIdentifier> sampleIdentifiers,
                                                              List<ClinicalDataFilter> clinicalDataIntervalFilters,
                                                              Boolean negateFilters) {
        return clinicalDataIntervalFilterApplier.apply(sampleIdentifiers, clinicalDataIntervalFilters, negateFilters);
    }

    private List<SampleIdentifier> equalityFilterClinicalData(List<SampleIdentifier> sampleIdentifiers,
                                                              List<ClinicalDataFilter> clinicalDataEqualityFilters,
                                                              Boolean negateFilters) {
        return clinicalDataEqualityFilterApplier.apply(sampleIdentifiers, clinicalDataEqualityFilters, negateFilters);
    }

    private List<SampleIdentifier> filterByProfiled(List<SampleIdentifier> sampleIdentifiers, Boolean criteria,
        BiFunction<List<String>, List<String>, List<String>> molecularProfileGetter) {

        List<String> studyIds = new ArrayList<>();
        List<String> sampleIds = new ArrayList<>();
        studyViewFilterUtil.extractStudyAndSampleIds(sampleIdentifiers, studyIds, sampleIds);
        List<String> firstMutationProfileIds = molecularProfileGetter.apply(studyIds, sampleIds);
        List<GenePanelData> genePanelDataList = genePanelService.fetchGenePanelDataInMultipleMolecularProfiles(firstMutationProfileIds,
            sampleIds).stream().filter(g -> g.getProfiled() == criteria).collect(Collectors.toList());
        return genePanelDataList.stream().map(d -> {
            SampleIdentifier sampleIdentifier = new SampleIdentifier();
            sampleIdentifier.setSampleId(d.getSampleId());
            sampleIdentifier.setStudyId(d.getStudyId());
            return sampleIdentifier;
        }).collect(Collectors.toList());
    }

    private List<SampleIdentifier> filterMutatedGenes(List<MutationGeneFilter> mutatedGenes, List<SampleIdentifier> sampleIdentifiers) {
        for (MutationGeneFilter molecularProfileGeneFilter : mutatedGenes) {
            List<String> studyIds = new ArrayList<>();
            List<String> sampleIds = new ArrayList<>();
            List<Integer> entrezGeneIds = geneService
                    .fetchGenes(molecularProfileGeneFilter.getHugoGeneSymbols(), GeneIdType.HUGO_GENE_SYMBOL.name(), Projection.SUMMARY.name())
                    .stream()
                    .map(gene -> gene.getEntrezGeneId())
                    .collect(Collectors.toList());
            
            studyViewFilterUtil.extractStudyAndSampleIds(sampleIdentifiers, studyIds, sampleIds);
            List<Mutation> mutations = mutationService.getMutationsInMultipleMolecularProfiles(
                    molecularProfileService.getFirstMutationProfileIds(studyIds, sampleIds), sampleIds, entrezGeneIds,
                    Projection.ID.name(), null, null, null, null);
            sampleIdentifiers = mutations.stream().map(m -> {
                SampleIdentifier sampleIdentifier = new SampleIdentifier();
                sampleIdentifier.setSampleId(m.getSampleId());
                sampleIdentifier.setStudyId(m.getStudyId());
                return sampleIdentifier;
            }).distinct().collect(Collectors.toList());
        }

        return sampleIdentifiers;
    }

    private List<SampleIdentifier> filterFusionGenes(List<FusionGeneFilter> fusionGenes, List<SampleIdentifier> sampleIdentifiers) {
        for (FusionGeneFilter molecularProfileGeneFilter : fusionGenes) {
            List<String> studyIds = new ArrayList<>();
            List<String> sampleIds = new ArrayList<>();
            List<Integer> entrezGeneIds = geneService
                    .fetchGenes(molecularProfileGeneFilter.getHugoGeneSymbols(), GeneIdType.HUGO_GENE_SYMBOL.name(), Projection.SUMMARY.name())
                    .stream()
                    .map(gene -> gene.getEntrezGeneId())
                    .collect(Collectors.toList());
            
            studyViewFilterUtil.extractStudyAndSampleIds(sampleIdentifiers, studyIds, sampleIds);
            List<Mutation> fusions = mutationService.getMutationsInMultipleMolecularProfiles(molecularProfileService
                    .getFirstMutationProfileIds(studyIds, sampleIds), sampleIds, entrezGeneIds, Projection.ID.name(), null, null, null, null);
            sampleIdentifiers = fusions.stream().map(m -> {
                SampleIdentifier sampleIdentifier = new SampleIdentifier();
                sampleIdentifier.setSampleId(m.getSampleId());
                sampleIdentifier.setStudyId(m.getStudyId());
                return sampleIdentifier;
            }).distinct().collect(Collectors.toList());
        }

        return sampleIdentifiers;
    }
    
    private List<SampleIdentifier> filterCNAGenes(List<CopyNumberGeneFilter> cnaGenes, List<SampleIdentifier> sampleIdentifiers) {

        for (CopyNumberGeneFilter copyNumberGeneFilter : cnaGenes) {

            List<String> studyIds = new ArrayList<>();
            List<String> sampleIds = new ArrayList<>();
            studyViewFilterUtil.extractStudyAndSampleIds(sampleIdentifiers, studyIds, sampleIds);

            List<DiscreteCopyNumberData> resultList = DiscreteCopyNumberEventType.HOMDEL_AND_AMP.getAlterationTypes()
                    .stream().flatMap(alterationType -> {
                        List<String> hugoGeneSymbols = copyNumberGeneFilter.getAlterations().stream()
                                .filter(a -> alterationType == a.getAlteration())
                                .map(CopyNumberGeneFilterElement::getHugoGeneSymbol).collect(Collectors.toList());

                        List<Integer> entrezGeneIds = geneService
                                .fetchGenes(new ArrayList<>(hugoGeneSymbols), GeneIdType.HUGO_GENE_SYMBOL.name(),
                                        Projection.SUMMARY.name())
                                .stream().map(gene -> gene.getEntrezGeneId()).collect(Collectors.toList());

                        List<DiscreteCopyNumberData> copyNumberDatas = new ArrayList<>();
                        if (!entrezGeneIds.isEmpty()) {
                            copyNumberDatas = discreteCopyNumberService
                                    .getDiscreteCopyNumbersInMultipleMolecularProfiles(
                                            molecularProfileService.getFirstDiscreteCNAProfileIds(studyIds, sampleIds),
                                            sampleIds, entrezGeneIds, Arrays.asList(alterationType),
                                            Projection.ID.name());

                        }
                        return copyNumberDatas.stream();
                    }).collect(Collectors.toList());

            sampleIdentifiers = resultList.stream().map(d -> {
                SampleIdentifier sampleIdentifier = new SampleIdentifier();
                sampleIdentifier.setSampleId(d.getSampleId());
                sampleIdentifier.setStudyId(d.getStudyId());
                return sampleIdentifier;
            }).distinct().collect(Collectors.toList());
        }

        return sampleIdentifiers;
    }

    private List<SampleIdentifier> filterMutationCountVsCNASelection(RectangleBounds mutationCountVsCNASelection, List<SampleIdentifier> sampleIdentifiers) {
        List<String> studyIds = new ArrayList<>();
        List<String> sampleIds = new ArrayList<>();
        studyViewFilterUtil.extractStudyAndSampleIds(sampleIdentifiers, studyIds, sampleIds);
        List<ClinicalData> clinicalDataList = clinicalDataService.fetchClinicalData(studyIds, sampleIds, 
            Arrays.asList(MUTATION_COUNT, FRACTION_GENOME_ALTERED), ClinicalDataType.SAMPLE.name(), Projection.SUMMARY.name());
        MultiKeyMap clinicalDataMap = new MultiKeyMap();
        for (ClinicalData clinicalData : clinicalDataList) {
            if (clinicalDataMap.containsKey(clinicalData.getSampleId(), clinicalData.getStudyId())) {
                ((List<ClinicalData>)clinicalDataMap.get(clinicalData.getSampleId(), clinicalData.getStudyId())).add(clinicalData);
            } else {
                List<ClinicalData> clinicalDatas = new ArrayList<>();
                clinicalDatas.add(clinicalData);
                clinicalDataMap.put(clinicalData.getSampleId(), clinicalData.getStudyId(), clinicalDatas);
            }
        }
        List<SampleIdentifier> filteredSampleIdentifiers = new ArrayList<>();
        sampleIdentifiers.forEach(sampleIdentifier -> {
            List<ClinicalData> entityClinicalData = (List<ClinicalData>) clinicalDataMap
                    .get(sampleIdentifier.getSampleId(), sampleIdentifier.getStudyId());
            if (entityClinicalData != null) {
                Optional<ClinicalData> fractionGenomeAlteredData = entityClinicalData.stream()
                        .filter(c -> c.getAttrId().equals(FRACTION_GENOME_ALTERED)).findFirst();
                Optional<ClinicalData> mutationCountData = entityClinicalData.stream()
                        .filter(c -> c.getAttrId().equals(MUTATION_COUNT)).findFirst();

                if (fractionGenomeAlteredData.isPresent() && mutationCountData.isPresent()) {
                    BigDecimal fractionGenomeAlteredValue = new BigDecimal(
                            fractionGenomeAlteredData.get().getAttrValue());
                    BigDecimal mutationCountValue = new BigDecimal(mutationCountData.get().getAttrValue());
                    if (fractionGenomeAlteredValue.compareTo(mutationCountVsCNASelection.getxStart()) >= 0
                            && fractionGenomeAlteredValue.compareTo(mutationCountVsCNASelection.getxEnd()) < 0
                            && mutationCountValue.compareTo(mutationCountVsCNASelection.getyStart()) >= 0
                            && mutationCountValue.compareTo(mutationCountVsCNASelection.getyEnd()) < 0) {

                        filteredSampleIdentifiers.add(sampleIdentifier);
                    }
                }
            }

        });
        return filteredSampleIdentifiers;
    }
}
