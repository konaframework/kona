package hello;

import dev.kona.concurrency.Concurrency;
import dev.kona.drip.Drip;
import dev.kona.process.Process;
import java.util.List;
import java.util.concurrent.Callable;

public final class M9Demo {
    public static List<String> run() {
        var process = Process.run("sh", "-c", "echo process-ok");
        var mapped = Concurrency.map(List.of(1, 2), n -> n * 10);
        var ran = Concurrency.run(List.of((Callable<String>) () -> "a", (Callable<String>) () -> "b"));
        Drip drip = new Drip();
        return List.of(
            "process=" + process.successful() + " " + process.getOutput().trim(),
            "map=" + mapped,
            "run=" + ran,
            "drip=" + drip.getClass().getSimpleName()
        );
    }

    private M9Demo() {}
}
