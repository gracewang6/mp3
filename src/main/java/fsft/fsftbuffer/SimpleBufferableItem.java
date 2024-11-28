package fsft.fsftbuffer;
import org.sqids.Sqids;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimpleBufferableItem implements Bufferable {
    private final String id;

    public SimpleBufferableItem(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Bufferable)) {
            return false;
        }
        Bufferable other = (Bufferable) obj;
        return this.id.equals(other.id());
    }
}
