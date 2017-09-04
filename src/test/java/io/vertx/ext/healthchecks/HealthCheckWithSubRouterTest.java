package io.vertx.ext.healthchecks;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static io.vertx.ext.healthchecks.Assertions.assertThatCheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

/**
 * Same as {@link HealthCheckTest} but using a health check handler mounter in a sub-router.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HealthCheckWithSubRouterTest extends HealthCheckTestBase {

  @Test
  public void testEmptyChecks() {
    RestAssured.get(prefix() + route())
      .then()
      .statusCode(204);
  }

  @Test
  public void testWithEmptySuccessfulCheck() {
    handler.register("foo", Future::complete);

    JsonObject json = getWithPrefix(200);
    assertThatCheck(json).hasOutcomeUp()
      .hasChildren(1)
      .hasAndGetCheck("foo").isUp().done();
  }

  @Test
  public void testWithEmptyFailedCheck() {
    handler.register("foo", future -> future.fail("BOOM"));

    JsonObject json = getWithPrefix(503);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown().hasData("cause", "BOOM").done();
  }

  @Test
  public void testWithExplicitSuccessfulCheck() {
    handler.register("bar", future -> future.complete(Status.OK()));

    JsonObject json = getWithPrefix(200);
    assertThatCheck(json).hasOutcomeUp()
      .hasChildren(1)
      .hasAndGetCheck("bar").isUp().done();
  }

  @Test
  public void testWithExplicitFailedCheck() {
    handler.register("bar", future -> future.complete(Status.KO()));

    JsonObject json = getWithPrefix(503);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("bar").isDown().done();
  }

  @Test
  public void testWithExplicitSuccessfulCheckAndData() {
    handler.register("bar", future -> future.complete(Status.OK(new JsonObject()
      .put("availableMemory", "2Mb"))));

    JsonObject json = getWithPrefix(200);
    assertThat(json.getMap())
      .contains(entry("outcome", "UP"));

    JsonArray array = json.getJsonArray("checks");
    assertThat(array.getList()).hasSize(1);
    JsonObject check = array.getJsonObject(0);
    assertThat(check.getMap()).hasSize(3)
      .contains(entry("status", "UP"), entry("id", "bar"),
        entry("data", ImmutableMap.of("availableMemory", "2Mb")));
  }

  @Test
  public void testWithExplicitFailedCheckAndData() {
    handler.register("bar", future -> future.complete(Status.KO(new JsonObject()
      .put("availableMemory", "2Mb"))));

    JsonObject json = getWithPrefix(503);
    assertThat(json.getMap())
      .contains(entry("outcome", "DOWN"));

    JsonArray array = json.getJsonArray("checks");
    assertThat(array.getList()).hasSize(1);
    JsonObject check = array.getJsonObject(0);
    assertThat(check.getMap()).hasSize(3)
      .contains(entry("status", "DOWN"), entry("id", "bar"),
        entry("data", ImmutableMap.of("availableMemory", "2Mb")));
  }

  @Test
  public void testWithOneSuccessfulAndOneFailedCheck() {
    handler
      .register("s", future -> future.complete(Status.OK()))
      .register("f", future -> future.complete(Status.KO()));

    JsonObject json = getWithPrefix(503);
    assertThatCheck(json).hasOutcomeDown().hasChildren(2);
  }

  @Test
  public void testWithTwoFailedChecks() {
    handler
      .register("f1", future -> future.complete(Status.KO()))
      .register("f2", future -> future.complete(Status.KO()));

    JsonObject json = getWithPrefix(503);
    assertThatCheck(json).hasOutcomeDown().hasChildren(2);
  }

  @Test
  public void testWithTwoSucceededChecks() {
    handler
      .register("s1", future -> future.complete(Status.OK()))
      .register("s2", future -> future.complete(Status.OK()));

    JsonObject json = getWithPrefix(200);
    assertThatCheck(json).hasOutcomeUp().hasChildren(2);
  }

  @Test
  public void testWithNestedCompositeThatSucceed() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.OK()));

    JsonObject json = getWithPrefix(200);

    assertThatCheck(json)
      .isUp()
      .hasOutcomeUp()
      .hasChildren(2)
      .hasAndGetCheck("sub").hasStatusUp()
      .hasAndGetCheck("B").hasStatusUp().done()
      .hasAndGetCheck("A").hasStatusUp().done()
      .done()

      .hasAndGetCheck("sub2").hasStatusUp()
      .hasAndGetCheck("c").hasStatusUp()
      .hasAndGetCheck("C1").hasStatusUp().done()
      .hasAndGetCheck("C2").hasStatusUp().done()
      .done()

      .done();
  }

  @Test
  public void testWithNestedCompositeThatFailed() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = getWithPrefix(503);

    assertThatCheck(json)
      .isDown()
      .hasOutcomeDown()
      .hasChildren(2)
      .hasAndGetCheck("sub").hasStatusUp()
      .hasAndGetCheck("B").hasStatusUp().done()
      .hasAndGetCheck("A").hasStatusUp().done()
      .done()

      .hasAndGetCheck("sub2").hasStatusDown()
      .hasAndGetCheck("c").hasStatusDown()
      .hasAndGetCheck("C1").hasStatusUp().done()
      .hasAndGetCheck("C2").hasStatusDown().done()
      .done()

      .done();
  }


  @Test
  public void testRetrievingAComposite() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = getWithPrefix("sub", 200);

    assertThatCheck(json)
      .isUp()
      .hasOutcomeUp()
      .hasChildren(2)
      .hasAndGetCheck("B").hasStatusUp().done()
      .hasAndGetCheck("A").hasStatusUp().done()
      .done();

    json = getWithPrefix("sub2", 503);

    assertThatCheck(json)
      .hasAndGetCheck("c").hasStatusDown()
      .hasAndGetCheck("C1").hasStatusUp().done()
      .hasAndGetCheck("C2").hasStatusDown().done()
      .done();

    json = getWithPrefix("sub2/c", 503);

    assertThatCheck(json)
      .hasAndGetCheck("C1").hasStatusUp().done()
      .hasAndGetCheck("C2").hasStatusDown().done();
  }

  @Test
  public void testRetrievingALeaf() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = getWithPrefix("sub/A", 200);

    assertThatCheck(json)
      .isUp()
      .hasStatusUp()
      .hasOutcomeUp()
      .done();

    json = getWithPrefix("sub2/c/C2", 503);

    assertThatCheck(json)
      .isDown()
      .hasOutcomeDown()
      .hasStatusDown()
      .done();

    // Not found
    getWithPrefix("missing", 404);
    // Illegal
    getWithPrefix("sub2/c/C1/foo", 400);
  }

  @Test
  public void testACheckThatTimeOut() {
    handler.register("foo", future -> {
      // Bad boy !
    });

    JsonObject json = getWithPrefix(500);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown()
      .hasData("procedure-execution-failure", true)
      .hasData("cause", "Timeout").done();
  }

  @Test
  public void testACheckThatFail() {
    handler.register("foo", future -> {
      throw new IllegalArgumentException("BOOM");
    });

    JsonObject json = getWithPrefix(500);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown()
      .hasData("cause", "BOOM")
      .hasData("procedure-execution-failure", true)
      .done();
  }


  @Test
  public void testACheckThatTimeOutButSucceed() {
    handler.register("foo", future -> vertx.setTimer(2000, l -> future.complete()));

    JsonObject json = getWithPrefix(500);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown().hasData("cause", "Timeout").done();
  }

  @Test
  public void testACheckThatTimeOutButFailed() {
    handler.register("foo", future -> vertx.setTimer(2000, l -> future.fail("BOOM")));

    JsonObject json = getWithPrefix(500);
    assertThatCheck(json).hasOutcomeDown()
      .hasChildren(1)
      .hasAndGetCheck("foo").isDown().hasData("cause", "Timeout").done();
  }

  @Test
  public void testRemovingComposite() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = getWithPrefix(503);

    assertThatCheck(json)
      .isDown()
      .hasOutcomeDown();

    handler.unregister("sub2/c");

    json = getWithPrefix(200);

    assertThatCheck(json)
      .isUp()
      .hasOutcomeUp();
  }

  @Test
  public void testRemovingLeaf() {
    handler
      .register("sub/A", future -> future.complete(Status.OK()))
      .register("sub/B", future -> future.complete(Status.OK()))
      .register("sub2/c/C1", future -> future.complete(Status.OK()))
      .register("sub2/c/C2", future -> future.complete(Status.KO()));

    JsonObject json = getWithPrefix(503);

    assertThatCheck(json)
      .isDown()
      .hasOutcomeDown();

    handler.unregister("sub2/c/C2");

    json = getWithPrefix(200);

    assertThatCheck(json)
      .isUp()
      .hasOutcomeUp();
  }

}
