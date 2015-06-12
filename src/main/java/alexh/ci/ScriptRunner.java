package alexh.ci;

import static alexh.Unchecker.uncheck;
import static com.google.common.base.Preconditions.checkArgument;
import alexh.Fluent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nullable;
import java.io.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;

public class ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(ScriptRunner.class);

    private final ProcessBuilder builder;
    private Optional<File> outputDir = Optional.empty();
    private Executor exe = ForkJoinPool.commonPool();
    private final Fluent.Map<Object, Object> status = new Fluent.LinkedHashMap<>();
    private final File script;

    public ScriptRunner(String scriptPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(scriptPath))) {
            String header = reader.readLine();
            this.builder = new ProcessBuilder(header.replaceFirst("^#!", ""), scriptPath);
        }
        catch (IOException ex) { throw Throwables.propagate(ex); }
        this.script = new File(scriptPath);
    }

    public ScriptRunner outputTo(File outputDirectory) {
        this.outputDir = Optional.of(outputDirectory);
        return this;
    }

    public ScriptRunner useDirectory(File directory) {
        builder.directory(directory);
        if (!directory.exists()) checkArgument(directory.mkdirs());
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
        return CompletableFuture.supplyAsync(uncheck(reader::readLine), exe)
            .thenCompose(line -> {
                if (line != null) {
                    print(writer, line);
                    flush(writer);
                    return writeAsync(writer, reader, exe);
                }
                return CompletableFuture.completedFuture(null);
            });
    }

    private void writeStatus() {
        outputDir.ifPresent(dir -> {
            try (PrintWriter writer = new PrintWriter(new File(dir, script.getName() + "-status.json"))) {
                writer.write(new ObjectMapper().writeValueAsString(status));
            }
            catch (IOException ex) { Throwables.propagate(ex); }
        });
    }

    private Optional<File> outLog() {
        return outputDir.map(dir -> new File(dir, script.getName() + "-out.log"));
    }

    /** @return future with the script exit code as result */
    public CompletableFuture<Integer> run() {
        outputDir.filter(file -> !file.getParentFile().exists())
            .ifPresent(outFile -> checkArgument(outFile.getParentFile().mkdirs()));

        return CompletableFuture.supplyAsync(() -> {
            ExecutorService writerPool = null;
            try (@Nullable PrintWriter writer = outLog().isPresent() ? new PrintWriter(outLog().get()) : null) {
                outputDir.filter(f -> !f.exists()).ifPresent(File::mkdirs);

                status.append("started", Instant.now().toString());
                writeStatus();

                Process process = builder.start();
                writerPool = Executors.newWorkStealingPool(2);

                try (InputStream in = process.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                     InputStream errorStream = process.getErrorStream();
                     BufferedReader errReader = new BufferedReader(new InputStreamReader(errorStream))) {

                    CompletableFuture<?> errWrite = writeAsync(writer, errReader, writerPool);
                    CompletableFuture<?> stdWrite = writeAsync(writer, reader, writerPool);

                    process.waitFor();

                    status.append("ended", Instant.now().toString())
                        .append("exitCode", process.exitValue());
                    writeStatus();

                    try {
                        stdWrite.get(250, TimeUnit.MILLISECONDS);
                        errWrite.get(250, TimeUnit.MILLISECONDS);
                    }
                    catch (TimeoutException timeout) { log.warn("Timed out waiting for input streams to write", timeout); }
                }
                return process.exitValue();
            }
            catch (Exception ex) { ex.printStackTrace(); return 1; }
            finally { if (writerPool != null) writerPool.shutdownNow(); }
        }, exe);
    }
}