package alexh.ci.resource;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import alexh.ci.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@Path("jobs")
public class JobResource {

    private static final Logger log = LoggerFactory.getLogger(JobResource.class);

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
        if (!workHome.exists())
            checkArgument(workHome.mkdir());

        new ScriptRunner(new File("jobs/single-job/scripts/script.sh").getAbsolutePath())
            .useDirectory(workHome)
//            .outputTo("")
            .run()
            .thenAccept(exit -> log.info("Ran single-job with exit code: "+ exit));
    }
}
