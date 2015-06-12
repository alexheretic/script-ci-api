package alexh.ci.resource;

import static alexh.weak.Converter.convert;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import alexh.Fluent;
import alexh.ci.ScriptRunner;
import alexh.ci.model.Job;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    public List<Job.WrittenJob> jobs() {
        return IntStream.rangeClosed(1, latestJobNumber())
            .mapToObj(id ->  new File("jobs/"+ id))
            .filter(File::exists)
            .map(Job.WrittenJob::new)
            .collect(toList());
    }

    /**
     * @param newJob new job configuration
     *   {
     *     okScript: {
     *       code: "#!...",
     *       errorScript: {
     *         code: "#!..."
     *       },
     *       okScript: {
     *         code: "#!...",
     *         okScript: {...}
     *       }
     *     }
     *   }
     *
     * @return result map
     *   {
     *     id: 123
     *   }
     */
    @POST
    @Consumes(APPLICATION_JSON)
    public Map newJob(Job newJob) throws Exception {
        if (newJob == null)
            throw new WebApplicationException(Response.status(400)
                .entity(ImmutableMap.of("message", "Missing payload"))
                .build());
        newJob.validateIn();

        File newJobDir;
        int newJobNumber;
        synchronized (this) {
            newJobNumber = latestJobNumber() + 1;
            newJobDir = new File("jobs/" + newJobNumber);
            checkArgument(newJobDir.mkdirs());
        }

        newJob.writeTo(newJobDir);

        return new Fluent.HashMap<>().append("id", newJobNumber);
    }

    private int latestJobNumber() {
        File jobs = new File("jobs");
        if (!jobs.exists()) checkArgument(jobs.mkdir());

        return Stream.of(jobs.list())
            .filter(name -> convert(name).intoIntegerWorks())
            .mapToInt(Integer::valueOf)
            .max()
            .orElse(0);
    }

    @PUT
    @Path("{jobId}")
    @Consumes(APPLICATION_JSON)
    public void updateJob(Job job) {
        if (job == null || job.id == null)
            throw new WebApplicationException(Response.status(400)
                .entity(ImmutableMap.of("message", "Missing payload"))
                .build());
        job.validateIn();

        job.writeTo(new File("jobs/"+ job.id));
    }

    @GET
    @Path("{jobId}")
    public Job.WrittenJob job(@PathParam("jobId") int id) {
        File jobDir = new File("jobs/"+ id);
        if (!jobDir.exists()) throw new NotFoundException();
        return new Job.WrittenJob(jobDir);
    }

    @POST
    @Path("{jobId}/delete")
    public void deleteJob(@PathParam("jobId") int id) {
        File jobDir = new File("jobs/"+ id);
        if (!jobDir.exists()) throw new NotFoundException();
        checkArgument(jobDir.renameTo(new File("jobs/" + id + "-deleted-" + Instant.now().toString().replace(":", ";"))));
    }

    /** @return result map { run: runId } */
    @POST
    @Path("{jobId}/run")
    public Map runJob(@PathParam("jobId") int id) {
        // todo jobs should have an executor each
        job(id).run(singleExecutor);
        // todo remove hardcode
        return new Fluent.HashMap<>().append("run", "1");
    }

    /**
     * @return status json
     * for example:
     * {
        "ended": "2015-06-12T09:09:52.753Z",
        "exitCode": 0,
        "run": 1,
        "started": "2015-06-12T09:09:52.688Z",
        "okScriptStatus": {
            "started": "2015-06-12T09:09:52.688Z",
            "ended": "2015-06-12T09:09:52.721Z",
            "exitCode": 0,
            "log": "...",
            "okScriptStatus": {
                "started": "2015-06-12T09:09:52.725Z",
                "ended": "2015-06-12T09:09:52.753Z",
                "exitCode": 0,
                "log": "..."
            }
        }
        }
     */
    @GET
    @Path("{jobId}/status/{runId}")
    public Map jobStatus(@PathParam("jobId") int id, @PathParam("runId") int run) {
        return job(id).status(run);
    }

    @GET
    @Path("{jobId}/status/latest")
    public Map jobStatus(@PathParam("jobId") int id) {
        return job(id).status(1);
    }

    @POST
    @Path("single")
    public synchronized void saveAndRunSingleScript(String script) throws Exception {
        File scriptHome = new File("jobs/single-job/scripts");
        if (!scriptHome.exists())
            checkArgument(scriptHome.mkdirs());

        try (PrintWriter writer = new PrintWriter(new File(scriptHome, "script.sh"))) {
            writer.write(script);
        }

        File workHome = new File("jobs/single-job/work");
        if (!workHome.exists()) checkArgument(workHome.mkdir());

        new ScriptRunner(new File("jobs/single-job/scripts/script.sh").getAbsolutePath())
            .useDirectory(workHome)
            .executeWith(singleExecutor)
            .outputTo(new File("jobs/single-job"))
            .run()
            .thenAccept(exit -> {
                log.info("Ran single-job with exit code: " + exit);
            });
    }

    @GET
    @Path("single/out")
    public Map singleJobOutput() throws IOException {
        File status = new File("jobs/single-job/script.sh-status.json");
        if (!status.exists()) return emptyMap();

        return new Fluent.HashMap<>()
            .appendAll(objectMapper.readValue(status, Map.class))
            .append("log", Files.toString(new File("jobs/single-job/script.sh-out.log"), UTF_8));
    }

    @GET
    @Path("single")
    public Map singleScript() throws IOException {
        return new Fluent.HashMap<>()
            .append("script", Files.toString(new File("jobs/single-job/scripts/script.sh"), UTF_8));
    }
}
