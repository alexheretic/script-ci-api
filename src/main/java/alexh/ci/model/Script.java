package alexh.ci.model;

import static alexh.Unchecker.uncheckedGet;
import static java.util.Collections.emptyMap;
import alexh.ci.ScriptRunner;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class Script {

    private static final Logger log = LoggerFactory.getLogger(Script.class);

    public Optional<? extends Script> okScript = Optional.empty();
    public Optional<? extends Script> errorScript = Optional.empty();
    public String code;

    public void validateIn() {
        if (code == null)
            throw new WebApplicationException(Response.status(400)
                .entity(ImmutableMap.of("message", "Missing Script#code"))
                .build());
        okScript.ifPresent(Script::validateIn);
        errorScript.ifPresent(Script::validateIn);
    }

    public static class WrittenScript extends Script {

        public final Optional<WrittenScript> okScript;
        public final Optional<WrittenScript> errorScript;

        @JsonIgnore
        public final File location;

        public WrittenScript(Script script, File location) {
            this.location = location;

            this.okScript = script.okScript.map(s -> new WrittenScript(s, childOkLocation()));
            this.errorScript = script.errorScript.map(s -> new WrittenScript(s, childErrorLocation()));
            this.code = script.code;
            try (PrintWriter writer = uncheckedGet(() -> new PrintWriter(location))) {
                writer.write(code);
            }
        }

        public WrittenScript(File location) {
            this.location = location;

            this.code = uncheckedGet(() -> Files.toString(location, Charsets.UTF_8));

            if (childOkLocation().exists()) this.okScript = Optional.of(new WrittenScript(childOkLocation()));
            else this.okScript = Optional.empty();

            if (childErrorLocation().exists()) this.errorScript = Optional.of(new WrittenScript(childErrorLocation()));
            else this.errorScript = Optional.empty();
        }

        private File childOkLocation() {
            return new File(location.getPath().replaceFirst("\\.sh$", "") + "o.sh");
        }

        private File childErrorLocation() {
            return new File(location.getPath().replaceFirst("\\.sh$", "") + "e.sh");
        }

        public CompletableFuture<Integer> run(File runsDirectory, Executor executor) {
            log.debug("Running script "+ location);

            return new ScriptRunner(location.getAbsolutePath())
                .outputTo(runsDirectory)
                .useDirectory(new File(runsDirectory.getParentFile().getParentFile(),  "work"))
                .executeWith(executor)
                .run()
                .thenCompose(exit -> {
                    log.debug(location + " ran with exit code: " + exit);

                    if (exit == 0 && okScript.isPresent()) return okScript.get().run(runsDirectory, executor);
                    else if (exit != 0 && errorScript.isPresent()) return errorScript.get().run(runsDirectory, executor);
                    return CompletableFuture.completedFuture(exit);
                });
        }

        public Map<String, Object> status(File runsDirectory) {
            final Map<String, Object> status = new LinkedHashMap<>();

            Optional.of(new File(runsDirectory, location.getName() + "-status.json"))
                .filter(File::exists)
                .map(f -> {
                    try { return new ObjectMapper().readValue(f, Map.class); }
                    catch (Exception ex) { return emptyMap(); }
                })
                .ifPresent(status::putAll);

            Optional.of(new File(runsDirectory, location.getName() + "-out.log"))
                .filter(File::exists)
                .map(f -> uncheckedGet(() -> Files.toString(f, Charsets.UTF_8)))
                .ifPresent(log -> status.put("log", log));

            okScript.map(s -> s.status(runsDirectory))
                .ifPresent(scriptStatus -> status.put("okScriptStatus", scriptStatus));
            errorScript.map(s -> s.status(runsDirectory))
                .ifPresent(scriptStatus -> status.put("errorScriptStatus", scriptStatus));
            return status;
        }
    }


}
