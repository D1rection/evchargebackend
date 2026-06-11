package bupt.evchargebackend.controller;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.hello.HelloService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目启动健康检查 & 全链路演示。
 *
 * 展示 Controller → Service（接口+实现） 的完整调用链。
 *
 * @author Deng Chao
 * @since 2026-06-12
 */
@RestController
public class HelloController {

    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    public Result<List<String>> hello() {
        List<String> greetings = helloService.listGreetings();
        return Result.success(greetings);
    }
}
