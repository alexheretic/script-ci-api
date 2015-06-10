package alexh.ci.resource;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import alexh.Fluent;
import alexh.ci.ScriptRunner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Path("jobs")
@Produces(APPLICATION_JSON)
public class JobResource {

    private static final Logger log = LoggerFactory.getLogger(JobResource.class);

    private final Executor singleExecutor;
    private final ObjectMapper objectMapper;

    public JobResource(Executor singleExecutor, ObjectMapper objectMapper) {
        this.singleExecutor = singleExecutor;
        this.objectMapper = objectMapper;
    }

    @GET
    public List<Map> jobs() {
        return emptyList();
    }

    @POST
    @Path("single")
    public synchronized void postScript(String script) throws Exception {
        File scriptHome = new File("jobs/single-job/scripts");
        if (!scriptHome.exists())
            checkArgument(scriptHome.mkdirs());

        try (PrintWriter writer = new PrintWriter(new File(scriptHome, "script.sh"))) {
            writer.write(script);
        }

        File workHome = new File("jobs/single-job/work");
        if (!workHome.exists()) checkArgument(workHome.mkdir());

        log.info("Running single-job...");
        final Fluent.Map status = new Fluent.HashMap<>().append("started", Instant.now().toString());
        writeSingleJobStatus(status);

        new ScriptRunner(new File("jobs/single-job/scripts/script.sh").getAbsolutePath())
            .useDirectory(workHome)
            .executeWith(singleExecutor)
            .outputTo(new File("jobs/single-job/out.log"))
            .run()
            .thenAccept(exit -> {
                writeSingleJobStatus(status
                    .append("ended", Instant.now().toString())
                    .append("exitCode", exit));
                log.info("Ran single-job with exit code: " + exit);
            });
    }

    private void writeSingleJobStatus(Map status) {
        try (PrintWriter writer = new PrintWriter(new File("jobs/single-job/status.json"))) {
            writer.write(objectMapper.writeValueAsString(status));
        }
        catch (JsonProcessingException | FileNotFoundException e) { Throwables.propagate(e); }
    }

    @GET
    @Path("single/out")
    public Map singleJobOutput() throws IOException {
        File status = new File("jobs/single-job/status.json");
        if (!status.exists()) return emptyMap();

        return new Fluent.HashMap<>()
            .appendAll(objectMapper.readValue(status, Map.class))
            .append("log", Files.toString(new File("jobs/single-job/out.log"), UTF_8));
    }

    @GET
    @Path("single")
    public Map singleScript() throws IOException {
        return new Fluent.HashMap<>()
            .append("script", Files.toString(new File("jobs/single-job/scripts/script.sh"), UTF_8));
    }
}
