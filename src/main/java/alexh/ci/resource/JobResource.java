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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.*;
import java.io.File;
import java.io.FileNotFoundException;
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

    @GET
    @Path("{jobId}")
    public Job job(@PathParam("jobId") int id) {
        File jobDir = new File("jobs/"+ id);
        if (!jobDir.exists()) throw new NotFoundException();
        return new Job.WrittenJob(jobDir);
    }

    @POST
    @Path("{jobId}/delete")
    public void deleteJob(@PathParam("jobId") int id) {
        File jobDir = new File("jobs/"+ id);
        if (!jobDir.exists()) throw new NotFoundException();
        checkArgument(jobDir.renameTo(new File("jobs/d" + jobDir.getName())));
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
