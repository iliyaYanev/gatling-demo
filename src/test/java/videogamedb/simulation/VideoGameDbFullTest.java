package videogamedb.simulation;

import static io.gatling.javaapi.core.CoreDsl.ElFileBody;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.jmesPath;
import static io.gatling.javaapi.core.CoreDsl.jsonFile;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

public class VideoGameDbFullTest extends Simulation {

    private static final int RAMP_DURATION = Integer.parseInt(System.getProperty("RAMP_DURATION", "10"));
    private static final int TEST_DURATION = Integer.parseInt(System.getProperty("TEST_DURATION", "60"));
    private static final int USER_COUNT = Integer.parseInt(System.getProperty("USERS", "5"));


    // HTTP PROTOCOL
    private final HttpProtocolBuilder httpProtocol = http
        // RUNTIME PARAMETERS
        .baseUrl("https://videogamedb.uk/api")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");


    // FEEDER FOR TEST - CSV, JSON etc.
    private static final FeederBuilder.FileBased<Object> jsonFeeder = jsonFile("data/gameJsonFile.json").random();

    // BEFORE BLOCK
    @Override
    public void before() {
        System.out.printf("Running test with %d users%n", USER_COUNT);
        System.out.printf("Ramping users over %d seconds%n", RAMP_DURATION);
        System.out.printf("Total test duration: %d seconds%n", TEST_DURATION);
    }

    // HTTP CALLS
    private static final ChainBuilder authenticate =
            exec(http("Authenticate")
                    .post("/authenticate")
                    .body(StringBody("""
                        {
                          "password": "admin",
                          "username": "admin"
                        }
                        """))
                    .check(jmesPath("token").saveAs("jwtToken")));

    private static final ChainBuilder getAllVideoGames =
            exec(http("Get all video games")
                    .get("/videogame"));

    private static final ChainBuilder createNewGame =
            feed(jsonFeeder)
                    .exec(http("Create New Game - #{name}")
                                    .post("/videogame")
                                    .header("Authorization", "Bearer #{jwtToken}")
                                    .body(ElFileBody("bodies/newGameTemplate.json")).asJson());

    private static final ChainBuilder getLastPostedGame =
            exec(http("Get Last Posted Game - #{name}")
                    .get("/videogame/#{id}")
                    .check(jmesPath("name").isEL("#{name}")));

    private static final ChainBuilder deleteLastPostedGame =
            exec(http("Delete game - #{name}")
                    .delete("/videogame/#{id}")
                    .header("Authorization", "Bearer #{jwtToken}")
                    .check(bodyString().is("Video game deleted")));

    // SCENARIO
    // 1. Get all video games
    // 2. Create a new game
    // 3. Get details of newly created game
    // 4. Delete newly created game
    private final ScenarioBuilder scn = scenario("Video game db - final simulation")
            .forever().on(
                    exec(getAllVideoGames)
                            .exec(authenticate)
                            .exec(createNewGame)
                            .exec(getLastPostedGame)
                            .exec(deleteLastPostedGame)
            );

    // LOAD SIMULATION
    {
        setUp(
                scn.injectOpen(
                        nothingFor(1),
                        rampUsers(USER_COUNT).during(RAMP_DURATION)
                ).protocols(httpProtocol)
        ).maxDuration(TEST_DURATION);
    }

    // AFTER BLOCK
    @Override
    public void after() {
        System.out.println("Stress test completed");
    }
}
