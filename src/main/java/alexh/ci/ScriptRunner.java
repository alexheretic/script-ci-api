package alexh.ci;

import static alexh.Unchecker.uncheck;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nullable;
import java.io.*;
import java.util.Optional;
import java.util.concurrent.*;

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

    private static void print(@Nullable PrintWriter writer, String outLine) {
        if (writer != null)  writer.println(outLine);
        else log.info(outLine);
    }

    private static void flush(@Nullable PrintWriter writer) {
        if (writer != null) writer.flush();
    }

    private static CompletableFuture<?> writeAsync(PrintWriter writer, BufferedReader reader, Executor exe) {
        return CompletableFuture.supplyAsync(uncheck(() -> reader.readLine()), exe)
            .thenCompose(line -> {
                if (line != null) {
                    print(writer, line);
                    flush(writer);
                    return writeAsync(writer, reader, exe);
                }
                return CompletableFuture.completedFuture(null);
            });
    }

    /** @return future with the script exit code as result */
    public CompletableFuture<Integer> run() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Process process = builder.start();
                ExecutorService exe = Executors.newSingleThreadExecutor();

                try (InputStream in = process.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                     InputStream errorStream = process.getErrorStream();
                     BufferedReader errReader = new BufferedReader(new InputStreamReader(errorStream));
                     @Nullable
                     PrintWriter writer = output.isPresent() ? new PrintWriter(output.get()) : null) {

                    CompletableFuture<?> errWrite = writeAsync(writer, errReader, exe);
                    CompletableFuture<?> stdWrite = writeAsync(writer, reader, MoreExecutors.directExecutor());

                    process.waitFor();
                    stdWrite.join();
                    try { errWrite.get(200, TimeUnit.MILLISECONDS); }
                    catch (TimeoutException timeout) { log.warn("Timed out waiting for err stream write", timeout); }
                }
                return process.exitValue();
            }
            catch (Exception ex) { ex.printStackTrace(); return 1; }
        }, exe);
    }
}