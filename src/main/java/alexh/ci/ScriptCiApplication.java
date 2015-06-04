package alexh.ci;

import alexh.ci.resource.VersionResource;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.java8.Java8Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

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
    }
}
