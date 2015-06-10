package alexh.ci;

import alexh.ci.resource.JobResource;
import alexh.ci.resource.VersionResource;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.java8.Java8Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

public class ScriptCiApplication extends Application<Configuration> {

    public static void main(String[] args) throws Exception {
        new ScriptCiApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        bootstrap.addBundle(new Java8Bundle());
    }

    @Override
    public void run(Configuration configuration, Environment environment) throws Exception {
        environment.jersey().register(new VersionResource());
        environment.jersey().register(new JobResource(environment.lifecycle().executorService("single-exe")
            .maxThreads(1)
            .shutdownTime(Duration.seconds(30l))
            .build(), environment.getObjectMapper()));

        configureCors(environment);
    }

    private void configureCors(Environment environment) {
        FilterRegistration.Dynamic filter = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        filter.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        filter.setInitParameter("allowedHeaders", "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin");
        filter.setInitParameter("allowCredentials", "true");
    }
}
