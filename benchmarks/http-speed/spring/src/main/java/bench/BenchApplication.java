package bench;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class BenchApplication {
  public static void main(String[] args) {
    SpringApplication.run(BenchApplication.class, args);
  }

  @RestController
  static class Endpoints {
    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    String ping() {
      return "pong";
    }

    @GetMapping("/json")
    Map<String, Object> json() {
      return Map.of("message", "hello", "ok", true, "n", 42);
    }
  }
}
