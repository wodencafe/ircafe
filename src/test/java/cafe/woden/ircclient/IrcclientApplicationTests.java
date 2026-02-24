package cafe.woden.ircclient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"ircafe.ui.enabled=false", "spring.main.headless=true"})
class IrcclientApplicationTests {
  @Test
  void contextLoads() {}
}
