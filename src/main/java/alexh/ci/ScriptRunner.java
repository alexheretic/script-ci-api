package alexh.ci;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nullable;
import java.io.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(ScriptRunner.class);

    private final ProcessBuilder builder;
    private Optional<File> output = Optional.empty();

    public ScriptRunner(String scriptPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(scriptPath))) {
            String header = reader.readLine();
            this.builder = new ProcessBuilder(header.replaceFirst("^#!", ""), scriptPath);
        }
        catch (IOException ex) { throw Throwables.propagate(ex); }
    }

    public ScriptRunner outputTo(File outputPath) {
        this.output = Optional.of(outputPath);
        return this;
    }

    public ScriptRunner useDirectory(File directory) {
        builder.directory(directory);
        return this;
    }

    private static void print(@Nullable PrintWriter writer, @Nullable String outLine, @Nullable String errLine) {
        if (writer != null) {
            if (errLine != null) writer.println(errLine);
            if (outLine != null) writer.println(outLine);
        }
        else {
            if (errLine != null) System.err.println(errLine);
            if (outLine != null) System.out.println(outLine);
        }
    }

    /** @return future with the script exit code as result */
    public CompletableFuture<Integer> run() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Process process = builder.start();

                try (InputStream in = process.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                     InputStream err = process.getErrorStream();
                     BufferedReader errReader = new BufferedReader(new InputStreamReader(err));
                     @Nullable
                     PrintWriter writer = output.isPresent() ? new PrintWriter(output.get()) : null) {

                    while (process.isAlive()) {
                        String outLine = null, errLine = null;
                        while ((errLine = errReader.readLine()) != null || (outLine = reader.readLine()) != null) {
                            print(writer, outLine, errLine);
                        }
                    }
                }
                return process.exitValue();
            }
            catch (IOException ex) { ex.printStackTrace(); return 1; }
        });
    }
}