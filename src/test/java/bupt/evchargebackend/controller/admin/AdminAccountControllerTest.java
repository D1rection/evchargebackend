package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.AdminAccountRequest;
import bupt.evchargebackend.service.admin.AdminAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * AdminAccountController 单元测试。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("管理员账号接口 - 单元测试")
class AdminAccountControllerTest {

    @Mock
    private AdminAccountService adminAccountService;

    @InjectMocks
    private AdminAccountController controller;

    @Nested
    @DisplayName("POST /admin/account/create - 注册")
    class Create {

        @Test
        @DisplayName("注册成功，返回 200 并携带 userId、userName、role")
        void shouldRegisterSuccessfully() {
            var request = new AdminAccountRequest("admin", "123456");
            Map<String, Object> svcResult = Map.of("userId", "UABC1234", "userName", "admin", "role", "ADMIN");
            when(adminAccountService.register("admin", "123456")).thenReturn(svcResult);

            Result<Map<String, Object>> result = controller.create(request);

            assertEquals(200, result.getCode());
            assertEquals("success", result.getMsg());
            assertNotNull(result.getData());
            assertEquals("UABC1234", result.getData().get("userId"));
            assertEquals("admin", result.getData().get("userName"));
            assertEquals("ADMIN", result.getData().get("role"));
        }

        @Test
        @DisplayName("用户名已存在，抛出 BusinessException(409)")
        void shouldFailWhenUsernameExists() {
            var request = new AdminAccountRequest("admin", "123456");
            when(adminAccountService.register("admin", "123456"))
                    .thenThrow(new BusinessException(409, "用户名已存在"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.create(request));

            assertEquals(409, ex.getCode());
            assertEquals("用户名已存在", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("POST /admin/account/login - 登录")
    class Login {

        @Test
        @DisplayName("登录成功，返回 200 并携带 userName 和 token")
        void shouldLoginSuccessfully() {
            var request = new AdminAccountRequest("admin", "123456");
            String token = "eyJhbGciOiJIUzI1NiJ9.xxx.yyy";
            Map<String, Object> svcResult = Map.of("userName", "admin", "token", token);
            when(adminAccountService.login("admin", "123456")).thenReturn(svcResult);

            Result<Map<String, Object>> result = controller.login(request);

            assertEquals(200, result.getCode());
            assertEquals("success", result.getMsg());
            assertNotNull(result.getData());
            assertEquals("admin", result.getData().get("userName"));
            assertEquals(token, result.getData().get("token"));
        }

        @Test
        @DisplayName("用户名或密码错误，抛出 BusinessException(401)")
        void shouldFailWhenWrongCredentials() {
            var request = new AdminAccountRequest("admin", "wrong");
            when(adminAccountService.login("admin", "wrong"))
                    .thenThrow(new BusinessException(401, "用户名或密码错误"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.login(request));

            assertEquals(401, ex.getCode());
            assertEquals("用户名或密码错误", ex.getMessage());
        }
    }
}
