import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Scanner;

public class Updater {
    public static String version;
    public static String url;

    public static String confVer;
    public static String confRepo;

    public static Path jarPath;

    public static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    public static void deleteTreeWithRetry(Path root, int attempts, Duration backoff) throws IOException {
        IOException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                deleteTree(root);
                return;
            } catch (IOException e) {
                last = e;
                try { Thread.sleep(backoff.toMillis()); } catch (InterruptedException ignored) {}
            }
        }
        if (last != null) throw last;
    }

    public static String getVersion() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/Vidrolll/"+confRepo+"/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GameUpdater/1.0 (+https://example.com)")
                .header("X-GitHub-Api-Version", "2022-11-28") // optional but recommended
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            JSONObject json = new JSONObject(resp.body());
            version = json.getString("tag_name"); // e.g. "v1.2.4"
            JSONArray assets = json.getJSONArray("assets");
            url = assets.getJSONObject(0).getString("browser_download_url");
            System.out.println("Version = " + version + ", url = " + url);
            return version;
        } else {
            System.out.println(resp.statusCode());
            System.exit(-1);
        }
        return null;
    }

    public static void downloadUpdate() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL) // GitHub redirects are common
                .build();
        Path downloads = Paths.get(System.getProperty("java.io.tmpdir"), "updater");
        Files.createDirectories(downloads);
        Path zipPath = downloads.resolve(version + ".zip");
        HttpRequest dl = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "GameUpdater/1.0")
                // Add auth if private: .header("Authorization", "Bearer " + System.getenv("GITHUB_TOKEN"))
                .build();
        HttpResponse<Path> dlResp = client.send(dl, HttpResponse.BodyHandlers.ofFile(zipPath));
        if (dlResp.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + dlResp.statusCode());
        }
        System.out.println("Downloaded: " + zipPath);
        unzip(zipPath,Paths.get(jarPath.toUri()));
        Path newDir = Paths.get(jarPath+"/"+version);
        Path oldDir = Paths.get(jarPath+"/"+confVer);
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "updater");
        if (Files.exists(oldDir) && !oldDir.equals(newDir)) {
            deleteTreeWithRetry(oldDir, 3, java.time.Duration.ofMillis(250));
        }
        if (Files.exists(tempDir)) {
            deleteTreeWithRetry(tempDir, 3, java.time.Duration.ofMillis(250));
        }
    }


    static void unzip(Path zipFile, Path destDir) throws IOException {
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(e.getName()).normalize();
                if (!out.startsWith(destDir)) throw new IOException("Zip path escape: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        } catch(Exception e) {
            e.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    public static void readConfig() {
        System.out.println(jarPath+"/updater.config");
        try(Scanner reader = new Scanner(new File(jarPath+"/updater.config"))) {
            while(reader.hasNext()) {
                String[] line = reader.nextLine().split(":");
                if(line[0].equals("ver")) confVer = line[1];
                if(line[0].equals("repo")) confRepo = line[1];
            }
        } catch(IOException e) {
            e.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    public static void updateConf() {
        try(FileWriter writer = new FileWriter(new File(jarPath+"/updater.config"))) {
            writer.write("ver:"+version+"\n"+"repo:"+confRepo);
        } catch(IOException e) {
            e.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        try {
            jarPath = Paths.get(
                    Updater.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).toAbsolutePath().normalize().getParent();
        } catch(Exception e) {
            e.printStackTrace(System.err);
            System.exit(-1);
        }
        readConfig();
        switch(args[0]) {
            case "--check":
                try {
                    String latestVersion = getVersion();
                    if(!latestVersion.equals(confVer)) System.exit(1);
                    else System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    System.exit(-1);
                }
            case "--apply":
                try {
                    getVersion();
                    downloadUpdate();
                    updateConf();
                    System.exit(1);
                } catch(Exception e) {
                    e.printStackTrace(System.err);
                    System.exit(-1);
                }
            default:
                System.exit(-1);
        }
    }
}