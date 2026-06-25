package budgethelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppUpdateService {
    private static final String REPO_OWNER = "ecrawford4";
    private static final String REPO_NAME = "budget-helper";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME
            + "/releases/latest";
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ASSET_PATTERN = Pattern.compile(
            "\\{[^{}]*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*\\}");

    private final HttpClient httpClient;

    public AppUpdateService() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Optional<UpdateInfo> findUpdate(String currentVersion) throws IOException, InterruptedException {
        String payload = fetchLatestReleasePayload();
        String latestTag = extractTag(payload);
        if (latestTag == null) {
            throw new IOException("Could not read latest release tag from GitHub.");
        }
        if (compareVersions(normalizeVersion(latestTag), normalizeVersion(currentVersion)) <= 0) {
            return Optional.empty();
        }
        String installerUrl = selectInstallerUrl(payload);
        if (installerUrl == null) {
            return Optional.empty();
        }
        String installerFileName = installerUrl.substring(installerUrl.lastIndexOf('/') + 1);
        return Optional.of(new UpdateInfo(latestTag, installerUrl, installerFileName));
    }

    public Path downloadInstaller(UpdateInfo updateInfo) throws IOException, InterruptedException {
        Path downloadDirectory = Files
                .createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "budget-helper-updates"));
        Path destination = downloadDirectory.resolve(updateInfo.installerFileName());
        Path tempFile = destination.resolveSibling(updateInfo.installerFileName() + ".part");

        HttpRequest request = HttpRequest.newBuilder(URI.create(updateInfo.installerUrl()))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "budget-helper-updater")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 300) {
            throw new IOException("Installer download failed with status " + response.statusCode() + ".");
        }
        try (InputStream inputStream = response.body(); OutputStream outputStream = Files.newOutputStream(tempFile)) {
            inputStream.transferTo(outputStream);
        }
        Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return destination;
    }

    public void launchInstaller(Path installerPath) throws IOException {
        String fileName = installerPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".msi")) {
            new ProcessBuilder("msiexec", "/i", installerPath.toAbsolutePath().toString()).start();
            return;
        }
        new ProcessBuilder(installerPath.toAbsolutePath().toString()).start();
    }

    private String fetchLatestReleasePayload() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "budget-helper-updater")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("GitHub latest release request failed with status " + response.statusCode() + ".");
        }
        return response.body();
    }

    private static String extractTag(String payload) {
        Matcher matcher = TAG_PATTERN.matcher(payload);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String selectInstallerUrl(String payload) {
        Matcher matcher = ASSET_PATTERN.matcher(payload);
        List<AssetCandidate> candidates = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            String url = matcher.group(2).replace("\\/", "/");
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".exe") || lowerName.endsWith(".msi")) {
                candidates.add(new AssetCandidate(name, url));
            }
        }
        return candidates.stream()
                .sorted(Comparator
                        .comparing((AssetCandidate candidate) -> !candidate.name().toLowerCase(Locale.ROOT)
                                .contains("budgethelper"))
                        .thenComparing(candidate -> !candidate.name().toLowerCase(Locale.ROOT).endsWith(".exe")))
                .map(AssetCandidate::url)
                .findFirst()
                .orElse(null);
    }

    private static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = parseVersionParts(left);
        int[] rightParts = parseVersionParts(right);
        int maxSize = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < maxSize; index++) {
            int leftPart = index < leftParts.length ? leftParts[index] : 0;
            int rightPart = index < rightParts.length ? rightParts[index] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int[] parseVersionParts(String version) {
        String[] tokens = version.split("[^0-9]+");
        int count = 0;
        for (String token : tokens) {
            if (!token.isBlank()) {
                count++;
            }
        }
        int[] parts = new int[count];
        int index = 0;
        for (String token : tokens) {
            if (!token.isBlank()) {
                parts[index++] = Integer.parseInt(token);
            }
        }
        return parts;
    }

    private record AssetCandidate(String name, String url) {
    }

    public record UpdateInfo(String latestTag, String installerUrl, String installerFileName) {
    }
}
