package name.falgout.jeffrey.moneydance.venmoservice.rest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.http.client.utils.URIBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

public class AuthTest {
  Auth auth;

  @Before
  public void before() {
    auth = new Auth(URI.create("http://localhost:12345"));
  }

  @After
  public void after() {
    auth.close();
  }

  @Test
  public void closeStopsServer() throws IOException {
    auth.captureAuthorization();

    try {
      HttpServer.create(Auth.REDIRECT_ADDRESS, -1);
      fail("Expected exception");
    } catch (IOException e) {
    }

    auth.close();

    HttpServer s = HttpServer.create(Auth.REDIRECT_ADDRESS, -1);
    s.stop(0);
  }

  @Test
  public void completingFutureStopsServer() throws IOException {
    CompletableFuture<String> token = auth.captureAuthorization();

    try {
      HttpServer.create(Auth.REDIRECT_ADDRESS, -1);
      fail("Expected exception");
    } catch (IOException e) {
    }

    token.complete("foo");

    HttpServer s = HttpServer.create(Auth.REDIRECT_ADDRESS, -1);
    s.stop(0);
  }

  @Test
  public void capturingAccessToken() throws MalformedURLException, IOException,
    InterruptedException, ExecutionException, URISyntaxException {
    CompletableFuture<String> token = auth.captureAuthorization();
    URI auth = new URIBuilder("http://localhost").setHost(Auth.REDIRECT_ADDRESS.getHostName())
        .setPort(Auth.REDIRECT_ADDRESS.getPort())
        .addParameter(VenmoClient.ACCESS_TOKEN, "foo")
        .setPath("/")
        .build();

    checkAuthResponse(Auth.getAuthSuccess(), auth.toURL().openStream());
    assertEquals("foo", token.get());
  }

  private void checkAuthResponse(byte[] expected, InputStream in) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    byte[] buf = new byte[1024];
    int numRead;
    while ((numRead = in.read(buf)) > 0) {
      sink.write(buf, 0, numRead);
    }

    byte[] allRead = sink.toByteArray();
    assertArrayEquals(expected, allRead);
  }

  @Test
  public void capturingAccessTokenError() throws MalformedURLException, IOException,
    InterruptedException, ExecutionException, URISyntaxException {
    CompletableFuture<String> token = auth.captureAuthorization();
    URI auth = new URIBuilder("http://localhost").setHost(Auth.REDIRECT_ADDRESS.getHostName())
        .setPort(Auth.REDIRECT_ADDRESS.getPort())
        .addParameter(VenmoClient.ERROR, "ruh roh")
        .build();

    InputStream response = auth.toURL().openStream();
    try {
      token.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      assertEquals("ruh roh", cause.getMessage());
    }
    checkAuthResponse(Auth.getAuthError(), response);
  }
}
