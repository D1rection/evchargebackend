package bupt.evchargebackend.service.hello.impl;

import bupt.evchargebackend.service.hello.HelloService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Hello 示例业务实现。
 *
 * 演示 Service 接口 + 实现分离的架构模式，当前返回静态数据。
 *
 * @author Deng Chao
 * @since 2026-06-12
 */
@Service
public class HelloServiceImpl implements HelloService {

    @Override
    public List<String> listGreetings() {
        return List.of("Hello World!", "你好，世界！");
    }
}
