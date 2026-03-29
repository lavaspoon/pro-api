package devlava.youproapi;

import devlava.youproapi.config.YouproAdminProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * YouPro API 메인 애플리케이션
 */
@SpringBootApplication
@EnableConfigurationProperties(YouproAdminProperties.class)
public class YouproApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouproApiApplication.class, args);
    }
}
