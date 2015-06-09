package alexh.ci;

import static alexh.Unchecker.uncheckedGet;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nullable;
import java.io.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(ScriptRunner.class);

    private final ProcessBuilder builder;
    private Optional<File> output = Optional.empty();
    private Executor exe = ForkJoinPool.commonPool();

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

    public ScriptRunner executeWith(Executor exe) {
        this.exe = exe;
        return this;
    }

    private static void print(@Nullable PrintWriter writer, @Nullable String outLine, @Nullable String errLine) {
        if (writer != null) {
            if (errLine != null) writer.println(errLine);
            if (outLine != null) writer.println(outLine);
        }
        else {
            if (errLine != null) log.warn(errLine);
            if (outLine != null) log.info(outLine);
        }
    }

    private static void flush(@Nullable PrintWriter writer) {
        if (writer != null) writer.flush();
    }

    private static CompletableFuture<?> writeAsync(PrintWriter writer, BufferedReader reader) {
        return CompletableFuture.supplyAsync(() -> uncheckedGet(() -> Optional.ofNullable(reader.readLine())))
            .thenCompose(line -> {
                if (line.isPresent()) {
                    print(writer, line.get(), null);
                    flush(writer);
                    return writeAsync(writer, reader);
                }
                return CompletableFuture.completedFuture(null);
            });
    }

    /** @return future with the script exit code as result */
    public CompletableFuture<Integer> run() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Process process = builder.start();

                try (InputStream in = process.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                     InputStream errorStream = process.getErrorStream();
                     BufferedReader errReader = new BufferedReader(new InputStreamReader(errorStream));
                     @Nullable
                     PrintWriter writer = output.isPresent() ? new PrintWriter(output.get()) : null) {

                    CompletableFuture.allOf(writeAsync(writer, reader),
                        writeAsync(writer, errReader)).join();
                }
                return process.exitValue();
            }
            catch (IOException ex) { ex.printStackTrace(); return 1; }
        }, exe);
    }
}