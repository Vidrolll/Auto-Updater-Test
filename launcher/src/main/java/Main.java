import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        try{
            Process p = new ProcessBuilder(
                    Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                    "-jar",
                    "out/artifacts/updater_jar/updater.jar",
                    "--check"
            ).redirectErrorStream(true).start();

            String output = new String(p.getInputStream().readAllBytes());
            int exitCode = p.waitFor();

            System.out.println("Updater output:\n" + output);
            System.out.println("Exit code: " + exitCode);
            if(exitCode == 1) {
                p = new ProcessBuilder(
                        Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                        "-jar",
                        "out/artifacts/updater_jar/updater.jar",
                        "--apply"
                ).redirectErrorStream(true).start();
                output = new String(p.getInputStream().readAllBytes());
                exitCode = p.waitFor();
                System.out.println("Updater output:\n" + output);
                System.out.println("Exit code: " + exitCode);
            }
        } catch(IOException | InterruptedException e) {
            e.printStackTrace(System.err);
        }
    }
}