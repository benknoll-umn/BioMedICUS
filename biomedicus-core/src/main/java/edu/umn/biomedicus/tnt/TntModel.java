/*
 * Copyright (c) 2015 Regents of the University of Minnesota.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umn.biomedicus.tnt;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import edu.umn.biomedicus.application.BiomedicusConfiguration;
import edu.umn.biomedicus.common.grams.Bigram;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.model.tuples.PosCap;
import edu.umn.biomedicus.model.tuples.WordCap;
import edu.umn.biomedicus.common.viterbi.CandidateProbability;
import edu.umn.biomedicus.common.viterbi.EmissionProbabilityModel;
import edu.umn.biomedicus.common.viterbi.TransitionProbabilityModel;
import edu.umn.biomedicus.common.viterbi.Viterbi;
import edu.umn.biomedicus.model.tuples.WordPos;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public class TntModel implements EmissionProbabilityModel<PosCap, WordCap>,
        TransitionProbabilityModel<PosCap, Bigram<PosCap>> {

    /**
     * Trigram model used for transition probability.
     */
    private final PosCapTrigramModel posCapTrigramModel;

    /**
     * Word probability models used for emission probability.
     */
    private final List<FilteredAdaptedWordProbabilityModel> filteredAdaptedWordProbabilities;

    @Inject
    TntModel(BiomedicusConfiguration biomedicusConfiguration) throws BiomedicusException {
        Path trigram = biomedicusConfiguration.resolveDataFile("tnt.trigram.path");
        Path wordModels = biomedicusConfiguration.resolveDataFile("tnt.word.path");

        Yaml yaml = new Yaml();

        try {
            posCapTrigramModel = (PosCapTrigramModel) yaml.load(Files.newInputStream(trigram));

            filteredAdaptedWordProbabilities = new ArrayList<>();
            Files.walkFileTree(wordModels, Collections.emptySet(), 0, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    FilteredAdaptedWordProbabilityModel filteredAdaptedWordProbabilityModel = (FilteredAdaptedWordProbabilityModel) yaml.load(Files.newInputStream(file));
                    filteredAdaptedWordProbabilities.add(filteredAdaptedWordProbabilityModel);
                    return FileVisitResult.CONTINUE;
                }
            });

            Collections.sort(filteredAdaptedWordProbabilities, (m1, m2) -> Integer.compare(m1.getPriority(), m2.getPriority()));
        } catch (IOException e) {
            throw new BiomedicusException(e);
        }
    }

    public void write(Path folder) throws IOException {
        Yaml yaml = new Yaml();

        Files.createDirectories(folder);

        yaml.dump(posCapTrigramModel, Files.newBufferedWriter(folder.resolve("trigram.yml")));

        Path words = folder.resolve("words");
        Files.createDirectory(words);

        for (FilteredAdaptedWordProbabilityModel filteredAdaptedWordProbability : filteredAdaptedWordProbabilities) {
            yaml.dump(filteredAdaptedWordProbability, Files.newBufferedWriter(words.resolve(filteredAdaptedWordProbability.getPriority() + ".yml")));
        }
    }

    public TntModel(PosCapTrigramModel posCapTrigramModel, List<FilteredAdaptedWordProbabilityModel> filteredAdaptedWordProbabilities) {
        this.posCapTrigramModel = posCapTrigramModel;
        this.filteredAdaptedWordProbabilities = filteredAdaptedWordProbabilities;
    }

    private FilteredAdaptedWordProbabilityModel getWordProbabilityModel(WordCap emittedValue) {
        FilteredAdaptedWordProbabilityModel filteredAdaptedWordProbability = null;
        for (FilteredAdaptedWordProbabilityModel probabilityModel : filteredAdaptedWordProbabilities) {
            if (probabilityModel.isKnown(emittedValue)) {
                filteredAdaptedWordProbability = probabilityModel;
                break;
            }
        }

        if (filteredAdaptedWordProbability == null) {
            throw new AssertionError("could not find any word probability model");
        }
        return filteredAdaptedWordProbability;
    }

    @Override
    public Collection<CandidateProbability<PosCap>> getCandidates(WordCap emittedValue) {
        FilteredAdaptedWordProbabilityModel filteredAdaptedWordProbability = getWordProbabilityModel(emittedValue);

        return filteredAdaptedWordProbability.getCandidates(emittedValue)
                .stream()
                .map(candidate -> {
                    double emissionLogProbability = filteredAdaptedWordProbability.logProbabilityOfWord(candidate, emittedValue);
                    PosCap candidatePosCap = PosCap.create(candidate, emittedValue.isCapitalized());
                    return Viterbi.candidateOf(candidatePosCap, emissionLogProbability);
                })
                .collect(Collectors.toList());
    }

    @Override
    public double getTransitionLogProbability(Bigram<PosCap> statesReduction, PosCap candidate) {
        return Math.log10(posCapTrigramModel.getTrigramProbability(statesReduction.getFirst(),
                statesReduction.getSecond(), candidate));
    }
}
