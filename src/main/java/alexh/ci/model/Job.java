package alexh.ci.model;

import static alexh.Unchecker.unchecked;
import static com.google.common.base.Preconditions.checkArgument;
import alexh.Fluent;
import alexh.weak.Dynamic;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class Job {

    public Script okScript;

    // for update ui -> api only
    public Integer id;

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
        private final File directory;

        public WrittenJob(Job job, File directory) {
            if (!directory.exists()) checkArgument(directory.mkdirs());
            File scriptDir = new File(directory, "scripts");
            if (!scriptDir.exists()) checkArgument(scriptDir.mkdir());

            this.directory = directory;
            this.id = Integer.valueOf(directory.getName());
            this.okScript = new Script.WrittenScript(job.okScript, new File(scriptDir, "script-o.sh"));
        }

        public WrittenJob(File directory) {
            this.directory = directory;
            this.id = Integer.valueOf(directory.getName());
            this.okScript = new Script.WrittenScript(new File(directory, "scripts/script-o.sh"));
        }

        public CompletableFuture<Integer> run(Executor executor) {
            // todo not always run in runs/1
            File runsDir = new File(okScript.location.getParentFile().getParentFile(), "runs/1");
            if (!runsDir.exists()) checkArgument(runsDir.mkdirs());
            unchecked(() -> FileUtils.cleanDirectory(runsDir));
            return okScript.run(runsDir, executor);
        }

        public Map<String, Object> status(int runId) {
            Map rootScriptStatus = okScript.status(new File(directory, "runs/"+ runId));
            LinkedList<Dynamic> statuses = listStatuses(rootScriptStatus);

            Map<String, Object> status = new Fluent.HashMap<String, Object>()
                .append("okScriptStatus", rootScriptStatus)
                .append("run", runId);

            statuses.getFirst().get("started").maybe()
                .ifPresent(started -> status.put("started", started.asString()));
            statuses.getLast().get("ended").maybe()
                .ifPresent(ended -> status.put("ended", ended.asString()));
            statuses.getLast().get("exitCode").maybe()
                .ifPresent(exitCode -> status.put("exitCode", exitCode.convert().intoInteger()));

            return status;
        }

        private LinkedList<Dynamic> listStatuses(Map rootScriptStatus) {
            LinkedList<Dynamic> list = new LinkedList<>();

            Dynamic scriptStatus = Dynamic.from(rootScriptStatus);
            while (scriptStatus.get("started").isPresent()) {
                list.add(scriptStatus);
                scriptStatus =  scriptStatus.get("okScriptStatus").maybe()
                    .orElse(scriptStatus.get("errorScriptStatus"));
            }

            return list;
        }
    }
}
