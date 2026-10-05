package com.hmdp;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class LoginInterceptorTest {

    private final LoginInterceptor interceptor = new LoginInterceptor();

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void allowsPublicReadsButRequiresLoginForMutationsAndAppointments() throws Exception {
        assertRequest("GET", "/doctor/1", true, 200);
        assertRequest("GET", "/schedule/doctor/1", true, 200);
        assertRequest("POST", "/doctor", false, 401);
        assertRequest("PUT", "/schedule", false, 401);
        assertRequest("GET", "/appointment/123", false, 401);
        assertRequest("POST", "/appointment/grab/1", false, 401);
        assertRequest("DELETE", "/upload/a.jpg", false, 401);

        UserHolder.saveUser(new UserDTO());
        assertRequest("POST", "/doctor", true, 200);
        assertRequest("GET", "/appointment/123", true, 200);
    }

    private void assertRequest(String method, String path, boolean expected, int expectedStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(expected, interceptor.preHandle(request, response, new Object()), method + " " + path);
        assertEquals(expectedStatus, response.getStatus(), method + " " + path + " status");
    }
}
