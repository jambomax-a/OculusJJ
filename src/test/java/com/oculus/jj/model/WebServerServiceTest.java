package com.oculus.jj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.oculus.jj.config.AppPreferences;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WebServerServiceTest {

    @Test
    void wadoEndpointRespondsWithBadRequestWhenRequiredQueryParametersAreMissing() throws Exception {
        WebServerService service = WebServerService.getInstance();
        boolean oldHttps = AppPreferences.getBool("webserver.httpsEnabled", false);
        boolean oldIdentifiedOnly = AppPreferences.getBool("webserver.wadoIdentifiedOnly", true);
        boolean oldAuthentication = AppPreferences.getBool("webserver.requireAuth", false);
        boolean oldWadoEnabled = AppPreferences.isWADOEnabled();
        service.stop();
        try {
            AppPreferences.setBool("webserver.httpsEnabled", false);
            AppPreferences.setBool("webserver.wadoIdentifiedOnly", false);
            AppPreferences.setBool("webserver.requireAuth", false);
            AppPreferences.setWADOEnabled(true);
            int port = service.startForTest();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/wado"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode());
        } finally {
            service.stop();
            AppPreferences.setBool("webserver.httpsEnabled", oldHttps);
            AppPreferences.setBool("webserver.wadoIdentifiedOnly", oldIdentifiedOnly);
            AppPreferences.setBool("webserver.requireAuth", oldAuthentication);
            AppPreferences.setWADOEnabled(oldWadoEnabled);
        }
    }
}
