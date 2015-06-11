package alexh.ci.model;

import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.ImmutableMap;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.File;

public class Job {

    public Script okScript;

    public void validateIn() {
        if (okScript == null)
            throw new WebApplicationException(Response.status(400)
                .entity(ImmutableMap.of("message", "Missing Job#okScript"))
                .build());
        okScript.validateIn();
    }

    public WrittenJob writeTo(File directory) {
        return new WrittenJob(this, directory);
    }

    public static class WrittenJob extends Job {
        public final Script.WrittenScript okScript;
        public final int id;

        public WrittenJob(Job job, File directory) {
            checkArgument(directory.mkdirs());
            this.id = Integer.valueOf(directory.getName());
            this.okScript = new Script.WrittenScript(job.okScript, new File(directory, "scripts/script-o.sh"));
        }

        public WrittenJob(File directory) {
            this.id = Integer.valueOf(directory.getName());
            this.okScript = new Script.WrittenScript(new File(directory, "scripts/script-o.sh"));
        }
    }
}
