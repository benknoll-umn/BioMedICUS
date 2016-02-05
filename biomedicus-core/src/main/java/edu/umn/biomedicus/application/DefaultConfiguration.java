package edu.umn.biomedicus.application;

import com.google.inject.Inject;
import edu.umn.biomedicus.common.settings.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

/**
 *
 */
class DefaultConfiguration implements BiomedicusConfiguration {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Settings settings;

    private final Path biomedicusHomeDir;

    private final Path configDir;

    private final Path dataDir;

    @Inject
    DefaultConfiguration() throws IOException {
        String home = System.getProperty("biomedicus.path.home");
        if (home == null) {
            home = System.getenv("BIOMEDICUS_HOME");
        }
        if (home == null) {
            throw new IllegalStateException("BioMedICUS home directory is not configured. Use either the" +
                    " BIOMEDICUS_HOME environment variable or the Java property -Dbiomedicus.path.home=[home dir].");
        }
        biomedicusHomeDir = Paths.get(home).normalize();

        String conf = System.getProperty("biomedicus.path.conf");
        Path configDir;
        if (conf == null) {
            conf = System.getenv("BIOMEDICUS_CONF");
        }

        if (conf != null) {
            configDir = Paths.get(conf);
            if (!configDir.isAbsolute()) {
                configDir = biomedicusHomeDir.resolve(configDir);
            }
        } else {
            configDir = biomedicusHomeDir.resolve("config");
        }
        LOGGER.info("Using configuration directory: {}", configDir);
        this.configDir = configDir;

        Path biomedicusProperties = configDir.resolve("biomedicus.properties");
        Settings.Builder builder = Settings.builder().loadProperties(biomedicusProperties);

        Properties properties = System.getProperties();

        for (Map.Entry<Object, Object> propertyEntry : properties.entrySet()) {
            String key = (String) propertyEntry.getKey();
            if (key.startsWith("biomedicus.")) {
                builder.put(key.replaceFirst("\\Abiomedicus\\.", ""), (String) propertyEntry.getValue());
            }
        }

        settings = builder.build();

        if (settings.containsSetting("path.data")) {
            dataDir = absoluteOrResolveAgainstHome(settings.getAsPath("path.data"));
        } else {
            dataDir = biomedicusHomeDir.resolve("data");
        }
    }

    @Override
    public Settings getSettings() {
        return settings;
    }

    @Override
    public Path getDataDir() {
        return dataDir;
    }

    private Path absoluteOrResolveAgainstHome(Path path) {
        if (path.isAbsolute()) {
            return path;
        }
        return biomedicusHomeDir.resolve(path);
    }
}