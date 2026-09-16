package com.metromind.data;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Loads a {@link MetroNetwork} from the {@code metro-network.json} source of truth.
 *
 * <p>The loader accepts an explicit file path (primary API, fully testable) and
 * also provides {@link #loadDefault()} as a convenience for the monorepo layout,
 * searching common locations for the dataset file.</p>
 */
public final class MetroNetworkLoader {

    private static final String DEFAULT_FILENAME = "metro-network.json";

    private MetroNetworkLoader() {
    }

    /**
     * Loads a metro network from a JSON file.
     *
     * @param jsonPath the path to {@code metro-network.json}
     * @return the deserialised {@link MetroNetwork}
     * @throws IOException if the file cannot be read or parsed
     */
    public static MetroNetwork load(Path jsonPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonPath.toFile(), MetroNetwork.class);
    }

    /**
     * Loads the default dataset by searching common locations in the monorepo,
     * then the classpath as a fallback.
     *
     * @return the deserialised {@link MetroNetwork}
     * @throws IOException if the default dataset cannot be located or parsed
     */
    public static MetroNetwork loadDefault() throws IOException {
        List<Path> candidates = List.of(
                Paths.get("data", DEFAULT_FILENAME),
                Paths.get("..", "data", DEFAULT_FILENAME),
                Paths.get("../../data", DEFAULT_FILENAME),
                Paths.get("../../../data", DEFAULT_FILENAME));

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return load(candidate.toAbsolutePath());
            }
        }

        try (InputStream in = MetroNetworkLoader.class.getClassLoader()
                .getResourceAsStream(DEFAULT_FILENAME)) {
            if (in == null) {
                throw new IOException("Could not locate " + DEFAULT_FILENAME
                        + " on the filesystem or classpath");
            }
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(in, MetroNetwork.class);
        }
    }
}