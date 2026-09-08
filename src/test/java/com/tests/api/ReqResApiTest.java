package com.tests.api;

import static com.framework.api.ApiAssertions.assertJsonPathExists;
import static com.framework.api.ApiAssertions.assertMatchesSchema;
import static com.framework.api.ApiAssertions.assertResponseTimeUnder;
import static com.framework.api.ApiAssertions.assertStatusCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.framework.api.APIResponse;
import com.tests.base.BaseApiTest;
import com.tests.models.User;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.Map;
import org.testng.annotations.Test;

@Epic("API")
@Feature("User Management")
public class ReqResApiTest extends BaseApiTest {

    @Test(groups = {"smoke", "api"}, priority = 1)
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET single user should return 200 and a valid user payload")
    public void getSingleUserShouldReturnValidPayload() {
        APIResponse response = apiClient.get("/users/2");

        assertStatusCode(response, 200);
        assertResponseTimeUnder(response, 3000);
        assertJsonPathExists(response, "data");
        assertThat(response.bodyAsJson().get("data").get("email").asText())
                .as("Email should be present for user id 2")
                .isNotBlank();
    }

    @Test(groups = {"regression", "api"}, priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST create user should return 201 and echo submitted fields, matching schema")
    public void createUserShouldReturn201AndMatchSchema() {
        User newUser = User.builder().name("Mudassir QA").job("Senior QA Engineer").build();

        APIResponse response = apiClient.post("/users", newUser);

        assertStatusCode(response, 201);
        assertMatchesSchema(response, "schemas/created-user-schema.json");
        assertThat(response.jsonPathValue("name")).isEqualTo("Mudassir QA");
        assertThat(response.jsonPathValue("job")).isEqualTo("Senior QA Engineer");
    }

    @Test(groups = {"regression", "api", "negative"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("GET non-existent user should return 404")
    public void getNonExistentUserShouldReturn404() {
        APIResponse response = apiClient.get("/users/23");
        assertStatusCode(response, 404);
    }

    @Test(groups = {"regression", "api"}, priority = 4)
    @Severity(SeverityLevel.MINOR)
    @Description("GET users list should support query params (pagination)")
    public void getUsersListShouldSupportPagination() {
        APIResponse response = apiClient.get("/users", Map.of("page", "2"));

        assertStatusCode(response, 200);
        assertThat(response.bodyAsJson().get("page").asInt()).isEqualTo(2);
    }
}
