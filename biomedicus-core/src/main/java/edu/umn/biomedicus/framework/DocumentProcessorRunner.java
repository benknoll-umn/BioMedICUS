/*
 * Copyright (c) 2016 Regents of the University of Minnesota.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umn.biomedicus.framework;

import com.google.inject.*;
import com.google.inject.name.Named;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.store.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 */
public final class DocumentProcessorRunner {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(DocumentProcessorRunner.class);

    private final List<Class<? extends PostProcessor>> postProcessors
            = new ArrayList<>();

    private final Injector injector;
    private final SettingsTransformer settingsTransformer;
    private final Map<String, Object> globalSettings;
    @Nullable private Injector settingsInjector;
    @Nullable private Class<? extends DocumentProcessor> documentProcessorClass;
    @Nullable private BiomedicusScopes.Context processorContext;

    @Inject
    DocumentProcessorRunner(
            Injector injector,
            SettingsTransformer settingsTransformer,
            @Named("globalSettings") Map<String, Object> globalSettings
    ) {
        this.injector = injector;
        this.settingsTransformer = settingsTransformer;
        this.globalSettings = globalSettings;
    }

    public static DocumentProcessorRunner create(Injector injector) {
        return injector.getInstance(DocumentProcessorRunner.class);
    }

    public void setDocumentProcessorClass(
            Class<? extends DocumentProcessor> documentProcessorClass
    ) {
        this.documentProcessorClass = documentProcessorClass;
    }

    public void setDocumentProcessorClassName(String documentProcessorClassName)
            throws ClassNotFoundException {
        this.documentProcessorClass = Class.forName(documentProcessorClassName)
                .asSubclass(DocumentProcessor.class);
    }

    public void addPostProcessorClass(
            Class<? extends PostProcessor> processorListenerClass
    ) {
        postProcessors.add(processorListenerClass);
    }

    public void addPostProcessorClassName(String postProcessorClassName)
            throws ClassNotFoundException {
        addPostProcessorClass(Class.forName(postProcessorClassName)
                .asSubclass(PostProcessor.class));
    }

    public void initialize(@Nullable Map<String, Object> processorSettings,
                           @Nullable Map<Key<?>, Object> processorScopedObjects)
            throws BiomedicusException {
        checkNotNull(documentProcessorClass,
                "Document processor class needs to be set");

        settingsTransformer.setAnnotationFunction(ProcessorSettingImpl::new);

        if (processorSettings != null) {
            settingsTransformer.addAll(processorSettings);
        }
        settingsTransformer.addAll(globalSettings);

        Map<Key<?>, Object> settingsSeededObjects = settingsTransformer
                .getSettings();
        settingsInjector = injector.createChildInjector(
                new ProcessorSettingsModule(settingsSeededObjects.keySet()));

        Map<Key<?>, Object> processorScopeMap = new HashMap<>();
        processorScopeMap.putAll(settingsSeededObjects);
        if (processorScopedObjects != null) {
            processorScopeMap.putAll(processorScopedObjects);
        }

        processorContext = BiomedicusScopes
                .createProcessorContext(processorScopeMap);
    }

    public void require(String className) throws BiomedicusException {
        try {
            require(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new BiomedicusException(e);
        }
    }

    public void require(Class<?> aClass) throws BiomedicusException {
        checkNotNull(processorContext,
                "Processor context is null, initialize not ran");

        checkNotNull(settingsInjector,
                "Settings injector is null, initialize not ran");

        try {
            processorContext.call(() -> {
                Provider<?> provider = settingsInjector.getProvider(aClass);
                if (provider instanceof EagerLoadable) {
                    ((EagerLoadable) provider).eagerLoad();
                    return null;
                }
                Object o = provider.get();
                if (o instanceof EagerLoadable) {
                    ((EagerLoadable) o).eagerLoad();
                }
                return null;
            });
        } catch (Exception e) {
            throw new BiomedicusException(e);
        }
    }

    public void processDocument(
            Document document,
            @Nullable Map<Key<?>, Object> documentScopedObjects
    ) throws BiomedicusException {
        checkNotNull(processorContext,
                "Processor context is null, initialize not ran");

        checkNotNull(settingsInjector,
                "Settings injector is null, initialize not ran");

        try {
            processorContext.call(() -> {
                Map<Key<?>, Object> seededObjects = new HashMap<>();
                seededObjects.put(Key.get(Document.class), document);
                Binding<String> nameBind = settingsInjector
                        .getBinding(Key.get(String.class,
                                new ProcessorSettingImpl("viewName")));
                if (documentScopedObjects != null) {
                    seededObjects.putAll(documentScopedObjects);
                }
                BiomedicusScopes.runInDocumentScope(() -> {
                    settingsInjector.getInstance(documentProcessorClass)
                            .process();
                    return null;
                }, seededObjects);
                return null;
            });
        } catch (Exception e) {
            LOGGER.error("Error during processing");
            throw new BiomedicusException(e);
        }
    }

    public void processingFinished() throws BiomedicusException {
        checkNotNull(processorContext,
                "Processor context is null, initialize not ran");

        checkNotNull(settingsInjector,
                "Settings injector is null, initialize not ran");

        try {
            processorContext.call(() -> {
                for (Class<? extends PostProcessor> post : postProcessors) {
                    settingsInjector.getInstance(post).afterProcessing();
                }
                return null;
            });
        } catch (Exception e) {
            LOGGER.error("Error during post processing.");
            throw new BiomedicusException(e);
        }
    }


}
