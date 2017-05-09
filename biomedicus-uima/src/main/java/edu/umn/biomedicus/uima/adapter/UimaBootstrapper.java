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

package edu.umn.biomedicus.uima.adapter;

import com.google.inject.Module;
import edu.umn.biomedicus.framework.Application;
import edu.umn.biomedicus.framework.Bootstrapper;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.uima.labels.LabelAdapterFactory;
import edu.umn.biomedicus.uima.labels.LabelAdapters;
import edu.umn.biomedicus.uima.labels.UimaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class UimaBootstrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(UimaBootstrapper.class);

    public static Application create(Module... additionalModules) throws BiomedicusException {
        ArrayList<Module> modules = new ArrayList<>();
        modules.add(new UimaModule());
        modules.addAll(Arrays.asList(additionalModules));
        Application application = Bootstrapper.create(modules.toArray(new Module[modules.size()]));
        Path uimaPluginsFile = application.confFolder().resolve("uimaPlugins.txt");
        List<String> pluginClassNames;
        try {
            pluginClassNames = Files.lines(uimaPluginsFile).collect(Collectors.toList());
        } catch (IOException e) {
            throw new BiomedicusException(e);
        }

        LabelAdapters labelAdapters = application.getInstance(LabelAdapters.class);
        for (String pluginClassName : pluginClassNames) {
            if (pluginClassName.isEmpty()) {
                continue;
            }
            LOGGER.info("Loading uima plugin: {}", pluginClassName);

            Class<? extends UimaPlugin> pluginClass;
            try {
                pluginClass = Class.forName(pluginClassName).asSubclass(UimaPlugin.class);
            } catch (ClassNotFoundException e) {
                throw new BiomedicusException(e);
            }

            UimaPlugin plugin = application.getInstance(pluginClass);

            Map<Class<?>, LabelAdapterFactory> labelAdapterFactories = plugin.getLabelAdapterFactories();
            for (Map.Entry<Class<?>, LabelAdapterFactory> entry : labelAdapterFactories.entrySet()) {
                LabelAdapterFactory value = entry.getValue();
                labelAdapters.addLabelAdapter(entry.getKey(), value);
            }
        }

        return application;
    }
}
