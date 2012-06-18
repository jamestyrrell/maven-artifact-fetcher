package org.funtime.maven.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.repository.internal.MavenServiceLocator;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.connector.wagon.WagonProvider;
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;
import org.sonatype.aether.util.artifact.DefaultArtifact;

public class ArtifactFetcher {

    private static final String GAV = "gav";
    private static final String CLASSIFIER = "classifier";
    private static final String EXTENSION = "extension";
    private static final String REPO_URL = "repo-url";
    private static final String OUTPUT = "output";

    public static void main(String[] args) throws Exception {

        final RepositorySystem repoSystem = newRepositorySystem();
        final RepositorySystemSession session = newSession(repoSystem);
        final Map<String, String> keyValues = extractKeyValues(args);
        final String[] gavParts = getGavParts(getValue(GAV, keyValues));
        final String extension = getValue(EXTENSION, keyValues);
        final String repoUrl = getValue(REPO_URL, keyValues);
        final String outputPath = getValue(OUTPUT, keyValues);

        String classifier = keyValues.get(CLASSIFIER);

        if (StringUtils.isBlank(classifier)) {
            classifier = null;
        }

        final DefaultArtifact artifact = new DefaultArtifact(gavParts[0], gavParts[1], classifier, extension, gavParts[2], null);
        final Dependency dependency = new Dependency(artifact, null);
        final RemoteRepository central = new RemoteRepository("remote-repo", "default", repoUrl);
        central.setPolicy(true, new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_WARN));

        final ArtifactRequest artifactRequest = new ArtifactRequest(dependency.getArtifact(), Arrays.asList(central), null);
        final ArtifactResult result = repoSystem.resolveArtifact(session, artifactRequest);

        final String outputDir = outputPath.substring(0, outputPath.lastIndexOf('/'));
        new File(outputDir).mkdirs();

        IOUtils.copy(new FileInputStream(result.getArtifact().getFile()), new FileOutputStream(outputPath));
    }

    private static String getValue(final String key, final Map<String, String> keyValues) {
        final String value = keyValues.get(key);
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException("Please provide a non-null '" + key + "' value");
        }
        return value.trim();
    }

    private static String[] getGavParts(final String gav) {
        final int firstSeparator = gav.indexOf(':');
        final int lastSeparator = gav.lastIndexOf(':');
        if (firstSeparator == -1 || lastSeparator == -1 || firstSeparator == lastSeparator) {
            throw new IllegalStateException("Invalid GAV: '" + gav + "'");
        }
        final String[] parts = new String[3];
        parts[0] = checkGavPart(gav.substring(0, firstSeparator), gav);
        parts[1] = checkGavPart(gav.substring(firstSeparator + 1, lastSeparator), gav);
        parts[2] = checkGavPart(gav.substring(lastSeparator + 1, gav.length()), gav);
        return parts;
    }

    private static String checkGavPart(final String part, final String gav) {
        if (part == null || part.length() == 0) {
            throw new IllegalStateException("Invalid GAV: '" + gav + "'");
        }
        return part.trim();
    }

    private static Map<String, String> extractKeyValues(final String[] args) {
        final Map<String, String> map = new HashMap<String, String>();
        for (String arg : args) {
            int separatorIndex = arg.indexOf('=');
            if (separatorIndex == -1) {
                throw new IllegalArgumentException("Invalid argument: '" + arg + "'");
            }
            final String key = arg.substring(0, separatorIndex);
            final String value = arg.substring(separatorIndex + 1, arg.length());
            map.put(key, value);
        }
        return map;
    }

    private static RepositorySystem newRepositorySystem() {
        MavenServiceLocator locator = new MavenServiceLocator();
        locator.setServices(WagonProvider.class, new ManualWagonProvider());
        locator.addService(RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession newSession(RepositorySystem system) {
        MavenRepositorySystemSession session = new MavenRepositorySystemSession();
        LocalRepository localRepo = new LocalRepository("local-repo");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepo));
        return session;
    }

    public static class ManualWagonProvider implements WagonProvider {

        public Wagon lookup(String roleHint)
                throws Exception {
            if ("http".equals(roleHint) || "https".equals(roleHint)) {
                return new LightweightHttpWagon();
            }
            return null;
        }

        public void release(Wagon wagon) {
            // Do nothing
        }
    }
}
