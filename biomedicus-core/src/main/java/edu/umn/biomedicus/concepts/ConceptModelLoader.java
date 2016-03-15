package edu.umn.biomedicus.concepts;

import edu.umn.biomedicus.application.BiomedicusConfiguration;
import edu.umn.biomedicus.application.DataLoader;
import edu.umn.biomedicus.common.terms.TermIndex;
import edu.umn.biomedicus.common.terms.TermVector;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.serialization.YamlSerialization;
import edu.umn.biomedicus.vocabulary.Vocabulary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
@Singleton
public class ConceptModelLoader extends DataLoader<ConceptModel> {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Path phrasesPath;

    private final Path normsPath;

    private final Path typesPath;

    private final Path suiToCUIsPath;

    private final TermIndex normIndex;

    @Inject
    ConceptModelLoader(BiomedicusConfiguration biomedicusConfiguration, Vocabulary vocabulary) {
        phrasesPath = biomedicusConfiguration.resolveDataFile("concepts.phrases.path");
        normsPath = biomedicusConfiguration.resolveDataFile("concepts.norms.path");
        typesPath = biomedicusConfiguration.resolveDataFile("concepts.types.path");
        suiToCUIsPath = biomedicusConfiguration.resolveDataFile("concepts.suisToCUIs.path");
        normIndex = vocabulary.normIndex();
    }

    @Override
    protected ConceptModel loadModel() throws BiomedicusException {
        Yaml yaml = YamlSerialization.createYaml(normIndex);

        try {
            LOGGER.info("Loading concepts phrases: {}", phrasesPath);
            @SuppressWarnings("unchecked")
            Map<String, SUI> phraseDictionary = (Map<String, SUI>) yaml.load(Files.newBufferedReader(phrasesPath));


            LOGGER.info("Loading SUI -> CUIs map: {}", suiToCUIsPath);
            @SuppressWarnings("unchecked")
            Map<SUI, List<CUI>> suiCUIs = (Map<SUI, List<CUI>>) yaml.load(Files.newBufferedReader(suiToCUIsPath));

            LOGGER.info("Loading CUI -> TUIs map: {}", typesPath);
            @SuppressWarnings("unchecked")
            Map<CUI, List<TUI>> cuiToTUIs = (Map<CUI, List<TUI>>) yaml.load(Files.newBufferedReader(typesPath));


            LOGGER.info("Loading concept norm vectors: {}", normsPath);
            Map<TermVector, List<CUI>> normDictionary = new HashMap<>();
            Pattern SPLITTER = Pattern.compile(",");
            try (BufferedReader normsReader = Files.newBufferedReader(normsPath)) {
                String line;
                while ((line = normsReader.readLine()) != null) {
                    TermVector termVector = normIndex.lookup(SPLITTER.split(line)).get();
                    String cuis = normsReader.readLine();
                    List<CUI> cuisList = Stream.of(SPLITTER.split(cuis)).map(CUI::new).collect(Collectors.toList());
                    normDictionary.put(termVector, cuisList);
                }
            }

            return new ConceptModel(cuiToTUIs, normDictionary, phraseDictionary, suiCUIs);
        } catch (IOException e) {
            throw new BiomedicusException(e);
        }
    }
}