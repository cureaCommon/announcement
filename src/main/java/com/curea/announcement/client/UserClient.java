package com.curea.announcement.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UserClient {

    private static final Logger logger = LoggerFactory.getLogger(UserClient.class);

    private record UserSummary(Long id, String name, String surname) {
    }

    private final RestClient restClient;

    public UserClient(@Value("${user.service-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Map<Long, String> fullNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        try {
            String idList = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            UserSummary[] response = restClient.get()
                    .uri(uri -> uri.path("/user/findUserSummaryByIdList").queryParam("idList", idList).build())
                    .retrieve()
                    .body(UserSummary[].class);
            Map<Long, String> names = new HashMap<>();
            if (response != null) {
                for (UserSummary user : response) {
                    String fullName = ((user.name() == null ? "" : user.name()) + " "
                            + (user.surname() == null ? "" : user.surname())).trim();
                    if (user.id() != null && !fullName.isEmpty()) {
                        names.put(user.id(), fullName);
                    }
                }
            }
            return names;
        } catch (RuntimeException ex) {
            logger.warn("User names could not be loaded: {}", ex.getMessage());
            return Map.of();
        }
    }
}
