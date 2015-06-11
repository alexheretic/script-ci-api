package alexh.ci.model;

import static alexh.Unchecker.uncheckedGet;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.PrintWriter;
import java.util.Optional;

public class Script {

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
    }
}
