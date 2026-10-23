package app.wire;

import org.konaframework.wire.Component;
import org.konaframework.wire.Renderless;

/** A single-file component: state, actions, and template in one class. */
public class Counter extends Component {
    public int count = 0;

    public void mount(int start) {
        count = start;
    }

    public void increment() {
        count++;
    }

    public void decrement() {
        count--;
    }

    public void add(int amount) {
        count += amount;
    }

    /** Returned to the browser; {@code await $wire.doubled()} resolves with it. */
    public int doubled() {
        return count * 2;
    }

    /** Updates state without re-rendering. */
    @Renderless
    public void clearQuietly() {
        count = 0;
    }

    @Override
    public String template() {
        return """
            <div class="card">
                <h2>Counter</h2>
                <button wire:click="decrement">-</button>
                <output>{{ count }}</output>
                <button wire:click="increment">+</button>
                <button wire:click="add(5)">+5</button>
                <button x-data x-on:click="alert(await $wire.doubled())">Doubled?</button>
            </div>
            """;
    }
}
