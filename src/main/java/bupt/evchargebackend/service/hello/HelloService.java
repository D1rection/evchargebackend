package bupt.evchargebackend.service.hello;

import java.util.List;

/**
 * Hello 示例业务接口。
 *
 * @author Deng Chao
 * @since 2026-06-12
 */
public interface HelloService {

    /** 获取所有问候语。 */
    List<String> listGreetings();
}
