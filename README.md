# SSDC RM Notify Service
A service for making requests to Gov Notify

## Building
As part of documentation auto-generation, an NPM library is required to be installed, which can be done by running: `sudo npm install -g widdershins`

To run all the tests and build the image
```  
   mvn clean install
```

Just build the image
```
    mvn -DskipTests -DskipITs -DdockerCompose.skip
```

### Local Docker Java Healthcheck

Since docker compose health checks are run inside the container, we need a method of checking service health that can
run in our minimal alpine Java JRE images. To accomplish this, we have a small Java health check class which simply
calls a http endpoint and succeeds if it gets a success status. This is compiled into a JAR, which is then mounted into
the containers, so it can be executed by the JRE at container runtime.

#### Building Changes

If you make changes to the [HealthCheck.java](src/test/resources/java_healthcheck/HealthCheck.java), you must then
run `make rebuild-java-healthcheck` to compile and package the updated class into the jar, and commit the resulting
built changes.

## Running Locally

You can run this service dockerised with [docker dev](https://github.com/ONSdigital/ssdc-rm-docker-dev). 

If you wish to run it from an IDE to debug first make sure you've set these environment variables in the run configuration so that it uses your local backing services, then spin up docker dev as usual and stop the `notifyservice` container so it does not conflict. 

```shell
SPRING_CLOUD_GCP_PUBSUB_EMULATOR_HOST=localhost:8538
SPRING_CLOUD_GCP_PUBSUB_PROJECT_ID=project
NOTIFY_BASEURL=http://localhost:8917
```

## Endpoints
The OpenAPI v3 spec can be found here: [api.json](docs/api.json)

The API is documented in human-readable format, here: [api.md](docs/api.md)