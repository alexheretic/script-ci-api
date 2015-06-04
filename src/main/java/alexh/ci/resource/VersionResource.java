package alexh.ci.resource;

import static java.util.Collections.unmodifiableMap;
import alexh.Fluent;
import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

@Path("version")
public class VersionResource {

    private static final Map versionMap;
    static {
        Properties props = new Properties();
        try (InputStream in = Resources.asByteSource(Resources.getResource("build.properties")).openStream()) {
            props.load(in);

        }
        catch (IOException ex) { Throwables.propagate(ex); }
        versionMap = unmodifiableMap(new Fluent.LinkedHashMap<>()
            .append("version", props.getProperty("version"))
            .append("buildTime", props.getProperty("build.time")));
    }

    @GET
    public Map getVersion() {
        return versionMap;
    }
}
